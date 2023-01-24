package searchengine.services;

import org.springframework.http.ResponseEntity;
import searchengine.response.ResponseResult;

import java.util.Map;

public interface SearchService {
    ResponseEntity<ResponseResult> search(Map<String, String> requestParams);
}
