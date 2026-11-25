package dev.evidencerag.infrastructure;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import org.springframework.beans.factory.DisposableBean;

/** Owns the embedding-only SDK client without making it compete with Spring AI's chat Client bean. */
public final class GeminiEmbeddingClientHolder implements DisposableBean {
    private final Client client;

    public GeminiEmbeddingClientHolder(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("GEMINI_API_KEY é obrigatória quando embeddings Gemini estão selecionados.");
        }
        this.client = Client.builder().apiKey(apiKey)
                .httpOptions(HttpOptions.builder().timeout(45_000)
                        .retryOptions(HttpRetryOptions.builder().attempts(1).build()).build())
                .build();
    }

    public Client client() {
        return client;
    }

    @Override
    public void destroy() {
        client.close();
    }
}
