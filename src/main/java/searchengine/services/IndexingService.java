package searchengine.services;

import org.springframework.http.ResponseEntity;
import searchengine.response.ResponseResult;

public interface IndexingService {
    ResponseEntity<ResponseResult> startIndexing();
    ResponseEntity<ResponseResult> stopIndexing();
    ResponseEntity<ResponseResult> indexPage(String url);
}
