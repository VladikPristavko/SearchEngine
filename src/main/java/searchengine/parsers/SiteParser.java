package searchengine.parsers;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.model.Status;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.ForkJoinTask;
import java.util.stream.Collectors;

public class SiteParser extends RecursiveAction {
    private static PageRepository pageRepository;
    private static SiteRepository siteRepository;
    private final String domain;
    private final Site site;
    private final String url;
    private static List<String> conditions;
    private static String userAgent;
    private static String referrer;

    public SiteParser(String url, Site site, String domain){
        this.url = url;
        this.site = site;
        this.domain = domain;
    }
    @Override
    protected void compute() {
        try {
            if (siteRepository.findByName(site.getName()).getStatus().equals(Status.FAILED)){
                System.out.println("kill");
                Thread.currentThread().interrupt();
            }
            List<String> childUrls = urlFinder(getConnection(url).get());
            if(childUrls.isEmpty()) {
                return;
            }
            childUrls.forEach(child -> {
                try {
                    Connection connection = getConnection(child);
                    Thread.sleep(100);
                    Page page = new Page();
                    page.setSite(site);
                    page.setPath(child.substring(child.indexOf(domain) + domain.length()));
                    page.setCode(connection.execute().statusCode());
                    page.setContent(connection.get().toString());
                    savePageInPages(child, page);
                } catch (IOException | InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            List<SiteParser> tasks = new ArrayList<>();
            for (String childUrl : childUrls) {
                SiteParser siteParser = new SiteParser(childUrl, site, domain);
                siteParser.fork();
                tasks.add(siteParser);
            }
            tasks.forEach(ForkJoinTask::join);
        } catch (IOException e) {
            Thread.currentThread().interrupt();
        }
    }
    private Connection getConnection(String url){
        return Jsoup.connect(url).userAgent(userAgent).referrer(referrer);
    }
    private List<String> urlFinder(Document document){
        return document.select("a[href]").stream()
           .map(element -> element.absUrl("href"))
           .map(String :: toLowerCase)
           .distinct()
           .filter(this :: urlFilter)
           .filter(this ::checkExistsInPages)
           .collect(Collectors.toList());
    }
    private boolean urlFilter(String url){
        if (!url.startsWith("http")){
            return false;
        }
        for (String condition : conditions) {
           if (url.contains(condition)) {
               return false;
           }
        }
        return url.contains(domain);
    }
    private boolean checkExistsInPages(String url){
        return !pageRepository.existsByPath(
           url.substring(url.indexOf(domain) + domain.length()));
    }
    private void savePageInPages(String url, Page page) {
        synchronized (this) {
            if (checkExistsInPages(url)) {
                pageRepository.save(page);
            }
        }
    }
    public void setPageRepository(PageRepository repository){
        pageRepository = repository;
    }
    public void setSiteRepository(SiteRepository repository){
        siteRepository = repository;
    }
    public void setConditions(List<String> uFilterConditions) {
        conditions = uFilterConditions;
    }
    public void setUserAgent(String uAgent) {
        userAgent = uAgent;
    }
    public void setReferrer(String uReferrer){
        referrer = uReferrer;
    }
    public SiteRepository getSiteRepository(){
        return siteRepository;
    }
}
