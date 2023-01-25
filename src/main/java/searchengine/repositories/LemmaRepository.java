package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import searchengine.model.Lemma;
import searchengine.model.Site;

public interface LemmaRepository extends JpaRepository<Lemma, Integer> {
    Lemma findByLemmaAndSite(String lemma, Site site);
    @Modifying
    @Query(value = "ALTER TABLE lemmas AUTO_INCREMENT = 1", nativeQuery = true)
    void restartAutoIncrement();
}
