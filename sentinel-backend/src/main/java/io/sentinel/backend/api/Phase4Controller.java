package io.sentinel.backend.api;

import io.sentinel.backend.service.KnowledgeBaseService;
import io.sentinel.backend.service.WorkflowAutomationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class Phase4Controller {

    private final WorkflowAutomationService workflowService;
    private final KnowledgeBaseService knowledgeBaseService;

    public Phase4Controller(WorkflowAutomationService workflowService,
                            KnowledgeBaseService knowledgeBaseService) {
        this.workflowService = workflowService;
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @GetMapping("/workflows/rules")
    public List<Map<String, Object>> listWorkflowRules() {
        return workflowService.listRules();
    }

    @PostMapping("/workflows/rules")
    public ResponseEntity<?> createWorkflowRule(@RequestBody Map<String, Object> body) {
        try {
            var rule = workflowService.createRule(body);
            return ResponseEntity.ok(Map.of(
                "id", rule.id,
                "name", rule.name,
                "enabled", rule.enabled,
                "priority", rule.priority
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PutMapping("/workflows/rules/{id}/enable")
    public ResponseEntity<?> setWorkflowRuleEnabled(@PathVariable String id,
                                                    @RequestParam(defaultValue = "true") boolean enabled) {
        try {
            workflowService.setRuleEnabled(id, enabled);
            return ResponseEntity.ok(Map.of("id", id, "enabled", enabled));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/workflows/rules/{id}")
    public ResponseEntity<?> deleteWorkflowRule(@PathVariable String id) {
        try {
            workflowService.deleteRule(id);
            return ResponseEntity.ok(Map.of("id", id, "deleted", true));
        } catch (IllegalArgumentException ex) {
            if ("rule not found".equalsIgnoreCase(ex.getMessage())) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/workflows/evaluate/dry-run")
    public ResponseEntity<?> evaluateWorkflowDryRun(@RequestBody Map<String, Object> body) {
        String mentionId = String.valueOf(body.getOrDefault("mentionId", "")).trim();
        if (mentionId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "mentionId is required"));
        }
        try {
            return ResponseEntity.ok(workflowService.evaluateDryRun(mentionId));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/workflows/execute")
    public ResponseEntity<?> executeWorkflow(@RequestBody Map<String, Object> body) {
        String mentionId = String.valueOf(body.getOrDefault("mentionId", "")).trim();
        if (mentionId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "mentionId is required"));
        }
        try {
            return ResponseEntity.ok(workflowService.executeNow(mentionId));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/workflows/executions")
    public List<Map<String, Object>> listWorkflowExecutions(@RequestParam(defaultValue = "20") int limit) {
        return workflowService.listExecutions(limit);
    }

    @GetMapping("/workflows/executions/{executionId}/steps")
    public List<Map<String, Object>> listWorkflowExecutionSteps(@PathVariable String executionId) {
        return workflowService.listExecutionSteps(executionId);
    }

    @GetMapping("/admin/kb/articles")
    public List<Map<String, Object>> listKbArticles() {
        return knowledgeBaseService.listArticles();
    }

    @PostMapping("/admin/kb/articles")
    public ResponseEntity<?> createKbArticle(@RequestBody Map<String, Object> body) {
        try {
            return ResponseEntity.ok(knowledgeBaseService.createArticle(body));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PutMapping("/admin/kb/articles/{id}")
    public ResponseEntity<?> updateKbArticle(@PathVariable String id,
                                             @RequestBody Map<String, Object> body) {
        try {
            return ResponseEntity.ok(knowledgeBaseService.updateArticle(id, body));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/admin/kb/articles/{id}/delete")
    public ResponseEntity<?> deleteKbArticle(@PathVariable String id) {
        try {
            knowledgeBaseService.deleteArticle(id);
            return ResponseEntity.ok(Map.of("deleted", true));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/admin/kb/search")
    public List<Map<String, Object>> adminKbSearch(@RequestParam(defaultValue = "") String q) {
        return knowledgeBaseService.adminSearch(q);
    }

    @GetMapping("/kb/search")
    public List<Map<String, Object>> kbSearch(@RequestParam(defaultValue = "") String q) {
        return knowledgeBaseService.complianceSearch(q);
    }
}

