package dev.evidencerag.banking.infrastructure;

import dev.evidencerag.banking.domain.BankDemoData;
import dev.evidencerag.banking.domain.DemoMode;
import dev.evidencerag.banking.domain.DemoSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class BankingDemoRepository {
    private final JdbcTemplate jdbc;

    public BankingDemoRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<DemoSession> findSession(UUID id) {
        return jdbc.query("SELECT id, created_at, expires_at, selected_mode FROM bank_demo_session WHERE id = ?",
                this::session, id).stream().findFirst();
    }

    public Optional<DemoSession> findActiveSession(UUID id, Instant now) {
        return jdbc.query("SELECT id, created_at, expires_at, selected_mode FROM bank_demo_session WHERE id = ? AND expires_at > ?",
                this::session, id, Timestamp.from(now)).stream().findFirst();
    }

    public DemoSession createSession(UUID id, Instant createdAt, Instant expiresAt, DemoMode mode) {
        jdbc.update("INSERT INTO bank_demo_session(id, created_at, expires_at, selected_mode) VALUES (?, ?, ?, ?)",
                id, Timestamp.from(createdAt), Timestamp.from(expiresAt), mode.name());
        return new DemoSession(id, createdAt, expiresAt, mode);
    }

    public void updateMode(UUID id, DemoMode mode) {
        jdbc.update("UPDATE bank_demo_session SET selected_mode = ? WHERE id = ?", mode.name(), id);
        jdbc.update("INSERT INTO bank_demo_session_overlay(session_id, overlay_key, payload, updated_at) VALUES (?, 'selected_mode', ?::jsonb, now()) " +
                        "ON CONFLICT (session_id, overlay_key) DO UPDATE SET payload = EXCLUDED.payload, updated_at = now()",
                id, "{\"mode\":\"" + mode.name() + "\"}");
    }

    public void deleteSession(UUID id) { jdbc.update("DELETE FROM bank_demo_session WHERE id = ?", id); }

    public void saveSessionNote(UUID id, UUID sessionId, String body) {
        jdbc.update("INSERT INTO bank_demo_session_note(id, session_id, body) VALUES (?, ?, ?)", id, sessionId, body);
    }

    public void saveConversationMessage(UUID id, UUID sessionId, DemoMode mode, String role, String body,
            String outcome, String answerMode, String providerIssue, String evidenceJson) {
        jdbc.update("INSERT INTO bank_demo_session_message(id, session_id, role, body, experience_mode, outcome, answer_mode, provider_issue, evidence) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)", id, sessionId, role, body, mode.name(), outcome, answerMode, providerIssue, evidenceJson);
    }

    public List<SessionMessage> conversationMessages(UUID sessionId, DemoMode mode, int limit) {
        return jdbc.query("SELECT id, role, body, experience_mode, outcome, answer_mode, provider_issue, evidence::text, created_at " +
                        "FROM bank_demo_session_message WHERE session_id = ? AND experience_mode = ? " +
                        "ORDER BY created_at DESC, id DESC LIMIT ?",
                (rs, row) -> new SessionMessage(rs.getObject("id", UUID.class), rs.getString("role"), rs.getString("body"),
                        DemoMode.valueOf(rs.getString("experience_mode")), rs.getString("outcome"), rs.getString("answer_mode"),
                        rs.getString("provider_issue"), rs.getString("evidence"), rs.getTimestamp("created_at").toInstant()),
                sessionId, mode.name(), Math.max(1, Math.min(limit, 100))).stream().sorted(
                java.util.Comparator.comparing(SessionMessage::createdAt).thenComparing(SessionMessage::id)).toList();
    }

    public record SessionMessage(UUID id, String role, String body, DemoMode mode, String outcome,
                                 String answerMode, String providerIssue, String evidenceJson, Instant createdAt) {}

    /** Atomic per-session interaction budget. Conversation content is stored separately with mode scoping. */
    public boolean consumeAiInteraction(UUID sessionId, int limit) {
        return !jdbc.query("INSERT INTO bank_demo_session_overlay(session_id, overlay_key, payload, updated_at) " +
                        "VALUES (?, 'ai_usage', jsonb_build_object('count', 1), now()) " +
                        "ON CONFLICT (session_id, overlay_key) DO UPDATE " +
                        "SET payload = jsonb_set(bank_demo_session_overlay.payload, '{count}', " +
                        "to_jsonb((bank_demo_session_overlay.payload->>'count')::int + 1)), updated_at = now() " +
                        "WHERE (bank_demo_session_overlay.payload->>'count')::int < ? RETURNING session_id",
                (rs, row) -> rs.getObject(1, UUID.class), sessionId, limit).isEmpty();
    }

    public int deleteExpiredSessions(Instant now) {
        return jdbc.update("DELETE FROM bank_demo_session WHERE expires_at <= ?", Timestamp.from(now));
    }

    public List<BankDemoData.Customer> customers() {
        return jdbc.query("SELECT id, display_name, scenario_code FROM bank_demo_customer ORDER BY id",
                (rs, row) -> new BankDemoData.Customer(rs.getString("id"), rs.getString("display_name"), rs.getString("scenario_code")));
    }

    public List<BankDemoData.Account> accounts() {
        return jdbc.query("SELECT a.id, a.customer_id, c.display_name, a.account_type, a.currency, a.opening_balance, a.current_balance " +
                        "FROM bank_demo_account a JOIN bank_demo_customer c ON c.id = a.customer_id ORDER BY a.id",
                (rs, row) -> new BankDemoData.Account(rs.getString("id"), rs.getString("customer_id"), rs.getString("display_name"),
                        rs.getString("account_type"), rs.getString("currency"), rs.getBigDecimal("opening_balance"), rs.getBigDecimal("current_balance")));
    }

    public List<BankDemoData.Transaction> transactions() {
        return jdbc.query("SELECT id, account_id, occurred_at, description, category, amount, direction, alert_id " +
                        "FROM bank_demo_transaction ORDER BY occurred_at DESC, id",
                (rs, row) -> new BankDemoData.Transaction(rs.getString("id"), rs.getString("account_id"), rs.getTimestamp("occurred_at").toInstant(),
                        rs.getString("description"), rs.getString("category"), rs.getBigDecimal("amount"), rs.getString("direction"), rs.getString("alert_id")));
    }

    public List<BankDemoData.Alert> alerts() {
        return jdbc.query("SELECT id, code, transaction_id, related_proposal_id, rule_code, summary FROM bank_demo_alert ORDER BY id",
                (rs, row) -> new BankDemoData.Alert(rs.getString("id"), rs.getString("code"), rs.getString("transaction_id"), rs.getString("related_proposal_id"), rs.getString("rule_code"), rs.getString("summary")));
    }

    public List<BankDemoData.Proposal> proposals() {
        return jdbc.query("SELECT id, company_name, requested_amount, proposed_term_months, contract_term_months, " +
                        "required_document_count, received_document_count, missing_document, policy_id FROM bank_demo_credit_proposal ORDER BY id",
                (rs, row) -> new BankDemoData.Proposal(rs.getString("id"), rs.getString("company_name"), rs.getBigDecimal("requested_amount"),
                        rs.getInt("proposed_term_months"), rs.getInt("contract_term_months"), rs.getInt("required_document_count"),
                        rs.getInt("received_document_count"), rs.getString("missing_document"), rs.getString("policy_id")));
    }

    public List<BankDemoData.Policy> policies() {
        return jdbc.query("SELECT id, version, title, section, effective_on, rule_text FROM bank_demo_policy ORDER BY id",
                (rs, row) -> new BankDemoData.Policy(rs.getString("id"), rs.getString("version"), rs.getString("title"),
                        rs.getString("section"), rs.getDate("effective_on").toLocalDate(), rs.getString("rule_text")));
    }

    /** PostgreSQL full-text retrieval over the immutable, versioned synthetic policy catalogue. */
    public List<BankDemoData.Policy> searchPolicies(String query, String policyId, int limit) {
        String tsQuery = java.util.Arrays.stream(query.toLowerCase(java.util.Locale.ROOT).split("[^\\p{L}0-9]+"))
                .filter(token -> token.length() > 2).distinct().limit(20).map(token -> token + ":*")
                .collect(java.util.stream.Collectors.joining(" | "));
        if (tsQuery.isBlank()) return List.of();
        return jdbc.query("SELECT id, version, title, section, effective_on, rule_text FROM bank_demo_policy " +
                        "WHERE (?::varchar IS NULL OR id = ?) AND " +
                        "to_tsvector('portuguese', concat_ws(' ', title, section, rule_text)) @@ to_tsquery('portuguese', ?) " +
                        "ORDER BY ts_rank(to_tsvector('portuguese', concat_ws(' ', title, section, rule_text)), to_tsquery('portuguese', ?)) DESC, id LIMIT ?",
                (rs, row) -> new BankDemoData.Policy(rs.getString("id"), rs.getString("version"), rs.getString("title"),
                        rs.getString("section"), rs.getDate("effective_on").toLocalDate(), rs.getString("rule_text")),
                policyId, policyId, tsQuery, tsQuery, Math.max(1, Math.min(limit, 5)));
    }

    private DemoSession session(ResultSet rs, int row) throws SQLException {
        return new DemoSession(rs.getObject("id", UUID.class), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("expires_at").toInstant(), DemoMode.valueOf(rs.getString("selected_mode")));
    }
}
