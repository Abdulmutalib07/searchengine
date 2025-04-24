package searchengine.services;

import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Jsoup;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
public class LemmaFinder {
    private static final String RUSSIAN_WORD_REGEX = "[^а-яА-Я\\s]";
    private static final String[] EXCLUDED_WORDS = new String[]{"ПРЕДЛ", "СОЮЗ", "МЕЖД", "|B", "|A", "|Z", "|G"};
    private final LuceneMorphology luceneMorphology;
    private Cleaner cleaner = new Cleaner(Safelist.none());

    private LemmaFinder() throws IOException {
        luceneMorphology = new RussianLuceneMorphology();
    }

    public Map<String, Integer> collectLemmas(String text) {
        String[] russianWords = getRussianWords(text);
        Map<String, Integer> result = new HashMap<>();

        for (String word : russianWords) {
            if (word.isBlank()) {
                continue;
            }
            List<String> morphInfo = luceneMorphology.getMorphInfo(word);
            if (containsExcludedWord(morphInfo)) {
                continue;
            }
            List<String> normalForms = luceneMorphology.getNormalForms(word);
            if (normalForms.isEmpty()) {
                continue;
            }
            result.merge(normalForms.get(0), 1, Integer::sum);
        }
        return result;
    }


    public String cleanHtmlOfTags(String html) {
        return cleaner.clean(Jsoup.parse(html)).text();
    }

    private boolean containsExcludedWord(List<String> morphInfo) {
        for (String excludedWord : EXCLUDED_WORDS) {
            if (morphInfo.get(0).contains(excludedWord)) {
                return true;
            }
        }
        return false;
    }

    private String[] getRussianWords(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replaceAll(RUSSIAN_WORD_REGEX, " ")
                .trim()
                .split("\\s+");
    }
}
