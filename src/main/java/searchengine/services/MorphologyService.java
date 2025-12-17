package searchengine.services;

import java.util.List;
import java.util.Map;

public interface MorphologyService {
    Map<String, Integer> getLemmas(String text);
    List<String> getNormalForms(String word);
    boolean isRussianWord(String word);
}

