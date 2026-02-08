package com.amit.semanticragsearch.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "documents", uniqueConstraints = @UniqueConstraint(name = "uq_documents_tenant_content", columnNames = {"tenant_id", "content_hash"}))
public class Document {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(length = 500)
    private String title;

    @Column(name = "source_url", length = 2000)
    private String sourceUrl;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata = new HashMap<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();

        if (createdAt == null)
            createdAt = now;

        if (updatedAt == null)
            updatedAt = now;

        if (metadata == null)
            metadata = new HashMap<>();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = OffsetDateTime.now();

        if (metadata == null)
            metadata = new HashMap<>();
    }


    // Setters and getters
    // For uuid
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    // For tenantId
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    // For title
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    // For sourceUrl
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }

    // For content
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    // For contentHash
    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }

    // For metadata
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = (metadata == null) ? new HashMap<>() : metadata;
    }

    // For createdAt
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    // For updatedAt
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}