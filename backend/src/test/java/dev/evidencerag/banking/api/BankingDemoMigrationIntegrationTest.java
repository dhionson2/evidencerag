package dev.evidencerag.banking.api;

import org.flywaydb.core.Flyway;
import dev.evidencerag.banking.application.BankingDemoService;
import dev.evidencerag.banking.domain.DemoMode;
import dev.evidencerag.banking.domain.DemoSessionExpiredException;
import dev.evidencerag.banking.infrastructure.BankingDemoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class BankingDemoMigrationIntegrationTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg17");

    @Test
    void realPostgresValidatesAbsoluteExpiryIsolationCleanupResetAndRagPreservation() {
        Flyway flyway = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration").placeholders(Map.of("embedding_dimensions", "768")).load();
        flyway.migrate();
        flyway.migrate(); // V1–V6 are safe to start against an already-migrated database.

        DriverManagerDataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        MutableClock clock = new MutableClock(Instant.parse("2026-11-25T12:00:00Z"));
        BankingDemoService service = new BankingDemoService(new BankingDemoRepository(jdbc), clock);

        UUID originalA = service.start(null, DemoMode.CLIENT).session().id();
        jdbc.update("INSERT INTO bank_demo_session_overlay(session_id, overlay_key, payload) VALUES (?, 'test_state', '{}'::jsonb)", originalA);
        jdbc.update("INSERT INTO bank_demo_session_note(id, session_id, body) VALUES (?, ?, 'nota antes do reinício')", UUID.randomUUID(), originalA);
        jdbc.update("INSERT INTO bank_demo_session_message(id, session_id, role, body) VALUES (?, ?, 'USER', 'mensagem antes do reinício')", UUID.randomUUID(), originalA);
        var sessionA = service.restart(originalA, DemoMode.CLIENT); // Reiniciar remove apenas a sessão recebida e cria TTL novo.
        UUID a = sessionA.session().id();
        assertThat(a).isNotEqualTo(originalA);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bank_demo_session WHERE id = ?", Integer.class, originalA)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bank_demo_session_overlay WHERE session_id = ?", Integer.class, originalA)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bank_demo_session_note WHERE session_id = ?", Integer.class, originalA)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bank_demo_session_message WHERE session_id = ?", Integer.class, originalA)).isZero();
        assertThat(sessionA.session().createdAt()).isEqualTo(clock.instant());
        assertThat(sessionA.session().expiresAt()).isEqualTo(clock.instant().plus(Duration.ofHours(4)));
        assertThat(sessionA.customers()).hasSize(2);
        assertThat(sessionA.accounts()).hasSize(2);
        assertThat(sessionA.transactions()).hasSize(30);
        assertThat(sessionA.alerts()).hasSize(4);
        assertThat(sessionA.proposals()).hasSize(3);
        assertThat(sessionA.policies()).hasSize(5);

        UUID ragId = UUID.randomUUID();
        UUID chunkId = UUID.randomUUID();
        UUID vectorId = UUID.randomUUID();
        jdbc.update("INSERT INTO rag_document(id, filename, media_type, sha256, status, chunk_count) VALUES (?, 'e1-test.txt', 'text/plain', ?, 'INDEXED', 1)", ragId, UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""));
        jdbc.update("INSERT INTO rag_document_chunk(id, document_id, vector_id, chunk_index, page_number, excerpt, start_offset, end_offset) VALUES (?, ?, ?, 0, 1, 'evidência RAG de teste', 0, 21)", chunkId, ragId, vectorId);
        jdbc.update("INSERT INTO vector_store(id, content, metadata, embedding) VALUES (?, 'evidência RAG de teste', '{}'::json, array_fill(0::real, ARRAY[768])::vector)", vectorId);
        int customersBefore = jdbc.queryForObject("SELECT count(*) FROM bank_demo_customer", Integer.class);
        int transactionsBefore = jdbc.queryForObject("SELECT count(*) FROM bank_demo_transaction", Integer.class);
        int policiesBefore = jdbc.queryForObject("SELECT count(*) FROM bank_demo_policy", Integer.class);

        jdbc.update("INSERT INTO bank_demo_session_note(id, session_id, body) VALUES (?, ?, 'nota A')", UUID.randomUUID(), a);
        jdbc.update("INSERT INTO bank_demo_session_message(id, session_id, role, body) VALUES (?, ?, 'USER', 'mensagem A')", UUID.randomUUID(), a);
        var analystA = service.changeMode(a, DemoMode.ANALYST);
        assertThat(analystA.session().expiresAt()).isEqualTo(sessionA.session().expiresAt());

        clock.advance(Duration.ofHours(3).plusMinutes(50));
        var sessionB = service.start(null, DemoMode.CLIENT);
        UUID b = sessionB.session().id();
        jdbc.update("INSERT INTO bank_demo_session_note(id, session_id, body) VALUES (?, ?, 'nota B')", UUID.randomUUID(), b);
        jdbc.update("INSERT INTO bank_demo_session_message(id, session_id, role, body) VALUES (?, ?, 'USER', 'mensagem B')", UUID.randomUUID(), b);
        service.changeMode(b, DemoMode.ANALYST);
        service.changeMode(b, DemoMode.CLIENT);
        assertThat(service.current(a).session().selectedMode()).isEqualTo(DemoMode.ANALYST);
        assertThat(service.current(b).session().selectedMode()).isEqualTo(DemoMode.CLIENT);
        assertThat(sessionB.session().expiresAt()).isEqualTo(sessionB.session().createdAt().plus(Duration.ofHours(4)));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bank_demo_session_note WHERE session_id = ?", Integer.class, a)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bank_demo_session_note WHERE session_id = ?", Integer.class, b)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bank_demo_session_message WHERE session_id = ?", Integer.class, a)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bank_demo_session_message WHERE session_id = ?", Integer.class, b)).isEqualTo(1);

        clock.advance(Duration.ofMinutes(9));
        assertThat(service.current(a).session().expiresAt()).isEqualTo(sessionA.session().expiresAt());
        clock.advance(Duration.ofMinutes(1));
        assertThat(clock.instant()).isEqualTo(sessionA.session().expiresAt());
        assertThatThrownBy(() -> service.current(a)).isInstanceOf(DemoSessionExpiredException.class);
        assertThat(service.current(b).session().id()).isEqualTo(b); // At T+4h, B created later remains active.

        assertThat(service.cleanExpiredSessions()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bank_demo_session WHERE id = ?", Integer.class, a)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bank_demo_session WHERE id = ?", Integer.class, b)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bank_demo_session_overlay WHERE session_id = ?", Integer.class, a)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bank_demo_session_note WHERE session_id = ?", Integer.class, a)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bank_demo_session_message WHERE session_id = ?", Integer.class, a)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bank_demo_session_note WHERE session_id = ?", Integer.class, b)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bank_demo_session_message WHERE session_id = ?", Integer.class, b)).isEqualTo(1);

        var restartedA = service.restart(a, DemoMode.CLIENT);
        assertThat(restartedA.session().id()).isNotEqualTo(a);
        assertThat(restartedA.session().expiresAt()).isEqualTo(clock.instant().plus(Duration.ofHours(4)));
        assertThat(service.current(b).session().id()).isEqualTo(b);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bank_demo_customer", Integer.class)).isEqualTo(customersBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bank_demo_transaction", Integer.class)).isEqualTo(transactionsBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bank_demo_policy", Integer.class)).isEqualTo(policiesBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rag_document WHERE id = ?", Integer.class, ragId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rag_document_chunk WHERE id = ?", Integer.class, chunkId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM vector_store WHERE id = ?", Integer.class, vectorId)).isEqualTo(1);

        jdbc.update("DELETE FROM bank_demo_session WHERE id IN (?, ?)", b, restartedA.session().id());
        jdbc.update("DELETE FROM vector_store WHERE id = ?", vectorId);
        jdbc.update("DELETE FROM rag_document WHERE id = ?", ragId);
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> instant;
        private MutableClock(Instant initial) { instant = new AtomicReference<>(initial); }
        private void advance(Duration duration) { instant.updateAndGet(value -> value.plus(duration)); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant.get(); }
    }

    @Test
    void migratesCoherentSyntheticSeedsAndCascadesOnlyExpiredSessionState() throws Exception {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration").placeholders(Map.of("embedding_dimensions", "768"))
                .load().migrate();

        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT " +
                     "(SELECT count(*) FROM bank_demo_customer), " +
                     "(SELECT count(*) FROM bank_demo_transaction), " +
                     "(SELECT count(*) FROM bank_demo_alert), " +
                     "(SELECT count(*) FROM bank_demo_credit_proposal), " +
                     "(SELECT count(*) FROM bank_demo_policy), " +
                     "(SELECT count(*) FROM bank_demo_credit_proposal WHERE id='CR-102' AND requested_amount=150000 AND received_document_count=4 AND required_document_count=5 AND proposed_term_months<>contract_term_months), " +
                     "(SELECT count(*) FROM bank_demo_account a WHERE a.current_balance <> a.opening_balance + (SELECT sum(CASE WHEN t.direction='CREDIT' THEN t.amount ELSE -t.amount END) FROM bank_demo_transaction t WHERE t.account_id=a.id)), " +
                     "(SELECT count(*) FROM bank_demo_alert WHERE code='ALERT-REVIEW-CR102' AND related_proposal_id='CR-102'), " +
                     "(SELECT count(*) FROM bank_demo_transaction h JOIN bank_demo_alert al ON al.code='ALERT-4800' JOIN bank_demo_transaction flagged ON flagged.id=al.transaction_id WHERE h.account_id=flagged.account_id AND h.direction='DEBIT' AND h.occurred_at < flagged.occurred_at AND h.occurred_at >= flagged.occurred_at - INTERVAL '90 days'), " +
                     "(SELECT max(h.amount) FROM bank_demo_transaction h JOIN bank_demo_alert al ON al.code='ALERT-4800' JOIN bank_demo_transaction flagged ON flagged.id=al.transaction_id WHERE h.account_id=flagged.account_id AND h.direction='DEBIT' AND h.occurred_at < flagged.occurred_at AND h.occurred_at >= flagged.occurred_at - INTERVAL '90 days'), " +
                     "(SELECT count(*) FROM bank_demo_transaction t JOIN bank_demo_alert al ON al.transaction_id=t.id WHERE al.code='ALERT-4800' AND t.occurred_at=TIMESTAMPTZ '2026-11-01 12:00:00+00'), " +
                     "(SELECT max(version::integer) FROM flyway_schema_history WHERE version ~ '^[0-9]+$')")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(2);
            assertThat(result.getInt(2)).isEqualTo(30);
            assertThat(result.getInt(3)).isEqualTo(4);
            assertThat(result.getInt(4)).isEqualTo(3);
            assertThat(result.getInt(5)).isEqualTo(5);
            assertThat(result.getInt(6)).isEqualTo(1);
            assertThat(result.getInt(7)).isZero();
            assertThat(result.getInt(8)).isEqualTo(1);
            assertThat(result.getInt(9)).isEqualTo(12);
            assertThat(result.getBigDecimal(10)).isEqualByComparingTo("480.00");
            assertThat(result.getInt(11)).isEqualTo(1);
            assertThat(result.getInt(12)).isEqualTo(6);
        }

        UUID expiredSessionId = UUID.randomUUID();
        UUID activeSessionId = UUID.randomUUID();
        Instant now = Instant.now();
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            try (var insert = connection.prepareStatement("INSERT INTO bank_demo_session(id, created_at, expires_at, selected_mode) VALUES (?, ?, ?, 'CLIENT')")) {
                insert.setObject(1, expiredSessionId); insert.setTimestamp(2, Timestamp.from(now.minusSeconds(4 * 60 * 60)));
                insert.setTimestamp(3, Timestamp.from(now)); insert.executeUpdate();
                insert.setObject(1, activeSessionId); insert.setTimestamp(2, Timestamp.from(now));
                insert.setTimestamp(3, Timestamp.from(now.plusSeconds(4 * 60 * 60))); insert.executeUpdate();
            }
            try (var insert = connection.prepareStatement("INSERT INTO bank_demo_session_overlay(session_id, overlay_key, payload) VALUES (?, 'selected_mode', '{\"mode\":\"CLIENT\"}'::jsonb)")) {
                insert.setObject(1, expiredSessionId); insert.executeUpdate();
                insert.setObject(1, activeSessionId); insert.executeUpdate();
            }
            try (var insert = connection.prepareStatement("INSERT INTO bank_demo_session_note(id, session_id, body) VALUES (?, ?, ?)")) {
                insert.setObject(1, UUID.randomUUID()); insert.setObject(2, expiredSessionId); insert.setString(3, "nota A"); insert.executeUpdate();
                insert.setObject(1, UUID.randomUUID()); insert.setObject(2, activeSessionId); insert.setString(3, "nota B"); insert.executeUpdate();
            }
            try (var insert = connection.prepareStatement("INSERT INTO bank_demo_session_message(id, session_id, role, body) VALUES (?, ?, 'USER', ?)")) {
                insert.setObject(1, UUID.randomUUID()); insert.setObject(2, expiredSessionId); insert.setString(3, "pergunta A"); insert.executeUpdate();
                insert.setObject(1, UUID.randomUUID()); insert.setObject(2, activeSessionId); insert.setString(3, "pergunta B"); insert.executeUpdate();
            }
            try (var cleanup = connection.prepareStatement("DELETE FROM bank_demo_session WHERE expires_at <= ?")) {
                cleanup.setTimestamp(1, Timestamp.from(now)); assertThat(cleanup.executeUpdate()).isEqualTo(1);
            }
            try (var verify = connection.createStatement(); var rows = verify.executeQuery("SELECT " +
                    "(SELECT count(*) FROM bank_demo_session_overlay), " +
                    "(SELECT count(*) FROM bank_demo_session_note), " +
                    "(SELECT count(*) FROM bank_demo_session_message), " +
                    "(SELECT count(*) FROM bank_demo_transaction), " +
                    "(SELECT count(*) FROM bank_demo_session WHERE id='" + activeSessionId + "'), " +
                    "(SELECT count(*) FROM bank_demo_session WHERE id='" + expiredSessionId + "'), " +
                    "(SELECT count(*) FROM bank_demo_session_note WHERE session_id='" + activeSessionId + "' AND body='nota B'), " +
                    "(SELECT count(*) FROM bank_demo_session_message WHERE session_id='" + activeSessionId + "' AND body='pergunta B')")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(1);
                assertThat(rows.getInt(2)).isEqualTo(1);
                assertThat(rows.getInt(3)).isEqualTo(1);
                assertThat(rows.getInt(4)).isEqualTo(30);
                assertThat(rows.getInt(5)).isEqualTo(1);
                assertThat(rows.getInt(6)).isZero();
                assertThat(rows.getInt(7)).isEqualTo(1);
                assertThat(rows.getInt(8)).isEqualTo(1);
            }
            try (var cleanup = connection.prepareStatement("DELETE FROM bank_demo_session WHERE id = ?")) {
                cleanup.setObject(1, activeSessionId); cleanup.executeUpdate();
            }
        }
    }
}
