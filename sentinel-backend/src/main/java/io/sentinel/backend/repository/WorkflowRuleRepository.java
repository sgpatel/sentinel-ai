package io.sentinel.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkflowRuleRepository extends JpaRepository<WorkflowRuleEntity, String> {
    List<WorkflowRuleEntity> findByTenantIdOrderByPriorityDescCreatedAtDesc(String tenantId);
    List<WorkflowRuleEntity> findByTenantIdAndEnabledTrueOrderByPriorityDescCreatedAtDesc(String tenantId);
    Optional<WorkflowRuleEntity> findByIdAndTenantId(String id, String tenantId);
}

