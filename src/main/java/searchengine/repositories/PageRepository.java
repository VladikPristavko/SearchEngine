package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import searchengine.model.Page;
import searchengine.model.Site;

public interface PageRepository extends JpaRepository<Page, Integer> {
    void deleteAllBySite(Site site);
    int countBySite(Site site);
    boolean existsByPath(String path);
    @Modifying
    @Query(value = "ALTER TABLE pages ADD INDEX path (path(60)) USING BTREE", nativeQuery = true)
    void setIndexOnPath();
    @Modifying
    @Query(value = "drop index path on pages", nativeQuery = true)
    void dropIndexOnPath();

    @Query(value = "SELECT COUNT(1) count FROM INFORMATION_SCHEMA.STATISTICS" +
            " WHERE table_schema=DATABASE() AND table_name='pages' AND index_name='path'", nativeQuery = true
    )
    int hasIndexOnPath();
    @Modifying
    @Query(value = "ALTER TABLE pages AUTO_INCREMENT = 1", nativeQuery = true)
    void restartAutoIncrement();
}
