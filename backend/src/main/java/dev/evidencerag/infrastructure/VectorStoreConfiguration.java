package dev.evidencerag.infrastructure;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class VectorStoreConfiguration {
    @Bean
    PgVectorStore evidenceVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel,
                                      org.springframework.core.env.Environment environment) {
        int dimensions = environment.getProperty("APP_EMBEDDING_DIMENSIONS", Integer.class, 1536);
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .dimensions(dimensions)
                .initializeSchema(false)
                .build();
    }
}
