package io.sentinel.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkflowExecutionStepRepository extends JpaRepository<WorkflowExecutionStepEntity, String> {
    List<WorkflowExecutionStepEntity> findByExecutionIdOrderByCreatedAtAsc(String executionId);
}

