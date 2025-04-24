package searchengine.services;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.Site;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Service
@Qualifier(value = "extended")
public class ExtendedStatisticServiceImpl implements StatisticsService{

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final WebLinkCrawlerService crawlerService;

    public ExtendedStatisticServiceImpl(SiteRepository siteRepository,
                                        PageRepository pageRepository,
                                        LemmaRepository lemmaRepository, WebLinkCrawlerService crawlerService) {
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.lemmaRepository = lemmaRepository;
        this.crawlerService = crawlerService;
    }

    @Override
    public StatisticsResponse getStatistics() {
        TotalStatistics totalStatistics = getTotalStatistics();
        List<DetailedStatisticsItem> detailedStatisticsItems = getDetailedStatistics();
        StatisticsData statisticsData = new StatisticsData();
        statisticsData.setTotal(totalStatistics);
        statisticsData.setDetailed(detailedStatisticsItems);
        StatisticsResponse statisticsResponse = new StatisticsResponse();
        statisticsResponse.setResult(true);
        statisticsResponse.setStatistics(statisticsData);
        return statisticsResponse;
    }

    private List<DetailedStatisticsItem> getDetailedStatistics() {
        List<DetailedStatisticsItem> result = new ArrayList<>();
        DetailedStatisticsItem detailedStatisticsItem;

        List<Site> allSitesFromDb = siteRepository.findAll();
        for (Site site : allSitesFromDb) {
            int siteId = site.getId();
            int countPagesBySite = pageRepository.countBySiteId(siteId);
            int countUniqueLemmasBySite = lemmaRepository.countUniqueLemmasBySite(siteId);
            detailedStatisticsItem = new DetailedStatisticsItem();
            detailedStatisticsItem.setUrl(site.getUrl());
            detailedStatisticsItem.setName(site.getName());
            detailedStatisticsItem.setStatus(site.getStatus().name());
            detailedStatisticsItem.setStatusTime(site.getStatusTime().getSecond());
            detailedStatisticsItem.setPages(countPagesBySite);
            detailedStatisticsItem.setLemmas(countUniqueLemmasBySite);
            result.add(detailedStatisticsItem);
        }

        return result;
    }

    private TotalStatistics getTotalStatistics() {
        int countSites = siteRepository.countSites();
        int countPages = pageRepository.countPages();
        int allUniqueLemmas = lemmaRepository.countAllUniqueLemmas();
        TotalStatistics totalStatistics = new TotalStatistics();
        totalStatistics.setSites(countSites);
        totalStatistics.setPages(countPages);
        totalStatistics.setLemmas(allUniqueLemmas);
        totalStatistics.setIndexing(crawlerService.getStopped());
        return totalStatistics;
    }
}
