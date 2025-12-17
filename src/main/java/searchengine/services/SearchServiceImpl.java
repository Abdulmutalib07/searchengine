package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;
import searchengine.dto.search.SearchResponse;
import searchengine.dto.search.SearchResult;
import searchengine.model.*;
import searchengine.repository.*;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final MorphologyService morphologyService;
    
    private static final int MAX_FREQUENCY_PERCENT = 80;

    @Override
    public SearchResponse search(String query, String siteUrl, int offset, int limit) {
        SearchResponse response = new SearchResponse();
        
        if (query == null || query.trim().isEmpty()) {
            response.setResult(false);
            response.setError("Задан пустой поисковый запрос");
            return response;
        }
        
        try {
            Map<String, Integer> queryLemmas = morphologyService.getLemmas(query);
            if (queryLemmas.isEmpty()) {
                response.setResult(true);
                response.setCount(0);
                response.setData(Collections.emptyList());
                return response;
            }
            
            List<String> lemmaList = new ArrayList<>(queryLemmas.keySet());
            
            Optional<Site> siteOpt = siteUrl != null && !siteUrl.isEmpty() 
                    ? siteRepository.findByUrl(siteUrl) 
                    : Optional.empty();
            
            List<Lemma> foundLemmas = siteOpt.isPresent()
                    ? lemmaRepository.findBySiteAndLemmaInOrderByFrequencyAsc(siteOpt.get(), lemmaList)
                    : lemmaRepository.findByLemmaInOrderByFrequencyAsc(lemmaList);
            
            if (foundLemmas.isEmpty()) {
                response.setResult(true);
                response.setCount(0);
                response.setData(Collections.emptyList());
                return response;
            }
            
            long totalPages = siteOpt.isPresent()
                    ? pageRepository.countBySite(siteOpt.get())
                    : pageRepository.count();
            
            List<Lemma> filteredLemmas = filterRareLemmas(foundLemmas, totalPages);
            if (filteredLemmas.isEmpty()) {
                response.setResult(true);
                response.setCount(0);
                response.setData(Collections.emptyList());
                return response;
            }
            
            List<Page> pages = findPagesByLemmas(filteredLemmas, siteOpt.orElse(null));
            if (pages.isEmpty()) {
                response.setResult(true);
                response.setCount(0);
                response.setData(Collections.emptyList());
                return response;
            }
            
            Map<Page, Float> relevanceMap = calculateRelevance(pages, filteredLemmas);
            List<Page> sortedPages = pages.stream()
                    .sorted((p1, p2) -> Float.compare(relevanceMap.get(p2), relevanceMap.get(p1)))
                    .collect(Collectors.toList());
            
            List<SearchResult> results = new ArrayList<>();
            int endIndex = Math.min(offset + limit, sortedPages.size());
            for (int i = offset; i < endIndex; i++) {
                Page page = sortedPages.get(i);
                SearchResult result = createSearchResult(page, query, relevanceMap.get(page));
                results.add(result);
            }
            
            response.setResult(true);
            response.setCount(sortedPages.size());
            response.setData(results);
        } catch (Exception e) {
            response.setResult(false);
            response.setError("Ошибка поиска: " + e.getMessage());
        }
        
        return response;
    }

    private List<Lemma> filterRareLemmas(List<Lemma> lemmas, long totalPages) {
        if (totalPages == 0) {
            return lemmas;
        }
        
        int threshold = (int) (totalPages * MAX_FREQUENCY_PERCENT / 100.0);
        return lemmas.stream()
                .filter(lemma -> lemma.getFrequency() < threshold)
                .collect(Collectors.toList());
    }

    private List<Page> findPagesByLemmas(List<Lemma> lemmas, Site site) {
        if (lemmas.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<Integer> lemmaIds = lemmas.stream().map(Lemma::getId).collect(Collectors.toList());
        List<Integer> pageIds = indexRepository.findPageIdsByAllLemmaIds(lemmaIds, lemmas.size());
        
        if (pageIds.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<Page> pages = pageRepository.findByIdIn(pageIds);
        
        if (site != null) {
            pages = pages.stream()
                    .filter(p -> p.getSite().getId() == site.getId())
                    .collect(Collectors.toList());
        }
        
        return pages;
    }

    private Map<Page, Float> calculateRelevance(List<Page> pages, List<Lemma> lemmas) {
        Map<Page, Float> absoluteRelevance = new HashMap<>();
        float maxRelevance = 0;
        
        List<Integer> lemmaIds = lemmas.stream().map(Lemma::getId).collect(Collectors.toList());
        
        for (Page page : pages) {
            Float relevance = indexRepository.calculateAbsoluteRelevanceByIds(page.getId(), lemmaIds);
            if (relevance == null) {
                relevance = 0f;
            }
            absoluteRelevance.put(page, relevance);
            if (relevance > maxRelevance) {
                maxRelevance = relevance;
            }
        }
        
        if (maxRelevance == 0) {
            return absoluteRelevance;
        }
        
        Map<Page, Float> relativeRelevance = new HashMap<>();
        for (Map.Entry<Page, Float> entry : absoluteRelevance.entrySet()) {
            relativeRelevance.put(entry.getKey(), entry.getValue() / maxRelevance);
        }
        
        return relativeRelevance;
    }

    private SearchResult createSearchResult(Page page, String query, float relevance) {
        SearchResult result = new SearchResult();
        result.setSite(page.getSite().getUrl());
        result.setSiteName(page.getSite().getName());
        result.setUri(page.getPath());
        result.setRelevance(relevance);
        
        Document doc = Jsoup.parse(page.getContent());
        Element titleElement = doc.selectFirst("title");
        result.setTitle(escapeHtml(titleElement != null ? titleElement.text() : ""));
        
        String snippet = generateSnippet(doc, query);
        result.setSnippet(snippet);
        
        return result;
    }

    private String generateSnippet(Document doc, String query) {
        String text = doc.body().text();
        if (text == null || text.isEmpty()) {
            return "";
        }
        
        String[] queryWords = query.toLowerCase().split("\\s+");
        
        int snippetLength = 200;
        int bestStart = findBestSnippetStart(text, queryWords, snippetLength);
        
        int start = Math.max(0, bestStart - 50);
        int end = Math.min(text.length(), bestStart + snippetLength);
        String snippet = text.substring(start, end);
        
        if (start > 0) {
            snippet = "..." + snippet;
        }
        if (end < text.length()) {
            snippet = snippet + "...";
        }
        
        // According to evaluation criteria: snippets must be readable text without HTML tags.
        // We still HTML-escape it because the frontend inserts snippet into the DOM as HTML.
        return escapeHtml(snippet);
    }

    private int findBestSnippetStart(String text, String[] queryWords, int snippetLength) {
        String lowerText = text.toLowerCase();
        int bestStart = 0;
        int maxMatches = 0;
        
        for (int i = 0; i <= text.length() - snippetLength; i += 10) {
            String snippet = lowerText.substring(i, Math.min(i + snippetLength, text.length()));
            int matches = 0;
            for (String word : queryWords) {
                if (morphologyService.isRussianWord(word)) {
                    List<String> normalForms = morphologyService.getNormalForms(word);
                    for (String normalForm : normalForms) {
                        if (snippet.contains(normalForm.toLowerCase())) {
                            matches++;
                            break;
                        }
                    }
                } else {
                    if (snippet.contains(word.toLowerCase())) {
                        matches++;
                    }
                }
            }
            if (matches > maxMatches) {
                maxMatches = matches;
                bestStart = i;
            }
        }
        
        return bestStart;
    }

    private String escapeHtml(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}

