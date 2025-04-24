package searchengine.services;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import searchengine.config.SiteFromConfig;
import searchengine.config.SitesList;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.util.SiteConverter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static searchengine.model.Site.Status.FAILED;
import static searchengine.model.Site.Status.INDEXED;

@Slf4j
@Service
public class WebLinkCrawlerService {

    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final SitesList sitesList;
    private final SiteConverter converter;
    private final LemmaService lemmaService;
    private final ForkJoinPool forkJoinPool = new ForkJoinPool();
    @Value(value = "${jsoup.user-agent}")
    private String userAgent;
    @Value(value = "${jsoup.referrer}")
    private String referrer;
    private AtomicBoolean stopped = new AtomicBoolean(true);
    private ConcurrentHashMap<String, AtomicInteger> counterLinks;
    private ConcurrentHashMap<String, Boolean> addedLink;

    public WebLinkCrawlerService(PageRepository pageRepository,
                                 SiteRepository siteRepository,
                                 SitesList sitesList,
                                 SiteConverter converter, LemmaService lemmaService) {
        this.pageRepository = pageRepository;
        this.siteRepository = siteRepository;
        this.sitesList = sitesList;
        this.converter = converter;
        this.lemmaService = lemmaService;
    }

    public void startIndexing() {
        stopped.set(false);
        counterLinks = new ConcurrentHashMap<>();
        addedLink = new ConcurrentHashMap<>();
        long before = System.currentTimeMillis();
        List<SiteFromConfig> fromConfigFile = sitesList.getSites();
        List<Site> list;
        List<LinkCrawler> tasks = new ArrayList<>();


        siteRepository.deleteAll();// удалить все потому что запускается новая индексация

        list = converter.convert(fromConfigFile);
        list.forEach(site ->
                tasks.add(new LinkCrawler(site, site.getUrl())));


        log.debug("Прочитали все сайты из конфиг файла и сохранили в List");
        siteRepository.saveAll(list);
        log.info("Сохранили все сайты из конфиг файла в БД");

        log.info("Проходим по каждому сайту и запускаем его индексацию");
        tasks.forEach(forkJoinPool::invoke);

        log.info("Закончили индексацию каждого сайта");
        siteRepository.saveAll(list);
        log.info("Статус сайтов обновлен в БД");
        long after = System.currentTimeMillis();
        stopped.set(true);
        log.info("Время выполнения  = {}, сек", (after - before) / 1000);
    }


    public boolean getStopped() {
        return stopped.get();
    }

    public void stopIndexing() {
        stopped.set(true);
    }


    private class LinkCrawler extends RecursiveAction {
        private final Site site;
        private final String path;

        public LinkCrawler(Site site, String path) {
            this.site = site;
            this.path = path;
        }

        private static boolean checkLinkOnCorrectFormat(String link) {
            String regex = "^(https?:\\/\\/)?([\\da-z\\.-]+)\\.([a-z\\.]{2,6})([\\/\\w \\.-]*)*\\/?(?!#.*)$";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(link);
            if (!matcher.matches()) {
                return false;
            }
            return (checkExtension(link));
        }

        private static boolean checkBaseUrl(String link, String baseUrl) {
            if (!link.isBlank()) {
                return link.toLowerCase().startsWith(baseUrl);
            }
            return false;
        }

        private static boolean checkExtension(String link) {
            String[] excludeFormats = {".jpg", ".jpeg", ".png", ".gif",
                    ".bmp", ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".zip", ".rar"};

            for (String extension : excludeFormats) {
                if (link.toLowerCase().endsWith(extension)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        protected void compute() {

            if (pageRepository.existsByPath(path)) {
                return;
            }

            List<LinkCrawler> taskList = new ArrayList<>();
            if (stopped.get() && !site.getStatus().equals(FAILED)) {
                site.setStatus(FAILED);
                site.setLastError("Индексация остановлена пользователем");
                log.info("Сохранил Failed");
                return;
            }
            final Page page;
            try {
                String tempUrl = path;
                if (site.getUrl().equals(path)) {
                    tempUrl = "/";
                }
                Connection connection = Jsoup.connect(site.getUrl() + tempUrl);

                Document document = connection
                        .userAgent(userAgent)
                        .referrer(referrer)
                        .timeout(0)
                        .get();

                int statusCode = connection.response().statusCode();
                String html = document.html();
                page = new Page();
                page.setSite(site);
                page.setCode(statusCode);
                page.setPath(tempUrl);
                page.setContent(html);
                Page savedPage = pageRepository.save(page);
                processPageLemmas(site, savedPage);

                if (!stopped.get()) {
                    getLinksForPage(document)
                            .forEach(link -> {
                                taskList.add(new LinkCrawler(site, link));
                                counterLinks.computeIfAbsent(site.getUrl(), k -> new AtomicInteger(0)).incrementAndGet();
                            });

                }
                invokeAll(taskList);
                AtomicInteger atomicInteger = counterLinks.get(site.getUrl());
                if (atomicInteger.decrementAndGet() == 0) {
                    site.setStatus(INDEXED);
                    siteRepository.save(site);
                    log.info("Сайт {} проиндексирован, статус обновлен на {}", site.getUrl(), site.getStatus());

                }
            } catch (IOException e) {

                log.error("Ошибка при обработке страницы: {}", e.getMessage());
                site.setLastError(e.getMessage());
                site.setStatus(Site.Status.FAILED);

            }
        }

        private void processPageLemmas(Site site, Page savedPage) {
            lemmaService.processPageLemmasAndIndex(site, savedPage);
        }

        private List<String> getLinksForPage(Document document) {
            Elements links = document.select("a");
            List<String> linksList = new ArrayList<>();
            for (Element link : links) {
                String absLink = link.attr("abs:href"); // Получаем абсолютный URL
                // Проверяем, что ссылка принадлежит текущему сайту и еще не посещена
                String relativeLink = link.attr("href");

                if (checkLinkOnCorrectFormat(absLink) && checkBaseUrl(absLink, link.baseUri())) {
                    String normalizedLink = linkNormalization(relativeLink);
                    if (addedLink.putIfAbsent(normalizedLink, true) == null) {
                        siteRepository.updateStatusTime(site.getId(), LocalDateTime.now());
                        linksList.add(normalizedLink);
                    }
                }
            }
            return linksList;
        }

        private String linkNormalization(String relativeLink) {
            String baseUrl = site.getUrl();
            String s = baseUrl.replaceAll("www.", "");
            String link;
            if (relativeLink.isBlank()) {
                link = "/";
            } else if (relativeLink.startsWith(baseUrl) || relativeLink.startsWith(s)) {
                link = relativeLink.substring(baseUrl.length());
                if (!link.startsWith("/")) {
                    link = "/".concat(link);
                }
            } else {
                link = relativeLink;
            }
            return link;

        }
    }
}

