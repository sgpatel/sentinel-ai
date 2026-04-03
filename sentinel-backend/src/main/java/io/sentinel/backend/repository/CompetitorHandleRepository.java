package io.sentinel.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CompetitorHandleRepository extends JpaRepository<CompetitorHandleEntity, String> {
    List<CompetitorHandleEntity> findByTenantIdOrderByCreatedAtDesc(String tenantId);
    List<CompetitorHandleEntity> findByTenantIdAndActiveTrueOrderByCreatedAtDesc(String tenantId);
    void deleteByTenantId(String tenantId);
}

