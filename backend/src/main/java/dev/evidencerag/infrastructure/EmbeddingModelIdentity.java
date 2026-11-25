package dev.evidencerag.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Stable namespace for vectors so different providers never share a search space. */
@Component
public final class EmbeddingModelIdentity {
    private final String value;
    private final String legacyValue;

    public EmbeddingModelIdentity(
            @Value("${EMBEDDING_PROVIDER:ollama}") String provider,
            @Value("${GEMINI_EMBEDDING_MODEL:gemini-embedding-001}") String geminiModel,
            @Value("${OPENAI_EMBEDDING_MODEL:text-embedding-3-small}") String openAiModel,
            @Value("${OLLAMA_EMBEDDING_MODEL:nomic-embed-text}") String ollamaModel,
            @Value("${APP_EMBEDDING_MODEL:}") String configuredModel) {
        String normalizedProvider = provider == null ? "" : provider.trim().toLowerCase(java.util.Locale.ROOT);
        String model = switch (normalizedProvider) {
            case "google-genai" -> geminiModel;
            case "openai" -> openAiModel;
            case "ollama" -> ollamaModel;
            default -> configuredModel;
        };
        if (normalizedProvider.isBlank() || model == null || !model.matches("[A-Za-z0-9._-]{1,80}")) {
            throw new IllegalArgumentException("Identidade do provedor/modelo de embedding inválida.");
        }
        this.value = normalizedProvider + ":" + model;
        this.legacyValue = model;
    }

    public String value() { return value; }
    public String legacyValue() { return legacyValue; }
}
