package com.amit.semanticragsearch.repository;

import com.amit.semanticragsearch.domain.UsageRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface UsageRecordRepository extends JpaRepository<UsageRecord, UUID> {
    List<UsageRecord> findByTenantIdAndCreatedAtBetween(UUID tenantId, OffsetDateTime start, OffsetDateTime end);
}  
