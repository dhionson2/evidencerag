package dev.evidencerag.banking.application;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BankingAiInteractionTelemetryTest {
    @Test
    void capturesEverySpringAiRoundTripFinishReasonAndTokenUsage() {
        java.util.concurrent.atomic.AtomicInteger turns = new java.util.concurrent.atomic.AtomicInteger();
        ChatModel delegate = new ChatModel() {
            @Override public org.springframework.ai.chat.prompt.ChatOptions getOptions() { return ToolCallingChatOptions.builder().build(); }
            @Override public ChatResponse call(Prompt prompt) {
                int turn = turns.incrementAndGet();
                return new ChatResponse(List.of(new Generation(new AssistantMessage("{}"),
                        ChatGenerationMetadata.builder().finishReason(turn == 1 ? "tool_calls" : "length").build())),
                        ChatResponseMetadata.builder().model("openai/gpt-oss-20b").usage(new DefaultUsage(120, 600, 720)).build());
            }
        };
        var telemetry = new BankingAiInteractionTelemetry(delegate, 600, new RollingTokenBudget(8000));

        telemetry.call(new Prompt("synthetic request"));
        telemetry.call(new Prompt("tool result"));
        telemetry.recordTool("listDemoTransactions");

        assertThat(telemetry.roundTrips()).isEqualTo(2);
        assertThat(telemetry.finishReasons()).containsExactlyInAnyOrder("tool_calls", "length");
        assertThat(telemetry.truncated()).isTrue();
        assertThat(telemetry.promptTokens()).isEqualTo(240);
        assertThat(telemetry.completionTokens()).isEqualTo(1200);
        assertThat(telemetry.toolCalls()).isEqualTo(1);
        assertThat(telemetry.toolNames()).containsExactly("listDemoTransactions");
    }

    @Test
    void enforcesRollingTokenBudgetBeforeCallingProvider() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-26T12:00:00Z"), ZoneOffset.UTC);
        var budget = new RollingTokenBudget(1000, clock);
        budget.reserve(700);
        assertThatThrownBy(() -> budget.reserve(400))
                .isInstanceOfSatisfying(RollingTokenBudget.TokenBudgetExceededException.class, error -> {
                    assertThat(error.usedTokens()).isEqualTo(700);
                    assertThat(error.requestedTokens()).isEqualTo(400);
                    assertThat(error.limitTokens()).isEqualTo(1000);
                });
    }

    @Test
    void recordsWhenTokenBudgetBlocksBeforeAnyProviderRoundTrip() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-26T12:00:00Z"), ZoneOffset.UTC);
        var budget = new RollingTokenBudget(1000, clock);
        budget.reserve(700);
        java.util.concurrent.atomic.AtomicInteger providerCalls = new java.util.concurrent.atomic.AtomicInteger();
        ChatModel delegate = new ChatModel() {
            @Override public org.springframework.ai.chat.prompt.ChatOptions getOptions() { return ToolCallingChatOptions.builder().build(); }
            @Override public ChatResponse call(Prompt prompt) {
                providerCalls.incrementAndGet();
                throw new AssertionError("Provider must not be called after local budget rejection");
            }
        };
        var telemetry = new BankingAiInteractionTelemetry(delegate, 600, budget);

        assertThatThrownBy(() -> telemetry.call(new Prompt("x")))
                .isInstanceOf(RollingTokenBudget.TokenBudgetExceededException.class);

        assertThat(providerCalls).hasValue(0);
        assertThat(telemetry.roundTrips()).isZero();
        assertThat(telemetry.roundTripEvents()).contains("roundTrip=1,providerCall=not_sent,failure=LOCAL_TOKEN_BUDGET");
        assertThat(BankingAiInteractionTelemetry.providerDiagnostic(telemetry.failure()))
                .isEqualTo("exception=TokenBudgetExceededException,budgetUsedTokens=700,budgetRequestedTokens=985,budgetLimitTokens=1000");
    }

    @Test
    void retriesGroqToolGenerationFailureAtZeroTemperatureExactlyOnce() {
        java.util.concurrent.atomic.AtomicInteger providerCalls = new java.util.concurrent.atomic.AtomicInteger();
        java.util.List<Double> temperatures = new java.util.ArrayList<>();
        ChatModel delegate = new ChatModel() {
            @Override public org.springframework.ai.chat.prompt.ChatOptions getOptions() {
                return org.springframework.ai.openai.OpenAiChatOptions.builder()
                        .model("openai/gpt-oss-20b").temperature(0.2).strict(true).build();
            }
            @Override public ChatResponse call(Prompt prompt) {
                var options = (org.springframework.ai.openai.OpenAiChatOptions) prompt.getOptions();
                temperatures.add(options.getTemperature());
                if (providerCalls.incrementAndGet() == 1) throw groqToolUseFailure();
                return new ChatResponse(List.of(new Generation(new AssistantMessage("{}"),
                        ChatGenerationMetadata.builder().finishReason("stop").build())));
            }
        };
        var telemetry = new BankingAiInteractionTelemetry(delegate, 600, new RollingTokenBudget(8000), true);
        var initialOptions = org.springframework.ai.openai.OpenAiChatOptions.builder()
                .model("openai/gpt-oss-20b").temperature(0.2).strict(true).build();

        ChatResponse response = telemetry.call(new Prompt("synthetic request", initialOptions));

        assertThat(response.getResults()).hasSize(1);
        assertThat(providerCalls).hasValue(2);
        assertThat(temperatures).containsExactly(0.2, 0.0);
        assertThat(telemetry.roundTrips()).isEqualTo(2);
        assertThat(telemetry.toolGenerationRetryAttempts()).isEqualTo(1);
        assertThat(telemetry.finishReasons()).containsExactly("stop");
        assertThat(telemetry.roundTripEvents()).contains("providerDetail=TOOL_ARGUMENTS_INVALID_JSON",
                "retry=1,reason=TOOL_CALL_GENERATION_FAILED,temperature=0.0");
    }

    @Test
    void classifiesQuotaTransportParsingToolAndBudgetFailuresSeparately() {
        assertThat(BankingAssistantService.failureCause(new RuntimeException(), "429", 0)).isEqualTo("PROVIDER_QUOTA");
        assertThat(BankingAssistantService.failureCause(new java.net.SocketTimeoutException(), "unavailable", 0)).isEqualTo("TRANSPORT_ERROR");
        assertThat(BankingAssistantService.failureCause(new JsonParseException(), "unavailable", 0)).isEqualTo("RESPONSE_PARSE_ERROR");
        assertThat(BankingAssistantService.failureCause(new ToolInvocationError(), "unavailable", 1)).isEqualTo("TOOL_EXECUTION_ERROR");
        assertThat(BankingAssistantService.failureCause(com.openai.errors.BadRequestException.builder()
                .headers(com.openai.core.http.Headers.builder().build())
                .error(com.openai.models.ErrorObject.builder().code("invalid_request_error").param("messages")
                        .type("invalid_request_error").message("synthetic").build())
                .build(), "400", 1)).isEqualTo("PROVIDER_REQUEST_ERROR");
        assertThat(BankingAssistantService.failureCause(new RollingTokenBudget.TokenBudgetExceededException(0, 1001, 1000), "unavailable", 0))
                .isEqualTo("LOCAL_TOKEN_BUDGET");
    }

    @Test
    void providerDiagnosticExposesOnlySanitizedOpenAiStatusAndErrorFields() {
        var error = com.openai.errors.BadRequestException.builder()
                .headers(com.openai.core.http.Headers.builder().build())
                .error(com.openai.models.ErrorObject.builder().code("invalid_request_error")
                        .param("messages[1].tool_calls").type("invalid_request_error").message("synthetic bad request").build())
                .build();

        String diagnostic = BankingAiInteractionTelemetry.providerDiagnostic(error);

        assertThat(diagnostic).contains("BadRequestException", "providerStatus=400", "providerCode=invalid_request_error", "providerParam=messages_1_.tool_calls")
                .doesNotContain("synthetic bad request", "secret");
    }

    @Test
    void sanitizesGroqToolFailureReasonWithoutLoggingGeneratedArguments() {
        var failure = groqToolUseFailure();

        String diagnostic = BankingAiInteractionTelemetry.providerDiagnostic(failure);

        assertThat(diagnostic).contains("providerCode=tool_use_failed", "providerDetail=TOOL_ARGUMENTS_INVALID_JSON")
                .doesNotContain("do-not-log-arguments", "attempted_arguments");
    }

    private static com.openai.errors.BadRequestException groqToolUseFailure() {
        return com.openai.errors.BadRequestException.builder()
                .headers(com.openai.core.http.Headers.builder().build())
                .error(com.openai.models.ErrorObject.builder().code("tool_use_failed").param("tools")
                        .type("invalid_request_error").message("Failed to parse tool call arguments as JSON")
                        .putAdditionalProperty("failed_generation", com.openai.core.JsonValue.from(
                                java.util.Map.of("reason", "Tool call arguments are not valid JSON",
                                        "attempted_arguments", "do-not-log-arguments")))
                        .build())
                .build();
    }

    private static final class JsonParseException extends RuntimeException {}
    private static final class ToolInvocationError extends RuntimeException {}
}
