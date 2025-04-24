package searchengine.services;

import searchengine.model.Page;
import searchengine.model.Site;

public interface LemmaService {

    void processPageLemmasAndIndex(Site site, Page page);
}
