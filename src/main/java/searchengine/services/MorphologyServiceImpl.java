package searchengine.services;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class MorphologyServiceImpl implements MorphologyService {
    private static final Pattern RUSSIAN_WORD_PATTERN = Pattern.compile("[а-яё]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern ENGLISH_WORD_PATTERN = Pattern.compile("[a-z]+", Pattern.CASE_INSENSITIVE);
    private static final String[] PARTICLES = {"МЕЖД", "ПРЕДЛ", "СОЮЗ", "ЧАСТ"};
    
    private final LuceneMorphology luceneMorphology;

    public MorphologyServiceImpl() throws IOException {
        this.luceneMorphology = new RussianLuceneMorphology();
    }

    @Override
    public Map<String, Integer> getLemmas(String text) {
        Map<String, Integer> lemmas = new HashMap<>();
        String[] words = text.toLowerCase()
                .replaceAll("[^а-яёa-z\\s]", " ")
                .trim()
                .split("\\s+");
        
        for (String word : words) {
            if (word.isEmpty() || word.length() < 2) {
                continue;
            }
            
            if (isRussianWord(word)) {
                List<String> normalForms = getNormalForms(word);
                if (normalForms.isEmpty()) {
                    continue;
                }
                
                String normalForm = normalForms.get(0);
                if (isParticle(normalForm)) {
                    continue;
                }
                
                lemmas.put(normalForm, lemmas.getOrDefault(normalForm, 0) + 1);
            } else if (isEnglishWord(word)) {
                lemmas.put(word, lemmas.getOrDefault(word, 0) + 1);
            }
        }
        
        return lemmas;
    }

    @Override
    public List<String> getNormalForms(String word) {
        try {
            if (!isRussianWord(word)) {
                return Collections.emptyList();
            }
            return luceneMorphology.getNormalForms(word.toLowerCase());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @Override
    public boolean isRussianWord(String word) {
        return RUSSIAN_WORD_PATTERN.matcher(word).matches() && word.length() > 1;
    }

    private boolean isEnglishWord(String word) {
        return ENGLISH_WORD_PATTERN.matcher(word).matches() && word.length() > 1;
    }

    private boolean isParticle(String word) {
        try {
            List<String> morphInfo = luceneMorphology.getMorphInfo(word.toLowerCase());
            for (String info : morphInfo) {
                for (String particle : PARTICLES) {
                    if (info.contains(particle)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return false;
    }
}

