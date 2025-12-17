package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.Page;
import searchengine.model.Site;

import java.util.List;
import java.util.Optional;

@Repository
public interface PageRepository extends JpaRepository<Page, Integer> {
    Optional<Page> findBySiteAndPath(Site site, String path);
    List<Page> findAllBySite(Site site);
    long countBySite(Site site);
    List<Page> findByIdIn(List<Integer> ids);
}

