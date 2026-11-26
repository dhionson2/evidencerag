package dev.evidencerag.banking.application;

import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/** Per-interaction Spring AI ChatModel decorator: observes every advisor/tool-loop roundtrip. */
final class BankingAiInteractionTelemetry implements ChatModel {
    private final ChatModel delegate;
    private final RollingTokenBudget budget;
    private final int completionReserve;
    private final boolean retryGroqToolGenerationFailure;
    private final AtomicInteger roundTrips = new AtomicInteger();
    private final AtomicInteger toolGenerationRetryAttempts = new AtomicInteger();
    private final AtomicInteger toolCalls = new AtomicInteger();
    private final Set<String> toolNames = ConcurrentHashMap.newKeySet();
    private final Set<String> finishReasons = ConcurrentHashMap.newKeySet();
    private final CopyOnWriteArrayList<String> roundTripEvents = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<String> toolOutcomes = new CopyOnWriteArrayList<>();
    private final LongAdder promptTokens = new LongAdder();
    private final LongAdder completionTokens = new LongAdder();
    private volatile Throwable failure;

    BankingAiInteractionTelemetry(ChatModel delegate, int completionReserve, RollingTokenBudget budget) {
        this(delegate, completionReserve, budget, false);
    }

    BankingAiInteractionTelemetry(ChatModel delegate, int completionReserve, RollingTokenBudget budget,
            boolean retryGroqToolGenerationFailure) {
        this.delegate = delegate;
        this.completionReserve = Math.max(64, completionReserve);
        this.budget = budget;
        this.retryGroqToolGenerationFailure = retryGroqToolGenerationFailure;
    }

    @Override public ChatOptions getOptions() { return delegate.getOptions(); }

    @Override public ChatResponse call(Prompt prompt) {
        Prompt activePrompt = prompt;
        for (int retry = 0; retry <= 1; retry++) {
            int nextRoundTrip = roundTrips.get() + 1;
            RollingTokenBudget.Reservation reservation;
            try {
                reservation = budget.reserve(estimatePromptTokens(activePrompt) + completionReserve);
            } catch (RollingTokenBudget.TokenBudgetExceededException ex) {
                failure = ex;
                roundTripEvents.add("roundTrip=" + nextRoundTrip + ",providerCall=not_sent,failure=LOCAL_TOKEN_BUDGET");
                throw ex;
            }
            int roundTrip = roundTrips.incrementAndGet();
            try {
                ChatResponse response = delegate.call(activePrompt);
                long actual = response == null || response.getMetadata() == null || response.getMetadata().getUsage() == null
                        || response.getMetadata().getUsage().getTotalTokens() == null
                        ? estimatePromptTokens(activePrompt) + completionReserve : response.getMetadata().getUsage().getTotalTokens();
                budget.settle(reservation, (int) Math.min(Integer.MAX_VALUE, actual));
                if (response != null) {
                    String finishReason = response.getResults() == null ? "unavailable" : response.getResults().stream()
                            .map(generation -> generation.getMetadata() == null ? null : generation.getMetadata().getFinishReason())
                            .filter(reason -> reason != null && !reason.isBlank()).distinct().collect(java.util.stream.Collectors.joining(","));
                    Usage responseUsage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
                    roundTripEvents.add("roundTrip=" + roundTrip + ",finish_reason=" + safe(finishReason) +
                            ",prompt_tokens=" + (responseUsage == null ? "unavailable" : responseUsage.getPromptTokens()) +
                            ",completion_tokens=" + (responseUsage == null ? "unavailable" : responseUsage.getCompletionTokens()));
                    if (response.getMetadata() != null) {
                        Usage usage = response.getMetadata().getUsage();
                        if (usage != null) {
                            if (usage.getPromptTokens() != null) promptTokens.add(usage.getPromptTokens());
                            if (usage.getCompletionTokens() != null) completionTokens.add(usage.getCompletionTokens());
                        }
                    }
                    if (response.getResults() != null) response.getResults().stream()
                            .map(generation -> generation.getMetadata() == null ? null : generation.getMetadata().getFinishReason())
                            .filter(reason -> reason != null && !reason.isBlank()).forEach(finishReasons::add);
                }
                failure = null;
                return response;
            } catch (RuntimeException ex) {
                failure = ex;
                roundTripEvents.add("roundTrip=" + roundTrip + ",exception=" + ex.getClass().getSimpleName() + "," + providerDiagnostic(ex));
                budget.settle(reservation, estimatePromptTokens(activePrompt) + completionReserve);
                if (retry == 0 && retryGroqToolGenerationFailure && isToolCallGenerationFailure(ex)
                        && activePrompt.getOptions() instanceof org.springframework.ai.openai.OpenAiChatOptions openAiOptions) {
                    toolGenerationRetryAttempts.incrementAndGet();
                    roundTripEvents.add("retry=1,reason=TOOL_CALL_GENERATION_FAILED,temperature=0.0");
                    activePrompt = new Prompt(activePrompt.getInstructions(), openAiOptions.mutate().temperature(0.0).build());
                    continue;
                }
                throw ex;
            }
        }
        throw new IllegalStateException("Unreachable tool-generation retry state");
    }

    void recordTool(String name) { toolCalls.incrementAndGet(); toolNames.add(name); }
    void recordToolSuccess(String name) { toolOutcomes.add("tool=" + safe(name) + ",outcome=success"); }
    void recordToolFailure(String name, Throwable error) {
        toolOutcomes.add("tool=" + safe(name) + ",outcome=failure,exception=" + error.getClass().getSimpleName());
    }
    int roundTrips() { return roundTrips.get(); }
    int toolGenerationRetryAttempts() { return toolGenerationRetryAttempts.get(); }
    int toolCalls() { return toolCalls.get(); }
    Set<String> toolNames() { return Set.copyOf(toolNames); }
    Set<String> finishReasons() { return Set.copyOf(finishReasons); }
    long promptTokens() { return promptTokens.sum(); }
    long completionTokens() { return completionTokens.sum(); }
    Throwable failure() { return failure; }
    boolean truncated() { return finishReasons.stream().anyMatch("length"::equalsIgnoreCase); }
    String roundTripEvents() { return roundTripEvents.isEmpty() ? "unavailable" : String.join(";", roundTripEvents); }
    String toolOutcomes() { return toolOutcomes.isEmpty() ? "unavailable" : String.join(";", toolOutcomes); }

    static String providerDiagnostic(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof RollingTokenBudget.TokenBudgetExceededException budgetError) {
                return "exception=TokenBudgetExceededException,budgetUsedTokens=" + budgetError.usedTokens() +
                        ",budgetRequestedTokens=" + budgetError.requestedTokens() + ",budgetLimitTokens=" + budgetError.limitTokens();
            }
            if (current instanceof com.openai.errors.OpenAIServiceException openAi) {
                return "exception=" + openAi.getClass().getSimpleName() + ",providerStatus=" + openAi.statusCode() + ",providerCode=" + openAi.code().map(BankingAiInteractionTelemetry::safe).orElse("unavailable") +
                        ",providerParam=" + openAi.param().map(BankingAiInteractionTelemetry::safe).orElse("unavailable") +
                        ",providerType=" + openAi.type().map(BankingAiInteractionTelemetry::safe).orElse("unavailable") +
                        ",providerDetail=" + safeToolFailureDetail(openAi);
            }
            current = current.getCause();
        }
        return "exception=" + (error == null ? "unavailable" : error.getClass().getSimpleName()) + ",providerStatus=unavailable";
    }

    static boolean isToolCallGenerationFailure(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof com.openai.errors.OpenAIServiceException openAi
                    && openAi.code().map("tool_use_failed"::equalsIgnoreCase).orElse(false)) return true;
            current = current.getCause();
        }
        return false;
    }

    private static String safeToolFailureDetail(com.openai.errors.OpenAIServiceException error) {
        if (!error.code().map("tool_use_failed"::equalsIgnoreCase).orElse(false)) return "unavailable";
        try {
            com.fasterxml.jackson.databind.JsonNode body = error.body().convert(com.fasterxml.jackson.databind.JsonNode.class);
            com.fasterxml.jackson.databind.JsonNode failed = body.path("failed_generation");
            String detail = failed.isObject() ? failed.path("reason").asText("") : "";
            String message = body.path("message").asText("");
            String normalized = (detail + " " + message).toLowerCase(java.util.Locale.ROOT);
            if (normalized.contains("argument") && (normalized.contains("json") || normalized.contains("parse"))) {
                return "TOOL_ARGUMENTS_INVALID_JSON";
            }
            if (normalized.contains("schema") || normalized.contains("required parameter")) return "TOOL_ARGUMENTS_SCHEMA_REJECTED";
            if (failed.isTextual()) return "TOOL_CALL_OUTPUT_REJECTED";
            if (!normalized.isBlank()) return "TOOL_CALL_GENERATION_REJECTED";
        } catch (RuntimeException ignored) {
            // Provider error bodies are optional and can vary across compatible APIs.
        }
        return "TOOL_CALL_GENERATION_REJECTED";
    }

    private static String safe(String value) {
        if (value == null || value.isBlank()) return "unavailable";
        return value.replaceAll("[^A-Za-z0-9_.-]", "_").substring(0, Math.min(100, value.replaceAll("[^A-Za-z0-9_.-]", "_").length()));
    }

    private static int estimatePromptTokens(Prompt prompt) {
        long chars = prompt.getInstructions().stream().mapToLong(message -> message.getText() == null ? 0 : message.getText().length()).sum();
        // Tool schemas and protocol framing are not in Prompt instructions; reserve a fixed margin.
        return (int) Math.min(Integer.MAX_VALUE, (chars + 3) / 4 + 384);
    }
}
