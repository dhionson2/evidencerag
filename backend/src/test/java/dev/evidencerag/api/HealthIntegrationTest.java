package dev.evidencerag.api;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class HealthIntegrationTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg17");
    @Test void flywayCreatesVectorAndDocumentCatalogTables() throws Exception {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration").placeholders(java.util.Map.of("embedding_dimensions", "768"))
                .load().migrate();
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement();
             var result = statement.executeQuery("select to_regclass('vector_store'), to_regclass('rag_document'), to_regclass('rag_document_chunk'), (select count(*) from information_schema.columns where table_name='rag_document_chunk' and column_name in ('start_offset','end_offset')), (select count(*) from pg_indexes where indexname='vector_store_embedding_hnsw_idx')")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isEqualTo("vector_store");
            assertThat(result.getString(2)).isEqualTo("rag_document");
            assertThat(result.getString(3)).isEqualTo("rag_document_chunk");
            assertThat(result.getInt(4)).isEqualTo(2);
            assertThat(result.getInt(5)).isEqualTo(1);
        }
    }
}
