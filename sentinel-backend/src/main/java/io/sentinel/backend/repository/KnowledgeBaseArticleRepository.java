package io.sentinel.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KnowledgeBaseArticleRepository extends JpaRepository<KnowledgeBaseArticleEntity, String> {
    List<KnowledgeBaseArticleEntity> findByTenantIdOrderByUpdatedAtDesc(String tenantId);
    List<KnowledgeBaseArticleEntity> findByTenantIdAndActiveTrueOrderByUpdatedAtDesc(String tenantId);
    Optional<KnowledgeBaseArticleEntity> findByIdAndTenantId(String id, String tenantId);
    Optional<KnowledgeBaseArticleEntity> findByIdAndTenantIdAndActiveTrue(String id, String tenantId);
}

