package searchengine.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
//@Transactional
public class LemmaServiceImpl implements LemmaService {

    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final HtmlTextProcessor htmlTextProcessor;
    private Object lock = new Object();


    @Autowired
    public LemmaServiceImpl(LemmaRepository lemmaRepository, IndexRepository indexRepository, HtmlTextProcessor htmlTextProcessor) {
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
        this.htmlTextProcessor = htmlTextProcessor;
    }

    @Override
    public void processPageLemmasAndIndex(Site site, Page page) {
        Objects.requireNonNull(site, "Сайт не может быть null");
        Objects.requireNonNull(page, "Страница не может быть null");

        Map<String, Integer> lemmas = htmlTextProcessor.extractLemmas(page.getContent());
        List<Lemma> savedLemmas = savedLemmasToDataBase(site, lemmas);
        saveIndexes(page, lemmas, savedLemmas);

    }

    private void saveIndexes(Page page, Map<String, Integer> lemmas, List<Lemma> savedLemmas) {
        List<Index> indexes = savedLemmas.stream()
                .map(lemma -> {
                    final Index index = new Index();
                    index.setPage(page);
                    index.setLemma(lemma);
                    index.setRank(lemmas.get(lemma.getLemma()));
                    return index;
                }).toList();
        indexRepository.saveAll(indexes);
    }

    /*
    TODO оптимизировать синхронизацию (для каждого слова)
     */
    private List<Lemma> savedLemmasToDataBase(Site site, Map<String, Integer> lemmas) {

        synchronized (lock) {
            List<Lemma> list = lemmas.keySet().stream()
                    .map(lemma -> getOrCreateLemma(site, lemma)).toList();
            return (List<Lemma>) lemmaRepository.saveAll(list);
        }
    }

    private Lemma getOrCreateLemma(Site site, String lemma) {
        return lemmaRepository.findLemmaBySiteAndLemma(site, lemma)
                .map(foundLemma -> {
                    foundLemma.setFrequency(foundLemma.getFrequency() + 1);
                    return foundLemma;
                })
                .orElseGet(() -> {
                    final Lemma tempLemma = new Lemma();
                    tempLemma.setSite(site);
                    tempLemma.setLemma(lemma);
                    tempLemma.setFrequency(1);
                    return tempLemma;
                });
    }
}
