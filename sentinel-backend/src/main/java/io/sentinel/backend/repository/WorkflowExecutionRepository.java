package io.sentinel.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkflowExecutionRepository extends JpaRepository<WorkflowExecutionEntity, String> {
    List<WorkflowExecutionEntity> findByTenantIdOrderByCreatedAtDesc(String tenantId);
    boolean existsByRuleId(String ruleId);
}

