package io.sentinel.backend.security;
import io.sentinel.backend.config.TenantContext;
import io.sentinel.backend.repository.MentionDlqRepository;
import io.sentinel.backend.repository.TenantConfigEntity;
import io.sentinel.backend.repository.TenantConfigRepository;
import io.sentinel.backend.repository.TenantRepository;
import io.sentinel.backend.service.MentionDlqService;
import io.sentinel.backend.service.MentionProcessingService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
@RestController
@RequestMapping("/api/admin")
// NOTE: /api/admin/setup is public for initial setup only
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final TenantRepository tenants;
    private final TenantConfigRepository tenantConfigs;
    private final MentionDlqRepository dlqRepo;
    private final MentionDlqService dlqService;
    private final MentionProcessingService processingService;
    public AdminController(UserRepository users, PasswordEncoder encoder,
        TenantRepository tenants, TenantConfigRepository tenantConfigs,
        MentionDlqRepository dlqRepo,
        MentionDlqService dlqService,
        MentionProcessingService processingService) {
        this.users = users;
        this.encoder = encoder;
        this.tenants = tenants;
        this.tenantConfigs = tenantConfigs;
        this.dlqRepo = dlqRepo;
        this.dlqService = dlqService;
        this.processingService = processingService;
    }
    @GetMapping("/users")
    public List<Map<String,Object>> listUsers() {
        return users.findAll().stream().map(u -> Map.<String,Object>of(
            "id", u.id, "username", u.username, "email", u.email,
            "role", u.role.name(), "active", u.active,
            "tenantId", u.tenantId)).toList();
    }
    @PutMapping("/users/{id}/role")
    public ResponseEntity<?> updateRole(@PathVariable String id,
        @RequestBody Map<String,String> body) {
        return users.findById(id).map(u -> {
            u.role = Role.valueOf(body.get("role").toUpperCase());
            users.save(u);
            return ResponseEntity.ok(Map.of("updated", true, "role", u.role.name()));
        }).orElse(ResponseEntity.notFound().build());
    }
    @PutMapping("/users/{id}/toggle")
    public ResponseEntity<?> toggleUser(@PathVariable String id) {
        return users.findById(id).map(u -> {
            u.active = !u.active;
            users.save(u);
            return ResponseEntity.ok(Map.of("active", u.active));
        }).orElse(ResponseEntity.notFound().build());
    }
    // ── Dev helper: promote any user to ADMIN via H2 console or this endpoint ──
    // This endpoint is ADMIN-only — to bootstrap use DataInitializer or H2 console:
    // UPDATE users SET role = 'ADMIN' WHERE username = 'yourname';
    @PutMapping("/users/{username}/promote")
    public ResponseEntity<?> promote(@PathVariable String username) {
        return users.findByUsername(username).map(u -> {
            u.role = Role.ADMIN;
            users.save(u);
            return ResponseEntity.ok(Map.of("promoted", true, "username", u.username, "role", u.role.name()));
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/tenants")
    public List<Map<String, Object>> listTenants() {
        return tenants.findAll().stream().map(t -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", t.id);
            r.put("name", t.name);
            r.put("slug", t.slug);
            r.put("status", t.status);
            r.put("plan", t.plan);
            return r;
        }).toList();
    }

    @GetMapping("/tenants/{tenantId}/config")
    public ResponseEntity<?> getTenantConfig(@PathVariable String tenantId) {
        return tenantConfigs.findById(tenantId)
            .map(c -> ResponseEntity.ok(Map.of(
                "tenantId", c.tenantId,
                "brandName", c.brandName,
                "handle", c.handle,
                "platform", c.platform,
                "brandTone", c.brandTone,
                "ticketSystem", c.ticketSystem,
                "autoReply", c.autoReply,
                "requireApproval", c.requireApproval,
                "active", c.active
            )))
            .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/tenants/{tenantId}/config")
    public ResponseEntity<?> upsertTenantConfig(@PathVariable String tenantId,
        @RequestBody Map<String, Object> body) {
        if (tenants.findById(tenantId).isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unknown tenantId"));
        }
        TenantConfigEntity c = tenantConfigs.findById(tenantId).orElseGet(() -> {
            TenantConfigEntity x = new TenantConfigEntity();
            x.tenantId = tenantId;
            x.brandName = "Your Brand Name";
            x.handle = "@YourHandleName";
            x.brandTone = "professional,empathetic,solution-focused";
            return x;
        });

        if (body.containsKey("brandName")) c.brandName = String.valueOf(body.get("brandName"));
        if (body.containsKey("handle")) c.handle = String.valueOf(body.get("handle"));
        if (body.containsKey("platform")) c.platform = String.valueOf(body.get("platform"));
        if (body.containsKey("brandTone")) c.brandTone = String.valueOf(body.get("brandTone"));
        if (body.containsKey("ticketSystem")) c.ticketSystem = String.valueOf(body.get("ticketSystem"));
        if (body.containsKey("autoReply")) c.autoReply = Boolean.parseBoolean(String.valueOf(body.get("autoReply")));
        if (body.containsKey("requireApproval")) {
            c.requireApproval = Boolean.parseBoolean(String.valueOf(body.get("requireApproval")));
        }
        if (body.containsKey("active")) c.active = Boolean.parseBoolean(String.valueOf(body.get("active")));

        tenantConfigs.save(c);
        return ResponseEntity.ok(Map.of("updated", true, "tenantId", tenantId));
    }

    @GetMapping("/dlq")
    public List<Map<String, Object>> listDlq(
        @RequestParam(defaultValue = "NEW") String status,
        @RequestParam(defaultValue = "50") int limit) {
        String tenantId = TenantContext.getOrDefault();
        return dlqService.listForTenant(tenantId, status.toUpperCase(), limit).stream()
            .map(r -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", r.id);
                m.put("mentionId", r.mentionId);
                m.put("tenantId", r.tenantId);
                m.put("status", r.status);
                m.put("retryCount", r.retryCount);
                m.put("failureStage", r.failureStage);
                m.put("errorMessage", r.errorMessage);
                m.put("createdAt", r.createdAt != null ? r.createdAt.toEpochMilli() : 0L);
                m.put("updatedAt", r.updatedAt != null ? r.updatedAt.toEpochMilli() : 0L);
                m.put("lastRetryAt", r.lastRetryAt != null ? r.lastRetryAt.toEpochMilli() : null);
                return m;
            })
            .toList();
    }

    @PostMapping("/dlq/{id}/replay")
    public ResponseEntity<?> replayDlqItem(@PathVariable String id) {
        String tenantId = TenantContext.getOrDefault();
        return dlqService.replayOneForTenant(id, tenantId)
            .map(r -> ResponseEntity.ok(Map.of(
                "replayed", true,
                "id", r.id,
                "status", r.status,
                "retryCount", r.retryCount
            )))
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/dlq/replay")
    public Map<String, Object> replayDlqBatch(@RequestParam(defaultValue = "20") int limit) {
        String tenantId = TenantContext.getOrDefault();
        int replayed = dlqService.replayBatchForTenant(tenantId, limit);
        return Map.of("replayed", replayed, "tenantId", tenantId);
    }

    @GetMapping("/reliability/metrics")
    public Map<String, Object> reliabilityMetrics() {
        String tenantId = TenantContext.getOrDefault();
        Map<String, Object> pipeline = processingService.getReliabilityMetrics();

        Map<String, Object> dlq = new LinkedHashMap<>();
        dlq.put("tenantId", tenantId);
        dlq.put("total", dlqRepo.countByTenantId(tenantId));
        dlq.put("new", dlqRepo.countByTenantIdAndStatus(tenantId, "NEW"));
        dlq.put("requeued", dlqRepo.countByTenantIdAndStatus(tenantId, "REQUEUED"));
        dlq.put("failed", dlqRepo.countByTenantIdAndStatus(tenantId, "FAILED"));

        return Map.of(
            "tenantId", tenantId,
            "pipeline", pipeline,
            "dlq", dlq
        );
    }
}