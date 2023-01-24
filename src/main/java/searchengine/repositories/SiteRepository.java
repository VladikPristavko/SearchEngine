package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import searchengine.model.Site;

public interface SiteRepository extends JpaRepository<Site, Integer> {
    Site findByName(String name);
    void deleteByName(String name);
    @Modifying
    @Query(value = "ALTER TABLE sites AUTO_INCREMENT = 1", nativeQuery = true)
    void restartAutoIncrement();

    @Modifying
    @Query(value = "SET foreign_key_checks = 0", nativeQuery = true)
    void disableForeignKeys();
    @Modifying
    @Query(value = "SET foreign_key_checks = 1", nativeQuery = true)
    void ableForeignKeys();
}
