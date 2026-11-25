package dev.evidencerag.api;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.DriverManager;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Optional local integration path for Docker Desktop setups where Testcontainers cannot discover the daemon. */
class HealthExternalDatabaseTest {
    @Test
    @EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = "jdbc:postgresql:.*")
    void appliesMigrationsAndCreatesConfiguredVectorDimensionAndIndex() throws Exception {
        String url = System.getenv("TEST_DATABASE_URL");
        String user = System.getenv().getOrDefault("TEST_DATABASE_USER", "mission");
        String password = System.getenv().getOrDefault("TEST_DATABASE_PASSWORD", "mission");
        Flyway.configure().dataSource(url, user, password).locations("classpath:db/migration")
                .placeholders(Map.of("embedding_dimensions", "768")).load().migrate();
        try (var connection = DriverManager.getConnection(url, user, password);
             var statement = connection.createStatement();
             var result = statement.executeQuery("select (select count(*) from information_schema.columns where table_name='rag_document_chunk' and column_name in ('start_offset','end_offset')), (select count(*) from pg_indexes where indexname='vector_store_embedding_hnsw_idx'), (select format_type(a.atttypid,a.atttypmod) from pg_attribute a where a.attrelid='vector_store'::regclass and a.attname='embedding')")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(2);
            assertThat(result.getInt(2)).isEqualTo(1);
            assertThat(result.getString(3)).isEqualTo("vector(768)");
        }
    }
}
