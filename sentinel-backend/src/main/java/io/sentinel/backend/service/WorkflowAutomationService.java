package io.sentinel.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sentinel.backend.config.TenantContext;
import io.sentinel.backend.repository.MentionEntity;
import io.sentinel.backend.repository.MentionRepository;
import io.sentinel.backend.repository.WorkflowExecutionStepRepository;
import io.sentinel.backend.repository.WorkflowExecutionEntity;
import io.sentinel.backend.repository.WorkflowExecutionRepository;
import io.sentinel.backend.repository.WorkflowExecutionStepEntity;
import io.sentinel.backend.repository.WorkflowRuleActionEntity;
import io.sentinel.backend.repository.WorkflowRuleActionRepository;
import io.sentinel.backend.repository.WorkflowRuleConditionEntity;
import io.sentinel.backend.repository.WorkflowRuleConditionRepository;
import io.sentinel.backend.repository.WorkflowRuleEntity;
import io.sentinel.backend.repository.WorkflowRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Optional;

@Service
public class WorkflowAutomationService {

    private final WorkflowRuleRepository ruleRepo;
    private final WorkflowRuleConditionRepository conditionRepo;
    private final WorkflowRuleActionRepository actionRepo;
    private final WorkflowExecutionRepository executionRepo;
    private final WorkflowExecutionStepRepository stepRepo;
    private final MentionRepository mentionRepo;
    private final KnowledgeBaseService knowledgeBaseService;
    private final ObjectMapper mapper;
    private final HttpClient httpClient;

    public WorkflowAutomationService(
        WorkflowRuleRepository ruleRepo,
        WorkflowRuleConditionRepository conditionRepo,
        WorkflowRuleActionRepository actionRepo,
        WorkflowExecutionRepository executionRepo,
        WorkflowExecutionStepRepository stepRepo,
        MentionRepository mentionRepo,
        KnowledgeBaseService knowledgeBaseService,
        ObjectMapper mapper
    ) {
        this.ruleRepo = ruleRepo;
        this.conditionRepo = conditionRepo;
        this.actionRepo = actionRepo;
        this.executionRepo = executionRepo;
        this.stepRepo = stepRepo;
        this.mentionRepo = mentionRepo;
        this.knowledgeBaseService = knowledgeBaseService;
        this.mapper = mapper;
        this.httpClient = HttpClient.newBuilder().build();
    }

    @Transactional
    public WorkflowRuleEntity createRule(Map<String, Object> body) {
        String tenantId = TenantContext.getOrDefault();

        WorkflowRuleEntity rule = new WorkflowRuleEntity();
        rule.tenantId = tenantId;
        rule.name = String.valueOf(body.getOrDefault("name", "")).trim();
        rule.description = String.valueOf(body.getOrDefault("description", "")).trim();
        rule.enabled = !Boolean.FALSE.equals(body.get("enabled"));
        rule.priority = parseInt(body.get("priority"), 100);
        rule.conflictStrategy = String.valueOf(body.getOrDefault("conflictStrategy", "FIRST_MATCH")).trim();
        if (rule.name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        ruleRepo.save(rule);

        List<Map<String, Object>> conditions = asObjectList(body.get("conditions"));
        for (int i = 0; i < conditions.size(); i++) {
            Map<String, Object> c = conditions.get(i);
            WorkflowRuleConditionEntity condition = new WorkflowRuleConditionEntity();
            condition.ruleId = rule.id;
            condition.fieldName = String.valueOf(c.getOrDefault("field", "")).trim();
            condition.operator = String.valueOf(c.getOrDefault("operator", "EQUALS")).trim();
            condition.valueText = String.valueOf(c.getOrDefault("value", "")).trim();
            condition.position = parseInt(c.get("position"), i + 1);
            if (condition.fieldName.isBlank()) {
                throw new IllegalArgumentException("condition field is required");
            }
            conditionRepo.save(condition);
        }

        List<Map<String, Object>> actions = asObjectList(body.get("actions"));
        for (int i = 0; i < actions.size(); i++) {
            Map<String, Object> a = actions.get(i);
            WorkflowRuleActionEntity action = new WorkflowRuleActionEntity();
            action.ruleId = rule.id;
            action.actionType = String.valueOf(a.getOrDefault("type", "")).trim();
            action.payloadJson = toJson(a.get("payload"));
            action.position = parseInt(a.get("position"), i + 1);
            if (action.actionType.isBlank()) {
                throw new IllegalArgumentException("action type is required");
            }
            actionRepo.save(action);
        }

        return rule;
    }

    public Map<String, Object> evaluateDryRun(String mentionId) {
        String tenantId = TenantContext.getOrDefault();
        MentionEntity mention = mentionRepo.findById(mentionId)
            .filter(m -> tenantId.equals(m.tenantId))
            .orElseThrow(() -> new IllegalArgumentException("mention not found"));

        Instant started = Instant.now();
        List<WorkflowRuleEntity> rules = ruleRepo.findByTenantIdAndEnabledTrueOrderByPriorityDescCreatedAtDesc(tenantId);
        List<Map<String, Object>> matched = new ArrayList<>();

        for (WorkflowRuleEntity rule : rules) {
            List<WorkflowRuleConditionEntity> conditions = conditionRepo.findByRuleIdOrderByPositionAsc(rule.id);
            boolean matchedAll = true;
            for (WorkflowRuleConditionEntity condition : conditions) {
                boolean ok = evaluateCondition(mention, condition);
                createExecutionAndStepAudit(rule.id, mention.id, tenantId, true, "CONDITION", condition.fieldName, ok,
                    Map.of("operator", condition.operator, "value", nullToEmpty(condition.valueText)));
                if (!ok) {
                    matchedAll = false;
                    break;
                }
            }
            if (!matchedAll) continue;

            List<WorkflowRuleActionEntity> actions = actionRepo.findByRuleIdOrderByPositionAsc(rule.id);
            List<Map<String, Object>> simulatedActions = new ArrayList<>();
            for (WorkflowRuleActionEntity action : actions) {
                createExecutionAndStepAudit(rule.id, mention.id, tenantId, true, "ACTION", action.actionType, true,
                    Map.of("mode", "DRY_RUN", "payload", safeJsonToMap(action.payloadJson)));
                simulatedActions.add(Map.of(
                    "type", action.actionType,
                    "payload", safeJsonToMap(action.payloadJson),
                    "simulated", true
                ));
            }
            matched.add(Map.of(
                "ruleId", rule.id,
                "ruleName", rule.name,
                "priority", rule.priority,
                "actions", simulatedActions
            ));

            if ("FIRST_MATCH".equalsIgnoreCase(rule.conflictStrategy)) {
                break;
            }
        }

        long durationMs = Math.max(0L, Instant.now().toEpochMilli() - started.toEpochMilli());
        return Map.of(
            "mentionId", mention.id,
            "tenantId", tenantId,
            "dryRun", true,
            "matchedRules", matched,
            "durationMs", durationMs
        );
    }

    @Transactional
    public Map<String, Object> executeNow(String mentionId) {
        String tenantId = TenantContext.getOrDefault();
        MentionEntity mention = mentionRepo.findById(mentionId)
            .filter(m -> tenantId.equals(m.tenantId))
            .orElseThrow(() -> new IllegalArgumentException("mention not found"));

        Instant started = Instant.now();
        List<WorkflowRuleEntity> rules = ruleRepo.findByTenantIdAndEnabledTrueOrderByPriorityDescCreatedAtDesc(tenantId);
        List<Map<String, Object>> applied = new ArrayList<>();

        for (WorkflowRuleEntity rule : rules) {
            List<WorkflowRuleConditionEntity> conditions = conditionRepo.findByRuleIdOrderByPositionAsc(rule.id);
            boolean matchedAll = true;
            for (WorkflowRuleConditionEntity condition : conditions) {
                boolean ok = evaluateCondition(mention, condition);
                createExecutionAndStepAudit(rule.id, mention.id, tenantId, false, "CONDITION", condition.fieldName, ok,
                    Map.of("operator", condition.operator, "value", nullToEmpty(condition.valueText)));
                if (!ok) {
                    matchedAll = false;
                    break;
                }
            }
            if (!matchedAll) continue;

            List<WorkflowRuleActionEntity> actions = actionRepo.findByRuleIdOrderByPositionAsc(rule.id);
            List<Map<String, Object>> actionResults = new ArrayList<>();
            for (WorkflowRuleActionEntity action : actions) {
                Map<String, Object> payload = safeJsonToMap(action.payloadJson);
                boolean success = true;
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("payload", payload);
                try {
                    applyAction(mention, action.actionType, payload, details);
                } catch (Exception ex) {
                    success = false;
                    details.put("error", ex.getMessage());
                }
                createExecutionAndStepAudit(rule.id, mention.id, tenantId, false, "ACTION", action.actionType, success, details);
                actionResults.add(Map.of(
                    "type", action.actionType,
                    "success", success,
                    "details", details
                ));
            }
            mentionRepo.save(mention);
            applied.add(Map.of(
                "ruleId", rule.id,
                "ruleName", rule.name,
                "priority", rule.priority,
                "actions", actionResults
            ));

            if ("FIRST_MATCH".equalsIgnoreCase(rule.conflictStrategy)) {
                break;
            }
        }

        long durationMs = Math.max(0L, Instant.now().toEpochMilli() - started.toEpochMilli());
        return Map.of(
            "mentionId", mention.id,
            "tenantId", tenantId,
            "dryRun", false,
            "appliedRules", applied,
            "replyText", nullToEmpty(mention.replyText),
            "priority", nullToEmpty(mention.priority),
            "assignedTeam", nullToEmpty(mention.assignedTeam),
            "durationMs", durationMs
        );
    }

    @Transactional
    public void setRuleEnabled(String ruleId, boolean enabled) {
        String tenantId = TenantContext.getOrDefault();
        WorkflowRuleEntity rule = ruleRepo.findByIdAndTenantId(ruleId, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("rule not found"));
        rule.enabled = enabled;
        ruleRepo.save(rule);
    }

    @Transactional
    public void deleteRule(String ruleId) {
        String tenantId = TenantContext.getOrDefault();
        WorkflowRuleEntity rule = ruleRepo.findByIdAndTenantId(ruleId, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("rule not found"));

        if (executionRepo.existsByRuleId(rule.id)) {
            throw new IllegalArgumentException("rule has execution history; disable it instead");
        }

        actionRepo.deleteByRuleId(rule.id);
        conditionRepo.deleteByRuleId(rule.id);
        ruleRepo.delete(rule);
    }

    public List<Map<String, Object>> listRules() {
        String tenantId = TenantContext.getOrDefault();
        return ruleRepo.findByTenantIdOrderByPriorityDescCreatedAtDesc(tenantId).stream()
            .map(r -> {
                List<Map<String, Object>> conditions = conditionRepo.findByRuleIdOrderByPositionAsc(r.id).stream()
                    .map(c -> Map.<String, Object>of(
                        "field", c.fieldName,
                        "operator", c.operator,
                        "value", nullToEmpty(c.valueText),
                        "position", c.position
                    ))
                    .toList();
                List<Map<String, Object>> actions = actionRepo.findByRuleIdOrderByPositionAsc(r.id).stream()
                    .map(a -> Map.<String, Object>of(
                        "type", a.actionType,
                        "payload", safeJsonToMap(a.payloadJson),
                        "position", a.position
                    ))
                    .toList();
                return Map.<String, Object>of(
                    "id", r.id,
                    "name", r.name,
                    "description", nullToEmpty(r.description),
                    "enabled", r.enabled,
                    "priority", r.priority,
                    "conflictStrategy", r.conflictStrategy,
                    "conditions", conditions,
                    "actions", actions
                );
            })
            .toList();
    }

    public List<Map<String, Object>> listExecutions(int limit) {
        String tenantId = TenantContext.getOrDefault();
        return executionRepo.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
            .sorted(Comparator.comparing((WorkflowExecutionEntity e) -> e.createdAt).reversed())
            .limit(Math.max(1, Math.min(200, limit)))
            .map(exec -> Map.<String, Object>of(
                "id", exec.id,
                "ruleId", nullToEmpty(exec.ruleId),
                "mentionId", nullToEmpty(exec.mentionId),
                "dryRun", exec.dryRun,
                "status", exec.status,
                "failureReason", nullToEmpty(exec.failureReason),
                "correlationId", nullToEmpty(exec.correlationId),
                "durationMs", exec.durationMs,
                "createdAt", exec.createdAt != null ? exec.createdAt.toEpochMilli() : 0L
            ))
            .toList();
    }

    public List<Map<String, Object>> listExecutionSteps(String executionId) {
        return stepRepo.findByExecutionIdOrderByCreatedAtAsc(executionId).stream()
            .map(step -> Map.<String, Object>of(
                "id", step.id,
                "stepType", step.stepType,
                "stepName", step.stepName,
                "success", step.success,
                "details", safeJsonToMap(step.detailsJson),
                "createdAt", step.createdAt != null ? step.createdAt.toEpochMilli() : 0L
            ))
            .toList();
    }

    private boolean evaluateCondition(MentionEntity mention, WorkflowRuleConditionEntity condition) {
        String field = condition.fieldName == null ? "" : condition.fieldName.trim().toLowerCase(Locale.ROOT);
        String operator = condition.operator == null ? "EQUALS" : condition.operator.trim().toUpperCase(Locale.ROOT);
        String expected = nullToEmpty(condition.valueText);

        String actualText = switch (field) {
            case "urgency" -> nullToEmpty(mention.urgency);
            case "sentiment" -> nullToEmpty(mention.sentimentLabel);
            case "platform" -> nullToEmpty(mention.platform);
            case "topic" -> nullToEmpty(mention.topic);
            case "priority" -> nullToEmpty(mention.priority);
            default -> "";
        };

        if ("authorfollowers".equals(field)) {
            long threshold = parseLong(expected, 0L);
            return switch (operator) {
                case "GTE", "GE", ">=" -> mention.authorFollowers >= threshold;
                case "GT", ">" -> mention.authorFollowers > threshold;
                case "LTE", "LE", "<=" -> mention.authorFollowers <= threshold;
                case "LT", "<" -> mention.authorFollowers < threshold;
                case "EQUALS", "EQ", "==" -> mention.authorFollowers == threshold;
                default -> false;
            };
        }

        return switch (operator) {
            case "EQUALS", "EQ", "==" -> actualText.equalsIgnoreCase(expected);
            case "CONTAINS" -> actualText.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT));
            case "NOT_EQUALS", "NE", "!=" -> !actualText.equalsIgnoreCase(expected);
            default -> false;
        };
    }

    private void createExecutionAndStepAudit(
        String ruleId,
        String mentionId,
        String tenantId,
        boolean dryRun,
        String stepType,
        String stepName,
        boolean success,
        Map<String, Object> details
    ) {
        WorkflowExecutionEntity execution = new WorkflowExecutionEntity();
        execution.ruleId = ruleId;
        execution.mentionId = mentionId;
        execution.tenantId = tenantId;
        execution.dryRun = dryRun;
        execution.status = success ? "SUCCESS" : "FAILED";
        execution.correlationId = UUID.randomUUID().toString().substring(0, 12);
        execution.durationMs = 0L;
        executionRepo.save(execution);

        WorkflowExecutionStepEntity step = new WorkflowExecutionStepEntity();
        step.executionId = execution.id;
        step.stepType = stepType;
        step.stepName = stepName;
        step.success = success;
        step.detailsJson = toJson(details);
        stepRepo.save(step);
    }

    private void applyAction(MentionEntity mention, String actionType, Map<String, Object> payload,
                             Map<String, Object> details) {
        String resolved = nullToEmpty(actionType).trim().toLowerCase(Locale.ROOT);
        switch (resolved) {
            case "escalate" -> {
                mention.priority = String.valueOf(payload.getOrDefault("priority", "P1"));
                mention.urgency = String.valueOf(payload.getOrDefault("urgency", "CRITICAL"));
                details.put("updatedPriority", mention.priority);
                details.put("updatedUrgency", mention.urgency);
            }
            case "assign" -> {
                mention.assignedTeam = String.valueOf(payload.getOrDefault("team", "REVIEW"));
                details.put("assignedTeam", mention.assignedTeam);
            }
            case "notify", "notify_webhook" -> {
                notifyWebhook(mention, payload, details);
            }
            case "attach_kb_article" -> {
                String articleId = String.valueOf(payload.getOrDefault("articleId", "")).trim();
                if (articleId.isBlank()) {
                    throw new IllegalArgumentException("articleId is required for attach_kb_article");
                }
                Optional<Map<String, Object>> article = knowledgeBaseService.findActiveArticleForAttachment(articleId);
                Map<String, Object> attached = article.orElseThrow(() ->
                    new IllegalArgumentException("article not found or not active"));
                String citation = String.valueOf(attached.getOrDefault("citation", ""));
                mention.replyText = appendCitation(mention.replyText, citation);
                details.put("attachedArticleId", articleId);
                details.put("citation", citation);
            }
            default -> throw new IllegalArgumentException("unsupported action: " + actionType);
        }
    }

    private String appendCitation(String currentReply, String citation) {
        String reply = nullToEmpty(currentReply);
        String cite = nullToEmpty(citation).trim();
        if (cite.isBlank()) return reply;
        if (reply.contains(cite)) return reply;
        if (reply.isBlank()) return "Reference: " + cite;
        return reply + "\n\nReference: " + cite;
    }

    private void notifyWebhook(MentionEntity mention, Map<String, Object> payload, Map<String, Object> details) {
        String url = String.valueOf(payload.getOrDefault("url", "")).trim();
        if (url.isBlank()) {
            details.put("notified", true);
            details.put("mode", "MOCK");
            details.put("destination", "inline-mock");
            return;
        }

        int maxAttempts = Math.max(1, Math.min(5, parseInt(payload.get("maxAttempts"), 3)));
        int timeoutMs = Math.max(200, Math.min(10_000, parseInt(payload.get("timeoutMs"), 1500)));
        long backoffMs = Math.max(50L, Math.min(3000L, parseLong(payload.get("backoffMs"), 200L)));
        String method = String.valueOf(payload.getOrDefault("method", "POST")).trim().toUpperCase(Locale.ROOT);
        String idempotencyKey = String.valueOf(
            payload.getOrDefault("idempotencyKey", mention.id + "-" + Instant.now().toEpochMilli()));

        Map<String, String> headers = extractHeaders(payload.get("headers"));
        headers.putIfAbsent("Content-Type", "application/json");
        headers.put("X-Idempotency-Key", idempotencyKey);

        String body = buildWebhookBody(mention, payload.get("body"));
        Exception last = null;

        for (int i = 1; i <= maxAttempts; i++) {
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(timeoutMs));

                headers.forEach(builder::header);
                if ("GET".equals(method)) {
                    builder.GET();
                } else {
                    builder.method(method, HttpRequest.BodyPublishers.ofString(body));
                }

                HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status >= 200 && status < 300) {
                    details.put("notified", true);
                    details.put("destination", url);
                    details.put("httpStatus", status);
                    details.put("attempt", i);
                    details.put("idempotencyKey", idempotencyKey);
                    return;
                }
                throw new IllegalStateException("webhook returned status " + status);
            } catch (Exception ex) {
                last = ex;
                if (i < maxAttempts) {
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("webhook retry interrupted", ie);
                    }
                    backoffMs = Math.min(5000L, backoffMs * 2);
                }
            }
        }

        details.put("notified", false);
        details.put("destination", url);
        details.put("idempotencyKey", idempotencyKey);
        throw new IllegalStateException(last != null ? last.getMessage() : "webhook notification failed");
    }

    private Map<String, String> extractHeaders(Object raw) {
        Map<String, String> out = new LinkedHashMap<>();
        if (raw instanceof Map<?, ?> map) {
            map.forEach((k, v) -> out.put(String.valueOf(k), String.valueOf(v)));
        }
        return out;
    }

    private String buildWebhookBody(MentionEntity mention, Object customBody) {
        if (customBody instanceof String s && !s.isBlank()) {
            return s;
        }
        if (customBody instanceof Map<?, ?> map) {
            Map<String, Object> body = new LinkedHashMap<>();
            map.forEach((k, v) -> body.put(String.valueOf(k), v));
            return toJson(body);
        }

        return toJson(Map.of(
            "mentionId", nullToEmpty(mention.id),
            "tenantId", nullToEmpty(mention.tenantId),
            "priority", nullToEmpty(mention.priority),
            "urgency", nullToEmpty(mention.urgency),
            "text", nullToEmpty(mention.text)
        ));
    }

    private String toJson(Object value) {
        if (value == null) return "{}";
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> safeJsonToMap(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) return Map.of();
        try {
            return mapper.readValue(payloadJson, Map.class);
        } catch (Exception e) {
            return Map.of("raw", payloadJson);
        }
    }

    private List<Map<String, Object>> asObjectList(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> row = new LinkedHashMap<>();
                map.forEach((k, v) -> row.put(String.valueOf(k), v));
                out.add(row);
            }
        }
        return out;
    }

    private static int parseInt(Object value, int fallback) {
        if (value == null) return fallback;
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return fallback;
        }
    }

    private static long parseLong(Object value, long fallback) {
        if (value == null) return fallback;
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

