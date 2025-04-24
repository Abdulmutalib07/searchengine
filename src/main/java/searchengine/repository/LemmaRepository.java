package searchengine.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.Lemma;
import searchengine.model.Site;

import java.util.Optional;

@Repository
public interface LemmaRepository extends CrudRepository<Lemma, Integer> {

    Optional<Lemma> findLemmaBySiteAndLemma(Site site, String lemma);
    @Query(value = "select count(DISTINCT l.lemma) from public.Lemma l", nativeQuery = true)
    int countAllUniqueLemmas();
    @Query(value = "select COUNT(DISTINCT l.lemma) from public.Lemma l where l.site_id=:siteId", nativeQuery = true)
    int countUniqueLemmasBySite(@Param("siteId") int siteId);
}
