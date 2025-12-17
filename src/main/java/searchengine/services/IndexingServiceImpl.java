package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.model.*;
import searchengine.repository.*;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.regex.Pattern;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final MorphologyService morphologyService;
    private final SitesList sitesList;
    
    @Value("${indexing-settings.user-agent}")
    private String userAgent;
    
    private volatile boolean isIndexing = false;
    private ForkJoinPool forkJoinPool;
    private final Set<String> indexedUrls = ConcurrentHashMap.newKeySet();
    private final Pattern fileExtensionPattern = Pattern.compile(".*\\.(pdf|zip|jpg|jpeg|png|gif|doc|docx|xls|xlsx|ppt|pptx)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTTP_SCHEME_PATTERN = Pattern.compile("(?i)^https?://.+");
    private static final int MAX_DEPTH = 10;

    @Override
    public IndexingResponse startIndexing() {
        if (isIndexing) {
            IndexingResponse response = new IndexingResponse();
            response.setResult(false);
            response.setError("Индексация уже запущена");
            return response;
        }
        
        isIndexing = true;
        forkJoinPool = new ForkJoinPool();
        
        forkJoinPool.submit(() -> {
            try {
                for (Site configSite : sitesList.getSites()) {
                    if (!isIndexing) {
                        break;
                    }
                    indexSite(configSite);
                }
            } finally {
                isIndexing = false;
            }
        });
        
        IndexingResponse response = new IndexingResponse();
        response.setResult(true);
        return response;
    }

    @Override
    public IndexingResponse stopIndexing() {
        IndexingResponse response = new IndexingResponse();
        
        if (!isIndexing) {
            List<searchengine.model.Site> indexingSites = siteRepository.findAll().stream()
                    .filter(s -> s.getStatus() == searchengine.model.Site.StatusType.INDEXING)
                    .collect(java.util.stream.Collectors.toList());
            
            if (indexingSites.isEmpty()) {
                response.setResult(false);
                response.setError("Индексация не запущена");
                return response;
            } else {
                isIndexing = false;
                for (searchengine.model.Site site : indexingSites) {
                    site.setStatus(searchengine.model.Site.StatusType.FAILED);
                    site.setLastError("Индексация остановлена пользователем");
                    site.setStatusTime(LocalDateTime.now());
                    siteRepository.save(site);
                }
                response.setResult(true);
                return response;
            }
        }
        
        isIndexing = false;
        if (forkJoinPool != null) {
            forkJoinPool.shutdownNow();
        }
        
        List<searchengine.model.Site> indexingSites = siteRepository.findAll().stream()
                .filter(s -> s.getStatus() == searchengine.model.Site.StatusType.INDEXING)
                .collect(java.util.stream.Collectors.toList());
        
        for (searchengine.model.Site site : indexingSites) {
            site.setStatus(searchengine.model.Site.StatusType.FAILED);
            site.setLastError("Индексация остановлена пользователем");
            site.setStatusTime(LocalDateTime.now());
            siteRepository.save(site);
        }
        
        response.setResult(true);
        return response;
    }

    @Override
    public IndexingResponse indexPage(String url) {
        IndexingResponse response = new IndexingResponse();
        
        try {
            IndexPageRequest req = normalizeAndValidateIndexPageRequest(url);
            if (!req.isOk()) {
                response.setResult(false);
                response.setError(req.getError());
                return response;
            }

            searchengine.model.Site site = getOrCreateSiteForSinglePage(req.getSiteRootUrl(), req.getSiteName());
            indexSinglePage(site, req.getPath(), req.getFullUrl());

            response.setResult(true);
        } catch (Exception e) {
            response.setResult(false);
            response.setError("Ошибка индексации: " + e.getMessage());
        }
        
        return response;
    }

    private searchengine.model.Site getOrCreateSiteForSinglePage(String siteRootUrl, String siteName) {
        return siteRepository.findByUrl(siteRootUrl)
                .orElseGet(() -> {
                    searchengine.model.Site site = new searchengine.model.Site();
                    site.setUrl(siteRootUrl);
                    site.setName(siteName);
                    site.setStatus(searchengine.model.Site.StatusType.INDEXED);
                    site.setStatusTime(LocalDateTime.now());
                    site.setLastError(null);
                    return siteRepository.save(site);
                });
    }

    private IndexPageRequest normalizeAndValidateIndexPageRequest(String rawUrl) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            return IndexPageRequest.error("Задан пустой адрес страницы");
        }

        String normalizedUrl = rawUrl.trim();
        if (!HTTP_SCHEME_PATTERN.matcher(normalizedUrl).matches()) {
            normalizedUrl = "https://" + normalizedUrl;
        }

        final URI uri;
        try {
            uri = URI.create(normalizedUrl);
        } catch (Exception e) {
            return IndexPageRequest.error("Некорректный URL");
        }

        if (uri.getScheme() == null || uri.getHost() == null) {
            return IndexPageRequest.error("Некорректный URL");
        }

        String path = uri.getPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        }

        // Check that the page belongs to one of the configured sites (spec requirement)
        final String finalNormalizedUrl = normalizedUrl;
        Site matchedSite = sitesList.getSites().stream()
                .filter(s -> finalNormalizedUrl.startsWith(s.getUrl()))
                // Prefer the longest prefix match in case of nested configs
                .max(Comparator.comparingInt(s -> s.getUrl().length()))
                .orElse(null);

        if (matchedSite == null) {
            return IndexPageRequest.error("Данная страница находится за пределами сайтов, указанных в конфигурационном файле");
        }

        return IndexPageRequest.ok(
                matchedSite.getUrl(),
                matchedSite.getName(),
                path,
                finalNormalizedUrl
        );
    }

    private static class IndexPageRequest {
        private final boolean ok;
        private final String error;
        private final String siteRootUrl;
        private final String siteName;
        private final String path;
        private final String fullUrl;

        private IndexPageRequest(boolean ok, String error, String siteRootUrl, String siteName, String path, String fullUrl) {
            this.ok = ok;
            this.error = error;
            this.siteRootUrl = siteRootUrl;
            this.siteName = siteName;
            this.path = path;
            this.fullUrl = fullUrl;
        }

        static IndexPageRequest ok(String siteRootUrl, String siteName, String path, String fullUrl) {
            return new IndexPageRequest(true, null, siteRootUrl, siteName, path, fullUrl);
        }

        static IndexPageRequest error(String error) {
            return new IndexPageRequest(false, error, null, null, null, null);
        }

        boolean isOk() {
            return ok;
        }

        String getError() {
            return error;
        }

        String getSiteRootUrl() {
            return siteRootUrl;
        }

        String getSiteName() {
            return siteName;
        }

        String getPath() {
            return path;
        }

        String getFullUrl() {
            return fullUrl;
        }
    }

    /**
     * Index exactly one page (add/update), without requiring global indexing to be running
     * and without crawling links.
     */
    private void indexSinglePage(searchengine.model.Site site, String path, String fullUrl) throws IOException {
        String normalizedPath = normalizePath(path);
        if (fileExtensionPattern.matcher(normalizedPath).matches()) {
            return;
        }

        Connection.Response response = Jsoup.connect(fullUrl)
                .userAgent(userAgent)
                .referrer("http://www.google.com")
                .timeout(10000)
                .ignoreHttpErrors(true)
                .execute();

        String html = response.body();
        int statusCode = response.statusCode();
        Document doc = Jsoup.parse(html, fullUrl);
        String contentText = doc.body() != null ? doc.body().text() : "";

        Optional<Page> existingPageOpt = pageRepository.findBySiteAndPath(site, normalizedPath);
        Page page;
        if (existingPageOpt.isPresent()) {
            page = existingPageOpt.get();
            deletePageData(page);
        } else {
            page = new Page();
            page.setSite(site);
            page.setPath(normalizedPath);
        }

        page.setCode(statusCode);
        page.setContent(html);
        page = pageRepository.save(page);
        touchSiteStatusTime(site);

        Map<String, Integer> lemmas = morphologyService.getLemmas(contentText);
        indexLemmas(site, page, lemmas);
    }

    @Override
    public boolean isIndexing() {
        return isIndexing;
    }

    private void indexSite(Site configSite) {
        searchengine.model.Site site = recreateSiteForIndexing(configSite);
        
        indexedUrls.clear();
        
        boolean success = true;
        try {
            indexPage(site, "/", configSite.getUrl(), 0);
        } catch (Exception e) {
            success = false;
            markSiteFailed(site, "Ошибка индексации: " + e.getMessage());
        }

        if (!isIndexing) {
            // stop requested
            if (site.getStatus() == searchengine.model.Site.StatusType.INDEXING) {
                markSiteFailed(site, "Индексация остановлена пользователем");
            }
            return;
        }

        if (success && site.getStatus() == searchengine.model.Site.StatusType.INDEXING) {
            site.setStatus(searchengine.model.Site.StatusType.INDEXED);
            site.setLastError(null);
            touchSiteStatusTime(site);
            siteRepository.save(site);
        }
    }

    private searchengine.model.Site recreateSiteForIndexing(Site configSite) {
        siteRepository.findByUrl(configSite.getUrl()).ifPresent(existing -> {
            deleteSiteData(existing);
            siteRepository.delete(Objects.requireNonNull(existing));
        });

        searchengine.model.Site site = new searchengine.model.Site();
        site.setUrl(configSite.getUrl());
        site.setName(configSite.getName());
        site.setStatus(searchengine.model.Site.StatusType.INDEXING);
        site.setStatusTime(LocalDateTime.now());
        site.setLastError(null);
        return siteRepository.save(site);
    }

    private void deleteSiteData(searchengine.model.Site site) {
        List<Page> sitePages = pageRepository.findAllBySite(site);
        if (!sitePages.isEmpty()) {
            indexRepository.deleteAllByPageIn(sitePages);
            pageRepository.deleteAll(sitePages);
        }

        List<Lemma> siteLemmas = lemmaRepository.findAllBySite(site);
        if (!siteLemmas.isEmpty()) {
            lemmaRepository.deleteAll(siteLemmas);
        }
    }

    private void indexPage(searchengine.model.Site site, String path, String fullUrl, int depth) {
        if (!isIndexing || depth > MAX_DEPTH) {
            return;
        }
        
        String normalizedPath = normalizePath(path);
        String urlKey = site.getUrl() + normalizedPath;
        if (indexedUrls.contains(urlKey)) {
            return;
        }
        
        if (fileExtensionPattern.matcher(normalizedPath).matches()) {
            return;
        }
        
        try {
            Connection.Response response = Jsoup.connect(fullUrl)
                    .userAgent(userAgent)
                    .referrer("http://www.google.com")
                    .timeout(10000)
                    .ignoreHttpErrors(true)
                    .execute();

            String html = response.body();
            int statusCode = response.statusCode();
            Document doc = Jsoup.parse(html, fullUrl);
            String content = doc.body() != null ? doc.body().text() : "";
            
            Optional<Page> existingPageOpt = pageRepository.findBySiteAndPath(site, normalizedPath);
            Page page;
            if (existingPageOpt.isPresent()) {
                page = existingPageOpt.get();
                deletePageData(page);
            } else {
                page = new Page();
                page.setSite(site);
                page.setPath(normalizedPath);
            }
            
            page.setCode(statusCode);
            page.setContent(html);
            page = pageRepository.save(page);
            touchSiteStatusTime(site);
            
            indexedUrls.add(site.getUrl() + normalizedPath);
            
            Map<String, Integer> lemmas = morphologyService.getLemmas(content);
            indexLemmas(site, page, lemmas);
            
            if (statusCode >= 200 && statusCode < 400) {
                Elements links = doc.select("a[href]");
                for (Element link : links) {
                    if (!isIndexing) {
                        break;
                    }
                    String href = link.attr("abs:href");
                    if (href.startsWith(site.getUrl()) && !href.contains("#") && !href.contains("?")) {
                        try {
                            URL linkUrl = new URL(href);
                            String linkPath = linkUrl.getPath();
                            if (linkPath.isEmpty()) {
                                linkPath = "/";
                            }
                            String normalizedLinkPath = normalizePath(linkPath);
                            if (!indexedUrls.contains(site.getUrl() + normalizedLinkPath)) {
                                indexPage(site, normalizedLinkPath, href, depth + 1);
                            }
                        } catch (Exception e) {
                            // skip invalid URLs
                        }
                    }
                }
            }
        } catch (IOException e) {
            // Non-fatal for the whole site: just record the error and continue.
            // If the root page fails, the caller will mark the whole site as FAILED.
            site.setLastError("Ошибка загрузки страницы: " + e.getMessage());
            touchSiteStatusTime(site);
            siteRepository.save(site);
        }
    }

    private void touchSiteStatusTime(searchengine.model.Site site) {
        site.setStatusTime(LocalDateTime.now());
        siteRepository.save(site);
    }

    private void markSiteFailed(searchengine.model.Site site, String error) {
        site.setStatus(searchengine.model.Site.StatusType.FAILED);
        site.setLastError(error);
        touchSiteStatusTime(site);
    }

    private String normalizePath(String path) {
        if (path.isEmpty() || path.equals("/")) {
            return "/";
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        if (path.endsWith("/") && path.length() > 1) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    private void deletePageData(Page page) {
        List<Index> indexes = indexRepository.findByPage(page);
        for (Index index : indexes) {
            Lemma lemma = index.getLemma();
            lemma.setFrequency(lemma.getFrequency() - 1);
            if (lemma.getFrequency() <= 0) {
                lemmaRepository.delete(lemma);
            } else {
                lemmaRepository.save(lemma);
            }
        }
        indexRepository.deleteAll(Objects.requireNonNull(indexes));
    }

    private void indexLemmas(searchengine.model.Site site, Page page, Map<String, Integer> lemmas) {
        int totalLemmas = lemmas.values().stream().mapToInt(Integer::intValue).sum();
        
        for (Map.Entry<String, Integer> entry : lemmas.entrySet()) {
            String lemmaText = entry.getKey();
            int count = entry.getValue();
            
            Lemma lemma = lemmaRepository.findBySiteAndLemma(site, lemmaText)
                    .orElseGet(() -> {
                        Lemma newLemma = new Lemma();
                        newLemma.setSite(site);
                        newLemma.setLemma(lemmaText);
                        newLemma.setFrequency(0);
                        return newLemma;
                    });
            
            lemma.setFrequency(lemma.getFrequency() + 1);
            lemma = lemmaRepository.save(lemma);
            
            Index index = new Index();
            index.setPage(page);
            index.setLemma(lemma);
            index.setRank((float) count / totalLemmas);
            indexRepository.save(index);
        }
    }
}

