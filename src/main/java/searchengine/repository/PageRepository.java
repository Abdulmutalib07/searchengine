package searchengine.repository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.Page;
import searchengine.model.Site;

import java.util.Optional;

@Repository
public interface PageRepository extends JpaRepository<Page, Integer> {
    @Query(value = "select COUNT(*) > 0 from page p where p.path=:path", nativeQuery = true)
    boolean existsByPath(@Param("path") String path);

    Optional<Page> findByPathAndSite(String path, Site site);

    @Query(value = "SELECT COUNT(*) FROM Page")
    int countPages();

    int countBySiteId(int siteId);
}
