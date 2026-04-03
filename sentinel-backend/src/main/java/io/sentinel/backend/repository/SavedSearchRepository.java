package io.sentinel.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SavedSearchRepository extends JpaRepository<SavedSearchEntity, String> {
    List<SavedSearchEntity> findByTenantIdAndUserIdOrderByUpdatedAtDesc(String tenantId, String userId);
    Optional<SavedSearchEntity> findByIdAndTenantIdAndUserId(String id, String tenantId, String userId);
}

