package dev.evidencerag;

import dev.evidencerag.infrastructure.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
@EnableScheduling
public class EvidenceRagApplication {
    public static void main(String[] args) {
        SpringApplication.run(EvidenceRagApplication.class, args);
    }
}
