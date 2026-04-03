# Sentinel AI — Implementation Guide: Top Advanced Features

## 1️⃣ Multi-Tenant Architecture (PRIORITY #1)

### Overview
Convert Sentinel from single-tenant to true multi-tenant SaaS. This is **foundational** — many other features depend on it.

### Database Changes

```sql
-- New tenant management tables
CREATE TABLE tenants (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name VARCHAR(255) NOT NULL,
  slug VARCHAR(100) UNIQUE NOT NULL,
  status VARCHAR(20) DEFAULT 'ACTIVE', -- ACTIVE, SUSPENDED, INACTIVE
  plan VARCHAR(50) DEFAULT 'FREE', -- FREE, PRO, ENTERPRISE
  max_mentions_per_month INT DEFAULT 10000,
  created_at TIMESTAMP DEFAULT NOW(),
  updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE tenant_configs (
  tenant_id UUID PRIMARY KEY REFERENCES tenants(id) ON DELETE CASCADE,
  brand_name VARCHAR(255),
  handle VARCHAR(100),
  brand_tone TEXT,
  
  -- LLM Provider per tenant (override global config)
  llm_provider VARCHAR(50), -- null = use global default
  llm_model VARCHAR(100),
  
  -- Feature flags per tenant
  enable_auto_reply BOOLEAN DEFAULT true,
  enable_multi_channel BOOLEAN DEFAULT false,
  enable_workflow_rules BOOLEAN DEFAULT false,
  enable_competitive_intel BOOLEAN DEFAULT false,
  
  -- Integration keys (encrypted)
  twitter_bearer_token_encrypted VARCHAR(1000),
  zendesk_api_key_encrypted VARCHAR(1000),
  slack_webhook_url_encrypted VARCHAR(1000),
  
  created_at TIMESTAMP DEFAULT NOW(),
  updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE tenant_users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  role VARCHAR(50), -- ADMIN, ANALYST, REVIEWER, READ_ONLY
  created_at TIMESTAMP DEFAULT NOW(),
  UNIQUE(tenant_id, user_id)
);

-- Tenant audit log
CREATE TABLE tenant_audit_log (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  user_id UUID REFERENCES users(id),
  action VARCHAR(100),
  resource VARCHAR(100),
  resource_id VARCHAR(100),
  changes JSONB,
  created_at TIMESTAMP DEFAULT NOW(),
  INDEX (tenant_id, created_at)
);

-- Update mentions table
ALTER TABLE mentions ADD COLUMN tenant_id UUID NOT NULL DEFAULT 'default' REFERENCES tenants(id);
CREATE INDEX idx_mentions_tenant_id ON mentions(tenant_id);
```

### Code Changes

#### TenantContext.java (ThreadLocal tenant context)
```java
package io.sentinel.backend.config;

import org.springframework.stereotype.Component;

@Component
public class TenantContext {
    private static final ThreadLocal<String> tenantId = ThreadLocal.withInitial(() -> "default");

    public static String getTenantId() {
        return tenantId.get();
    }

    public static void setTenantId(String id) {
        tenantId.set(id);
    }

    public static void clear() {
        tenantId.remove();
    }
}
```

#### TenantInterceptor.java (Extract tenant from JWT)
```java
package io.sentinel.backend.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class TenantInterceptor implements HandlerInterceptor {
    @Value("${sentinel.jwt.secret}")
    private String jwtSecret;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                Claims claims = Jwts.parserBuilder()
                    .setSigningKey(jwtSecret.getBytes())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
                
                String tenantId = (String) claims.get("tenant_id");
                if (tenantId != null) {
                    TenantContext.setTenantId(tenantId);
                }
            } catch (Exception e) {
                // Token invalid or tenant_id missing
                return false;
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        TenantContext.clear();
    }
}
```

#### Update MentionRepository with tenant filtering
```java
package io.sentinel.backend.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.List;

@Repository
public interface MentionRepository extends CrudRepository<MentionEntity, String> {
    
    // All queries now filter by tenant_id
    @Query("SELECT m FROM MentionEntity m WHERE m.tenantId = :tenantId AND m.postedAt > :since ORDER BY m.postedAt DESC")
    List<MentionEntity> findByTenantAndPostedAfter(String tenantId, Instant since);
    
    @Query("SELECT m FROM MentionEntity m WHERE m.tenantId = :tenantId AND m.sentimentLabel = :sentiment ORDER BY m.postedAt DESC")
    List<MentionEntity> findByTenantAndSentiment(String tenantId, String sentiment);
    
    @Query("SELECT m FROM MentionEntity m WHERE m.tenantId = :tenantId AND m.priority = :priority ORDER BY m.postedAt DESC")
    List<MentionEntity> findByTenantAndPriority(String tenantId, String priority);
    
    // Helper to get tenant's mentions
    @Query("SELECT COUNT(m) FROM MentionEntity m WHERE m.tenantId = :tenantId")
    long countByTenant(String tenantId);
}
```

#### Update MentionEntity
```java
@Entity
@Table(name = "mentions")
public class MentionEntity {
    @Id
    public String id;
    
    @Column(nullable = false)
    public String tenantId; // Add this field
    
    public String platform;
    public String handle;
    // ... rest of fields
}
```

#### Update MentionController to use TenantContext
```java
@RestController
@RequestMapping("/api")
public class MentionController {
    
    @GetMapping("/mentions")
    public List<MentionEntity> getMentions(@RequestParam(required = false) String sentiment) {
        String tenantId = TenantContext.getTenantId();
        if (sentiment != null) {
            return repo.findByTenantAndSentiment(tenantId, sentiment);
        }
        return repo.findByTenantAndPostedAfter(tenantId, Instant.now().minus(7, ChronoUnit.DAYS));
    }
    
    @PostMapping("/mentions/ingest")
    public ResponseEntity<?> ingestMention(@RequestBody Map<String, Object> body) {
        String tenantId = TenantContext.getTenantId();
        MentionEntity m = new MentionEntity();
        m.id = "MENTION-" + UUID.randomUUID().toString().substring(0, 8);
        m.tenantId = tenantId; // Set tenant
        // ... rest of code
        return ResponseEntity.ok("Ingested");
    }
}
```

#### AdminController.java (Tenant management)
```java
package io.sentinel.backend.api;

import io.sentinel.backend.repository.TenantRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final TenantRepository tenantRepo;
    
    // Only SUPER_ADMIN can call these
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping("/tenants")
    public Tenant createTenant(@RequestBody Map<String, Object> body) {
        Tenant t = new Tenant();
        t.name = (String) body.get("name");
        t.slug = (String) body.get("slug");
        t.plan = (String) body.get("plan");
        return tenantRepo.save(t);
    }
    
    @GetMapping("/tenants")
    public List<Tenant> listTenants() {
        return tenantRepo.findAll();
    }
    
    @GetMapping("/tenants/{tenantId}/usage")
    public Map<String, Object> getTenantUsage(@PathVariable String tenantId) {
        long mentionsThisMonth = mentionRepo.countByTenantAndPostedAfter(
            tenantId, Instant.now().minus(30, ChronoUnit.DAYS)
        );
        return Map.of(
            "mentionsThisMonth", mentionsThisMonth,
            "planLimit", 10000,
            "percentageUsed", (mentionsThisMonth / 10000.0) * 100
        );
    }
}
```

### Migration Strategy
1. Create new tables (above)
2. Set default tenant_id='default' for all existing mentions
3. Create default tenant row in `tenants` table
4. Deploy TenantInterceptor to Spring config
5. Update all repository queries to include tenant filtering
6. Test with multi-tenant scenario (2-3 tenants, cross-verify isolation)

### Testing
```java
@Test
public void testTenantIsolation() {
    // Create 2 mentions for different tenants
    MentionEntity m1 = new MentionEntity();
    m1.tenantId = "tenant-1";
    m1.text = "Issue A";
    repo.save(m1);
    
    MentionEntity m2 = new MentionEntity();
    m2.tenantId = "tenant-2";
    m2.text = "Issue B";
    repo.save(m2);
    
    // Set context to tenant-1
    TenantContext.setTenantId("tenant-1");
    
    List<MentionEntity> results = repo.findByTenantAndPostedAfter("tenant-1", Instant.now().minusSeconds(60));
    assert results.size() == 1;
    assert results.get(0).text.equals("Issue A");
}
```

---

## 2️⃣ Feedback Loop & Agent Learning

### Overview
Agents improve over time by learning from human feedback on approved/rejected replies.

### Database Schema

```sql
CREATE TABLE reply_feedback (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  mention_id VARCHAR(50) NOT NULL REFERENCES mentions(id),
  agent_name VARCHAR(100), -- ReplyAgent, ComplianceAgent, etc.
  original_reply TEXT,
  feedback_reply TEXT, -- human-revised reply
  approved BOOLEAN, -- true = approved, false = rejected
  reason TEXT, -- why was it rejected?
  sentiment_improvement DECIMAL, -- 0.0-1.0, how much did sentiment improve?
  reviewer_id UUID REFERENCES users(id),
  created_at TIMESTAMP DEFAULT NOW(),
  INDEX (tenant_id, agent_name, created_at)
);

CREATE TABLE training_examples (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL,
  agent_name VARCHAR(100),
  mention_text TEXT,
  feedback_text TEXT,
  is_positive BOOLEAN, -- true = approved, false = rejected
  embedding VECTOR(384), -- Optional: for similarity search
  created_at TIMESTAMP DEFAULT NOW(),
  INDEX (tenant_id, agent_name)
);

CREATE TABLE agent_performance_metrics (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL,
  agent_name VARCHAR(100),
  date DATE,
  total_replies_generated INT,
  approved_count INT,
  rejected_count INT,
  avg_sentiment_improvement DECIMAL,
  created_at TIMESTAMP DEFAULT NOW(),
  UNIQUE (tenant_id, agent_name, date)
);
```

### Code Implementation

#### ReplyFeedbackService.java
```java
package io.sentinel.backend.service;

import io.sentinel.backend.repository.ReplyFeedbackRepository;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Service
public class ReplyFeedbackService {
    private final ReplyFeedbackRepository feedbackRepo;
    private final TrainingDataService trainingService;
    private final MentionRepository mentionRepo;

    public void recordFeedback(String tenantId, String mentionId, String agentName,
                              String originalReply, String feedbackReply, 
                              boolean approved, String reason) {
        ReplyFeedback feedback = new ReplyFeedback();
        feedback.tenantId = tenantId;
        feedback.mentionId = mentionId;
        feedback.agentName = agentName;
        feedback.originalReply = originalReply;
        feedback.feedbackReply = feedbackReply;
        feedback.approved = approved;
        feedback.reason = reason;
        feedback.createdAt = Instant.now();

        // Measure sentiment improvement
        MentionEntity mention = mentionRepo.findById(mentionId).orElse(null);
        if (mention != null) {
            // If sentiment changed from NEGATIVE to POSITIVE after our reply = +1.0 improvement
            double improvement = calculateSentimentImprovement(mention, feedbackReply);
            feedback.sentimentImprovement = improvement;
        }

        feedbackRepo.save(feedback);

        // Add to training data if approved
        if (approved) {
            trainingService.addTrainingExample(tenantId, agentName, 
                mention.text, feedbackReply, true);
        } else {
            trainingService.addTrainingExample(tenantId, agentName,
                mention.text, feedbackReply, false);
        }

        // Update metrics
        updateAgentMetrics(tenantId, agentName, approved);
    }

    private double calculateSentimentImprovement(MentionEntity mention, String feedbackReply) {
        // Simple heuristic: positive words = improvement
        // In production, use ML model
        int positiveWords = feedbackReply.toLowerCase().split("(?i)(thanks|great|resolved|fixed|happy|love)").length - 1;
        return Math.min(1.0, positiveWords * 0.2);
    }

    private void updateAgentMetrics(String tenantId, String agentName, boolean approved) {
        LocalDate today = LocalDate.now();
        AgentMetrics metrics = metricsRepo.findByTenantAndAgentAndDate(tenantId, agentName, today)
            .orElse(new AgentMetrics());
        
        metrics.tenantId = tenantId;
        metrics.agentName = agentName;
        metrics.date = today;
        metrics.totalRepliesGenerated++;
        
        if (approved) {
            metrics.approvedCount++;
        } else {
            metrics.rejectedCount++;
        }
        
        metricsRepo.save(metrics);
    }

    // Get top approved replies for agent (for few-shot examples)
    public List<ReplyFeedback> getTopExamples(String tenantId, String agentName, int limit) {
        return feedbackRepo.findByTenantAndAgentAndApprovedOrderByCreatedAtDesc(tenantId, agentName, true)
            .stream().limit(limit).toList();
    }

    // Get agent approval rate
    public double getApprovalRate(String tenantId, String agentName, int days) {
        Instant since = Instant.now().minus(Duration.ofDays(days));
        List<ReplyFeedback> feedback = feedbackRepo.findByTenantAndAgentAndCreatedAfter(tenantId, agentName, since);
        
        if (feedback.isEmpty()) return 0;
        long approved = feedback.stream().filter(f -> f.approved).count();
        return (approved / (double) feedback.size()) * 100;
    }
}
```

#### TrainingDataService.java (Generate few-shot examples)
```java
package io.sentinel.backend.service;

import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class TrainingDataService {
    private final TrainingExampleRepository trainingRepo;

    public void addTrainingExample(String tenantId, String agentName, 
                                   String mention, String reply, boolean isPositive) {
        TrainingExample ex = new TrainingExample();
        ex.tenantId = tenantId;
        ex.agentName = agentName;
        ex.mentionText = mention;
        ex.feedbackText = reply;
        ex.isPositive = isPositive;
        trainingRepo.save(ex);
    }

    // Enhance agent prompt with few-shot examples
    public String buildEnhancedPrompt(String tenantId, String agentName, String basePrompt) {
        List<TrainingExample> examples = trainingRepo.findByTenantAndAgentAndIsPositive(
            tenantId, agentName, true
        ).stream().limit(5).toList();

        if (examples.isEmpty()) {
            return basePrompt;
        }

        StringBuilder enhancedPrompt = new StringBuilder(basePrompt);
        enhancedPrompt.append("\n\nHere are examples of high-quality replies from your team:\n");
        
        for (int i = 0; i < examples.size(); i++) {
            TrainingExample ex = examples.get(i);
            enhancedPrompt.append(String.format(
                "Example %d:\nMention: %s\nReply: %s\n\n",
                i + 1, ex.mentionText, ex.feedbackText
            ));
        }

        return enhancedPrompt.toString();
    }

    // Export training data for fine-tuning
    public String exportForOpenAIFineTuning(String tenantId, String agentName) {
        List<TrainingExample> examples = trainingRepo.findByTenantAndAgent(tenantId, agentName);
        
        StringBuilder jsonl = new StringBuilder();
        for (TrainingExample ex : examples) {
            String line = String.format(
                "{\"messages\": [{\"role\": \"user\", \"content\": \"%s\"}, " +
                "{\"role\": \"assistant\", \"content\": \"%s\"}]}\n",
                escapeJson(ex.mentionText), escapeJson(ex.feedbackText)
            );
            jsonl.append(line);
        }
        
        return jsonl.toString();
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
```

#### Update ReplyAgent to use training examples
```java
@Agent(role = SUPPORT, name = "ReplyAgent")
public class ReplyAgent {
    
    @Autowired
    private ReplyFeedbackService feedbackService;
    
    @Autowired
    private TrainingDataService trainingService;
    
    // When generating reply, use enhanced prompt
    public String generateReply(String tenantId, String mention) {
        String basePrompt = "You are a customer service reply specialist...";
        
        // Enhance with tenant's training examples
        String enhancedPrompt = trainingService.buildEnhancedPrompt(tenantId, "ReplyAgent", basePrompt);
        enhancedPrompt += "\n\nNow, reply to this mention:\n" + mention;
        
        // Call LLM with enhanced prompt
        return callLLM(enhancedPrompt);
    }
}
```

#### MentionController: Hook feedback recording
```java
@PostMapping("/mentions/{id}/reply/approve")
public ResponseEntity<?> approveReply(@PathVariable String id, @RequestBody Map<String, String> body) {
    String tenantId = TenantContext.getTenantId();
    return repo.findById(id).map(m -> {
        m.replyStatus = "APPROVED";
        
        // Record feedback for agent learning
        String originalReply = m.replyText;
        String approvedReply = body.getOrDefault("replyText", originalReply);
        
        feedbackService.recordFeedback(
            tenantId, m.id, "ReplyAgent",
            originalReply, approvedReply, true, "Human approved"
        );
        
        return ResponseEntity.ok(repo.save(m));
    }).orElse(ResponseEntity.notFound().build());
}

@PostMapping("/mentions/{id}/reply/reject")
public ResponseEntity<?> rejectReply(@PathVariable String id, @RequestBody Map<String, String> body) {
    String tenantId = TenantContext.getTenantId();
    return repo.findById(id).map(m -> {
        m.replyStatus = "REJECTED";
        String revisedReply = body.getOrDefault("revisedReply", "");
        
        // Record rejection feedback
        feedbackService.recordFeedback(
            tenantId, m.id, "ReplyAgent",
            m.replyText, revisedReply, false,
            body.getOrDefault("reason", "")
        );
        
        m.replyText = revisedReply;
        return ResponseEntity.ok(repo.save(m));
    }).orElse(ResponseEntity.notFound().build());
}
```

### Frontend: Show Agent Performance
```typescriptreact
// New dashboard tab
function AgentPerformanceTab() {
    const [metrics, setMetrics] = useState<any>(null);
    
    useEffect(() => {
        fetch(`${API}/api/analytics/agent-performance?days=30`)
            .then(r => r.json())
            .then(setMetrics);
    }, []);
    
    return (
        <div style={{ padding: "20px" }}>
            <h2>Agent Performance (Last 30 Days)</h2>
            {metrics?.agents?.map((agent: any) => (
                <div key={agent.name} style={{ marginBottom: "20px", padding: "12px", background: "var(--bg2)", borderRadius: "8px" }}>
                    <div style={{ display: "flex", justifyContent: "space-between", marginBottom: "8px" }}>
                        <strong>{agent.name}</strong>
                        <span style={{ color: "#22c55e", fontWeight: "bold" }}>{agent.approvalRate}% ↑{agent.improvement}</span>
                    </div>
                    <div style={{ fontSize: "12px", color: "var(--muted)" }}>
                        {agent.totalReplies} replies generated | {agent.approved} approved | {agent.rejected} rejected
                    </div>
                </div>
            ))}
        </div>
    );
}
```

---

## 3️⃣ Viral Prediction & Crisis Early Warning

### Overview
Detect mentions trending toward virality 24h+ before peak, enable proactive response.

### New Agent: PredictionAgent

```java
package io.sentinel.backend.agents;

import io.squados.annotation.*;

@Agent(
    role = AgentRole.RESEARCHER,
    name = "PredictionAgent",
    description = "You are a crisis prediction specialist. Given recent mention trends, " +
        "predict the likelihood of this mention going viral in 6, 12, and 24 hours. " +
        "Consider: follower count, retweet velocity, sentiment trend, topic (fraud > feature > complaint), " +
        "and historical similar patterns. Return structured JSON with virality scores and recommended actions."
)
@MissionProfile("work")
public class PredictionAgent {
    
    @SquadPlan(description = "Crisis prediction result")
    public static class CrisisPrediction {
        public double viralityScore6h;   // 0-100
        public double viralityScore12h;
        public double viralityScore24h;
        public String escalationLevel;   // LOW, MEDIUM, HIGH, CRITICAL
        public String recommendedAction; // MONITOR, PREPARE_RESPONSE, ACTIVATE_CRISIS_TEAM
        public String reasoning;
        public List<String> similarPastIncidents;
    }
}
```

### PredictionService.java

```java
package io.sentinel.backend.service;

import io.sentinel.backend.repository.MentionEntity;
import io.squados.context.SquadContext;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class PredictionService {
    private final SquadContext ctx;
    private final MentionRepository mentionRepo;
    private final PredictionHistoryRepository historyRepo;

    public CrisisPrediction predictVirality(String tenantId, MentionEntity mention) {
        // Get recent similar mentions (same topic, same handle)
        Instant last7days = Instant.now().minus(7, ChronoUnit.DAYS);
        List<MentionEntity> similar = mentionRepo.findByTenantAndTopicAndPostedAfter(
            tenantId, mention.topic, last7days
        );

        // Calculate trend metrics
        double reweetVelocity = calculateVelocity(mention);
        double sentimentVelocity = calculateSentimentTrend(similar);
        
        String prompt = String.format(
            "Predict virality for this mention:\n" +
            "Text: %s\n" +
            "Author followers: %d\n" +
            "Current retweets: %d\n" +
            "Retweet velocity (per hour): %.1f\n" +
            "Sentiment: %s\n" +
            "Topic: %s\n" +
            "Similar incidents in last 7 days: %d\n" +
            "Sentiment trend: %s\n" +
            "Return JSON with virality scores 6h/12h/24h.",
            mention.text, mention.authorFollowers, mention.retweetCount,
            reweetVelocity, mention.sentimentLabel, mention.topic,
            similar.size(), sentimentVelocity
        );

        CrisisPrediction pred = (CrisisPrediction) ctx.submitTo(
            AgentRole.RESEARCHER, prompt, CrisisPrediction.class
        );

        // Save prediction for analysis
        savePredictionHistory(tenantId, mention.id, pred);

        // Auto-escalate if high virality predicted
        if (pred.viralityScore24h > 75) {
            mention.priority = "P1";
            mention.isViral = true;
            mention.viralRiskScore = (int) pred.viralityScore24h;
        }

        return pred;
    }

    private double calculateVelocity(MentionEntity mention) {
        // Retweets per hour since posted
        long hoursElapsed = ChronoUnit.HOURS.between(mention.postedAt, Instant.now());
        if (hoursElapsed == 0) return 0;
        return mention.retweetCount / (double) hoursElapsed;
    }

    private double calculateSentimentTrend(List<MentionEntity> mentions) {
        // Trend: are recent mentions more negative than older ones?
        if (mentions.size() < 2) return 0;
        
        long recent = mentions.stream()
            .filter(m -> m.sentimentLabel.equals("NEGATIVE"))
            .count();
        return (recent / (double) mentions.size()) * 100;
    }

    private void savePredictionHistory(String tenantId, String mentionId, CrisisPrediction pred) {
        PredictionHistory history = new PredictionHistory();
        history.tenantId = tenantId;
        history.mentionId = mentionId;
        history.viralityScore24h = pred.viralityScore24h;
        history.escalationLevel = pred.escalationLevel;
        history.predictedAt = Instant.now();
        historyRepo.save(history);
    }

    // Check which past predictions were accurate (for agent calibration)
    public Map<String, Object> getPredictionAccuracy(String tenantId, int days) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        List<PredictionHistory> predictions = historyRepo.findByTenantAndPredictedAfter(tenantId, since);

        int correct = 0;
        int total = 0;

        for (PredictionHistory pred : predictions) {
            MentionEntity mention = mentionRepo.findById(pred.mentionId).orElse(null);
            if (mention != null) {
                total++;
                // Did it actually go viral? (> 100 retweets)
                boolean actuallyViral = mention.retweetCount > 100;
                boolean predictedViral = pred.viralityScore24h > 70;
                
                if (actuallyViral == predictedViral) {
                    correct++;
                }
            }
        }

        return Map.of(
            "accuracy", total > 0 ? (correct / (double) total) * 100 : 0,
            "totalPredictions", total
        );
    }
}
```

### Update MentionProcessingService to run prediction

```java
private void runPipeline(MentionEntity mention) {
    // ... existing sentiment, escalation, reply agents ...

    // NEW: Run prediction if NEGATIVE or high urgency
    if ("NEGATIVE".equals(mention.sentimentLabel) || "CRITICAL".equals(mention.urgency)) {
        try {
            CrisisPrediction prediction = predictionService.predictVirality(tenantId, mention);
            
            if (prediction.viralityScore24h > 75) {
                // Send proactive alert to team
                sendCrisisAlert(tenantId, mention, prediction);
                
                // Bump to P1 if wasn't already
                if (!"P1".equals(mention.priority)) {
                    mention.priority = "P1";
                    System.out.println("[MentionService] Prediction escalated to P1: " + mention.id);
                }
            }
        } catch (Exception e) {
            System.err.println("[MentionService] Prediction failed: " + e.getMessage());
        }
    }
}

private void sendCrisisAlert(String tenantId, MentionEntity mention, CrisisPrediction pred) {
    // Send to Slack/Teams/Email
    String message = String.format(
        "🚨 CRISIS ALERT: Mention predicted to go viral in 24h (%.0f%% virality)\n" +
        "Text: %s\nAction: %s",
        pred.viralityScore24h, mention.text, pred.recommendedAction
    );
    
    notificationService.sendAlert(tenantId, "CRITICAL", message);
    ws.broadcast("alert.predicted_crisis", Map.of(
        "mention", mention,
        "prediction", pred
    ));
}
```

### WebSocket event for frontend

```typescriptreact
// Frontend listens for predictions
useEffect(() => {
    ws.addEventListener('message', (e) => {
        const event = JSON.parse(e.data);
        
        if (event.type === 'alert.predicted_crisis') {
            showToast({
                type: 'warning',
                title: '⚠️ Crisis Predicted',
                message: `Mention trending viral: "${event.data.mention.text.substring(0, 50)}..."`
            });
        }
    });
}, [ws]);
```

---

## Implementation Sequence

**Week 1:** Multi-Tenant Architecture (foundation)
**Week 2:** Feedback Loop & Agent Learning
**Week 3:** Viral Prediction & Crisis Alerts
**Week 4:** Testing, documentation, deploy to staging

---

See **ADVANCED_FEATURES_ROADMAP.md** for other features (Multi-Channel, Competitive Intelligence, etc.)

