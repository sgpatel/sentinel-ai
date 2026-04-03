package io.sentinel.backend.api;
import io.sentinel.backend.config.TenantContext;
import io.sentinel.backend.connector.TicketConnectorFactory;
import io.sentinel.backend.ingestion.MockMentionIngestionService;
import io.sentinel.backend.repository.MentionEntity;
import io.sentinel.backend.repository.MentionRepository;
import io.sentinel.backend.repository.SavedSearchEntity;
import io.sentinel.backend.repository.SavedSearchRepository;
import io.sentinel.backend.security.UserEntity;
import io.sentinel.backend.service.AnalyticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class MentionController {

    private final MentionRepository       repo;
    private final SavedSearchRepository   savedSearchRepo;
    private final AnalyticsService        analytics;
    private final TicketConnectorFactory  ticketFactory;
    private final MockMentionIngestionService ingestion;

    @Value("${sentinel.handle:@YourHandleName}")
    private String handle;

    @Value("${sentinel.brand.name:Your Brand Name}")
    private String brandName;

    @Value("${sentinel.brand.tone:professional,empathetic,solution-focused}")
    private String brandTone;

    public MentionController(MentionRepository repo, SavedSearchRepository savedSearchRepo,
        AnalyticsService analytics,
        TicketConnectorFactory ticketFactory, MockMentionIngestionService ingestion) {
        this.repo = repo;
        this.savedSearchRepo = savedSearchRepo;
        this.analytics = analytics;
        this.ticketFactory = ticketFactory; this.ingestion = ingestion;
    }

    @GetMapping("/mentions")
    public List<MentionEntity> getMentions(
        @RequestParam(defaultValue="100") int limit,
        @RequestParam(required=false) String sentiment,
        @RequestParam(required=false) String status,
        @RequestParam(required=false) String priority) {
        String tenantId = TenantContext.getOrDefault();
        List<MentionEntity> all = new ArrayList<>(repo.findByTenantIdOrderByPostedAtDesc(tenantId));
        all.sort(Comparator.comparing((MentionEntity m) ->
            m.postedAt != null ? m.postedAt : Instant.EPOCH).reversed());
        if (sentiment != null) all.removeIf(m -> !sentiment.equalsIgnoreCase(m.sentimentLabel));
        if (status   != null) all.removeIf(m -> !status.equalsIgnoreCase(m.processingStatus));
        if (priority != null) all.removeIf(m -> !priority.equalsIgnoreCase(m.priority));
        return all.stream().limit(limit).toList();
    }

    @GetMapping("/mentions/{id}")
    public ResponseEntity<MentionEntity> getMention(@PathVariable String id) {
        String tenantId = TenantContext.getOrDefault();
        return repo.findById(id)
            .filter(m -> tenantId.equals(m.tenantId))
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/mentions/search")
    public Map<String, Object> searchMentions(
        @RequestParam(required=false) String q,
        @RequestParam(required=false) String sentiment,
        @RequestParam(required=false) String priority,
        @RequestParam(required=false) String urgency,
        @RequestParam(required=false) String topic,
        @RequestParam(required=false) Long minFollowers,
        @RequestParam(required=false) Long maxFollowers,
        @RequestParam(required=false) Long fromEpochMs,
        @RequestParam(required=false) Long toEpochMs,
        @RequestParam(defaultValue="0") int page,
        @RequestParam(defaultValue="25") int size,
        @RequestParam(defaultValue="postedAt") String sortBy,
        @RequestParam(defaultValue="desc") String direction
    ) {
        String tenantId = TenantContext.getOrDefault();
        Specification<MentionEntity> spec = (root, query, cb) -> cb.equal(root.get("tenantId"), tenantId);

        if (q != null && !q.isBlank()) {
            String like = "%" + q.toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                cb.like(cb.lower(cb.coalesce(root.get("text"), "")), like),
                cb.like(cb.lower(cb.coalesce(root.get("authorUsername"), "")), like),
                cb.like(cb.lower(cb.coalesce(root.get("topic"), "")), like)
            ));
        }
        if (sentiment != null && !sentiment.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("sentimentLabel"), sentiment.toUpperCase(Locale.ROOT)));
        }
        if (priority != null && !priority.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("priority"), priority.toUpperCase(Locale.ROOT)));
        }
        if (urgency != null && !urgency.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("urgency"), urgency.toUpperCase(Locale.ROOT)));
        }
        if (topic != null && !topic.isBlank()) {
            String likeTopic = "%" + topic.toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(cb.coalesce(root.get("topic"), "")), likeTopic));
        }
        if (minFollowers != null) {
            spec = spec.and((root, query, cb) -> cb.ge(root.get("authorFollowers"), minFollowers));
        }
        if (maxFollowers != null) {
            spec = spec.and((root, query, cb) -> cb.le(root.get("authorFollowers"), maxFollowers));
        }
        if (fromEpochMs != null) {
            Instant from = Instant.ofEpochMilli(fromEpochMs);
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("postedAt"), from));
        }
        if (toEpochMs != null) {
            Instant to = Instant.ofEpochMilli(toEpochMs);
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("postedAt"), to));
        }

        int boundedPage = Math.max(0, page);
        int boundedSize = Math.max(1, Math.min(200, size));
        Set<String> sortable = Set.of(
            "postedAt", "createdAt", "updatedAt", "authorFollowers", "urgencyScore", "viralRiskScore"
        );
        String resolvedSortBy = sortable.contains(sortBy) ? sortBy : "postedAt";
        Sort sort = "asc".equalsIgnoreCase(direction)
            ? Sort.by(resolvedSortBy).ascending()
            : Sort.by(resolvedSortBy).descending();

        var result = repo.findAll(spec, PageRequest.of(boundedPage, boundedSize, sort));
        return Map.of(
            "content", result.getContent(),
            "page", result.getNumber(),
            "size", result.getSize(),
            "totalElements", result.getTotalElements(),
            "totalPages", result.getTotalPages(),
            "hasNext", result.hasNext(),
            "hasPrevious", result.hasPrevious()
        );
    }

    @PostMapping("/mentions/ingest")
    public ResponseEntity<Map<String,String>> ingestMention(@RequestBody Map<String,Object> body) {
        String text      = (String) body.get("text");
        String author    = (String) body.getOrDefault("author", "test_user");
        long followers   = body.containsKey("followers") ? Long.parseLong(body.get("followers").toString()) : 100L;
        String platform  = (String) body.getOrDefault("platform", "TWITTER");
        if (text == null || text.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "text is required"));
        ingestion.ingestCustomMention(text, author, followers, platform);
        return ResponseEntity.ok(Map.of("status","ingested","message","Processing in background"));
    }

    @PostMapping("/mentions/{id}/reply/approve")
    public ResponseEntity<MentionEntity> approveReply(@PathVariable String id) {
        String tenantId = TenantContext.getOrDefault();
        return repo.findById(id).filter(m -> tenantId.equals(m.tenantId)).map(m -> {
            m.replyStatus = "APPROVED"; m.updatedAt = Instant.now();
            return ResponseEntity.ok(repo.save(m));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/mentions/{id}/reply/reject")
    public ResponseEntity<MentionEntity> rejectReply(@PathVariable String id,
        @RequestBody(required=false) Map<String,String> body) {
        String tenantId = TenantContext.getOrDefault();
        return repo.findById(id).filter(m -> tenantId.equals(m.tenantId)).map(m -> {
            m.replyStatus = "REJECTED";
            if (body != null && body.containsKey("revisedReply")) m.replyText = body.get("revisedReply");
            m.updatedAt = Instant.now();
            return ResponseEntity.ok(repo.save(m));
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/analytics/summary")
    public Map<String,Object> getAnalyticsSummary(@RequestParam(defaultValue="24") int hours) {
        return analytics.getSummary(TenantContext.getOrDefault(), hours);
    }

    @GetMapping("/analytics/trend")
    public List<Map<String,Object>> getSentimentTrend(@RequestParam(defaultValue="24") int hours) {
        return analytics.getSentimentTrend(TenantContext.getOrDefault(), hours);
    }

    @GetMapping("/analytics/categories")
    public Map<String,Long> getCategoryBreakdown(@RequestParam(defaultValue="24") int hours) {
        return analytics.getCategoryBreakdown(TenantContext.getOrDefault(), hours);
    }

    @GetMapping("/analytics/health")
    public Map<String,Object> getBrandHealth() {
        return analytics.getBrandHealth(TenantContext.getOrDefault());
    }

    @GetMapping("/tickets")
    public List<Map<String,Object>> getTickets() {
        String tenantId = TenantContext.getOrDefault();
        // Return mock tickets from mentions that have ticketId
        return repo.findByTenantIdOrderByPostedAtDesc(tenantId).stream()
            .filter(m -> m.ticketId != null)
            .map(m -> {
                Map<String,Object> t = new LinkedHashMap<>();
                t.put("id", m.ticketId);
                t.put("title", m.summary != null ? m.summary : m.text.substring(0, Math.min(80, m.text.length())));
                t.put("status", m.ticketStatus != null ? m.ticketStatus : "OPEN");
                t.put("priority", m.priority != null ? m.priority : "P3");
                t.put("team", m.assignedTeam != null ? m.assignedTeam : "SUPPORT");
                t.put("mentionId", m.id);
                t.put("mentionText", m.text);
                t.put("system", m.ticketSystem != null ? m.ticketSystem : "MOCK");
                t.put("createdAt", m.createdAt != null ? m.createdAt.toEpochMilli() : 0);
                return t;
            }).toList();
    }

    @PostMapping("/tickets/{id}/resolve")
    public ResponseEntity<Map<String,Object>> resolveTicket(@PathVariable String id,
        @RequestBody Map<String,String> body) {
        String tenantId = TenantContext.getOrDefault();
        String resolution = body.getOrDefault("resolution", "Resolved via dashboard");
        boolean ok = ticketFactory.get().updateStatus(id, "RESOLVED", resolution);
        repo.findByTenantIdOrderByPostedAtDesc(tenantId).stream()
            .filter(m -> id.equals(m.ticketId)).findFirst().ifPresent(m -> {
            m.ticketStatus = "RESOLVED"; m.updatedAt = Instant.now(); repo.save(m);
        });
        return ok ? ResponseEntity.ok(Map.of("status","resolved"))
                  : ResponseEntity.ok(Map.of("status","updated locally"));
    }

    @GetMapping("/pending-replies")
    public List<MentionEntity> getPendingReplies() {
        return repo.findByTenantIdAndReplyStatusOrderByPostedAtDesc(
            TenantContext.getOrDefault(), "PENDING");
    }

    @GetMapping("/alerts")
    public List<MentionEntity> getAlerts() {
        String tenantId = TenantContext.getOrDefault();
        Instant since = Instant.now().minus(1, ChronoUnit.HOURS);
        return repo.findUrgentByTenantAndHandle(tenantId, this.handle, since)
            .stream().filter(m -> m.urgencyScore > 70 || "P1".equals(m.priority)).toList();
    }

    @GetMapping("/config")
    public Map<String,Object> getConfig() {
        return Map.of(
            "handle", handle,
            "brandName", brandName,
            "brandTone", brandTone
        );
    }

    @GetMapping("/saved-searches")
    public List<Map<String, Object>> getSavedSearches() {
        String tenantId = TenantContext.getOrDefault();
        String userId = currentUserId();
        return savedSearchRepo.findByTenantIdAndUserIdOrderByUpdatedAtDesc(tenantId, userId).stream()
            .map(s -> Map.<String, Object>of(
                "id", s.id,
                "name", s.name,
                "queryJson", s.queryJson,
                "createdAt", s.createdAt != null ? s.createdAt.toEpochMilli() : 0L,
                "updatedAt", s.updatedAt != null ? s.updatedAt.toEpochMilli() : 0L
            ))
            .toList();
    }

    @PostMapping("/saved-searches")
    public ResponseEntity<?> createSavedSearch(@RequestBody Map<String, Object> body) {
        String name = String.valueOf(body.getOrDefault("name", "")).trim();
        String queryJson = String.valueOf(body.getOrDefault("queryJson", "")).trim();
        if (name.isBlank() || queryJson.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "name and queryJson are required"));
        }

        SavedSearchEntity s = new SavedSearchEntity();
        s.tenantId = TenantContext.getOrDefault();
        s.userId = currentUserId();
        s.name = name;
        s.queryJson = queryJson;
        savedSearchRepo.save(s);
        return ResponseEntity.ok(Map.of("id", s.id, "name", s.name, "queryJson", s.queryJson));
    }

    @PutMapping("/saved-searches/{id}")
    public ResponseEntity<?> updateSavedSearch(@PathVariable String id,
        @RequestBody Map<String, Object> body) {
        String tenantId = TenantContext.getOrDefault();
        String userId = currentUserId();
        return savedSearchRepo.findByIdAndTenantIdAndUserId(id, tenantId, userId).map(s -> {
            if (body.containsKey("name")) {
                String name = String.valueOf(body.get("name")).trim();
                if (!name.isBlank()) s.name = name;
            }
            if (body.containsKey("queryJson")) {
                String queryJson = String.valueOf(body.get("queryJson")).trim();
                if (!queryJson.isBlank()) s.queryJson = queryJson;
            }
            savedSearchRepo.save(s);
            return ResponseEntity.ok(Map.of("id", s.id, "name", s.name, "queryJson", s.queryJson));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/saved-searches/{id}")
    public ResponseEntity<?> deleteSavedSearch(@PathVariable String id) {
        String tenantId = TenantContext.getOrDefault();
        String userId = currentUserId();
        return savedSearchRepo.findByIdAndTenantIdAndUserId(id, tenantId, userId).map(s -> {
            savedSearchRepo.delete(s);
            return ResponseEntity.ok(Map.of("deleted", true));
        }).orElse(ResponseEntity.notFound().build());
    }

    private String currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserEntity u) return u.id;
        return "anonymous";
    }
}