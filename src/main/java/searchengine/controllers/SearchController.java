package searchengine.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.response.ResponseResult;
import searchengine.services.SearchService;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class SearchController {
    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/search")
    public ResponseEntity<ResponseResult> indexPage(@RequestParam Map<String, String> requestParams){
        return searchService.search(requestParams);
    }
}
