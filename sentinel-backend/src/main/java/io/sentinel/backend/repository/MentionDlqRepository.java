package io.sentinel.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MentionDlqRepository extends JpaRepository<MentionDlqEntity, String> {
    List<MentionDlqEntity> findByStatusOrderByCreatedAtAsc(String status);
    List<MentionDlqEntity> findByTenantIdAndStatusOrderByCreatedAtAsc(String tenantId, String status);
    Optional<MentionDlqEntity> findByIdAndTenantId(String id, String tenantId);
    long countByTenantIdAndStatus(String tenantId, String status);
    long countByTenantId(String tenantId);
}

