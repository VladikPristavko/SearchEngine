package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.dto.search.SearchData;
import searchengine.response.ErrorResult;
import searchengine.response.ResponseResult;
import searchengine.response.SearchResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService{
    @Override
    public ResponseEntity<ResponseResult> search(Map<String, String> requestParams) {
        if (requestParams.get("query").equals("")){
            return ErrorResult.get("Задан пустой поисковый запрос", 400);
        }
        List<SearchData> data = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            SearchData d = new SearchData();
            d.setSite("https:///site_" + i);
            d.setSiteName("name_" + i);
            d.setUri("uri_" + i);
            d.setTitle("title_" + i);
            d.setSnippet("snippet_" + i);
            d.setRelevance(Math.random());
            data.add(d);
        }
        return SearchResult.get(10, data);
    }
}
