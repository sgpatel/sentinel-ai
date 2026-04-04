package io.sentinel.backend.service;

import io.sentinel.backend.agents.ComplianceAgent;
import io.sentinel.backend.agents.EscalationAgent;
import io.sentinel.backend.agents.ReplyAgent;
import io.sentinel.backend.agents.SentimentAgent;
import io.sentinel.backend.agents.TicketAgent;
import io.sentinel.backend.connector.TicketConnectorFactory;
import io.sentinel.backend.repository.MentionDlqEntity;
import io.sentinel.backend.repository.MentionDlqRepository;
import io.sentinel.backend.repository.MentionEntity;
import io.sentinel.backend.repository.MentionRepository;
import io.sentinel.backend.websocket.MentionWebSocketHandler;
import io.squados.annotation.AgentRole;
import io.squados.context.SquadContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Core 5-step AI processing pipeline for each mention.
 *
 * Every agent call is wrapped in try/catch so a partial LLM response
 * (missing fields, bad JSON, timeout) applies safe defaults and continues.
 * The mention is NEVER silently dropped.
 *
 * Steps:
 *   1. Sentiment analysis  (SentimentAgent  - ANALYST)
 *   2. Escalation scoring  (EscalationAgent - CRITIC)
 *   3. Reply generation    (ReplyAgent      - SUPPORT)
 *   3b. Compliance review  (ComplianceAgent - CRITIC)
 *   4. Ticket creation     (TicketAgent     - SUPPORT, conditional)
 */
@Service
public class MentionProcessingService {

    private final SquadContext           ctx;
    private final MentionRepository      repo;
    private final MentionDlqRepository   dlqRepo;
    private final PredictionService      predictionService;
    private final KnowledgeBaseService   knowledgeBaseService;
    private final TicketConnectorFactory connectorFactory;
    private final MentionWebSocketHandler ws;
    @Value("${sentinel.retry.max-attempts:3}")
    private int retryMaxAttempts;
    @Value("${sentinel.retry.base-delay-ms:200}")
    private long retryBaseDelayMs;
    @Value("${sentinel.circuit-breaker.failure-threshold:5}")
    private int circuitFailureThreshold;
    @Value("${sentinel.circuit-breaker.open-ms:30000}")
    private long circuitOpenMs;
    @Value("${sentinel.prediction.alert-threshold:70}")
    private double predictionAlertThreshold;
    @Value("${sentinel.prediction.priority-bump-threshold:85}")
    private double predictionPriorityBumpThreshold;
    private final Map<String, CircuitState> circuits = new ConcurrentHashMap<>();
    private final Map<String, OperationStats> opStats = new ConcurrentHashMap<>();

    public MentionProcessingService(SquadContext ctx,
                                    MentionRepository repo,
                                    MentionDlqRepository dlqRepo,
                                    PredictionService predictionService,
                                    KnowledgeBaseService knowledgeBaseService,
                                    TicketConnectorFactory connectorFactory,
                                    MentionWebSocketHandler ws) {
        this.ctx              = ctx;
        this.repo             = repo;
        this.dlqRepo          = dlqRepo;
        this.predictionService = predictionService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.connectorFactory = connectorFactory;
        this.ws               = ws;
    }

    public void process(MentionEntity mention) {
        mention.processingStatus = "ANALYSING";
        repo.save(mention);
        try {
            runPipeline(mention);
            mention.processingStatus = "DONE";
        } catch (Exception fatal) {
            mention.processingStatus = "ERROR";
            System.err.println("[MentionService] Fatal pipeline error for "
                + mention.id + ": " + fatal.getMessage());
            saveToDlq(mention, fatal);
        } finally {
            mention.updatedAt = Instant.now();
            repo.save(mention);
            ws.broadcast("mention.processed", mention);
        }
    }

    private void runPipeline(MentionEntity mention) {

        // ── Step 1: Sentiment analysis ────────────────────────────
        String sentimentPrompt =
            "Analyse the sentiment of this social media mention.\n" +
            "Platform: "  + nvl(mention.platform)       + "\n" +
            "Author: @"   + nvl(mention.authorUsername)
                          + " (" + mention.authorFollowers + " followers)\n" +
            "Text: "      + nvl(mention.text)            + "\n" +
            "Brand: "     + nvl(mention.handle);

        SentimentAgent.SentimentAnalysis sentiment = null;
        try {
            sentiment = withRetry("SentimentAgent", () ->
                (SentimentAgent.SentimentAnalysis) ctx.submitTo(
                    AgentRole.ANALYST, sentimentPrompt, SentimentAgent.SentimentAnalysis.class));
        } catch (Exception e) {
            System.err.println("[MentionService] Sentiment failed for "
                + mention.id + " (defaults applied): " + e.getMessage());
        }

        if (sentiment != null && sentiment.sentiment != null) {
            mention.sentimentLabel = sentiment.sentiment;
            mention.sentimentScore = parseDouble(sentiment.score, 0.5);
            mention.primaryEmotion = nvl(sentiment.primaryEmotion, "NEUTRAL");
            mention.urgency        = nvl(sentiment.urgency,        "LOW");
            mention.topic          = truncate(nvl(sentiment.topic, "GENERAL"), 100);
            mention.summary        = nvl(sentiment.summary, truncate(mention.text, 100));
        } else {
            mention.sentimentLabel = "NEUTRAL";
            mention.sentimentScore = 0.5;
            mention.primaryEmotion = "NEUTRAL";
            mention.urgency        = "LOW";
            mention.topic          = "GENERAL";  // already within 100 chars
            mention.summary        = truncate(mention.text, 100);
        }

        // ── Step 2: Escalation scoring ────────────────────────────
        String escalationPrompt =
            "Assess the priority and escalation path for this social mention.\n" +
            "Text: "      + nvl(mention.text)           + "\n" +
            "Sentiment: " + nvl(mention.sentimentLabel) + "\n" +
            "Urgency: "   + nvl(mention.urgency)        + "\n" +
            "Followers: " + mention.authorFollowers     + "\n" +
            "Retweets: "  + mention.retweetCount;

        EscalationAgent.EscalationDecision escalation = null;
        try {
            escalation = withRetry("EscalationAgent", () ->
                (EscalationAgent.EscalationDecision) ctx.submitTo(
                    AgentRole.CRITIC, escalationPrompt, EscalationAgent.EscalationDecision.class));
        } catch (Exception e) {
            System.err.println("[MentionService] Escalation failed for "
                + mention.id + " (defaults applied): " + e.getMessage());
        }

        if (escalation != null && escalation.priority != null) {
            mention.priority      = normalizePriority(escalation.priority);
            mention.assignedTeam  = nvl(escalation.escalationPath, "TECH_SUPPORT");
            mention.isViral       = "true".equalsIgnoreCase(
                                       String.valueOf(escalation.isViralRisk));
            mention.viralRiskScore = mention.isViral ? 80 : 20;
        } else {
            mention.priority     = normalizePriority(mention.sentimentLabel);
            mention.assignedTeam = "TECH_SUPPORT";
        }

        // ── Step 2b: Viral prediction + proactive alerting ───────
        boolean predictionCandidate = "NEGATIVE".equalsIgnoreCase(mention.sentimentLabel)
            || "P1".equalsIgnoreCase(mention.priority)
            || "HIGH".equalsIgnoreCase(mention.urgency)
            || "CRITICAL".equalsIgnoreCase(mention.urgency);
        if (predictionCandidate) {
            try {
                Map<String, Object> prediction = predictionService.predictAndStore(mention);
                double score6h = ((Number) prediction.getOrDefault("viralityScore6h", 0.0)).doubleValue();
                double score24h = ((Number) prediction.getOrDefault("viralityScore24h", 0.0)).doubleValue();
                String oldPriority = mention.priority;
                Map<String, Object> policy = evaluatePredictionPolicy(score24h, mention.priority);
                boolean alertTriggered = Boolean.TRUE.equals(policy.get("alertTriggered"));
                boolean priorityBumped = Boolean.TRUE.equals(policy.get("priorityBumped"));
                String newPriority = String.valueOf(policy.get("newPriority"));

                mention.viralRiskScore = Math.max(mention.viralRiskScore, (int) Math.round(score24h));
                mention.isViral = mention.isViral || alertTriggered;
                mention.priority = newPriority;

                if (priorityBumped) {
                    mention.assignedTeam = nvl(mention.assignedTeam, "CRISIS_RESPONSE");
                }
                if (alertTriggered) {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("contractVersion", "1.0");
                    payload.put("eventType", "alert.predicted_crisis");
                    payload.put("triggeredAtEpochMs", Instant.now().toEpochMilli());
                    payload.put("mentionId", mention.id);
                    payload.put("tenantId", nvl(mention.tenantId, "default"));
                    payload.put("triggerReason", "virality_score_24h");
                    payload.put("thresholds", Map.of(
                        "alertThreshold", predictionAlertThreshold,
                        "priorityBumpThreshold", predictionPriorityBumpThreshold
                    ));
                    payload.put("scores", Map.of(
                        "viralityScore6h", score6h,
                        "viralityScore24h", score24h
                    ));
                    payload.put("policy", Map.of(
                        "alertTriggered", alertTriggered,
                        "priorityBumped", priorityBumped,
                        "oldPriority", oldPriority,
                        "newPriority", newPriority
                    ));
                    payload.put("escalationLevel", prediction.get("escalationLevel"));
                    payload.put("recommendedAction", prediction.get("recommendedAction"));
                    ws.broadcast("alert.predicted_crisis", payload);
                }
            } catch (Exception e) {
                System.err.println("[MentionService] Prediction failed for " + mention.id + ": " + e.getMessage());
            }
        }

        // ── Step 3: Reply generation ──────────────────────────────
        String replyPrompt =
            "Generate a professional social media reply for this mention.\n" +
            "Brand: "     + nvl(mention.handle)        + "\n" +
            "Mention: "   + nvl(mention.text)           + "\n" +
            "Sentiment: " + nvl(mention.sentimentLabel) + "\n" +
            "Priority: "  + nvl(mention.priority)       + "\n" +
            "Topic: "     + nvl(mention.topic);

        List<Map<String, Object>> kbContext = knowledgeBaseService.retrieveForReply(
            nvl(mention.topic) + " " + nvl(mention.text), 2);
        if (!kbContext.isEmpty()) {
            StringBuilder kbBlock = new StringBuilder("\nUse these knowledge snippets if relevant:\n");
            for (Map<String, Object> snippet : kbContext) {
                kbBlock.append("- ")
                    .append(snippet.getOrDefault("title", "Untitled"))
                    .append(": ")
                    .append(snippet.getOrDefault("snippet", ""))
                    .append(" [")
                    .append(snippet.getOrDefault("citation", ""))
                    .append("]\n");
            }
            replyPrompt += kbBlock;
        }
        final String promptForReplyAgent = replyPrompt;

        ReplyAgent.GeneratedReply reply = null;
        try {
            reply = withRetry("ReplyAgent", () ->
                (ReplyAgent.GeneratedReply) ctx.submitTo(
                    AgentRole.SUPPORT, promptForReplyAgent, ReplyAgent.GeneratedReply.class));
        } catch (Exception e) {
            System.err.println("[MentionService] Reply generation failed for "
                + mention.id + ": " + e.getMessage());
        }

        if (reply != null && reply.replyText != null && !reply.replyText.isBlank()) {
            mention.replyText = reply.replyText;
        } else {
            mention.replyText = "Thank you for reaching out to "
                + nvl(mention.handle, "@us")
                + ". Our team is reviewing this and will respond shortly.";
        }

        if (!kbContext.isEmpty()) {
            String citations = kbContext.stream()
                .map(s -> String.valueOf(s.getOrDefault("citation", "")))
                .filter(c -> !c.isBlank())
                .distinct()
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
            if (!citations.isBlank()) {
                mention.replyText = mention.replyText + "\n\nSources: " + citations;
            }
        }
        mention.replyStatus = "PENDING";

        // ── Step 3b: Compliance review ────────────────────────────
        String compliancePrompt =
            "Review this reply for brand and regulatory compliance.\n" +
            "Original mention: " + nvl(mention.text)      + "\n" +
            "Proposed reply: "   + nvl(mention.replyText) + "\n" +
            "Brand: "            + nvl(mention.handle)    + "\n" +
            "Sentiment: "        + nvl(mention.sentimentLabel);

        ComplianceAgent.ComplianceReview compliance = null;
        try {
            compliance = withRetry("ComplianceAgent", () ->
                (ComplianceAgent.ComplianceReview) ctx.submitTo(
                    AgentRole.CRITIC, compliancePrompt, ComplianceAgent.ComplianceReview.class));
        } catch (Exception e) {
            System.err.println("[MentionService] Compliance check non-fatal for "
                + mention.id + ": " + e.getMessage());
        }

        if (compliance != null) {
            // getBestReply returns revisedReply when not approved, else original
            String best = compliance.getBestReply(mention.replyText);
            if (best != null && !best.isBlank()) {
                mention.replyText = best;
            }
            if (!compliance.isApproved()) {
                System.out.println("[MentionService] Compliance revised reply for: " + mention.id);
            }
        } else {
            System.out.println("[MentionService] Compliance null for " + mention.id
                + " — original reply queued for human review");
        }

        // ── Step 4: Ticket creation (NEGATIVE / P1 / P2) ─────────
        boolean needsTicket = "NEGATIVE".equals(mention.sentimentLabel)
            || "P1".equals(mention.priority)
            || "P2".equals(mention.priority);

        if (needsTicket) {
            String ticketPrompt =
                "Create a support ticket for this social media mention.\n" +
                "Text: "      + nvl(mention.text)           + "\n" +
                "Sentiment: " + nvl(mention.sentimentLabel) + "\n" +
                "Priority: "  + nvl(mention.priority)       + "\n" +
                "Topic: "     + nvl(mention.topic)          + "\n" +
                "Author: @"   + nvl(mention.authorUsername) + "\n" +
                "URL: "       + nvl(mention.url);

            TicketAgent.TicketPayload ticketPayload = null;
            try {
                ticketPayload = withRetry("TicketAgent", () ->
                    (TicketAgent.TicketPayload) ctx.submitTo(
                        AgentRole.SUPPORT, ticketPrompt, TicketAgent.TicketPayload.class));
            } catch (Exception e) {
                System.err.println("[MentionService] Ticket agent failed for "
                    + mention.id + ": " + e.getMessage());
            }

            if (ticketPayload == null) {
                ticketPayload           = new TicketAgent.TicketPayload();
                ticketPayload.title     = truncate(mention.text, 80);
                ticketPayload.description = "Mention from @" + mention.authorUsername
                                          + ": " + mention.text;
                ticketPayload.priority  = mention.priority;
                ticketPayload.category  = nvl(mention.topic, "GENERAL");
            }

            try {
                final TicketAgent.TicketPayload payloadForCreate = ticketPayload;
                String ticketId = withRetry("TicketConnector", () ->
                    connectorFactory.get().createTicket(mention, payloadForCreate));
                mention.ticketId     = ticketId;
                mention.ticketStatus = "OPEN";
                // getName() returns "ZENDESK", "JIRA", or "MOCK"
                mention.ticketSystem = connectorFactory.get().getName();
            } catch (Exception e) {
                System.err.println("[MentionService] Ticket connector failed for "
                    + mention.id + ": " + e.getMessage());
            }
        }
    }

    // ─── Utility helpers ─────────────────────────────────────────

    private String normalizePriority(String raw) {
        if (raw == null) return "P3";
        return switch (raw.toUpperCase().trim()) {
            case "P1", "CRITICAL", "URGENT", "HIGHEST" -> "P1";
            case "P2", "HIGH"                           -> "P2";
            case "P3", "MEDIUM", "NORMAL", "MODERATE"  -> "P3";
            case "P4", "LOW"                            -> "P4";
            case "NEGATIVE"                             -> "P2";
            case "POSITIVE"                             -> "P4";
            default -> raw.length() == 2 && raw.startsWith("P") ? raw : "P3";
        };
    }

    private String nvl(String s)            { return s != null ? s : ""; }
    private String nvl(String s, String def){ return (s != null && !s.isBlank()) ? s : def; }

    private double parseDouble(String s, double def) {
        if (s == null) return def;
        try { return Double.parseDouble(s); } catch (Exception e) { return def; }
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }

    @FunctionalInterface
    private interface RetryableSupplier<T> {
        T get() throws Exception;
    }

    private <T> T withRetry(String op, RetryableSupplier<T> action) throws Exception {
        OperationStats stats = opStats.computeIfAbsent(op, k -> new OperationStats());
        stats.calls.incrementAndGet();
        CircuitState state = circuits.computeIfAbsent(op, k -> new CircuitState());
        long now = System.currentTimeMillis();
        if (state.isOpen(now)) {
            stats.failures.incrementAndGet();
            stats.lastError = "Circuit open";
            stats.lastFailureAt = Instant.now().toString();
            throw new RuntimeException("Circuit open for " + op + " until " + state.openUntilEpochMs);
        }

        int attempts = Math.max(1, retryMaxAttempts);
        long delay = Math.max(50L, retryBaseDelayMs);
        Exception last = null;
        for (int i = 1; i <= attempts; i++) {
            try {
                T result = action.get();
                state.onSuccess();
                stats.successes.incrementAndGet();
                return result;
            } catch (Exception e) {
                last = e;
                stats.failures.incrementAndGet();
                stats.lastError = truncate(nvl(e.getMessage(), e.getClass().getSimpleName()), 300);
                stats.lastFailureAt = Instant.now().toString();
                state.onFailure(Math.max(1, circuitFailureThreshold), Math.max(1000L, circuitOpenMs),
                    System.currentTimeMillis());
                if (state.isOpen(System.currentTimeMillis())) {
                    stats.circuitOpens.incrementAndGet();
                    System.err.println("[MentionService] Circuit opened for " + op
                        + " after " + state.consecutiveFailures + " consecutive failures");
                    break;
                }
                if (i == attempts) break;
                stats.retries.incrementAndGet();
                System.err.println("[MentionService] " + op + " failed attempt "
                    + i + "/" + attempts + " — retrying in " + delay + "ms: " + e.getMessage());
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Retry interrupted for " + op, ie);
                }
                delay *= 2;
            }
        }
        throw last != null ? last : new RuntimeException("Retry failed for " + op);
    }

    public Map<String, Object> getReliabilityMetrics() {
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> ops = new LinkedHashMap<>();
        long now = System.currentTimeMillis();
        List<String> keys = opStats.keySet().stream().sorted().toList();
        for (String op : keys) {
            OperationStats s = opStats.get(op);
            CircuitState c = circuits.get(op);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("calls", s.calls.get());
            r.put("successes", s.successes.get());
            r.put("failures", s.failures.get());
            r.put("retries", s.retries.get());
            r.put("circuitOpens", s.circuitOpens.get());
            r.put("circuitOpen", c != null && c.isOpen(now));
            r.put("circuitOpenUntil", c != null ? c.openUntilEpochMs : 0L);
            r.put("lastError", s.lastError);
            r.put("lastFailureAt", s.lastFailureAt);
            ops.put(op, r);
        }
        out.put("operations", ops);
        out.put("retryMaxAttempts", retryMaxAttempts);
        out.put("retryBaseDelayMs", retryBaseDelayMs);
        out.put("circuitFailureThreshold", circuitFailureThreshold);
        out.put("circuitOpenMs", circuitOpenMs);
        out.put("predictionAlertThreshold", predictionAlertThreshold);
        out.put("predictionPriorityBumpThreshold", predictionPriorityBumpThreshold);
        return out;
    }

    public Map<String, Object> evaluatePredictionPolicy(double score24h, String currentPriority) {
        String normalizedPriority = normalizePriority(currentPriority);
        boolean alertTriggered = score24h >= predictionAlertThreshold;
        boolean priorityBumped = score24h >= predictionPriorityBumpThreshold;
        String newPriority = priorityBumped ? "P1" : normalizedPriority;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("score24h", score24h);
        out.put("alertThreshold", predictionAlertThreshold);
        out.put("priorityBumpThreshold", predictionPriorityBumpThreshold);
        out.put("alertTriggered", alertTriggered);
        out.put("priorityBumped", priorityBumped);
        out.put("oldPriority", normalizedPriority);
        out.put("newPriority", newPriority);
        return out;
    }

    private static final class CircuitState {
        private int consecutiveFailures;
        private long openUntilEpochMs;

        synchronized boolean isOpen(long nowEpochMs) {
            return openUntilEpochMs > nowEpochMs;
        }

        synchronized void onSuccess() {
            consecutiveFailures = 0;
            openUntilEpochMs = 0L;
        }

        synchronized void onFailure(int threshold, long openMs, long nowEpochMs) {
            if (isOpen(nowEpochMs)) return;
            consecutiveFailures++;
            if (consecutiveFailures >= threshold) {
                openUntilEpochMs = nowEpochMs + openMs;
            }
        }
    }

    private static final class OperationStats {
        private final AtomicLong calls = new AtomicLong();
        private final AtomicLong successes = new AtomicLong();
        private final AtomicLong failures = new AtomicLong();
        private final AtomicLong retries = new AtomicLong();
        private final AtomicLong circuitOpens = new AtomicLong();
        private volatile String lastError = "";
        private volatile String lastFailureAt = "";
    }

    private void saveToDlq(MentionEntity mention, Exception fatal) {
        try {
            MentionDlqEntity row = new MentionDlqEntity();
            row.mentionId = mention.id;
            row.tenantId = nvl(mention.tenantId, "default");
            row.failureStage = "PIPELINE";
            row.errorMessage = truncate(nvl(fatal.getMessage(), fatal.getClass().getSimpleName()), 2000);
            row.stackTrace = stackTraceOf(fatal);
            row.payloadJson = payloadSnapshot(mention);
            row.status = "NEW";
            dlqRepo.save(row);
        } catch (Exception dlqErr) {
            System.err.println("[MentionService] DLQ save failed for " + mention.id + ": " + dlqErr.getMessage());
        }
    }

    private String stackTraceOf(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private String payloadSnapshot(MentionEntity m) {
        return "{"
            + "\"id\":\"" + jsonEsc(nvl(m.id)) + "\","
            + "\"tenantId\":\"" + jsonEsc(nvl(m.tenantId, "default")) + "\","
            + "\"platform\":\"" + jsonEsc(nvl(m.platform)) + "\","
            + "\"authorUsername\":\"" + jsonEsc(nvl(m.authorUsername)) + "\","
            + "\"handle\":\"" + jsonEsc(nvl(m.handle)) + "\","
            + "\"text\":\"" + jsonEsc(nvl(m.text)) + "\""
            + "}";
    }

    private String jsonEsc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
