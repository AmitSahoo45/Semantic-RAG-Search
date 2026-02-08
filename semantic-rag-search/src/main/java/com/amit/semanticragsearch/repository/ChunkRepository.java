package com.amit.semanticragsearch.repository;

import com.amit.semanticragsearch.domain.Chunk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChunkRepository extends JpaRepository<Chunk, UUID> {
    List<Chunk> findByDocumentIdOrderByChunkIndexAsc(UUID documentId);
    List<Chunk> findByTenantIdAndDocumentIdOrderByChunkIndexAsc(UUID tenantId, UUID documentId);
    void deleteByDocumentId(UUID documentId);
}
