package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;

import java.util.Collection;
import java.util.List;

@Repository
public interface IndexRepository extends JpaRepository<Index, Integer> {
    List<Index> findByPage(Page page);
    List<Index> findByLemma(Lemma lemma);
    void deleteAllByPageIn(Collection<Page> pages);
    
    @Query(value = "SELECT DISTINCT i.page_id FROM search_index i WHERE i.lemma_id = :lemmaId", nativeQuery = true)
    List<Integer> findPageIdsByLemmaId(@Param("lemmaId") Integer lemmaId);
    
    @Query(value = "SELECT DISTINCT i.page_id FROM search_index i WHERE i.lemma_id IN :lemmaIds GROUP BY i.page_id HAVING COUNT(DISTINCT i.lemma_id) = :lemmaCount", nativeQuery = true)
    List<Integer> findPageIdsByAllLemmaIds(@Param("lemmaIds") List<Integer> lemmaIds, @Param("lemmaCount") long lemmaCount);
    
    @Query(value = "SELECT SUM(i.rank_value) FROM search_index i WHERE i.page_id = :pageId AND i.lemma_id IN :lemmaIds", nativeQuery = true)
    Float calculateAbsoluteRelevanceByIds(@Param("pageId") Integer pageId, @Param("lemmaIds") List<Integer> lemmaIds);
}

