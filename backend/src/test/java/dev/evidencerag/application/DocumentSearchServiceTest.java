package dev.evidencerag.application;

import dev.evidencerag.infrastructure.AppProperties;
import dev.evidencerag.infrastructure.EmbeddingModelIdentity;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.assertThat;

class DocumentSearchServiceTest {
    @Test void publicDemoSearchAlwaysScopesToApprovedCorpusAndEmbeddingModel() {
        VectorStore store = mock(VectorStore.class);
        when(store.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        var properties = new AppProperties(10_000_000, 2_000_000, 900, 120, 6, .72, 8,
                "https://demo.example", "public-demo", "gemini-embedding-001", 768, true, 1,
                new AppProperties.Security(""));
        new DocumentSearchService(store, properties, identity()).search("Pergunta fictícia?");
        ArgumentCaptor<SearchRequest> request = ArgumentCaptor.forClass(SearchRequest.class);
        verify(store, times(1)).similaritySearch(request.capture());
        assertThat(request.getValue().hasFilterExpression()).isTrue();
        assertThat(request.getValue().getFilterExpression().toString()).contains("public-demo", "google-genai:gemini-embedding-001", "gemini-embedding-001");
    }

    @Test void localSearchExcludesVectorsCreatedByDifferentEmbeddingModels() {
        VectorStore store = mock(VectorStore.class);
        when(store.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        var properties = new AppProperties(10_000_000, 2_000_000, 900, 120, 6, .72, 8,
                "http://localhost:5173", "local", "gemini-embedding-001", 768, false, 2,
                new AppProperties.Security(""));

        new DocumentSearchService(store, properties, identity()).search("Pergunta fictícia?");

        ArgumentCaptor<SearchRequest> request = ArgumentCaptor.forClass(SearchRequest.class);
        verify(store).similaritySearch(request.capture());
        assertThat(request.getValue().hasFilterExpression()).isTrue();
        assertThat(request.getValue().getFilterExpression().toString()).contains("google-genai:gemini-embedding-001")
                .doesNotContain("public-demo");
    }

    @Test void localOllamaSearchKeepsLegacyModelMetadataVisible() {
        VectorStore store = mock(VectorStore.class);
        when(store.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        var properties = new AppProperties(10_000_000, 2_000_000, 900, 120, 6, .72, 8,
                "http://localhost:5173", "local", "nomic-embed-text", 768, false, 2,
                new AppProperties.Security(""));
        var identity = new EmbeddingModelIdentity("ollama", "gemini-embedding-001", "text-embedding-3-small", "nomic-embed-text", "");

        new DocumentSearchService(store, properties, identity).search("Pergunta fictícia?");

        ArgumentCaptor<SearchRequest> request = ArgumentCaptor.forClass(SearchRequest.class);
        verify(store).similaritySearch(request.capture());
        assertThat(request.getValue().getFilterExpression().toString())
                .contains("ollama:nomic-embed-text", "nomic-embed-text");
    }

    private EmbeddingModelIdentity identity() {
        return new EmbeddingModelIdentity("google-genai", "gemini-embedding-001", "text-embedding-3-small", "nomic-embed-text", "");
    }
}
