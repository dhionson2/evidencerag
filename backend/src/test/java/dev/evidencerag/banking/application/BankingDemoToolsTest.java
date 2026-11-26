package dev.evidencerag.banking.application;

import dev.evidencerag.banking.domain.BankDemoData;
import dev.evidencerag.banking.domain.DemoMode;
import dev.evidencerag.banking.domain.DemoSession;
import dev.evidencerag.banking.infrastructure.BankingDemoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BankingDemoToolsTest {
    private final BankingDemoService sessions = mock(BankingDemoService.class);
    private final BankingDemoRepository repository = mock(BankingDemoRepository.class);

    @Test
    void onlyExposesNamedTypedToolsAndValidatesInputsAndModuleScope() throws Exception {
        assertThat(BankingDemoTools.class.getDeclaredMethod("getDemoAccountSummary", String.class).isAnnotationPresent(Tool.class)).isTrue();
        assertThat(BankingDemoTools.class.getDeclaredMethod("listDemoTransactions", String.class, String.class, int.class).isAnnotationPresent(Tool.class)).isTrue();
        UUID session = UUID.randomUUID();
        when(sessions.current(session)).thenReturn(snapshot(session, DemoMode.CLIENT));
        BankingDemoTools finance = new BankingDemoTools(session, BankingDemoTools.Module.FINANCE, sessions, repository);

        var summary = finance.getDemoAccountSummary("ACC-001");
        assertThat(summary.status()).isEqualTo("GROUNDED");
        assertThat(summary.facts()).anyMatch(f -> f.contains("5.654.00") || f.contains("5654.00"));
        assertThat(summary.evidence()).hasSize(1);
        assertThatThrownBy(() -> finance.getDemoAccountSummary("ACC-002")).isInstanceOf(IllegalArgumentException.class);
        assertThat(finance.listDemoTransactions("ACC-001", null, 30).status()).isEqualTo("GROUNDED");
        assertThatThrownBy(() -> finance.listDemoTransactions("ACC-001", null, 31)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> finance.getCreditProposal("CR-102")).isInstanceOf(IllegalArgumentException.class);
        verify(sessions, atLeast(1)).current(session);
    }

    @Test
    void analystToolsUseTheBackendSessionAndReturnVersionedPolicyEvidence() {
        UUID a = UUID.randomUUID(); UUID b = UUID.randomUUID();
        when(sessions.current(a)).thenReturn(snapshot(a, DemoMode.ANALYST));
        when(sessions.current(b)).thenReturn(snapshot(b, DemoMode.ANALYST));
        var toolsA = new BankingDemoTools(a, BankingDemoTools.Module.CREDIT, sessions, repository);
        var toolsB = new BankingDemoTools(b, BankingDemoTools.Module.COMPLIANCE, sessions, repository);
        when(repository.searchPolicies(anyString(), nullable(String.class), anyInt())).thenReturn(List.of(policy()));

        var comparison = toolsA.compareProposalToPolicy("CR-102");
        assertThat(comparison.facts().getFirst()).contains("4/5", "24", "36", "POL-CRED v2.1", "§4.2 Documentação");
        assertThat(comparison.evidence()).hasSize(2);
        assertThat(toolsB.searchPolicyEvidence("documentos prazo contrato", "POL-CRED").evidence())
                .singleElement().satisfies(ref -> assertThat(ref.label()).contains("v2.1", "§4.2 Documentação"));
        verify(sessions).current(a);
        verify(sessions).current(b);
    }

    @Test
    void spendingSummaryCalculatesOnlyDebitsAndReturnsLargestExpensesInOrderWithSources() {
        UUID session = UUID.randomUUID();
        var base = snapshot(session, DemoMode.CLIENT);
        Instant first = Instant.parse("2026-10-22T12:00:00Z");
        List<BankDemoData.Transaction> transactions = List.of(
                transaction("TX-SALARY", first.plusSeconds(86400), "Salário fictício", "Renda", "6200.00", "CREDIT"),
                transaction("TX-4800", first.plusSeconds(2 * 86400), "Compra fictícia", "Compras", "4800.00", "DEBIT"),
                transaction("TX-480", first.plusSeconds(3 * 86400), "Compra fictícia", "Compras", "480.00", "DEBIT"),
                transaction("TX-170", first.plusSeconds(4 * 86400), "Compra fictícia", "Compras", "170.00", "DEBIT"),
                transaction("TX-120", first.plusSeconds(5 * 86400), "Compra fictícia", "Compras", "120.00", "DEBIT"),
                transaction("TX-85", first.plusSeconds(10 * 86400), "Compra fictícia", "Compras", "85.00", "DEBIT"));
        when(sessions.current(session)).thenReturn(new BankDemoData.Snapshot(base.session(), base.customers(), base.accounts(),
                transactions, base.alerts(), base.proposals(), base.policies()));
        var tools = new BankingDemoTools(session, BankingDemoTools.Module.FINANCE, sessions, repository);

        var result = tools.summarizeDemoSpending("ACC-001", null, 5);

        assertThat(result.status()).isEqualTo("GROUNDED");
        assertThat(result.facts()).anySatisfy(fact -> assertThat(fact).contains("Total calculado no backend: 5655.00 BRL", "2026-10-24", "2026-11-01"));
        assertThat(result.evidence()).extracting(BankingToolEvidence.Evidence::recordId)
                .containsExactly("TX-4800", "TX-480", "TX-170", "TX-120", "TX-85");
        assertThat(result.evidence()).noneMatch(ref -> "TX-SALARY".equals(ref.recordId()));
    }

    @Test
    void onlyPermitsSessionScopedSimulatedReviewWritesForKnownAnalystCases() {
        UUID session = UUID.randomUUID();
        when(sessions.current(session)).thenReturn(snapshot(session, DemoMode.ANALYST));
        var tools = new BankingDemoTools(session, BankingDemoTools.Module.FRAUD, sessions, repository);
        assertThat(tools.saveSimulatedReviewNote("ALERT-4800", "Encaminhar para conferência").status()).isEqualTo("GROUNDED");
        verify(repository).saveSessionNote(any(UUID.class), eq(session), contains("ALERT-4800"));
        assertThatThrownBy(() -> tools.saveSimulatedReviewNote("ALERT-4800", " ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fraudContextRecalculatesTheRuleFromPriorSyntheticDebits() {
        UUID session = UUID.randomUUID();
        var base = snapshot(session, DemoMode.ANALYST);
        Instant alertTime = Instant.parse("2026-11-01T12:00:00Z");
        var transactions = new ArrayList<BankDemoData.Transaction>();
        BigDecimal[] amounts = {new BigDecimal("480.00"), new BigDecimal("85.00"), new BigDecimal("120.00"),
                new BigDecimal("170.00"), new BigDecimal("210.00"), new BigDecimal("28.00"), new BigDecimal("95.00"),
                new BigDecimal("350.00"), new BigDecimal("35.00"), new BigDecimal("74.00"), new BigDecimal("89.00"),
                new BigDecimal("210.00"), new BigDecimal("60.00")};
        for (int i = 0; i < amounts.length; i++) {
            transactions.add(new BankDemoData.Transaction("HIST-" + i, "ACC-001", alertTime.minusSeconds((i + 1L) * 86400),
                    "Débito sintético histórico", "Compras", amounts[i], "DEBIT", null));
        }
        transactions.add(new BankDemoData.Transaction("TX-1", "ACC-001", alertTime, "Compra fictícia", "Compras",
                new BigDecimal("4800.00"), "DEBIT", "ALT-001"));
        when(sessions.current(session)).thenReturn(new BankDemoData.Snapshot(base.session(), base.customers(), base.accounts(),
                transactions, base.alerts(), base.proposals(), base.policies()));
        var tools = new BankingDemoTools(session, BankingDemoTools.Module.FRAUD, sessions, repository);

        var context = tools.getFraudAlertContext("ALERT-4800");

        assertThat(context.facts()).anyMatch(f -> f.contains("Maior débito individual") && f.contains("480.00 BRL") && f.contains("4800.00 BRL"));
        assertThat(context.facts()).noneMatch(f -> f.contains("não contém débitos anteriores"));
        assertThat(context.evidence()).hasSizeGreaterThan(2);
        assertThat(String.join(" ", context.facts())).contains("não comprova fraude");
    }

    @Test
    void fraudContextReportsInsufficientHistoryWithoutClaimingRuleVerified() {
        UUID session = UUID.randomUUID();
        when(sessions.current(session)).thenReturn(snapshot(session, DemoMode.ANALYST));
        var tools = new BankingDemoTools(session, BankingDemoTools.Module.FRAUD, sessions, repository);
        var context = tools.getFraudAlertContext("ALERT-4800");
        assertThat(context.facts()).anyMatch(f -> f.contains("não contém débitos anteriores") && f.contains("não pode ser recalculada"));
        assertThat(String.join(" ", context.facts())).contains("não comprova fraude");
    }

    private static BankDemoData.Snapshot snapshot(UUID id, DemoMode mode) {
        Instant now = Instant.parse("2026-11-25T12:00:00Z");
        var session = new DemoSession(id, now, now.plusSeconds(14400), mode);
        var a = new BankDemoData.Account("ACC-001", "CUST-001", "Cliente A", "Conta corrente", "BRL", new BigDecimal("5600.00"), new BigDecimal("5654.00"));
        var b = new BankDemoData.Account("ACC-002", "CUST-002", "Cliente B", "Conta corrente", "BRL", new BigDecimal("8500.00"), new BigDecimal("12928.00"));
        var proposal = new BankDemoData.Proposal("CR-102", "Comercial Exemplo Ltda.", new BigDecimal("150000.00"), 24, 36, 5, 4, "Autorização de consulta", "POL-CRED");
        var policy = policy();
        var tx = new BankDemoData.Transaction("TX-1", "ACC-001", now, "Compra fictícia", "Compras", new BigDecimal("4800.00"), "DEBIT", "ALT-001");
        var alert = new BankDemoData.Alert("ALT-001", "ALERT-4800", "TX-1", null, "HIGH_VALUE_VS_HISTORY", "Alerta para revisão; não comprova fraude.");
        return new BankDemoData.Snapshot(session, List.of(), List.of(a, b), List.of(tx), List.of(alert), List.of(proposal), List.of(policy));
    }

    private static BankDemoData.Policy policy() {
        return new BankDemoData.Policy("POL-CRED", "2.1", "Política fictícia de crédito PJ", "4.2 Documentação", LocalDate.of(2026, 1, 15), "Cinco documentos são exigidos; o prazo do contrato deve corresponder; pendências seguem para revisão humana.");
    }

    private static BankDemoData.Transaction transaction(String id, Instant at, String description, String category, String amount, String direction) {
        return new BankDemoData.Transaction(id, "ACC-001", at, description, category, new BigDecimal(amount), direction, null);
    }
}
