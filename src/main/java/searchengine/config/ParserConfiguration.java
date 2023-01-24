package searchengine.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "parser-config")
public class ParserConfiguration {
    private List<String> filterConditions;
    private String userAgent;
    private String referrer;
}
