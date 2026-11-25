package dev.evidencerag;

import dev.evidencerag.infrastructure.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class EvidenceRagApplication {
    public static void main(String[] args) {
        SpringApplication.run(EvidenceRagApplication.class, args);
    }
}
