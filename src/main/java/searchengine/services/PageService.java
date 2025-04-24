package searchengine.services;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import searchengine.repository.PageRepository;
import searchengine.exception.InvalidUrlException;
import searchengine.exception.OutOfBoundsUrlException;
import searchengine.exception.PageException;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repository.SiteRepository;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

@Slf4j
@Service
public class PageService {
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final LemmaService lemmaService;

    public PageService(PageRepository pageRepository, SiteRepository siteRepository, LemmaService lemmaService) {
        this.pageRepository = pageRepository;
        this.siteRepository = siteRepository;
        this.lemmaService = lemmaService;
    }


    public void processPage(String url) throws PageException {
        if (url == null || url.isBlank()) {
            throw new InvalidUrlException("Введите адрес страницы");
        }
        String[] urlAndPath = splitInputOnBaseUrlAndPath(url);
        String baseUrl = urlAndPath[0];
        Site site = siteRepository.findSiteByUrl(baseUrl)
                .orElseThrow(() -> new OutOfBoundsUrlException("Данная страница находится за пределами сайтов," +
                                                               " указанных в конфигурационном файле"));
        Optional<Page> pageFromDb = pageRepository.findByPathAndSite(urlAndPath[1], site);
        Page updatedPage = updateOrSavePage(pageFromDb.orElseGet(() -> createNewPage(site, urlAndPath[1])));
        lemmaService.processPageLemmasAndIndex(site, updatedPage);
    }

    private Page updateOrSavePage(Page page) {
        try {
            Connection connect = Jsoup.connect(page.getSite().getUrl().concat(page.getPath()));
            Document document = connect.get();
            int statusCode = connect.response().statusCode();
            String html = document.html();
            page.setCode(statusCode);
            page.setContent(html);
        } catch (IOException e) {
            log.error("Ошибка подключения");
            throw new RuntimeException(e);
        }
        log.info("Сохранили обновленную страницу");
        return pageRepository.save(page);
    }

    private Page createNewPage(Site site, String pagePath) {
        log.info("Создали новую страницу");
        Page page = new Page();
        page.setSite(site);
        page.setPath(pagePath);
        return page;
    }


    private String[] splitInputOnBaseUrlAndPath(String url) throws InvalidUrlException {
        try {
            log.info("Разделяем входящую строку на базовый URI и Path");
            URI uri = new URI(url);
            String baseUrl = uri.getScheme() + "://" + uri.getHost();
            String path = uri.getPath() + (uri.getQuery() != null ? "?" + uri.getQuery() : "");
            return new String[]{baseUrl, path.isEmpty() ? "/" : path};
        } catch (URISyntaxException e) {
            throw new InvalidUrlException("Некорректный URL: " + e.getMessage());
        }
    }
}
