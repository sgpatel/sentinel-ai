package io.sentinel.backend.service;

import io.sentinel.backend.repository.MentionDlqEntity;
import io.sentinel.backend.repository.MentionDlqRepository;
import io.sentinel.backend.repository.MentionEntity;
import io.sentinel.backend.repository.MentionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class MentionDlqService {

    private final MentionDlqRepository dlqRepo;
    private final MentionRepository mentionRepo;
    private final MentionProcessingService processor;

    @Value("${sentinel.dlq.replay.execute:true}")
    private boolean replayExecute;
    @Value("${sentinel.dlq.auto-replay.enabled:false}")
    private boolean autoReplayEnabled;
    @Value("${sentinel.dlq.auto-replay.batch-size:20}")
    private int autoReplayBatchSize;

    public MentionDlqService(MentionDlqRepository dlqRepo,
                             MentionRepository mentionRepo,
                             MentionProcessingService processor) {
        this.dlqRepo = dlqRepo;
        this.mentionRepo = mentionRepo;
        this.processor = processor;
    }

    public List<MentionDlqEntity> listForTenant(String tenantId, String status, int limit) {
        List<MentionDlqEntity> all = dlqRepo.findByTenantIdAndStatusOrderByCreatedAtAsc(tenantId, status);
        return all.stream().limit(Math.max(1, Math.min(500, limit))).toList();
    }

    public Optional<MentionDlqEntity> replayOneForTenant(String dlqId, String tenantId) {
        Optional<MentionDlqEntity> maybe = dlqRepo.findByIdAndTenantId(dlqId, tenantId);
        maybe.ifPresent(this::requeueOne);
        return maybe;
    }

    public int replayBatchForTenant(String tenantId, int limit) {
        List<MentionDlqEntity> pending = dlqRepo.findByTenantIdAndStatusOrderByCreatedAtAsc(tenantId, "NEW")
            .stream().limit(Math.max(1, Math.min(500, limit))).toList();
        pending.forEach(this::requeueOne);
        return pending.size();
    }

    @Scheduled(fixedDelayString = "${sentinel.dlq.auto-replay.interval-ms:60000}")
    public void autoReplay() {
        if (!autoReplayEnabled) return;
        List<MentionDlqEntity> pending = dlqRepo.findByStatusOrderByCreatedAtAsc("NEW")
            .stream().limit(Math.max(1, Math.min(500, autoReplayBatchSize))).toList();
        pending.forEach(this::requeueOne);
    }

    private void requeueOne(MentionDlqEntity row) {
        row.retryCount += 1;
        row.lastRetryAt = Instant.now();
        row.status = "REQUEUED";
        dlqRepo.save(row);

        if (!replayExecute) return;

        Optional<MentionEntity> maybeMention = mentionRepo.findById(row.mentionId);
        if (maybeMention.isEmpty()) {
            row.status = "FAILED";
            row.errorMessage = "Replay failed: mention not found";
            dlqRepo.save(row);
            return;
        }

        MentionEntity mention = maybeMention.get();
        mention.processingStatus = "NEW";
        mention.updatedAt = Instant.now();
        mentionRepo.save(mention);

        new Thread(() -> processor.process(mention)).start();
    }
}

