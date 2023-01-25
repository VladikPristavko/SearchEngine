package searchengine.parsers;

import org.apache.lucene.morphology.LuceneMorphology;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.groupingBy;

public class PageIndexer {
    private static String servicePartFilter;
    private static List<String> userFilterConditions;
    private static LuceneMorphology morphology;
    private static LemmaRepository lemmaRepository;
    private static IndexRepository indexRepository;
    private Stream<String> cleanContent(String content){
        String cleanContent = content.toLowerCase()
                .replaceAll("[!-~—»«©]", " ")
                .trim()
                .replaceAll("[\\s]{2,}", " ");
        return Arrays.stream(cleanContent.split(" "));
    }
    private boolean isNotServicePart(String word){
        String morphInfoForWord = morphology.getMorphInfo(word).get(0);
        return !servicePartFilter.contains(String.valueOf(morphInfoForWord.charAt(morphInfoForWord.indexOf("|") + 1)));
    }
    private boolean isNotInUserFilter(String word){
        return !userFilterConditions.contains(word);
    }
    private Map<String, Long> getLemmasAndCount(Stream<String> stream) {
        return stream.filter(word -> word.length() != 1)
                .filter(this::isNotServicePart)
                .filter(this::isNotInUserFilter)
                .flatMap(word -> morphology.getNormalForms(word).stream())
                .collect(groupingBy(Function.identity(), Collectors.counting()));
    }
    public void indexPage(Site site, Page page){
        Map<String, Long> lemmas = getLemmasAndCount(cleanContent(page.getContent()));
        lemmas.keySet().forEach(key -> {
            Lemma lemma = lemmaRepository.findByLemmaAndSite(key, site);
            if (lemma != null) {
                lemma.setFrequency(lemma.getFrequency() + 1);
            } else {
                lemma = new Lemma();
                lemma.setSite(site);
                lemma.setLemma(key);
                lemma.setFrequency(1);
                lemmaRepository.save(lemma);
            }
                Index index = new Index();
                index.setPage(page);
                index.setLemma(lemma);
                index.setRank(lemmas.get(key));
                indexRepository.save(index);

        });
    }
    public LemmaRepository getLemmaRepository(){
        return lemmaRepository;
    }
    public void setLemmaRepository(LemmaRepository lemmaRepository){
        PageIndexer.lemmaRepository = lemmaRepository;
    }
    public void setIndexRepository(IndexRepository indexRepository){
        PageIndexer.indexRepository = indexRepository;
    }
    public void setUserFilterConditions(List<String> userFilterConditions){
        PageIndexer.userFilterConditions = userFilterConditions;
    }
    public void setServicePartFilter(String servicePartFilter){
        PageIndexer.servicePartFilter = servicePartFilter;
    }
    public void setMorphology(LuceneMorphology morphology) {
        PageIndexer.morphology = morphology;
    }
}
