package searchengine.response;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.ResponseEntity;
import searchengine.dto.search.SearchData;

import java.util.List;
@Getter
@Setter
public class SearchResult extends OkResult implements ResponseResult{
    private int count;
    private List<SearchData> data;
    public static ResponseEntity<ResponseResult> get(int count, List<SearchData> data){
        SearchResult searchResult = new SearchResult();
        searchResult.setResult(true);
        searchResult.setCount(count);
        searchResult.setData(data);
        return ResponseEntity.ok(searchResult);
    }
}
