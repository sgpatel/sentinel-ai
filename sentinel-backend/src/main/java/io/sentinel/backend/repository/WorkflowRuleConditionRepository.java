package io.sentinel.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkflowRuleConditionRepository extends JpaRepository<WorkflowRuleConditionEntity, String> {
    List<WorkflowRuleConditionEntity> findByRuleIdOrderByPositionAsc(String ruleId);
    void deleteByRuleId(String ruleId);
}

