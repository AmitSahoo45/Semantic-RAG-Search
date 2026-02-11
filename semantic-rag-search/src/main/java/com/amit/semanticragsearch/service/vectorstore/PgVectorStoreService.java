package com.amit.semanticragsearch.service.vectorstore;

import com.amit.semanticragsearch.domain.Chunk;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;


@Service
public class PgVectorStoreService implements VectorStoreService {

    private static final Logger log = LoggerFactory.getLogger(PgVectorStoreService.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final int expectedDimension;


    private static final String UPSERT_CHUNK_SQL = """
            INSERT INTO chunks (id, document_id, tenant_id, chunk_index, content, token_count, embedding, metadata)
            VALUES (?, ?, ?, ?, ?, ?, ?::vector, ?::jsonb)
            ON CONFLICT (document_id, chunk_index) DO UPDATE SET
                content = EXCLUDED.content,
                token_count = EXCLUDED.token_count,
                embedding = EXCLUDED.embedding,
                metadata = EXCLUDED.metadata
            """;

    private static final String SIMILARITY_SEARCH_SQL = """
            SELECT
                c.id, c.document_id, c.tenant_id, c.chunk_index,
                c.content, c.token_count, c.metadata,
                1 - (c.embedding <=> ?::vector) AS similarity
            FROM chunks c
            WHERE c.tenant_id = ?
              AND c.embedding IS NOT NULL
            ORDER BY c.embedding <=> ?::vector ASC
            LIMIT ?
            """;

    private static final String DELETE_BY_DOCUMENT_SQL = """
            DELETE FROM chunks WHERE document_id = ?
            """;

    public PgVectorStoreService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            @Value("${spring.ai.vectorstore.pgvector.dimensions:768}") int expectedDimension) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.expectedDimension = expectedDimension;
        log.info("Initialized PgVectorStoreService with expectedDimension={}", expectedDimension);
    }

    @Override
    @Transactional
    public void store(Chunk chunk) {
        validateChunk(chunk);

        long startTime = System.currentTimeMillis();

        jdbcTemplate.update(UPSERT_CHUNK_SQL,
                chunk.getId(),
                chunk.getDocumentId(),
                chunk.getTenantId(),
                chunk.getChunkIndex(),
                chunk.getContent(),
                chunk.getTokenCount(),
                toVectorString(chunk.getEmbedding()),
                toJsonString(chunk.getMetadata())
        );

        long elapsed = System.currentTimeMillis() - startTime;
        log.debug("Stored chunk: id={}, documentId={}, chunkIndex={}, latency={}ms",
                chunk.getId(), chunk.getDocumentId(), chunk.getChunkIndex(), elapsed);
    }

    @Override
    @Transactional
    public void storeBatch(List<Chunk> chunks) {
        if (chunks == null || chunks.isEmpty())
            throw new IllegalArgumentException("Chunks list cannot be null or empty");


        for (int i = 0; i < chunks.size(); i++) {
            try {
                validateChunk(chunks.get(i));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Chunk at index " + i + " failed validation: " + e.getMessage(), e);
            }
        }

        long startTime = System.currentTimeMillis();

        jdbcTemplate.batchUpdate(UPSERT_CHUNK_SQL, chunks, chunks.size(),
                (ps, chunk) -> {
                    ps.setObject(1, chunk.getId());
                    ps.setObject(2, chunk.getDocumentId());
                    ps.setObject(3, chunk.getTenantId());
                    ps.setInt(4, chunk.getChunkIndex());
                    ps.setString(5, chunk.getContent());
                    ps.setInt(6, chunk.getTokenCount());
                    ps.setString(7, toVectorString(chunk.getEmbedding()));
                    ps.setString(8, toJsonString(chunk.getMetadata()));
                });

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("Stored batch of {} chunks in {}ms (avg={}ms per chunk)",
                chunks.size(), elapsed,
                chunks.isEmpty() ? 0 : elapsed / chunks.size());
    }

    @Override
    public List<SimilarityResult> search(UUID tenantId, float[] queryEmbedding, int topK) {
        if (tenantId == null)
            throw new IllegalArgumentException("Tenant ID cannot be null");

        validateEmbedding(queryEmbedding);

        if (topK < 1 || topK > 100)
            throw new IllegalArgumentException("topK must be between 1 and 100, got: " + topK);


        long startTime = System.currentTimeMillis();
        String embeddingStr = toVectorString(queryEmbedding);


        List<SimilarityResult> results = jdbcTemplate.query(
                SIMILARITY_SEARCH_SQL,
                new ChunkSimilarityRowMapper(),
                embeddingStr,
                tenantId,
                embeddingStr,
                topK
        );

        long elapsed = System.currentTimeMillis() - startTime;
        log.debug("Search completed: tenantId={}, topK={}, resultsFound={}, latency={}ms",
                tenantId, topK, results.size(), elapsed);

        return results;
    }

    @Override
    @Transactional
    public void deleteByDocumentId(UUID documentId) {
        if (documentId == null)
            throw new IllegalArgumentException("Document ID cannot be null");


        long startTime = System.currentTimeMillis();
        int deletedCount = jdbcTemplate.update(DELETE_BY_DOCUMENT_SQL, documentId);

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("Deleted {} chunks for documentId={} in {}ms", deletedCount, documentId, elapsed);
    }



    private void validateChunk(Chunk chunk) {
        if (chunk == null)
            throw new IllegalArgumentException("Chunk cannot be null");

        if (chunk.getId() == null)
            throw new IllegalArgumentException("Chunk id cannot be null, caller must set it before storing");

        if (chunk.getDocumentId() == null)
            throw new IllegalArgumentException("Chunk documentId cannot be null");

        if (chunk.getTenantId() == null)
            throw new IllegalArgumentException("Chunk tenantId cannot be null");

        if (chunk.getChunkIndex() == null)
            throw new IllegalArgumentException("Chunk chunkIndex cannot be null");

        if (chunk.getContent() == null || chunk.getContent().isBlank())
            throw new IllegalArgumentException("Chunk content cannot be null or blank");

        if (chunk.getTokenCount() == null || chunk.getTokenCount() < 0)
            throw new IllegalArgumentException("Chunk tokenCount must be a non-negative integer");

        validateEmbedding(chunk.getEmbedding());
    }

    private void validateEmbedding(float[] embedding) {
        if (embedding == null)
            throw new IllegalArgumentException("Embedding cannot be null");

        if (embedding.length != expectedDimension)
            throw new IllegalArgumentException(
                    "Embedding dimension mismatch: expected " + expectedDimension
                            + ", got " + embedding.length);

    }


    private String toVectorString(float[] embedding) {
        StringBuilder sb = new StringBuilder(embedding.length * 8); // rough capacity estimate

        sb.append('[');
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0)
                sb.append(',');

            sb.append(embedding[i]);
        }

        sb.append(']');
        return sb.toString();
    }



    private String toJsonString(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty())
            return "{}";

        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize chunk metadata to JSON", e);
        }
    }


    private class ChunkSimilarityRowMapper implements RowMapper<SimilarityResult> {
        @Override
        public SimilarityResult mapRow(ResultSet rs, int rowNum) throws SQLException {
            Chunk chunk = new Chunk();
            chunk.setId(UUID.fromString(rs.getString("id")));
            chunk.setDocumentId(UUID.fromString(rs.getString("document_id")));
            chunk.setTenantId(UUID.fromString(rs.getString("tenant_id")));
            chunk.setChunkIndex(rs.getInt("chunk_index"));
            chunk.setContent(rs.getString("content"));
            chunk.setTokenCount(rs.getInt("token_count"));

            String metadataJson = rs.getString("metadata");
            if (metadataJson != null && !metadataJson.isBlank()) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> metadata = objectMapper.readValue(metadataJson, Map.class);
                    chunk.setMetadata(metadata);
                } catch (JsonProcessingException e) {
                    log.warn("Failed to deserialize metadata for chunk {}: {}", chunk.getId(), e.getMessage());
                    chunk.setMetadata(new HashMap<>());
                }
            }

            double similarity = rs.getDouble("similarity");

            similarity = Math.max(0.0, Math.min(1.0, similarity));

            return new SimilarityResult(chunk, similarity);
        }
    }
}