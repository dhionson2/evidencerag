package dev.evidencerag.infrastructure;

import org.springframework.ai.google.genai.embedding.GoogleGenAiEmbeddingConnectionDetails;
import org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiTextEmbeddingProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@Conditional(GeminiProviderActiveCondition.class)
public class GeminiProviderConfiguration {
    @Bean
    @ConditionalOnProperty(name = "spring.ai.model.embedding.text", havingValue = "google-genai")
    GeminiEmbeddingClientHolder geminiEmbeddingClientHolder(
            @org.springframework.beans.factory.annotation.Value("${GEMINI_API_KEY:}") String apiKey) {
        return new GeminiEmbeddingClientHolder(apiKey);
    }

    @Bean
    @ConditionalOnProperty(name = "spring.ai.model.embedding.text", havingValue = "google-genai")
    GoogleGenAiEmbeddingConnectionDetails geminiEmbeddingConnectionDetails(
            @org.springframework.beans.factory.annotation.Value("${GEMINI_API_KEY:}") String apiKey,
            GeminiEmbeddingClientHolder geminiEmbeddingClientHolder) {
        return GoogleGenAiEmbeddingConnectionDetails.builder().apiKey(apiKey)
                .genAiClient(geminiEmbeddingClientHolder.client()).build();
    }

    @Bean
    @ConditionalOnProperty(name = "spring.ai.model.embedding.text", havingValue = "google-genai")
    TaskAwareGoogleGenAiTextEmbeddingModel geminiTextEmbeddingModel(
            GoogleGenAiEmbeddingConnectionDetails connectionDetails,
            GoogleGenAiTextEmbeddingProperties embeddingProperties) {
        return new TaskAwareGoogleGenAiTextEmbeddingModel(connectionDetails, embeddingProperties.toOptions());
    }
}
