package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.config.ParserConfiguration;
import searchengine.config.SitesList;
import searchengine.model.Site;
import searchengine.model.Status;
import searchengine.parsers.SiteParser;
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
    private final ParserConfiguration parserConfiguration;
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
                siteRepository.save(site);
            }
        });
        return OkResult.get();
    }

    @Override
    @Transactional
    public ResponseEntity<ResponseResult> indexPage(String url) {
        searchengine.config.Site siteFromConfig = null;
        for (searchengine.config.Site site : sites.getSites()) {
            if (site.getUrl().equals(url)) {
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
        Site site = siteRepository.findByName(siteFromConfig.getName());
        if (site != null){
            siteRepository.disableForeignKeys();
            siteRepository.deleteByName(siteFromConfig.getName());
            pageRepository.deleteAllBySite(site);
            siteRepository.ableForeignKeys();
        }
        searchengine.config.Site finalSiteFromConfig = siteFromConfig;
        String validUrl;
        try {
            validUrl = urlValidator(siteFromConfig);
        } catch (IOException e) {
            return ErrorResult.get(e.getMessage(), 404);
        }
        executorService = Executors.newCachedThreadPool();
        executorService.execute(() -> parseSite(finalSiteFromConfig.getName(), validUrl));
        executorService.execute(this::updateIndexingTime);
        return OkResult.get();
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
        if (url.equals("")){
            return;
        }
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
        siteParser.setConditions(parserConfiguration.getFilterConditions());
        siteParser.setUserAgent(parserConfiguration.getUserAgent());
        siteParser.setReferrer(parserConfiguration.getReferrer());
    }
    private void clearAllTables() {
        siteRepository.disableForeignKeys();
        siteRepository.deleteAllInBatch();
        pageRepository.deleteAllInBatch();
        siteRepository.restartAutoIncrement();
        pageRepository.restartAutoIncrement();
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
