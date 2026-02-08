package com.amit.semanticragsearch.repository;

import com.amit.semanticragsearch.domain.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {
    Optional<Document> findByTenantIdAndId(UUID tenantId, UUID id);
    boolean existsByTenantIdAndContentHash(UUID tenantId, String contentHash);
    List<Document> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
