package searchengine.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.response.ResponseResult;
import searchengine.services.IndexingService;

@RestController
@RequestMapping("/api")
public class IndexingController {
    private final IndexingService indexingService;

    public IndexingController(IndexingService indexingService) {
        this.indexingService = indexingService;
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<ResponseResult> startIngexing() {
        return indexingService.startIndexing();
    }
    @GetMapping("/stopIndexing")
    public ResponseEntity<ResponseResult> stopIngexing() {
        return indexingService.stopIndexing();
    }
    @PostMapping("/indexPage")
    public ResponseEntity<ResponseResult> indexPage(@RequestParam String url) {
        return indexingService.indexPage(url);
    }
}
