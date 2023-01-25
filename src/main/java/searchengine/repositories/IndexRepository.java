package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import searchengine.model.Index;
import searchengine.model.Page;

import java.util.List;

public interface IndexRepository extends JpaRepository<Index, Integer> {
    List<Index> findAllByPage(Page page);
    void deleteAllByPage(Page page);
    @Modifying
    @Query(value = "ALTER TABLE indexes AUTO_INCREMENT = 1", nativeQuery = true)
    void restartAutoIncrement();
}
