package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.Lemma;
import searchengine.model.Site;

import java.util.List;
import java.util.Optional;

@Repository
public interface LemmaRepository extends JpaRepository<Lemma, Integer> {
    Optional<Lemma> findBySiteAndLemma(Site site, String lemma);
    List<Lemma> findAllBySite(Site site);
    List<Lemma> findBySiteAndLemmaIn(Site site, List<String> lemmas);
    List<Lemma> findByLemmaIn(List<String> lemmas);
    long countBySite(Site site);
    
    @Query("SELECT l FROM Lemma l WHERE l.lemma IN :lemmas AND l.site = :site ORDER BY l.frequency ASC")
    List<Lemma> findBySiteAndLemmaInOrderByFrequencyAsc(@Param("site") Site site, @Param("lemmas") List<String> lemmas);
    
    @Query("SELECT l FROM Lemma l WHERE l.lemma IN :lemmas ORDER BY l.frequency ASC")
    List<Lemma> findByLemmaInOrderByFrequencyAsc(@Param("lemmas") List<String> lemmas);
}

