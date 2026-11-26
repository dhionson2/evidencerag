package dev.evidencerag.banking.application;

import dev.evidencerag.banking.domain.BankDemoData;
import dev.evidencerag.banking.domain.DemoMode;
import dev.evidencerag.banking.infrastructure.BankingDemoRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import static dev.evidencerag.banking.application.BankingToolEvidence.*;

/** Per-request tool object. Session and mode come from the trusted cookie-backed backend context. */
public final class BankingDemoTools {
    public enum Module { FINANCE, FRAUD, CREDIT, COMPLIANCE, CASE_REVIEW }

    private static final Pattern ACCOUNT = Pattern.compile("ACC-[0-9]{3}");
    private static final Pattern ALERT = Pattern.compile("ALERT-[A-Z0-9-]{1,32}");
    private static final Pattern PROPOSAL = Pattern.compile("CR-[0-9]{3}");
    private final UUID sessionId;
    private final Module module;
    private final BankingDemoService sessions;
    private final BankingDemoRepository repository;
    private final List<Evidence> collected = new ArrayList<>();

    public BankingDemoTools(UUID sessionId, Module module, BankingDemoService sessions, BankingDemoRepository repository) {
        this.sessionId = sessionId;
        this.module = module;
        this.sessions = sessions;
        this.repository = repository;
    }

    public List<Evidence> evidence() { return List.copyOf(collected); }

    /** Removes model-selected evidence before a deterministic recovery query starts. */
    public void clearEvidence() { collected.clear(); }

    @Tool(description = "Consulta o saldo e os dados resumidos de uma conta fictícia autorizada na sessão atual.")
    public ToolResult getDemoAccountSummary(String accountId) {
        var data = active(); requireModule(Module.FINANCE, Module.FRAUD);
        String id = account(accountId, data.session().selectedMode());
        BankDemoData.Account account = data.accounts().stream().filter(a -> a.id().equals(id)).findFirst().orElse(null);
        if (account == null) return empty("Conta não encontrada no escopo desta sessão.");
        Evidence ref = add(fact("account", account.id(), "Conta " + account.id(), "Saldo atual " + account.currentBalance() + " " + account.currency()));
        return result("Consulta concluída.", List.of("Conta " + account.id(), "Saldo atual: " + account.currentBalance() + " " + account.currency()), List.of(ref));
    }

    @Tool(description = "Lista até trinta transações fictícias de uma conta permitida, opcionalmente filtradas por categoria.")
    public ToolResult listDemoTransactions(String accountId, @ToolParam(required = false, description = "Categoria fictícia opcional") String category, int limit) {
        var data = active(); requireModule(Module.FINANCE, Module.FRAUD);
        String id = account(accountId, data.session().selectedMode());
        if (limit < 1 || limit > 30) throw new IllegalArgumentException("limit deve estar entre 1 e 30.");
        if (category != null && (category.length() > 60 || !category.matches("[\\p{L}0-9 _.-]+"))) throw new IllegalArgumentException("Categoria inválida.");
        var rows = data.transactions().stream().filter(t -> t.accountId().equals(id))
                .filter(t -> category == null || t.category().equalsIgnoreCase(category.trim()))
                .sorted(Comparator.comparing(BankDemoData.Transaction::occurredAt).reversed()).limit(limit).toList();
        List<Evidence> refs = new ArrayList<>();
        List<String> facts = new ArrayList<>();
        for (var tx : rows) {
            String text = tx.id() + " | " + tx.occurredAt() + " | " + tx.direction() + " " + tx.amount() + " BRL | " + tx.category() + " | " + tx.description();
            facts.add(text); refs.add(add(fact("transaction", tx.id(), tx.description() + " · " + tx.category(), text)));
        }
        return result(rows.isEmpty() ? "Nenhuma transação corresponde ao filtro." : "Transações recuperadas.", facts, refs);
    }

    @Tool(description = "Calcula no backend despesas fictícias reais. Sem categoria, analisa o período da tela (as seis movimentações mais recentes) e ordena apenas débitos; com categoria, calcula todas as despesas daquela categoria da conta. Use para maiores despesas, totais por categoria e maior compra. Retorna período, cálculo e fontes por transação.")
    public ToolResult summarizeDemoSpending(String accountId,
            @ToolParam(required = false, description = "Categoria opcional como Alimentação ou Compras") String category,
            @ToolParam(description = "Quantidade máxima de transações a citar, de 1 a 30") int limit) {
        var data = active(); requireModule(Module.FINANCE, Module.FRAUD);
        String id = account(accountId, data.session().selectedMode());
        if (limit < 1 || limit > 30) throw new IllegalArgumentException("limit deve estar entre 1 e 30.");
        if (category != null && (category.isBlank() || category.length() > 60 || !category.matches("[\\p{L}0-9 _.-]+"))) {
            throw new IllegalArgumentException("Categoria inválida.");
        }
        var periodRows = data.transactions().stream().filter(t -> t.accountId().equals(id))
                .filter(t -> category == null || t.category().equalsIgnoreCase(category.trim()))
                .sorted(Comparator.comparing(BankDemoData.Transaction::occurredAt).reversed())
                .limit(category == null ? 6 : 30).toList();
        var rows = periodRows.stream().filter(t -> "DEBIT".equals(t.direction())).toList();
        if (rows.isEmpty()) return empty("Nenhuma despesa corresponde ao filtro informado.");
        BigDecimal total = rows.stream().map(BankDemoData.Transaction::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        var ranked = rows.stream().sorted(Comparator.comparing(BankDemoData.Transaction::amount).reversed())
                .limit(limit).toList();
        String from = rows.stream().map(BankDemoData.Transaction::occurredAt).min(Comparator.naturalOrder()).orElseThrow().toString();
        String to = rows.stream().map(BankDemoData.Transaction::occurredAt).max(Comparator.naturalOrder()).orElseThrow().toString();
        var refs = new ArrayList<Evidence>();
        var facts = new ArrayList<String>();
        facts.add("Total calculado no backend: " + total + " BRL; categoria " + (category == null ? "todas" : category) + "; período " + from + " a " + to + ".");
        for (var tx : ranked) {
            String text = tx.id() + " | " + tx.occurredAt() + " | DEBIT " + tx.amount() + " BRL | " + tx.category() + " | " + tx.description();
            facts.add(text);
            refs.add(add(fact("transaction", tx.id(), tx.description() + " · " + tx.category(), text)));
        }
        return result("Resumo calculado deterministicamente sobre o ledger fictício.", facts, refs);
    }

    @Tool(description = "Recupera contexto verificável de um alerta sintético e a movimentação relacionada; alerta não significa fraude comprovada.")
    public ToolResult getFraudAlertContext(String alertCode) {
        var data = active(); requireModule(Module.FRAUD, Module.FINANCE, Module.CASE_REVIEW);
        if (alertCode == null || !ALERT.matcher(alertCode).matches()) throw new IllegalArgumentException("Código de alerta inválido.");
        var alert = data.alerts().stream().filter(a -> a.code().equals(alertCode)).findFirst().orElse(null);
        if (alert == null) return empty("Alerta não encontrado.");
        var tx = data.transactions().stream().filter(t -> t.id().equals(alert.transactionId())).findFirst().orElse(null);
        var facts = new ArrayList<String>(); var refs = new ArrayList<Evidence>();
        String summary = alert.code() + " · " + alert.ruleCode() + " · " + alert.summary();
        facts.add(summary); refs.add(add(fact("alert", alert.id(), "Alerta de compra · " + alert.ruleCode(), summary)));
        if (tx != null) {
            String txn = tx.id() + " · " + tx.occurredAt() + " · " + tx.direction() + " " + tx.amount() + " BRL · " + tx.description();
            facts.add(txn); refs.add(add(fact("transaction", tx.id(), tx.description() + " · " + tx.category(), txn)));
            var historicalDebits = data.transactions().stream().filter(t -> t.accountId().equals(tx.accountId()))
                    .filter(t -> "DEBIT".equals(t.direction()) && t.occurredAt().isBefore(tx.occurredAt())
                            && !t.occurredAt().isBefore(tx.occurredAt().minus(java.time.Duration.ofDays(90))))
                    .toList();
            if (historicalDebits.isEmpty()) {
                facts.add("O catálogo atual não contém débitos anteriores nesta janela de 90 dias; a regra comparativa não pode ser recalculada com os registros disponíveis.");
            } else {
                BigDecimal maxDebit = historicalDebits.stream().map(BankDemoData.Transaction::amount).max(BigDecimal::compareTo).orElseThrow();
                facts.add("Maior débito individual nos 90 dias anteriores: " + maxDebit + " BRL; montante do alerta: " + tx.amount() + " BRL.");
                for (var historical : historicalDebits.stream().limit(5).toList()) {
                    String history = historical.id() + " · " + historical.occurredAt() + " · débito " + historical.amount() + " BRL · " + historical.description();
                    refs.add(add(fact("transaction", historical.id(), "Débito histórico relacionado", history)));
                }
            }
        }
        facts.add("O alerta solicita revisão e não comprova fraude.");
        return result("Contexto de revisão recuperado; nenhuma conclusão automática.", facts, refs);
    }

    @Tool(description = "Consulta uma proposta de crédito fictícia, seus documentos recebidos e divergência de prazo.")
    public ToolResult getCreditProposal(String proposalId) {
        var data = active(); requireModule(Module.CREDIT, Module.COMPLIANCE, Module.CASE_REVIEW);
        analyst(data.session().selectedMode());
        if (proposalId == null || !PROPOSAL.matcher(proposalId).matches()) throw new IllegalArgumentException("Identificador de proposta inválido.");
        var proposal = data.proposals().stream().filter(p -> p.id().equals(proposalId)).findFirst().orElse(null);
        if (proposal == null) return empty("Proposta não encontrada.");
        String text = proposal.id() + " · valor " + proposal.requestedAmount() + " BRL · documentos " + proposal.receivedDocumentCount() + "/" + proposal.requiredDocumentCount()
                + " · pendência " + (proposal.missingDocument() == null ? "nenhuma" : proposal.missingDocument())
                + " · prazo solicitado " + proposal.proposedTermMonths() + " meses · contrato " + proposal.contractTermMonths() + " meses";
        Evidence ref = add(fact("proposal", proposal.id(), "Proposta " + proposal.id(), text));
        return result("Dados da proposta recuperados; não é uma decisão de crédito.", List.of(text), List.of(ref));
    }

    @Tool(description = "Compara determinística e explicavelmente os dados da proposta com a política fictícia vinculada.")
    public ToolResult compareProposalToPolicy(String proposalId) {
        var data = active(); requireModule(Module.CREDIT, Module.COMPLIANCE, Module.CASE_REVIEW); analyst(data.session().selectedMode());
        if (proposalId == null || !PROPOSAL.matcher(proposalId).matches()) throw new IllegalArgumentException("Identificador de proposta inválido.");
        var p = data.proposals().stream().filter(x -> x.id().equals(proposalId)).findFirst().orElse(null);
        if (p == null) return empty("Proposta não encontrada.");
        var policy = data.policies().stream().filter(x -> x.id().equals(p.policyId())).findFirst().orElse(null);
        if (policy == null) return empty("Política vinculada indisponível.");
        boolean docs = p.receivedDocumentCount() == p.requiredDocumentCount();
        boolean term = p.proposedTermMonths() == p.contractTermMonths();
        String fact = p.id() + ": documentos " + (docs ? "completos" : "incompletos (" + p.receivedDocumentCount() + "/" + p.requiredDocumentCount() + ")")
                + "; prazo " + (term ? "coerente" : "divergente (proposta " + p.proposedTermMonths() + ", contrato " + p.contractTermMonths() + " meses)")
                + ". Política " + policy.id() + " v" + policy.version() + " §" + policy.section() + ". Revisão humana necessária; sem aprovação/rejeição automática.";
        Evidence proposalRef = add(fact("proposal", p.id(), "Proposta " + p.id(), fact));
        Evidence policyRef = add(policyEvidence(policy));
        return result("Comparação determinística concluída; encaminhamento é demonstrativo.", List.of(fact, policy.ruleText()), List.of(proposalRef, policyRef));
    }

    @Tool(description = "Recupera trechos relevantes das políticas bancárias fictícias por busca textual, com versão e seção; consulta sem resultado deve ser tratada como evidência insuficiente.")
    public ToolResult searchPolicyEvidence(String query, @ToolParam(required = false, description = "Restringe a uma política conhecida, por exemplo POL-CRED") String policyId) {
        var data = active(); requireModule(Module.CREDIT, Module.COMPLIANCE, Module.FRAUD, Module.CASE_REVIEW);
        if (query == null || query.isBlank() || query.length() > 300) throw new IllegalArgumentException("Consulta de política deve ter de 1 a 300 caracteres.");
        if (policyId != null && !policyId.matches("POL-[A-Z]{3,12}")) throw new IllegalArgumentException("Identificador de política inválido.");
        var ranked = repository.searchPolicies(query, policyId, 3);
        var refs = new ArrayList<Evidence>(); var facts = new ArrayList<String>();
        for (var p : ranked) {
            String text = "Política " + p.id() + " v" + p.version() + " · " + p.title() + " · §" + p.section() + " · vigente desde " + p.effectiveOn() + ": " + p.ruleText();
            facts.add(text); refs.add(add(policyEvidence(p)));
        }
        return result(ranked.isEmpty() ? "INSUFFICIENT_EVIDENCE: nenhuma política correspondeu à busca." : "Trechos recuperados do catálogo fictício versionado.", facts, refs);
    }

    @Tool(description = "Registra somente uma nota simulada de revisão na sessão anônima atual. Nunca altera contas, alertas ou propostas.")
    public ToolResult saveSimulatedReviewNote(String caseId, String note) {
        var data = active(); requireModule(Module.FRAUD, Module.CREDIT, Module.COMPLIANCE); analyst(data.session().selectedMode());
        if (caseId == null || !(ALERT.matcher(caseId).matches() || PROPOSAL.matcher(caseId).matches())) throw new IllegalArgumentException("Caso inválido.");
        boolean known = data.alerts().stream().anyMatch(a -> a.code().equals(caseId)) || data.proposals().stream().anyMatch(p -> p.id().equals(caseId));
        if (!known) throw new IllegalArgumentException("Caso não encontrado.");
        if (note == null || note.isBlank() || note.length() > 1000) throw new IllegalArgumentException("Nota deve ter de 1 a 1000 caracteres.");
        String safe = note.trim();
        repository.saveSessionNote(UUID.randomUUID(), sessionId, caseId + ": " + safe);
        Evidence ref = add(fact("simulated-review-note", caseId, "Nota simulada " + caseId, "Nota registrada na sessão atual."));
        return result("Nota simulada salva apenas nesta sessão.", List.of("Encaminhamento demonstrativo registrado; nenhuma operação financeira foi realizada."), List.of(ref));
    }

    private BankDemoData.Snapshot active() { return sessions.current(sessionId); }
    private static String account(String id, DemoMode mode) {
        if (id == null || !ACCOUNT.matcher(id).matches()) throw new IllegalArgumentException("Identificador de conta inválido.");
        if (mode == DemoMode.CLIENT && !"ACC-001".equals(id)) throw new IllegalArgumentException("Conta fora do escopo do modo Cliente.");
        return id;
    }
    private static void analyst(DemoMode mode) { if (mode != DemoMode.ANALYST) throw new IllegalArgumentException("Ferramenta disponível somente no modo Analista."); }
    private void requireModule(Module... allowed) { for (Module value : allowed) if (module == value) return; throw new IllegalArgumentException("Ferramenta fora do escopo do módulo selecionado."); }
    private Evidence add(Evidence ref) { collected.add(ref); return ref; }
    private static ToolResult empty(String summary) { return new ToolResult("INSUFFICIENT_EVIDENCE", summary, List.of(), List.of()); }
    private static ToolResult result(String summary, List<String> facts, List<Evidence> evidence) { return new ToolResult("GROUNDED", summary, facts, evidence); }
    private static Evidence policyEvidence(BankDemoData.Policy p) { return fact("policy", p.id() + "@" + p.version() + "#" + p.section(), p.title() + " v" + p.version() + " §" + p.section(), p.ruleText()); }
}
