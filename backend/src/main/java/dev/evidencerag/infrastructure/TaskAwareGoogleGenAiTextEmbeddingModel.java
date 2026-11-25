package dev.evidencerag.infrastructure;

import com.google.genai.Client;
import com.google.genai.types.ContentEmbedding;
import com.google.genai.types.EmbedContentConfig;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.ai.google.genai.embedding.GoogleGenAiEmbeddingConnectionDetails;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingModel;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions;
import org.springframework.ai.retry.RetryUtils;

import java.util.ArrayList;
import java.util.List;

/** Applies Gemini's retrieval task type to the SDK request; Spring AI 2.1.0 maps the property to options but omits it from EmbedContentConfig. */
public final class TaskAwareGoogleGenAiTextEmbeddingModel extends GoogleGenAiTextEmbeddingModel {
    private final Client client;

    public TaskAwareGoogleGenAiTextEmbeddingModel(GoogleGenAiEmbeddingConnectionDetails connectionDetails,
                                                   GoogleGenAiTextEmbeddingOptions options) {
        super(connectionDetails, options);
        this.client = connectionDetails.getGenAiClient();
    }

    @Override
    public float[] embed(String text) {
        var queryOptions = GoogleGenAiTextEmbeddingOptions.builder()
                .model(options.getModel())
                .dimensions(options.getDimensions())
                .taskType(GoogleGenAiTextEmbeddingOptions.TaskType.RETRIEVAL_QUERY)
                .build();
        return call(new EmbeddingRequest(List.of(text), queryOptions)).getResult().getOutput();
    }

    @Override
    public float[] embed(Document document) {
        var documentOptions = GoogleGenAiTextEmbeddingOptions.builder()
                .model(options.getModel())
                .dimensions(options.getDimensions())
                .taskType(GoogleGenAiTextEmbeddingOptions.TaskType.RETRIEVAL_DOCUMENT)
                .build();
        return call(new EmbeddingRequest(List.of(document.getFormattedContent()), documentOptions))
                .getResult().getOutput();
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        GoogleGenAiTextEmbeddingOptions requestOptions = request.getOptions() instanceof GoogleGenAiTextEmbeddingOptions googleOptions
                ? googleOptions : options;
        String model = requestOptions.getModel() == null ? options.getModel() : requestOptions.getModel();
        var configBuilder = EmbedContentConfig.builder()
                .taskType(requestOptions.getTaskType().name());
        if (requestOptions.getDimensions() != null) configBuilder.outputDimensionality(requestOptions.getDimensions());
        if (requestOptions.getTitle() != null) configBuilder.title(requestOptions.getTitle());
        var config = configBuilder.build();
        List<String> input = request.getInstructions();
        List<String> validInput = input.stream().filter(text -> text != null && !text.isBlank()).toList();
        if (validInput.isEmpty()) throw new IllegalArgumentException("No embedding input is provided.");

        var response = RetryUtils.execute(RetryUtils.DEFAULT_RETRY_TEMPLATE,
                () -> client.models.embedContent(model, validInput, config));
        List<ContentEmbedding> returned = response.embeddings().orElseThrow(
                () -> new IllegalStateException("Gemini não retornou embeddings."));
        if (returned.size() != validInput.size()) {
            throw new IllegalStateException("Gemini retornou quantidade de embeddings incompatível com a entrada.");
        }

        List<Embedding> results = new ArrayList<>(input.size());
        int responseIndex = 0;
        int tokens = 0;
        for (int inputIndex = 0; inputIndex < input.size(); inputIndex++) {
            String text = input.get(inputIndex);
            if (text == null || text.isBlank()) {
                results.add(new Embedding(new float[0], inputIndex));
                continue;
            }
            ContentEmbedding item = returned.get(responseIndex++);
            List<Float> values = item.values().orElseThrow(
                    () -> new IllegalStateException("Gemini retornou embedding sem valores."));
            float[] vector = new float[values.size()];
            for (int index = 0; index < values.size(); index++) vector[index] = values.get(index);
            results.add(new Embedding(vector, inputIndex));
            if (item.statistics().isPresent() && item.statistics().get().tokenCount().isPresent()) {
                tokens += item.statistics().get().tokenCount().get().intValue();
            }
        }
        return new EmbeddingResponse(results,
                new EmbeddingResponseMetadata(model, new DefaultUsage(0, 0, tokens)));
    }

    @Override
    public int dimensions() {
        return options.getDimensions() == null ? super.dimensions() : options.getDimensions();
    }
}
