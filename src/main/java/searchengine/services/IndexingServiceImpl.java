package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.config.IndexerConfiguration;
import searchengine.config.ParserConfiguration;
import searchengine.config.SitesList;
import searchengine.model.Lemma;
import searchengine.parsers.PageIndexer;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.model.Status;
import searchengine.parsers.SiteParser;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.response.ErrorResult;
import searchengine.response.OkResult;
import searchengine.response.ResponseResult;

import javax.transaction.Transactional;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService{
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final ParserConfiguration parserConfiguration;
    private final IndexerConfiguration indexerConfiguration;
    private final SitesList sites;
    private ExecutorService executorService;

    @Override
    @Transactional
    public ResponseEntity<ResponseResult> startIndexing() {
        if (executorService != null){
            return ErrorResult.get("Индексация уже запущена", 503);
        }
        executorService = Executors.newCachedThreadPool();
        clearAllTables();
        if (pageRepository.hasIndexOnPath() != 1) {
            pageRepository.setIndexOnPath();
        }
        String validUrl;
        int sitesWithErrorCount = 0;
        for (searchengine.config.Site site : sites.getSites()) {
            try {
                validUrl = urlValidator(site);
            } catch (IOException e) {
                sitesWithErrorCount++;
                continue;
            }
            String finalValidUrl = validUrl;
            executorService.execute(() -> parseSite(site.getName(), finalValidUrl));
        }
        if (sitesWithErrorCount == sites.getSites().size()){
            executorService = null;
            return ErrorResult.get("Главные страницы всех сайтов недоступны", 404);
        }
        executorService.execute(this::updateIndexingTime);
        return OkResult.get();
    }


    @Override
    @Transactional
    public ResponseEntity<ResponseResult> stopIndexing() {
        if (executorService == null){
            return ErrorResult.get("Индексация не запущена", 405);
        }
        executorService.shutdownNow();
        executorService = null;
        siteRepository.findAll().forEach(site -> {
            if (site.getStatus().equals(Status.INDEXING)) {
                site.setStatusTime(LocalDateTime.now());
                site.setStatus(Status.FAILED);
                site.setLastError("Индексация остановлена пользователем");
                //siteRepository.save(site);
            }
        });
        return OkResult.get();
    }

    @Override
    @Transactional
    public ResponseEntity<ResponseResult> indexPage(String url) {
        if (url.equals("")){
            return ErrorResult.get("Не введен адрес страницы", 400);
        }
        searchengine.config.Site siteFromConfig = null;
        for (searchengine.config.Site site : sites.getSites()) {
            if (url.startsWith(site.getUrl())) {
                siteFromConfig = site;
                break;
            }
        }
        if (siteFromConfig == null) {
            return ErrorResult.get("Данная станица находится за пределами сайтов, " +
                    "указанных в конфигурационном файле", 400);
        }
        if (executorService != null){
            return ErrorResult.get("Индексация уже запущена, ожидайте завершения", 503);
        }
        int code = 0;
        String content = "";
        try {
            Connection connection = Jsoup.connect(url)
                    .userAgent(parserConfiguration.getUserAgent())
                    .referrer(parserConfiguration.getReferrer());
            url = connection.execute().url().toString();
            code = connection.execute().statusCode();
            content = connection.get().toString();
        } catch (IOException e) {
            return ErrorResult.get("Страница сайта недоступна", 404);
        }
        String path = url.substring(siteFromConfig.getUrl().length());
        Page page = pageRepository.findByPath(path);
        Site site = siteRepository.findByName(siteFromConfig.getName());
        if (page != null){
            clearIndexAndLemmasByPage(site, page);
        }
        page = new Page();
        page.setSite(site);
        page.setPath(path);
        page.setCode(code);
        page.setContent(content);
        pageRepository.save(page);
        PageIndexer pageIndexer = new PageIndexer();
        if (pageIndexer.getLemmaRepository() == null){
            try {
                pageIndexerConfigure(pageIndexer);
            } catch (IOException e) {
                return ErrorResult.get("Ошибка инициализации Lucene.Morphology", 503);
            }
        }
        pageIndexer.indexPage(site, page);

      /*  searchengine.config.Site finalSiteFromConfig = siteFromConfig;
        String validUrl;
        try {
            validUrl = urlValidator(siteFromConfig);
        } catch (IOException e) {
            return ErrorResult.get(e.getMessage(), 404);
        }
        executorService = Executors.newCachedThreadPool();
        executorService.execute(() -> parseSite(finalSiteFromConfig.getName(), validUrl));
        executorService.execute(this::updateIndexingTime);*/
        return OkResult.get();
    }
    private void clearIndexAndLemmasByPage(Site site, Page page){
        siteRepository.disableForeignKeys();
        indexRepository.findAllByPage(page).forEach(item -> {
            String lemmaName = item.getLemma().getLemma();
            Lemma lemma = lemmaRepository.findByLemmaAndSite(lemmaName, site);
            if (lemma.getFrequency() == 1) {
                lemmaRepository.delete(lemma);
            } else {
                lemma.setFrequency(lemma.getFrequency() - 1);
            }
        });
        indexRepository.deleteAllByPage(page);
        pageRepository.delete(page);
        siteRepository.ableForeignKeys();
    }
    private void updateIndexingTime(){
        boolean isInterrupted = false;
        while (!isInterrupted) {
            int siteCount = 0;
            List<Site> siteList = siteRepository.findAll();
            for(Site site : siteList) {
                switch (site.getStatus()){
                    case FAILED, INDEXED -> siteCount++;
                    case INDEXING -> {
                        site.setStatusTime(LocalDateTime.now());
                        siteRepository.save(site);
                    }
                }
                if (siteCount == siteList.size()){
                    isInterrupted = true;
                }
            }
            try {
                Thread.sleep(60_000);
            } catch (InterruptedException e) {
                isInterrupted = true;
            }
        }
    }
    private void parseSite(String name, String url){
        Site site = new Site();
        site.setStatusTime(LocalDateTime.now());
        site.setName(name);
        site.setStatus(Status.INDEXING);
        site.setUrl(url);
        siteRepository.save(site);
        SiteParser siteParser = new SiteParser(url, site, getDomain(url));
        if (siteParser.getSiteRepository() == null) {
            siteParserConfigure(siteParser);
        }
        ForkJoinPool joinPool = new ForkJoinPool();
        joinPool.invoke(siteParser);
        site = siteRepository.findByName(site.getName());
        if (site.getStatus().equals(Status.INDEXING)) {
            site.setStatusTime(LocalDateTime.now());
            site.setStatus(Status.INDEXED);
            siteRepository.save(site);
        }
    }
    private void siteParserConfigure(SiteParser siteParser){
        siteParser.setPageRepository(pageRepository);
        siteParser.setSiteRepository(siteRepository);
        siteParser.setConditions(parserConfiguration.getFilterConditionsParser());
        siteParser.setUserAgent(parserConfiguration.getUserAgent());
        siteParser.setReferrer(parserConfiguration.getReferrer());
    }

    private void pageIndexerConfigure(PageIndexer pageIndexer) throws IOException {
        pageIndexer.setLemmaRepository(lemmaRepository);
        pageIndexer.setIndexRepository(indexRepository);
        pageIndexer.setUserFilterConditions(indexerConfiguration.getFilterConditionsIndexer());
        pageIndexer.setServicePartFilter(indexerConfiguration.getServicePartFilter());
        pageIndexer.setMorphology(new RussianLuceneMorphology());
    }
    private void clearAllTables() {
        siteRepository.disableForeignKeys();
        siteRepository.deleteAllInBatch();
        pageRepository.deleteAllInBatch();
        lemmaRepository.deleteAllInBatch();
        indexRepository.deleteAllInBatch();
        siteRepository.restartAutoIncrement();
        pageRepository.restartAutoIncrement();
        lemmaRepository.restartAutoIncrement();
        indexRepository.restartAutoIncrement();
        siteRepository.ableForeignKeys();
    }
    private String urlValidator(searchengine.config.Site siteFromConfig) throws IOException {
        try {
            String url = Jsoup.connect(siteFromConfig.getUrl()).execute().url().toString();
            return url.endsWith("/") ? url : url + "/";
        } catch (IOException e) {
            Site site = new Site();
            site.setUrl(siteFromConfig.getUrl());
            site.setName(siteFromConfig.getName());
            site.setStatusTime(LocalDateTime.now());
            site.setStatus(Status.FAILED);
            site.setLastError("Главная страница сайта недоступна");
            siteRepository.save(site);
            throw new IOException("Главная страница сайта недоступна");
        }
    }
    private String getDomain(String url){
        if (url.startsWith("https")) {
            url = url.replace("https://", "");
        }
        if (url.startsWith("https")) {
            url = url.replace("http://", "");
        }
        return url.substring(0, url.length() - 1);
    }
}
