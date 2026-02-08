package com.amit.semanticragsearch.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;


@Entity
@Table(name = "tenants")
public class Tenant {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "api_key_hash", nullable = false, unique = true, length = 64)
    private String apiKeyHash;

    @Column(name = "token_limit_per_day", nullable = false)
    private Long tokenLimitPerDay = 100000L; // Default token limit

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();

        if(createdAt == null)
            createdAt = now;

        if (updatedAt == null)
            updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // Setters and getters

    // For uuid
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    // for name
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    // for apiKeyHash
    public String getApiKeyHash() { return apiKeyHash; }
    public void setApiKeyHash(String apiKeyHash) { this.apiKeyHash = apiKeyHash; }

    // for tokenLimitPerDay
    public Long getTokenLimitPerDay() { return tokenLimitPerDay; }
    public void setTokenLimitPerDay(Long tokenLimitPerDay) { this.tokenLimitPerDay = tokenLimitPerDay; }

    // for createdAt
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    // for updatedAt
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}