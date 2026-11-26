package dev.evidencerag.banking.api;

import dev.evidencerag.banking.domain.BankDemoData;
import dev.evidencerag.banking.domain.DemoMode;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;

public final class BankingDemoDtos {
    private BankingDemoDtos() {}

    public record StartRequest(@NotNull DemoMode mode) {}
    public record ModeRequest(@NotNull DemoMode mode) {}
    public record SessionResponse(Instant createdAt, Instant expiresAt, DemoMode selectedMode) {}
    public record CustomerResponse(String id, String displayName, String scenarioCode) {}
    public record AccountResponse(String id, String customerId, String customerName, String accountType,
                                  String currency, BigDecimal openingBalance, BigDecimal currentBalance) {}
    public record TransactionResponse(String id, String accountId, Instant occurredAt, String description,
                                      String category, BigDecimal amount, String direction, String alertId) {}
    public record AlertResponse(String id, String code, String transactionId, String relatedProposalId, String ruleCode, String summary) {}
    public record ProposalResponse(String id, String companyName, BigDecimal requestedAmount, int proposedTermMonths,
                                  int contractTermMonths, int requiredDocumentCount, int receivedDocumentCount,
                                  String missingDocument, String policyId) {}
    public record PolicyResponse(String id, String version, String title, String section, LocalDate effectiveOn, String ruleText) {}
    public record DemoResponse(SessionResponse session, List<CustomerResponse> customers, List<AccountResponse> accounts,
                               List<TransactionResponse> transactions, List<AlertResponse> alerts,
                               List<ProposalResponse> proposals, List<PolicyResponse> policies) {
        static DemoResponse from(BankDemoData.Snapshot data) { return from(data, true); }

        static DemoResponse from(BankDemoData.Snapshot data, boolean analystEnabled) {
            var session = data.session();
            var customers = analystEnabled ? data.customers() : data.customers().stream().filter(c -> "CUST-001".equals(c.id())).toList();
            var accounts = analystEnabled ? data.accounts() : data.accounts().stream().filter(a -> "CUST-001".equals(a.customerId())).toList();
            Set<String> accountIds = accounts.stream().map(BankDemoData.Account::id).collect(Collectors.toSet());
            var transactions = analystEnabled ? data.transactions() : data.transactions().stream().filter(t -> accountIds.contains(t.accountId())).toList();
            Set<String> transactionIds = transactions.stream().map(BankDemoData.Transaction::id).collect(Collectors.toSet());
            var alerts = analystEnabled ? data.alerts() : data.alerts().stream().filter(a -> transactionIds.contains(a.transactionId())).toList();
            var policies = analystEnabled ? data.policies() : data.policies().stream().filter(p -> "POL-ALERT".equals(p.id())).toList();
            return new DemoResponse(new SessionResponse(session.createdAt(), session.expiresAt(), session.selectedMode()),
                    customers.stream().map(c -> new CustomerResponse(c.id(), c.displayName(), c.scenarioCode())).toList(),
                    accounts.stream().map(a -> new AccountResponse(a.id(), a.customerId(), a.customerName(), a.accountType(), a.currency(), a.openingBalance(), a.currentBalance())).toList(),
                    transactions.stream().map(t -> new TransactionResponse(t.id(), t.accountId(), t.occurredAt(), t.description(), t.category(), t.amount(), t.direction(), t.alertId())).toList(),
                    alerts.stream().map(a -> new AlertResponse(a.id(), a.code(), a.transactionId(), analystEnabled ? a.relatedProposalId() : null, a.ruleCode(), a.summary())).toList(),
                    analystEnabled ? data.proposals().stream().map(p -> new ProposalResponse(p.id(), p.companyName(), p.requestedAmount(), p.proposedTermMonths(), p.contractTermMonths(), p.requiredDocumentCount(), p.receivedDocumentCount(), p.missingDocument(), p.policyId())).toList() : List.of(),
                    policies.stream().map(p -> new PolicyResponse(p.id(), p.version(), p.title(), p.section(), p.effectiveOn(), p.ruleText())).toList());
        }
    }
    public record ErrorResponse(String code, String message) {}
}
