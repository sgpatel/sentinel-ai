package io.sentinel.backend.service;

import io.sentinel.backend.repository.CompetitorHandleRepository;
import io.sentinel.backend.repository.MentionEntity;
import io.sentinel.backend.repository.MentionRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class CompetitiveAnalyticsService {

    private final MentionRepository mentionRepo;
    private final CompetitorHandleRepository competitorRepo;

    public CompetitiveAnalyticsService(MentionRepository mentionRepo,
                                       CompetitorHandleRepository competitorRepo) {
        this.mentionRepo = mentionRepo;
        this.competitorRepo = competitorRepo;
    }

    public List<Map<String, Object>> getSentimentComparison(String tenantId, String primaryHandle, int hours) {
        Instant since = Instant.now().minus(Math.max(1, hours), ChronoUnit.HOURS);
        List<String> deduped = resolveHandles(tenantId, primaryHandle);

        List<Map<String, Object>> out = new ArrayList<>();
        for (String handle : deduped) {
            List<MentionEntity> rows = mentionRepo
                .findByTenantIdAndHandleAndPostedAtAfterOrderByPostedAtDesc(tenantId, handle, since);

            long total = rows.size();
            long positive = rows.stream().filter(m -> "POSITIVE".equalsIgnoreCase(m.sentimentLabel)).count();
            long negative = rows.stream().filter(m -> "NEGATIVE".equalsIgnoreCase(m.sentimentLabel)).count();
            long neutral = rows.stream().filter(m -> "NEUTRAL".equalsIgnoreCase(m.sentimentLabel)).count();

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("handle", handle);
            row.put("isPrimary", handle.equalsIgnoreCase(primaryHandle));
            row.put("totalMentions", total);
            row.put("positiveMentions", positive);
            row.put("negativeMentions", negative);
            row.put("neutralMentions", neutral);
            row.put("positivePct", total == 0 ? 0.0 : round1((positive * 100.0) / total));
            row.put("negativePct", total == 0 ? 0.0 : round1((negative * 100.0) / total));
            row.put("neutralPct", total == 0 ? 0.0 : round1((neutral * 100.0) / total));
            out.add(row);
        }
        return out;
    }

    public List<Map<String, Object>> getVolumeTrendComparison(
        String tenantId, String primaryHandle, int hours, int bucketHours) {
        int boundedHours = Math.max(1, hours);
        int boundedBucket = Math.max(1, bucketHours);
        Instant since = Instant.now().minus(boundedHours, ChronoUnit.HOURS);
        List<String> handles = resolveHandles(tenantId, primaryHandle);

        List<Map<String, Object>> out = new ArrayList<>();
        for (String handle : handles) {
            List<MentionEntity> rows = mentionRepo
                .findByTenantIdAndHandleAndPostedAtAfterOrderByPostedAtDesc(tenantId, handle, since);

            Map<Long, Long> countsByBucket = new LinkedHashMap<>();
            long total = 0;
            for (MentionEntity m : rows) {
                if (m.postedAt == null) continue;
                long ageHours = ChronoUnit.HOURS.between(m.postedAt, Instant.now());
                if (ageHours < 0 || ageHours > boundedHours) continue;
                long bucketStart = (ageHours / boundedBucket) * boundedBucket;
                countsByBucket.put(bucketStart, countsByBucket.getOrDefault(bucketStart, 0L) + 1L);
                total++;
            }

            List<Map<String, Object>> points = new ArrayList<>();
            for (long start = 0; start < boundedHours; start += boundedBucket) {
                Map<String, Object> point = new LinkedHashMap<>();
                point.put("bucketStartHoursAgo", start);
                point.put("bucketEndHoursAgo", Math.min(boundedHours, start + boundedBucket));
                point.put("count", countsByBucket.getOrDefault(start, 0L));
                points.add(point);
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("handle", handle);
            row.put("isPrimary", handle.equalsIgnoreCase(primaryHandle));
            row.put("totalMentions", total);
            row.put("bucketHours", boundedBucket);
            row.put("windowHours", boundedHours);
            row.put("points", points);
            out.add(row);
        }
        return out;
    }

    private List<String> resolveHandles(String tenantId, String primaryHandle) {
        List<String> handles = new ArrayList<>();
        handles.add(primaryHandle);
        competitorRepo.findByTenantIdAndActiveTrueOrderByCreatedAtDesc(tenantId).forEach(c -> {
            if (c.handle != null && !c.handle.isBlank()) handles.add(c.handle);
        });

        // Dedupe case-insensitively while preserving first seen casing for DB lookup.
        LinkedHashMap<String, String> byNorm = new LinkedHashMap<>();
        for (String h : handles) {
            String normalized = norm(h);
            if (normalized.isBlank()) continue;
            byNorm.putIfAbsent(normalized, h.trim());
        }
        return new ArrayList<>(byNorm.values());
    }

    private String norm(String h) {
        return h == null ? "" : h.trim().toLowerCase(Locale.ROOT);
    }

    private double round1(double x) {
        return Math.round(x * 10.0) / 10.0;
    }
}

