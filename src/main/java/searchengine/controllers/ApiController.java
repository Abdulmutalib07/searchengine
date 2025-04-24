package searchengine.controllers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.ApiResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.exception.PageException;
import searchengine.services.PageService;
import searchengine.services.StatisticsService;
import searchengine.services.WebLinkCrawlerService;

@Slf4j
@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final WebLinkCrawlerService webLinkCrawlerService;
    private final PageService pageService;

    @Autowired
    public ApiController(@Qualifier(value = "extended") StatisticsService statisticsService,
                         WebLinkCrawlerService webLinkCrawlerService, PageService pageService) {
        this.statisticsService = statisticsService;
        this.webLinkCrawlerService = webLinkCrawlerService;
        this.pageService = pageService;
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    /*
    Запуск полной индексации
    */
    @GetMapping("/startIndexing")
    public ResponseEntity<ApiResponse> startIndexing() {
        if (!webLinkCrawlerService.getStopped()) {
            return new ResponseEntity<>(ApiResponse.error("Индексация уже запущена"), HttpStatus.BAD_REQUEST);
        }
        webLinkCrawlerService.startIndexing();
        return new ResponseEntity<>(ApiResponse.success(), HttpStatus.OK);
    }

    /*
    Остановка текущей индексации
     */
    @GetMapping("/stopIndexing")
    public ResponseEntity<ApiResponse> stopIndexing() {
        if (webLinkCrawlerService.getStopped()) {
            return new ResponseEntity<>(ApiResponse.error("Индексация не запущена"), HttpStatus.BAD_REQUEST);
        }

        webLinkCrawlerService.stopIndexing();
        log.info("Нажата кнопка остановки индексации");
        return new ResponseEntity<>(ApiResponse.success(), HttpStatus.OK);
    }

    /*
    Добавление или обновление отдельной страницы
     */
    @PostMapping("/indexPage")
    public ResponseEntity<ApiResponse> addOrUpdateIndexPage(@RequestParam(value = "url", required = false) String url) {

        try {
            pageService.processPage(url);
            return new ResponseEntity<>(ApiResponse.success(), HttpStatus.CREATED);
        } catch (PageException e) {
            return new ResponseEntity<>(ApiResponse.error(e.getMessage()), HttpStatus.BAD_REQUEST);
        }
    }


    /*
    TODO: Написать метод (Получение данных по поисковому запросу) GET /api/search
     */


}
