package dev.evidencerag.banking.infrastructure;

import dev.evidencerag.banking.domain.DemoMode;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class BankingAssistantRepositoryIntegrationTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg17");

    @Test
    void fullTextPolicyRetrievalAndAtomicInteractionBudgetsAreSessionScoped() {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration").placeholders(Map.of("embedding_dimensions", "768")).load().migrate();
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        var repository = new BankingDemoRepository(jdbc);
        Instant now = Instant.parse("2026-11-25T15:00:00Z");
        UUID a = UUID.randomUUID(); UUID b = UUID.randomUUID();
        jdbc.update("INSERT INTO bank_demo_session(id, created_at, expires_at, selected_mode) VALUES (?, ?, ?, 'ANALYST')",
                a, java.sql.Timestamp.from(now), java.sql.Timestamp.from(now.plusSeconds(14400)));
        jdbc.update("INSERT INTO bank_demo_session(id, created_at, expires_at, selected_mode) VALUES (?, ?, ?, 'ANALYST')",
                b, java.sql.Timestamp.from(now), java.sql.Timestamp.from(now.plusSeconds(14400)));

        var policies = repository.searchPolicies("documentos contrato prazo", "POL-CRED", 3);
        assertThat(policies).hasSize(1);
        assertThat(policies.getFirst().version()).isEqualTo("2.1");
        assertThat(policies.getFirst().section()).isEqualTo("4.2 Documentação");
        assertThat(repository.searchPolicies("tópico sem correspondência", "POL-CRED", 3)).isEmpty();

        for (int i = 0; i < 20; i++) assertThat(repository.consumeAiInteraction(a, 20)).isTrue();
        assertThat(repository.consumeAiInteraction(a, 20)).isFalse();
        assertThat(repository.consumeAiInteraction(b, 20)).isTrue();
        repository.saveSessionNote(UUID.randomUUID(), a, "ALERT-4800: conferir transação");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bank_demo_session_note WHERE session_id = ?", Integer.class, a)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bank_demo_session_note WHERE session_id = ?", Integer.class, b)).isZero();
        assertThat(jdbc.queryForObject("SELECT current_balance FROM bank_demo_account WHERE id='ACC-001'", java.math.BigDecimal.class))
                .isEqualByComparingTo("5654.00");
        jdbc.update("DELETE FROM bank_demo_session WHERE id IN (?, ?)", a, b);
    }

    @Test
    void conversationHistoryIsIsolatedBySessionAndExperienceMode() {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration").placeholders(Map.of("embedding_dimensions", "768")).load().migrate();
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        var repository = new BankingDemoRepository(jdbc);
        Instant now = Instant.parse("2026-11-25T15:00:00Z");
        UUID a = UUID.randomUUID(); UUID b = UUID.randomUUID();
        repository.createSession(a, now, now.plusSeconds(14400), DemoMode.CLIENT);
        repository.createSession(b, now, now.plusSeconds(14400), DemoMode.CLIENT);
        try {
            repository.saveConversationMessage(UUID.randomUUID(), a, DemoMode.CLIENT, "USER", "pergunta cliente", "USER", null, null, "[]");
            repository.saveConversationMessage(UUID.randomUUID(), a, DemoMode.CLIENT, "ASSISTANT", "resposta cliente", "GROUNDED", "AI_TOOL_LOOP", null, "[]");
            repository.saveConversationMessage(UUID.randomUUID(), a, DemoMode.ANALYST, "USER", "pergunta analista", "USER", null, null, "[]");
            repository.saveConversationMessage(UUID.randomUUID(), b, DemoMode.CLIENT, "USER", "pergunta outra sessão", "USER", null, null, "[]");

            assertThat(repository.conversationMessages(a, DemoMode.CLIENT, 60)).extracting(BankingDemoRepository.SessionMessage::body)
                    .containsExactlyInAnyOrder("pergunta cliente", "resposta cliente");
            assertThat(repository.conversationMessages(a, DemoMode.ANALYST, 60)).extracting(BankingDemoRepository.SessionMessage::body)
                    .containsExactly("pergunta analista");
            assertThat(repository.conversationMessages(b, DemoMode.CLIENT, 60)).extracting(BankingDemoRepository.SessionMessage::body)
                    .containsExactly("pergunta outra sessão");
        } finally {
            repository.deleteSession(a);
            repository.deleteSession(b);
        }
    }
}
