package io.sentinel.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChannelReplyPostRepository extends JpaRepository<ChannelReplyPostEntity, String> {
    List<ChannelReplyPostEntity> findByMentionIdOrderByCreatedAtDesc(String mentionId);
    List<ChannelReplyPostEntity> findByTenantIdOrderByCreatedAtDesc(String tenantId);
}

