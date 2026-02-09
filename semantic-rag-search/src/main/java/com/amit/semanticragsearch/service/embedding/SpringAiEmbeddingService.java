package com.amit.semanticragsearch.service.embedding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
public class SpringAiEmbeddingService implements EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(SpringAiEmbeddingService.class);

    private final EmbeddingModel embeddingModel;
    private final int dimension;

    public SpringAiEmbeddingService(
            EmbeddingModel embeddingModel,
            @Value("${spring.ai.vectorstore.pgvector.dimensions:768}") int dimension) {
        this.embeddingModel = embeddingModel;
        this.dimension = dimension;
        log.info("Initialized Spring AI embedding service with dimension={}", dimension);
    }

    @Override
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Text to embed cannot be null or blank");
        }

        long startTime = System.currentTimeMillis();
        float[] embedding = embeddingModel.embed(text);

        long elapsed = System.currentTimeMillis() - startTime;
        log.debug("Generated embedding: textLength={}, dimension={}, latency={}ms",
                text.length(), embedding.length, elapsed);

        return embedding;
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            throw new IllegalArgumentException("Texts list cannot be null or empty");
        }

        long startTime = System.currentTimeMillis();
        EmbeddingResponse response = embeddingModel.call(
                new EmbeddingRequest(texts, null)
        );

        List<float[]> embeddings = response.getResults()
                .stream()
                .map(embedding -> embedding.getOutput())
                .toList();

        long elapsed = System.currentTimeMillis() - startTime;
        log.debug("Generated {} embeddings in {}ms (avg={}ms per text)",
                embeddings.size(), elapsed,
                embeddings.isEmpty() ? 0 : elapsed / embeddings.size());

        return embeddings;
    }

    @Override
    public int getDimension() {
        return dimension;
    }
}