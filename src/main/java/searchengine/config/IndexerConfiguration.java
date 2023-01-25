package searchengine.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "indexer-config")
public class IndexerConfiguration {
    private List<String> filterConditionsIndexer;
    private String servicePartFilter;
}
