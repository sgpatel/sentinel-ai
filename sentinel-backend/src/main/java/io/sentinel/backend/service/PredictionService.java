package io.sentinel.backend.service;

import io.sentinel.backend.repository.MentionEntity;
import io.sentinel.backend.repository.PredictionHistoryEntity;
import io.sentinel.backend.repository.PredictionHistoryRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class PredictionService {

    private final PredictionHistoryRepository historyRepo;

    public PredictionService(PredictionHistoryRepository historyRepo) {
        this.historyRepo = historyRepo;
    }

    public Map<String, Object> predictAndStore(MentionEntity mention) {
        double score6h = score(mention, 0.7);
        double score24h = Math.min(100.0, score6h + 8.0);

        String escalation = score24h >= 85 ? "CRITICAL"
            : score24h >= 70 ? "HIGH"
            : score24h >= 50 ? "MEDIUM"
            : "LOW";

        String action = switch (escalation) {
            case "CRITICAL" -> "ACTIVATE_CRISIS_TEAM";
            case "HIGH" -> "PREPARE_ESCALATION";
            case "MEDIUM" -> "MONITOR_CLOSELY";
            default -> "NORMAL_MONITORING";
        };

        PredictionHistoryEntity row = new PredictionHistoryEntity();
        row.mentionId = mention.id;
        row.tenantId = mention.tenantId != null ? mention.tenantId : "default";
        row.viralityScore6h = round1(score6h);
        row.viralityScore24h = round1(score24h);
        row.escalationLevel = escalation;
        row.recommendedAction = action;
        historyRepo.save(row);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("mentionId", mention.id);
        out.put("tenantId", row.tenantId);
        out.put("viralityScore6h", row.viralityScore6h);
        out.put("viralityScore24h", row.viralityScore24h);
        out.put("escalationLevel", row.escalationLevel);
        out.put("recommendedAction", row.recommendedAction);
        out.put("predictedAt", row.predictedAt);
        return out;
    }

    private double score(MentionEntity m, double horizonWeight) {
        double followerScore = Math.min(40, Math.log10(Math.max(1, m.authorFollowers)) * 10);
        double engagementScore = Math.min(25, (m.retweetCount * 1.5 + m.likeCount * 0.5));
        double sentimentRisk = "NEGATIVE".equalsIgnoreCase(m.sentimentLabel) ? 15 : 5;
        double urgencyRisk = switch (String.valueOf(m.urgency).toUpperCase()) {
            case "CRITICAL" -> 20;
            case "HIGH" -> 14;
            case "MEDIUM" -> 8;
            default -> 3;
        };
        double priorityRisk = switch (String.valueOf(m.priority).toUpperCase()) {
            case "P1" -> 20;
            case "P2" -> 12;
            default -> 3;
        };
        double raw = followerScore + engagementScore + sentimentRisk + urgencyRisk + priorityRisk;
        return Math.max(0, Math.min(100, raw * horizonWeight));
    }

    private double round1(double x) {
        return Math.round(x * 10.0) / 10.0;
    }
}

