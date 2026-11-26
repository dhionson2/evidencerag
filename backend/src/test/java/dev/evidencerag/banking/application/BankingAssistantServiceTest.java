package dev.evidencerag.banking.application;

import dev.evidencerag.application.CitationValidationService;
import dev.evidencerag.banking.api.BankingAssistantDtos;
import dev.evidencerag.banking.domain.BankDemoData;
import dev.evidencerag.banking.domain.DemoMode;
import dev.evidencerag.banking.domain.DemoSession;
import dev.evidencerag.banking.infrastructure.BankingDemoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class BankingAssistantServiceTest {
    @Test
    void annualSpendingQuestionWithPartialLedgerReturnsInsufficientEvidenceWithoutCallingTheModel() {
        UUID sessionId = UUID.randomUUID();
        BankingDemoService sessions = mock(BankingDemoService.class);
        BankingDemoRepository repository = mock(BankingDemoRepository.class);
        ChatModel model = mock(ChatModel.class);
        when(sessions.current(sessionId)).thenReturn(clientSnapshot(sessionId));
        when(repository.consumeAiInteraction(sessionId, 20)).thenReturn(true);
        var service = new BankingAssistantService(sessions, repository, model, new CitationValidationService(), 1, 4, 20, 10);

        BankingAssistantDtos.AskResponse answer;
        try { answer = service.ask(sessionId, "Quanto gastei por ano em despesas?", "annual-partial-ledger"); }
        finally { service.stopAiExecutor(); }

        assertThat(answer.status()).isEqualTo("INSUFFICIENT_EVIDENCE");
        assertThat(answer.answerMode()).isEqualTo("DETERMINISTIC_FALLBACK");
        assertThat(answer.providerIssue()).isNull();
        assertThat(answer.evidence()).isEmpty();
        assertThat(answer.answer()).contains("menos de doze meses", "Não vou anualizar");
        verify(repository).consumeAiInteraction(sessionId, 20);
        verifyNoInteractions(model);
    }

    @Test
    void annualSpendingIntentDetectionIsLanguageBasedAndNotAResponsePhraseList() {
        assertThat(BankingAssistantService.requestsAnnualSpendingEstimate("Estime meu gasto anual de alimentação" )).isTrue();
        assertThat(BankingAssistantService.requestsAnnualSpendingEstimate("Quanto gastei por ano em compras?" )).isTrue();
        assertThat(BankingAssistantService.requestsAnnualSpendingEstimate("Quais foram minhas maiores despesas?" )).isFalse();
    }

    @Test
    void groqRequestOptionsBoundOutputAndDisableUnsupportedParallelReasoningReplay() {
        var options = BankingAssistantService.groqRequestOptions("openai/gpt-oss-20b", 600).build();

        assertThat(options.getModel()).isEqualTo("openai/gpt-oss-20b");
        assertThat(options.getMaxCompletionTokens()).isEqualTo(600);
        assertThat(options.getParallelToolCalls()).isFalse();
        assertThat(options.getReplayReasoningContent()).isFalse();
        assertThat(options.getStrict()).isTrue();
        assertThat(options.getReasoningEffort()).isEqualTo("low");
        assertThat(BankingAssistantService.groqRequestOptions("model", 10).build().getMaxCompletionTokens()).isEqualTo(64);
        assertThat(BankingAssistantService.groqRequestOptions("model", 50_000).build().getMaxCompletionTokens()).isEqualTo(1200);
    }

    @Test
    void classifiesGroqRejectedToolGenerationSeparatelyFromGenericBadRequests() {
        var toolUseFailure = com.openai.errors.BadRequestException.builder()
                .headers(com.openai.core.http.Headers.builder().build())
                .error(com.openai.models.ErrorObject.builder().code("tool_use_failed")
                        .type("invalid_request_error").param("tools")
                        .message("synthetic tool generation rejection").build())
                .build();

        assertThat(BankingAssistantService.failureCause(toolUseFailure, "400", 1))
                .isEqualTo("TOOL_CALL_GENERATION_FAILED");
        var genericFailure = com.openai.errors.BadRequestException.builder()
                .headers(com.openai.core.http.Headers.builder().build())
                .error(com.openai.models.ErrorObject.builder().code("invalid_request_error")
                        .type("invalid_request_error").param("none").message("synthetic generic rejection").build())
                .build();
        assertThat(BankingAssistantService.failureCause(genericFailure, "400", 1))
                .isEqualTo("PROVIDER_REQUEST_ERROR");
    }

    @Test
    void boundsConversationContextByMessageCountAtRepositoryAndCharacterBudget() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        List<BankingDemoRepository.SessionMessage> messages = List.of(
                new BankingDemoRepository.SessionMessage(UUID.randomUUID(), "USER", "pergunta antiga", DemoMode.CLIENT,
                        "USER", null, null, "[]", now.minusSeconds(2)),
                new BankingDemoRepository.SessionMessage(UUID.randomUUID(), "ASSISTANT", "resposta antiga", DemoMode.CLIENT,
                        "GROUNDED", "AI_TOOL_LOOP", null, "[]", now.minusSeconds(1)),
                new BankingDemoRepository.SessionMessage(id, "USER", "pedido mais recente e comprido", DemoMode.CLIENT,
                        "USER", null, null, "[]", now));

        List<org.springframework.ai.chat.messages.Message> context = BankingAssistantService.boundedContext(messages, 45);

        assertThat(context).hasSize(2);
        assertThat(context.get(0).getText()).isEqualTo("resposta antiga");
        assertThat(context.get(1).getText()).isEqualTo("pedido mais recente e comprido");
        assertThat(context.stream().mapToInt(message -> message.getText().length()).sum()).isEqualTo(45);
    }

    @Test
    void runsTheSpringAiToolLoopAndValidatesEvidenceFromTheSessionScopedBackend() {
        UUID sessionId = UUID.randomUUID();
        BankingDemoService sessions = mock(BankingDemoService.class);
        BankingDemoRepository repository = mock(BankingDemoRepository.class);
        when(sessions.current(sessionId)).thenReturn(clientSnapshot(sessionId));
        when(repository.consumeAiInteraction(sessionId, 20)).thenReturn(true);
        var mapper = new tools.jackson.databind.ObjectMapper();
        AtomicInteger calls = new AtomicInteger();
        ChatModel model = new ChatModel() {
            @Override public org.springframework.ai.chat.prompt.ChatOptions getOptions() {
                return ToolCallingChatOptions.builder().build();
            }
            @Override public ChatResponse call(org.springframework.ai.chat.prompt.Prompt prompt) {
                assertThat(prompt.getOptions()).isInstanceOf(ToolCallingChatOptions.class);
                assertThat(((ToolCallingChatOptions) prompt.getOptions()).getToolCallbacks()).hasSize(4);
                if (calls.incrementAndGet() == 1) {
                    var toolCall = new AssistantMessage.ToolCall("tool-1", "function", "getDemoAccountSummary", "{\"accountId\":\"ACC-001\"}");
                    var response = new ChatResponse(List.of(new Generation(AssistantMessage.builder().toolCalls(List.of(toolCall)).build())));
                    assertThat(response.hasToolCalls()).isTrue();
                    return response;
                }
                var toolMessage = prompt.getInstructions().stream()
                        .filter(message -> message.getMessageType() == org.springframework.ai.chat.messages.MessageType.TOOL)
                        .map(org.springframework.ai.chat.messages.ToolResponseMessage.class::cast).findFirst().orElseThrow();
                assertThat(toolMessage.getResponses()).singleElement();
                var toolResult = mapper.readTree(toolMessage.getResponses().getFirst().responseData());
                assertThat(toolResult.path("status").asString()).isEqualTo("GROUNDED");
                assertThat(toolResult.path("evidence").get(0).path("recordId").asString()).isEqualTo("ACC-001");
                String evidenceId = toolResult.path("evidence").get(0).path("id").asString();
                return new ChatResponse(List.of(new Generation(new AssistantMessage(
                        "{\"answer\":\"Saldo fictício R$ 5.654,00.\",\"citedEvidenceIds\":[\"" + evidenceId + "\"],\"status\":\"GROUNDED\"}"))));
            }
        };

        var service = new BankingAssistantService(sessions, repository, model, new CitationValidationService(), 2, 4, 20, 10);
        BankingAssistantDtos.AskResponse answer;
        try {
            answer = service.ask(sessionId, BankingDemoTools.Module.FINANCE, "Consulte o saldo da ACC-001");
        } finally { service.stopAiExecutor(); }

        assertThat(calls).hasValue(2);
        assertThat(answer.status()).isEqualTo("GROUNDED");
        assertThat(answer.answer()).isEqualTo("Saldo fictício R$ 5.654,00.");
        assertThat(answer.answerMode()).isEqualTo("AI_TOOL_LOOP");
        assertThat(answer.evidence()).singleElement().satisfies(ref -> assertThat(ref.recordId()).isEqualTo("ACC-001"));
        verify(sessions, atLeast(2)).current(sessionId);
    }

    @Test
    void clearsIrrelevantModelToolEvidenceBeforeRecoveringAlertRuleAfterProvider400() {
        UUID sessionId = UUID.randomUUID();
        BankingDemoService sessions = mock(BankingDemoService.class);
        BankingDemoRepository repository = mock(BankingDemoRepository.class);
        when(sessions.current(sessionId)).thenReturn(analystSnapshot(sessionId));
        when(repository.consumeAiInteraction(sessionId, 20)).thenReturn(true);
        when(repository.searchPolicies(anyString(), eq("POL-ALERT"), eq(3))).thenReturn(List.of(
                new dev.evidencerag.banking.domain.BankDemoData.Policy("POL-ALERT", "1.3",
                        "Regras fictícias de alertas transacionais", "2.1 Valor atípico", java.time.LocalDate.parse("2026-02-01"),
                        "Compra acima de R$ 3.500 pode ser sinalizada quando superar três vezes o maior débito individual histórico.")));

        var provider400 = com.openai.errors.BadRequestException.builder()
                .headers(com.openai.core.http.Headers.builder().build())
                .error(com.openai.models.ErrorObject.builder().code("invalid_request_error")
                        .param("messages[1].tool_calls").type("invalid_request_error")
                        .message("synthetic bad request").build())
                .build();
        AtomicInteger roundTrips = new AtomicInteger();
        ChatModel model = new ChatModel() {
            @Override public org.springframework.ai.chat.prompt.ChatOptions getOptions() { return ToolCallingChatOptions.builder().build(); }
            @Override public ChatResponse call(org.springframework.ai.chat.prompt.Prompt prompt) {
                if (roundTrips.incrementAndGet() == 1) {
                    var call = new AssistantMessage.ToolCall("tc-1", "function", "getCreditProposal", "{\"proposalId\":\"CR-102\"}");
                    return new ChatResponse(List.of(new Generation(AssistantMessage.builder().toolCalls(List.of(call)).build(),
                            org.springframework.ai.chat.metadata.ChatGenerationMetadata.builder().finishReason("tool_calls").build())));
                }
                throw provider400;
            }
        };
        var service = new BankingAssistantService(sessions, repository, model, new CitationValidationService(), 1, 4, 20, 10);
        try {
            var result = service.ask(sessionId, "Que regra se aplica à compra sinalizada?");
            assertThat(roundTrips).hasValue(2);
            assertThat(result.answerMode()).isEqualTo("DETERMINISTIC_FALLBACK");
            assertThat(result.providerIssue()).isEqualTo("PROVIDER_REQUEST_ERROR");
            assertThat(result.evidence()).extracting(BankingToolEvidence.Evidence::kind)
                    .contains("alert", "transaction", "policy").doesNotContain("proposal");
            assertThat(result.answer()).contains("POL-ALERT", "Valor atípico");
            verify(repository).searchPolicies(anyString(), eq("POL-ALERT"), eq(3));
        } finally { service.stopAiExecutor(); }
    }

    @Test
    void requiresAlertTransactionAndPolicyCitationsForAlertRuleAnswer() throws Exception {
        UUID sessionId = UUID.randomUUID();
        BankingDemoService sessions = mock(BankingDemoService.class);
        BankingDemoRepository repository = mock(BankingDemoRepository.class);
        when(sessions.current(sessionId)).thenReturn(analystSnapshot(sessionId));
        when(repository.consumeAiInteraction(sessionId, 20)).thenReturn(true);
        when(repository.searchPolicies(anyString(), eq("POL-ALERT"), eq(3))).thenReturn(List.of(
                new dev.evidencerag.banking.domain.BankDemoData.Policy("POL-ALERT", "1.3",
                        "Regras fictícias de alertas transacionais", "2.1 Valor atípico", java.time.LocalDate.parse("2026-02-01"),
                        "Compra acima de R$ 3.500 pode ser sinalizada quando superar três vezes o maior débito individual histórico.")));

        var mapper = new tools.jackson.databind.ObjectMapper();
        AtomicInteger roundTrips = new AtomicInteger();
        ChatModel model = new ChatModel() {
            @Override public org.springframework.ai.chat.prompt.ChatOptions getOptions() { return ToolCallingChatOptions.builder().build(); }
            @Override public ChatResponse call(org.springframework.ai.chat.prompt.Prompt prompt) {
                if (roundTrips.incrementAndGet() == 1) {
                    var call = new AssistantMessage.ToolCall("tc-rule", "function", "getFraudAlertContext", "{\"alertCode\":\"ALERT-4800\"}");
                    return new ChatResponse(List.of(new Generation(AssistantMessage.builder().toolCalls(List.of(call)).build(),
                            org.springframework.ai.chat.metadata.ChatGenerationMetadata.builder().finishReason("tool_calls").build())));
                }
                var toolMessage = prompt.getInstructions().stream()
                        .filter(message -> message.getMessageType() == org.springframework.ai.chat.messages.MessageType.TOOL)
                        .map(org.springframework.ai.chat.messages.ToolResponseMessage.class::cast).findFirst().orElseThrow();
                var toolResult = mapper.readTree(toolMessage.getResponses().getFirst().responseData());
                String alertEvidenceId = null;
                for (var item : toolResult.path("evidence")) {
                    if ("alert".equals(item.path("kind").asString())) alertEvidenceId = item.path("id").asString();
                }
                return new ChatResponse(List.of(new Generation(new AssistantMessage(
                        "{\"answer\":\"A compra se enquadra na regra sintética.\",\"citedEvidenceIds\":[\"" + alertEvidenceId + "\"],\"status\":\"GROUNDED\"}"),
                        org.springframework.ai.chat.metadata.ChatGenerationMetadata.builder().finishReason("stop").build())));
            }
        };
        var service = new BankingAssistantService(sessions, repository, model, new CitationValidationService(), 1, 4, 20, 10);
        try {
            var result = service.ask(sessionId, "Que regra se aplica à compra sinalizada?");
            assertThat(roundTrips).hasValue(2);
            assertThat(result.status()).isEqualTo("GROUNDED");
            assertThat(result.answerMode()).isEqualTo("DETERMINISTIC_FALLBACK");
            assertThat(result.providerIssue()).isEqualTo("MISSING_REQUIRED_EVIDENCE");
            assertThat(result.evidence()).extracting(BankingToolEvidence.Evidence::kind)
                    .contains("alert", "transaction", "policy").doesNotContain("proposal");
            assertThat(result.evidence()).anySatisfy(ref -> assertThat(ref.recordId()).startsWith("POL-ALERT@"));
            assertThat(result.answer()).contains("Regra aplicável", "POL-ALERT");
            verify(repository).searchPolicies(anyString(), eq("POL-ALERT"), eq(3));
        } finally { service.stopAiExecutor(); }
    }

    @Test
    void completesCr102ProposalPolicyToolLoopWithoutRequiringFraudEvidence() throws Exception {
        UUID sessionId = UUID.randomUUID();
        BankingDemoService sessions = mock(BankingDemoService.class);
        BankingDemoRepository repository = mock(BankingDemoRepository.class);
        when(sessions.current(sessionId)).thenReturn(analystSnapshot(sessionId));
        when(repository.consumeAiInteraction(sessionId, 20)).thenReturn(true);

        var mapper = new tools.jackson.databind.ObjectMapper();
        AtomicInteger roundTrips = new AtomicInteger();
        ChatModel model = new ChatModel() {
            @Override public org.springframework.ai.chat.prompt.ChatOptions getOptions() { return ToolCallingChatOptions.builder().build(); }
            @Override public ChatResponse call(org.springframework.ai.chat.prompt.Prompt prompt) {
                int roundTrip = roundTrips.incrementAndGet();
                if (roundTrip == 1) {
                    var call = new AssistantMessage.ToolCall("tc-proposal", "function", "getCreditProposal", "{\"proposalId\":\"CR-102\"}");
                    return new ChatResponse(List.of(new Generation(AssistantMessage.builder().toolCalls(List.of(call)).build(),
                            org.springframework.ai.chat.metadata.ChatGenerationMetadata.builder().finishReason("tool_calls").build())));
                }
                if (roundTrip == 2) {
                    var call = new AssistantMessage.ToolCall("tc-compare", "function", "compareProposalToPolicy", "{\"proposalId\":\"CR-102\"}");
                    return new ChatResponse(List.of(new Generation(AssistantMessage.builder().toolCalls(List.of(call)).build(),
                            org.springframework.ai.chat.metadata.ChatGenerationMetadata.builder().finishReason("tool_calls").build())));
                }
                java.util.Set<String> evidenceIds = new java.util.LinkedHashSet<>();
                for (var toolMessage : prompt.getInstructions().stream()
                        .filter(message -> message.getMessageType() == org.springframework.ai.chat.messages.MessageType.TOOL)
                        .map(org.springframework.ai.chat.messages.ToolResponseMessage.class::cast).toList()) {
                    for (var toolResponse : toolMessage.getResponses()) {
                        var result = mapper.readTree(toolResponse.responseData());
                        for (var evidence : result.path("evidence")) {
                            if ("proposal".equals(evidence.path("kind").asString()) || "policy".equals(evidence.path("kind").asString())) {
                                evidenceIds.add(evidence.path("id").asString());
                            }
                        }
                    }
                }
                assertThat(evidenceIds).hasSize(2);
                String ids = evidenceIds.stream().map(id -> "\"" + id + "\"").collect(java.util.stream.Collectors.joining(","));
                return new ChatResponse(List.of(new Generation(new AssistantMessage(
                        "{\"answer\":\"A CR-102 apresenta 4/5 documentos; a política aplicável exige revisão humana.\",\"citedEvidenceIds\":[" + ids + "],\"status\":\"GROUNDED\"}"),
                        org.springframework.ai.chat.metadata.ChatGenerationMetadata.builder().finishReason("stop").build())));
            }
        };
        var service = new BankingAssistantService(sessions, repository, model, new CitationValidationService(), 1, 4, 20, 10);
        try {
            var result = service.ask(sessionId, "A proposta CR-102 atende à política?");
            assertThat(roundTrips).hasValue(3);
            assertThat(result.status()).isEqualTo("GROUNDED");
            assertThat(result.answerMode()).isEqualTo("AI_TOOL_LOOP");
            assertThat(result.providerIssue()).isNull();
            assertThat(result.evidence()).extracting(BankingToolEvidence.Evidence::kind)
                    .contains("proposal", "policy").doesNotContain("alert", "transaction");
            assertThat(result.evidence()).anySatisfy(ref -> assertThat(ref.recordId()).isEqualTo("CR-102"));
            assertThat(result.evidence()).anySatisfy(ref -> assertThat(ref.recordId()).startsWith("POL-CRED@"));
            verify(sessions, atLeast(3)).current(sessionId);
        } finally { service.stopAiExecutor(); }
    }

    @Test
    void serializesAndExecutesTheTypedToolResultWithSpringAiJsonContract() throws Exception {
        UUID sessionId = UUID.randomUUID();
        BankingDemoService sessions = mock(BankingDemoService.class);
        BankingDemoRepository repository = mock(BankingDemoRepository.class);
        when(sessions.current(sessionId)).thenReturn(clientSnapshot(sessionId));
        BankingDemoTools toolSet = new BankingDemoTools(sessionId, BankingDemoTools.Module.FINANCE, sessions, repository);
        var callback = java.util.Arrays.stream(ToolCallbacks.from(toolSet))
                .filter(item -> item.getToolDefinition().name().equals("getDemoAccountSummary"))
                .findFirst().orElseThrow();

        String result = callback.call("{\"accountId\":\"ACC-001\"}");

        var json = new tools.jackson.databind.ObjectMapper().readTree(result);
        assertThat(json.path("status").asString()).isEqualTo("GROUNDED");
        assertThat(json.path("evidence").get(0).path("recordId").asString()).isEqualTo("ACC-001");
        assertThat(toolSet.evidence()).hasSize(1);
    }

    @Test
    void exposesOnlyTheToolsAllowedForEachModule() {
        assertThat(BankingAssistantService.permittedToolNames(BankingDemoTools.Module.FINANCE))
                .contains("getDemoAccountSummary", "listDemoTransactions", "getFraudAlertContext")
                .doesNotContain("getCreditProposal", "saveSimulatedReviewNote");
        assertThat(BankingAssistantService.permittedToolNames(BankingDemoTools.Module.CREDIT))
                .contains("getCreditProposal", "compareProposalToPolicy", "searchPolicyEvidence", "saveSimulatedReviewNote")
                .doesNotContain("getDemoAccountSummary");
    }

    @Test
    void displaysOnlyEvidenceReturnedByToolsAndAbstainsOnUnknownReferences() {
        var validation = new CitationValidationService();
        var ref = BankingToolEvidence.fact("account", "ACC-001", "Conta ACC-001", "Saldo atual 5654 BRL");
        var grounded = BankingAssistantService.validatedAnswer(
                new BankingAssistantService.GeneratedAnswer("Saldo fictício R$ 5.654,00.", List.of(ref.id()), "GROUNDED"),
                List.of(ref), validation);
        assertThat(grounded.status()).isEqualTo("GROUNDED");
        assertThat(grounded.evidence()).containsExactly(ref);

        var invalid = BankingAssistantService.validatedAnswer(
                new BankingAssistantService.GeneratedAnswer("Resposta sem suporte", List.of(UUID.randomUUID().toString()), "GROUNDED"),
                List.of(ref), validation);
        assertThat(invalid.status()).isEqualTo("INSUFFICIENT_EVIDENCE");
        assertThat(invalid.evidence()).isEmpty();
        assertThat(invalid.answer()).contains("evidências suficientes");
    }

    @Test
    void ignoresLegacyModuleAndScopesToolsFromTheCookieBackedExperience() {
        UUID sessionId = UUID.randomUUID();
        BankingDemoService sessions = mock(BankingDemoService.class);
        BankingDemoRepository repository = mock(BankingDemoRepository.class);
        ChatModel model = noEvidenceModel();
        when(sessions.current(sessionId)).thenReturn(clientSnapshot(sessionId));
        var service = new BankingAssistantService(sessions, repository, model, new CitationValidationService(), 2, 4, 20, 30);
        when(repository.consumeAiInteraction(sessionId, 20)).thenReturn(true);
        var answer = service.ask(sessionId, BankingDemoTools.Module.CREDIT, "Quanto tenho na conta?");
        assertThat(answer.status()).isEqualTo("GROUNDED");
        assertThat(answer.answerMode()).isEqualTo("DETERMINISTIC_FALLBACK");
        verify(repository).consumeAiInteraction(sessionId, 20);
        service.stopAiExecutor();
    }

    @Test
    void enforcesPerSessionInteractionQuotaBeforeCallingTheModel() {
        UUID sessionId = UUID.randomUUID();
        BankingDemoService sessions = mock(BankingDemoService.class);
        BankingDemoRepository repository = mock(BankingDemoRepository.class);
        when(sessions.current(sessionId)).thenReturn(clientSnapshot(sessionId));
        when(repository.consumeAiInteraction(sessionId, 1)).thenReturn(false);
        AtomicInteger modelCalls = new AtomicInteger();
        ChatModel model = new ChatModel() {
            @Override public org.springframework.ai.chat.prompt.ChatOptions getOptions() {
                return ToolCallingChatOptions.builder().build();
            }
            @Override public ChatResponse call(org.springframework.ai.chat.prompt.Prompt prompt) {
                modelCalls.incrementAndGet();
                return noEvidenceModel().call(prompt);
            }
        };
        var service = new BankingAssistantService(sessions, repository, model,
                new CitationValidationService(), 1, 4, 1, 5);

        try {
            assertThatThrownBy(() -> service.ask(sessionId, BankingDemoTools.Module.FINANCE, "Saldo?"))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("Limite de 1 interações");
            verify(repository).consumeAiInteraction(sessionId, 1);
            assertThat(modelCalls).hasValue(0);
        } finally { service.stopAiExecutor(); }
    }

    @Test
    void rejectsConcurrentAiWorkWhenTheGlobalSlotIsOccupied() throws Exception {
        UUID sessionId = UUID.randomUUID();
        BankingDemoService sessions = mock(BankingDemoService.class);
        BankingDemoRepository repository = mock(BankingDemoRepository.class);
        when(sessions.current(sessionId)).thenReturn(clientSnapshot(sessionId));
        when(repository.consumeAiInteraction(sessionId, 20)).thenReturn(true);
        CountDownLatch modelEntered = new CountDownLatch(1);
        CountDownLatch releaseModel = new CountDownLatch(1);
        ChatModel model = new ChatModel() {
            @Override public org.springframework.ai.chat.prompt.ChatOptions getOptions() {
                return ToolCallingChatOptions.builder().build();
            }
            @Override public ChatResponse call(org.springframework.ai.chat.prompt.Prompt prompt) {
                modelEntered.countDown();
                try {
                    if (!releaseModel.await(3, TimeUnit.SECONDS)) throw new IllegalStateException("test timeout");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
                return noEvidenceModel().call(prompt);
            }
        };
        var service = new BankingAssistantService(sessions, repository, model,
                new CitationValidationService(), 1, 4, 20, 5);

        try {
            var first = CompletableFuture.supplyAsync(() -> service.ask(sessionId, BankingDemoTools.Module.FINANCE, "Saldo?"));
            assertThat(modelEntered.await(2, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> service.ask(sessionId, BankingDemoTools.Module.FINANCE, "Saldo?"))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("assistente está ocupado");
            releaseModel.countDown();
            var completed = first.get(5, TimeUnit.SECONDS);
            assertThat(completed.status()).isEqualTo("GROUNDED");
            assertThat(completed.answerMode()).isEqualTo("DETERMINISTIC_FALLBACK");
            verify(repository, times(1)).consumeAiInteraction(sessionId, 20);
        } finally {
            releaseModel.countDown();
            service.stopAiExecutor();
        }
    }

    @Test
    void usesLabeledDeterministicBalanceWhenTheProviderCallTimesOut() {
        UUID sessionId = UUID.randomUUID();
        BankingDemoService sessions = mock(BankingDemoService.class);
        BankingDemoRepository repository = mock(BankingDemoRepository.class);
        when(sessions.current(sessionId)).thenReturn(clientSnapshot(sessionId));
        when(repository.consumeAiInteraction(sessionId, 20)).thenReturn(true);
        ChatModel model = new ChatModel() {
            @Override public org.springframework.ai.chat.prompt.ChatOptions getOptions() {
                return ToolCallingChatOptions.builder().build();
            }
            @Override public ChatResponse call(org.springframework.ai.chat.prompt.Prompt prompt) {
                try { Thread.sleep(TimeUnit.SECONDS.toMillis(3)); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
                return noEvidenceModel().call(prompt);
            }
        };
        var service = new BankingAssistantService(sessions, repository, model,
                new CitationValidationService(), 1, 4, 20, 1);

        try {
            var answer = service.ask(sessionId, BankingDemoTools.Module.FINANCE, "Saldo?");
            assertThat(answer.status()).isEqualTo("GROUNDED");
            assertThat(answer.answerMode()).isEqualTo("DETERMINISTIC_FALLBACK");
            assertThat(answer.providerIssue()).isEqualTo("TIMEOUT");
            assertThat(answer.evidence()).anySatisfy(ref -> assertThat(ref.recordId()).isEqualTo("ACC-001"));
        } finally { service.stopAiExecutor(); }
    }

    @Test
    void returns429WhenProviderQuotaFailureIsSimulated() {
        UUID sessionId = UUID.randomUUID();
        BankingDemoService sessions = mock(BankingDemoService.class);
        BankingDemoRepository repository = mock(BankingDemoRepository.class);
        when(sessions.current(sessionId)).thenReturn(clientSnapshot(sessionId));
        when(repository.consumeAiInteraction(sessionId, 20)).thenReturn(true);
        ChatModel providerQuotaStub = new ChatModel() {
            @Override public org.springframework.ai.chat.prompt.ChatOptions getOptions() {
                return ToolCallingChatOptions.builder().build();
            }
            @Override public ChatResponse call(org.springframework.ai.chat.prompt.Prompt prompt) {
                throw new com.google.genai.errors.ApiException(429, "RESOURCE_EXHAUSTED", "synthetic provider quota detail");
            }
        };
        var service = new BankingAssistantService(sessions, repository, providerQuotaStub,
                new CitationValidationService(), 1, 4, 20, 5);

        try {
            var answer = service.ask(sessionId, BankingDemoTools.Module.FINANCE, "Consulte o saldo fictício.");
            assertThat(answer.status()).isEqualTo("GROUNDED");
            assertThat(answer.answerMode()).isEqualTo("DETERMINISTIC_FALLBACK");
            assertThat(answer.providerIssue()).isEqualTo("PROVIDER_QUOTA");
            assertThat(answer.answer()).contains("R$ 5.654,00").doesNotContain("synthetic provider quota detail");
            assertThatThrownBy(() -> service.ask(sessionId, BankingDemoTools.Module.FINANCE, "Resuma o fluxo de crédito comercial externo."))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(429));
        } finally { service.stopAiExecutor(); }
    }

    private static ChatModel noEvidenceModel() {
        return new ChatModel() {
            @Override public org.springframework.ai.chat.prompt.ChatOptions getOptions() {
                return ToolCallingChatOptions.builder().build();
            }
            @Override public ChatResponse call(org.springframework.ai.chat.prompt.Prompt prompt) {
                return new ChatResponse(List.of(new Generation(new AssistantMessage(
                        "{\"answer\":\"\",\"citedEvidenceIds\":[],\"status\":\"INSUFFICIENT_EVIDENCE\"}"))));
            }
        };
    }

    private static BankDemoData.Snapshot clientSnapshot(UUID id) {
        Instant now = Instant.parse("2026-11-25T12:00:00Z");
        var session = new DemoSession(id, now, now.plusSeconds(14400), DemoMode.CLIENT);
        var account = new BankDemoData.Account("ACC-001", "CUST-001", "Cliente Exemplo A", "Conta corrente", "BRL", new BigDecimal("5600.00"), new BigDecimal("5654.00"));
        return new BankDemoData.Snapshot(session, List.of(), List.of(account), List.of(), List.of(), List.of(), List.of());
    }

    private static BankDemoData.Snapshot analystSnapshot(UUID id) {
        Instant now = Instant.parse("2026-11-26T12:00:00Z");
        var session = new DemoSession(id, now, now.plusSeconds(14400), DemoMode.ANALYST);
        var account = new BankDemoData.Account("ACC-001", "CUST-001", "Cliente Exemplo A", "Conta corrente", "BRL", new BigDecimal("5600"), new BigDecimal("5654"));
        var tx = new BankDemoData.Transaction("47d88dae-0de0-faca-d415-7508c544c5df", "ACC-001", Instant.parse("2026-11-01T12:00:00Z"),
                "Compra eletrônica de demonstração", "Compras", new BigDecimal("4800"), "DEBIT", "ALT-001");
        var alert = new BankDemoData.Alert("ALT-001", "ALERT-4800", tx.id(), null, "HIGH_VALUE_VS_HISTORY", "Compra de R$ 4.800 acima do limiar sintético; requer revisão.");
        var proposal = new BankDemoData.Proposal("CR-102", "Comercial Exemplo Ltda.", new BigDecimal("150000"), 24, 36, 5, 4,
                "Autorização de consulta", "POL-CRED");
        var creditPolicy = new BankDemoData.Policy("POL-CRED", "2.1", "Política fictícia de crédito", "4.2 Documentação",
                java.time.LocalDate.parse("2026-01-15"), "Cinco documentos são exigidos.");
        var alertPolicy = new BankDemoData.Policy("POL-ALERT", "1.3", "Regras fictícias de alertas", "2.1 Valor atípico",
                java.time.LocalDate.parse("2026-02-01"), "Limite de compra atípica para revisão.");
        return new BankDemoData.Snapshot(session, List.of(new BankDemoData.Customer("CUST-001", "Cliente Exemplo A", "SC-01")),
                List.of(account), List.of(tx), List.of(alert), List.of(proposal), List.of(creditPolicy, alertPolicy));
    }
}
