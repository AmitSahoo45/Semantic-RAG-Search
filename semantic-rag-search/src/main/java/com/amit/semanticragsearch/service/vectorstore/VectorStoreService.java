package com.amit.semanticragsearch.service.vectorstore;

import com.amit.semanticragsearch.domain.Chunk;

import java.util.List;
import java.util.UUID;


public interface VectorStoreService {
    void store(Chunk chunk);
    void storeBatch(List<Chunk> chunks);
    void deleteByDocumentId(UUID documentId);

    List<SimilarityResult> search(UUID tenantId, float[] queryEmbedding, int topK);

    record SimilarityResult(Chunk chunk, double score) {
        public SimilarityResult {
            if (chunk == null) 
                throw new IllegalArgumentException("Chunk cannot be null in SimilarityResult");
            
            if (score < 0.0 || score > 1.0) 
                throw new IllegalArgumentException("Score must be between 0.0 and 1.0, got: " + score);
        }
    }
}