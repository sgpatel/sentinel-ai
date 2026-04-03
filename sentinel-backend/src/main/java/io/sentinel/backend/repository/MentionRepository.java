package io.sentinel.backend.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.List;
@Repository
public interface MentionRepository extends JpaRepository<MentionEntity, String>, JpaSpecificationExecutor<MentionEntity> {
    List<MentionEntity> findByTenantIdOrderByPostedAtDesc(String tenantId);
    List<MentionEntity> findByTenantIdAndReplyStatusOrderByPostedAtDesc(String tenantId, String status);
    List<MentionEntity> findByTenantIdAndPostedAtAfterOrderByPostedAtDesc(String tenantId, Instant since);
    List<MentionEntity> findByTenantIdAndHandleAndPostedAtAfterOrderByPostedAtDesc(
        String tenantId, String handle, Instant since);
    List<MentionEntity> findByTenantIdAndPriorityAndProcessingStatusNot(
        String tenantId, String priority, String status);

    List<MentionEntity> findByHandleOrderByPostedAtDesc(String handle);
    List<MentionEntity> findByHandleAndSentimentLabelOrderByPostedAtDesc(String handle, String label);
    List<MentionEntity> findByReplyStatusOrderByPostedAtDesc(String status);
    List<MentionEntity> findByPriorityAndProcessingStatusNot(String priority, String status);
    List<MentionEntity> findByPostedAtAfterOrderByPostedAtDesc(Instant since);
    @Query("SELECT m FROM MentionEntity m WHERE m.handle = ?1 AND m.postedAt > ?2 ORDER BY m.urgencyScore DESC")
    List<MentionEntity> findUrgentByHandle(String handle, Instant since);
    @Query("SELECT m.sentimentLabel, COUNT(m) FROM MentionEntity m WHERE m.handle = ?1 AND m.postedAt > ?2 GROUP BY m.sentimentLabel")
    List<Object[]> countBySentiment(String handle, Instant since);
    @Query("SELECT m.topic, COUNT(m) FROM MentionEntity m WHERE m.handle = ?1 AND m.postedAt > ?2 GROUP BY m.topic ORDER BY COUNT(m) DESC")
    List<Object[]> countByTopic(String handle, Instant since);

    @Query("SELECT m FROM MentionEntity m WHERE m.tenantId = ?1 AND m.handle = ?2 AND m.postedAt > ?3 ORDER BY m.urgencyScore DESC")
    List<MentionEntity> findUrgentByTenantAndHandle(String tenantId, String handle, Instant since);

    @Query("SELECT m FROM MentionEntity m WHERE m.tenantId = ?1")
    List<MentionEntity> findAllByTenantId(String tenantId);

    long countByHandleAndPostedAtAfter(String handle, Instant since);
    long countByHandleAndSentimentLabelAndPostedAtAfter(String handle, String label, Instant since);
}