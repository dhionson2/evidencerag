package dev.evidencerag.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(long maxUploadBytes, int maxDocumentCharacters, int chunkSize,
                            int chunkOverlap, int retrievalTopK, double minSimilarity, int rateLimitPerMinute,
                            String corsOrigin, String documentVisibility, String embeddingModel, int embeddingDimensions,
                            boolean publicDemoCorpusOnly, int maxConcurrentAiRequests, Security security) {
    public AppProperties {
        if (embeddingModel == null || !embeddingModel.matches("[A-Za-z0-9._-]{1,80}")) {
            throw new IllegalArgumentException("app.embedding-model deve conter somente letras, números, ponto, hífen ou sublinhado.");
        }
        if (documentVisibility == null || !documentVisibility.matches("[A-Za-z0-9._-]{1,40}")) {
            throw new IllegalArgumentException("app.document-visibility inválido.");
        }
        if (maxConcurrentAiRequests < 1 || maxConcurrentAiRequests > 20) {
            throw new IllegalArgumentException("app.max-concurrent-ai-requests deve estar entre 1 e 20.");
        }
        if (embeddingDimensions < 1 || embeddingDimensions > 2000) {
            throw new IllegalArgumentException("app.embedding-dimensions deve estar entre 1 e 2000 para compatibilidade com HNSW.");
        }
    }
    public record Security(String token) {}
}
