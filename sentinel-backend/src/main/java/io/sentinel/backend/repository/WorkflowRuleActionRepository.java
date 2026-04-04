package io.sentinel.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkflowRuleActionRepository extends JpaRepository<WorkflowRuleActionEntity, String> {
    List<WorkflowRuleActionEntity> findByRuleIdOrderByPositionAsc(String ruleId);
    void deleteByRuleId(String ruleId);
}

