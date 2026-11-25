package dev.evidencerag.infrastructure;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.google.genai.embedding.GoogleGenAiEmbeddingConnectionDetails;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingModel;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions;
import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatAutoConfiguration;
import org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiTextEmbeddingAutoConfiguration;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;
import java.net.InetSocketAddress;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeminiEmbeddingConfigurationTest {
    private ApplicationContextRunner geminiContext;

    @BeforeEach
    void configureGeminiAutoConfiguration() {
        geminiContext = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        GoogleGenAiChatAutoConfiguration.class,
                        GoogleGenAiTextEmbeddingAutoConfiguration.class,
                        ToolCallingAutoConfiguration.class))
                .withUserConfiguration(GeminiProviderConfiguration.class)
                .withPropertyValues(
                        "spring.ai.model.chat=google-genai",
                        "spring.ai.model.embedding=google-genai",
                        "spring.ai.model.embedding.text=google-genai",
                        "spring.ai.google.genai.api-key=context-test-key",
                        "spring.ai.google.genai.embedding.api-key=context-test-key",
                        "spring.ai.google.genai.embedding.text.model=gemini-embedding-001",
                        "spring.ai.google.genai.embedding.text.task-type=RETRIEVAL_DOCUMENT",
                        "spring.ai.google.genai.embedding.text.dimensions=768",
                        "GEMINI_API_KEY=context-test-key");
    }

    @Test
    void selectsSeparateGeminiChatAndEmbeddingClientsAndAppliesOptions() {
        geminiContext.run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals("GoogleGenAiChatModel", context.getBean(ChatModel.class).getClass().getSimpleName());
            assertTrue(context.getBean(EmbeddingModel.class) instanceof TaskAwareGoogleGenAiTextEmbeddingModel);

            Client chatClient = context.getBean(Client.class);
            Client embeddingClient = context.getBean(GoogleGenAiEmbeddingConnectionDetails.class).getGenAiClient();
            assertEquals("googleGenAiClient", context.getBeanNamesForType(Client.class)[0]);
            assertNotSame(chatClient, embeddingClient);

            var options = ((GoogleGenAiTextEmbeddingModel) context.getBean(EmbeddingModel.class)).options;
            assertEquals("gemini-embedding-001", options.getModel());
            assertEquals(GoogleGenAiTextEmbeddingOptions.TaskType.RETRIEVAL_DOCUMENT, options.getTaskType());
            assertEquals(768, options.getDimensions());
        });
    }

    @Test
    void sendsRetrievalDocumentForIndexingAndRetrievalQueryForSearch() throws Exception {
        var requestBodies = new java.util.concurrent.CopyOnWriteArrayList<com.fasterxml.jackson.databind.JsonNode>();
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String values = IntStream.range(0, 768).mapToObj(ignored -> "0.1")
                .collect(java.util.stream.Collectors.joining(","));
        byte[] responseBody = ("{\"embeddings\":[{\"values\":[" + values + "]}]} ")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        server.createContext("/", exchange -> {
            requestBodies.add(mapper.readTree(exchange.getRequestBody()));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBody.length);
            exchange.getResponseBody().write(responseBody);
            exchange.close();
        });
        server.start();

        try (Client client = Client.builder().apiKey("local-test-key")
                .httpOptions(HttpOptions.builder().baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                        .timeout(3_000).retryOptions(HttpRetryOptions.builder().attempts(1).build()).build())
                .build()) {
            var details = GoogleGenAiEmbeddingConnectionDetails.builder()
                    .apiKey("local-test-key").genAiClient(client).build();
            var options = GoogleGenAiTextEmbeddingOptions.builder()
                    .model("gemini-embedding-001").dimensions(768)
                    .taskType(GoogleGenAiTextEmbeddingOptions.TaskType.RETRIEVAL_DOCUMENT).build();
            var model = new TaskAwareGoogleGenAiTextEmbeddingModel(details, options);

            assertEquals(768, model.embed(List.of("Trecho fictício para indexar.")).getFirst().length);
            assertEquals(768, model.embed("Pergunta fictícia para recuperar.").length);
            assertEquals("RETRIEVAL_DOCUMENT", taskType(requestBodies.get(0)));
            assertEquals("RETRIEVAL_QUERY", taskType(requestBodies.get(1)));
            assertEquals(768, embeddingConfig(requestBodies.get(0)).path("outputDimensionality").asInt());
            assertEquals(768, embeddingConfig(requestBodies.get(1)).path("outputDimensionality").asInt());
        } finally {
            server.stop(0);
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
    void springAiChatAndBatchedEmbeddingsWorkTogetherWithFictionalInput() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        GoogleGenAiChatAutoConfiguration.class,
                        GoogleGenAiTextEmbeddingAutoConfiguration.class,
                        ToolCallingAutoConfiguration.class))
                .withUserConfiguration(GeminiProviderConfiguration.class)
                .withPropertyValues(
                        "spring.ai.model.chat=google-genai",
                        "spring.ai.model.embedding=google-genai",
                        "spring.ai.model.embedding.text=google-genai",
                        "spring.ai.google.genai.api-key=" + System.getenv("GEMINI_API_KEY"),
                        "spring.ai.google.genai.chat.model=gemini-2.5-flash",
                        "spring.ai.google.genai.embedding.text.model=gemini-embedding-001",
                        "spring.ai.google.genai.embedding.text.task-type=RETRIEVAL_DOCUMENT",
                        "spring.ai.google.genai.embedding.text.dimensions=768")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    var chunks = IntStream.range(0, 27)
                            .mapToObj(index -> "Trecho fictício " + index + " para validar a ingestão.")
                            .toList();
                    var embeddings = context.getBean(EmbeddingModel.class).embed(chunks);
                    assertEquals(27, embeddings.size());
                    assertEquals(768, embeddings.getFirst().length);
                    assertEquals(768, context.getBean(EmbeddingModel.class)
                            .embed("Pergunta fictícia para validação do embedding de consulta.").length);

                    var response = context.getBean(ChatModel.class).call(new Prompt("Responda somente: OK"));
                    assertTrue(response.getResult().getOutput().getText() != null);
                });
    }

    @Test
    void embeddingOptionsPreserveTheGeminiRetrievalDocumentTaskAnd768Dimensions() {
        var options = GoogleGenAiTextEmbeddingOptions.builder()
                .model("gemini-embedding-001")
                .taskType(GoogleGenAiTextEmbeddingOptions.TaskType.RETRIEVAL_DOCUMENT)
                .dimensions(768)
                .build();

        assertEquals("gemini-embedding-001", options.getModel());
        assertEquals(GoogleGenAiTextEmbeddingOptions.TaskType.RETRIEVAL_DOCUMENT, options.getTaskType());
        assertEquals(768, options.getDimensions());
    }

    private String taskType(com.fasterxml.jackson.databind.JsonNode body) {
        var config = embeddingConfig(body);
        var value = config.has("taskType") ? config.get("taskType") : config.get("task_type");
        return value == null ? null : value.asText();
    }

    private com.fasterxml.jackson.databind.JsonNode embeddingConfig(com.fasterxml.jackson.databind.JsonNode body) {
        return body.has("requests") ? body.path("requests").get(0) : body;
    }
}
