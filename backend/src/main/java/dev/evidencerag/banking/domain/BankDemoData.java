package dev.evidencerag.banking.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class BankDemoData {
    private BankDemoData() {}

    public record Customer(String id, String displayName, String scenarioCode) {}
    public record Account(String id, String customerId, String customerName, String accountType,
                          String currency, BigDecimal openingBalance, BigDecimal currentBalance) {}
    public record Transaction(String id, String accountId, Instant occurredAt, String description,
                              String category, BigDecimal amount, String direction, String alertId) {}
    public record Alert(String id, String code, String transactionId, String relatedProposalId, String ruleCode, String summary) {}
    public record Proposal(String id, String companyName, BigDecimal requestedAmount, int proposedTermMonths,
                          int contractTermMonths, int requiredDocumentCount, int receivedDocumentCount,
                          String missingDocument, String policyId) {}
    public record Policy(String id, String version, String title, String section, LocalDate effectiveOn, String ruleText) {}
    public record Snapshot(DemoSession session, List<Customer> customers, List<Account> accounts,
                           List<Transaction> transactions, List<Alert> alerts, List<Proposal> proposals,
                           List<Policy> policies) {}
}
