package io.sentinel.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface PredictionHistoryRepository extends JpaRepository<PredictionHistoryEntity, String> {
    List<PredictionHistoryEntity> findByTenantIdOrderByPredictedAtDesc(String tenantId);
    List<PredictionHistoryEntity> findByTenantIdAndPredictedAtAfterOrderByPredictedAtDesc(String tenantId, Instant since);
}

