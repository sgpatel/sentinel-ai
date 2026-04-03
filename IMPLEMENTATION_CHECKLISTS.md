# Sentinel AI — Quick Implementation Checklists

## Multi-Tenant Architecture Checklist

### Database Setup
- [ ] Create `tenants` table (id, name, slug, plan, status)
- [ ] Create `tenant_configs` table (handle, brand_name, tone, LLM settings)
- [ ] Create `tenant_users` junction table (many-to-many)
- [ ] Add `tenant_id` column to all tables (mentions, tickets, feedback, etc.)
- [ ] Create indexes on `tenant_id` for all queries
- [ ] Run Flyway migration (set existing mentions to tenant_id='default')

### Java Code
- [ ] Create `TenantContext.java` (ThreadLocal holder)
- [ ] Create `TenantInterceptor.java` (extract from JWT, set context)
- [ ] Create `TenantRepository.java` (CRUD for tenants)
- [ ] Update all existing repositories with tenant filtering
- [ ] Update `MentionEntity` add `tenant_id` field
- [ ] Create `AdminController.java` (tenant CRUD, billing, usage)
- [ ] Update `MentionController` to use `TenantContext.getTenantId()`

### Spring Config
- [ ] Register `TenantInterceptor` in `WebMvcConfigurer`
- [ ] Add `@Value` for multi-tenant JWT claims
- [ ] Update security config (allow admin endpoints for SUPER_ADMIN role)

### Frontend
- [ ] Create `TenantSwitcher.tsx` component
- [ ] Add tenant dropdown to header
- [ ] Store selected tenant in React Context / localStorage
- [ ] Update API calls to use selected tenant
- [ ] Add tenant info to user profile menu

### Testing
- [ ] Unit test: `testTenantIsolation()` (create 2 tenants, verify isolation)
- [ ] Integration test: API calls from Tenant A can't see Tenant B's mentions
- [ ] Security test: JWT without tenant_id defaults safely
- [ ] Load test: Query performance with 1M mentions across 100 tenants

### Deployment
- [ ] Test migration with backup of production DB
- [ ] Rollback plan if migration fails
- [ ] Monitor query performance post-migration
- [ ] Document tenant creation process for ops

---

## Feedback Loop & Agent Learning Checklist

### Database Setup
- [ ] Create `reply_feedback` table (mention, agent_name, original_reply, feedback_reply, approved, reason, sentiment_improvement)
- [ ] Create `training_examples` table (mention_text, feedback_text, is_positive, embedding)
- [ ] Create `agent_performance_metrics` table (date, agent_name, total, approved, rejected, avg_improvement)
- [ ] Create indexes on (tenant_id, agent_name, created_at)

### Java Services
- [ ] Create `ReplyFeedbackRepository` (query by agent, tenant, date)
- [ ] Create `ReplyFeedbackService` (recordFeedback, getApprovalRate, getTopExamples)
- [ ] Create `TrainingDataService` (addTrainingExample, buildEnhancedPrompt, exportForFineTuning)
- [ ] Create `AgentMetricsRepository` and update logic

### Integration Points
- [ ] Hook `MentionController.approveReply()` → call `feedbackService.recordFeedback(..., true)`
- [ ] Hook `MentionController.rejectReply()` → call `feedbackService.recordFeedback(..., false)`
- [ ] Update `ReplyAgent` to use `trainingService.buildEnhancedPrompt()` before LLM call
- [ ] Update `SentimentAgent` similarly for multi-class examples

### Analytics
- [ ] New endpoint: `GET /api/analytics/agent-performance?days=30`
- [ ] Return: approval rate, rejection reasons, avg sentiment improvement per agent
- [ ] New endpoint: `GET /api/analytics/export-training?agent=ReplyAgent&format=jsonl`

### Frontend
- [ ] New dashboard tab: "Agent Performance"
- [ ] Show: approval rate trend, top rejection reasons, learning curve
- [ ] Chart: improvement over time (day 1 approval=60%, day 30 approval=85%)
- [ ] Export button for fine-tuning (OpenAI format)

### Testing
- [ ] Unit test: recordFeedback updates metrics correctly
- [ ] Test: buildEnhancedPrompt includes top 5 approved examples
- [ ] Test: exportForOpenAIFineTuning generates valid JSONL
- [ ] Integration test: end-to-end approve → metric update → enhanced prompt

### Deployment
- [ ] Run migration to add 3 new tables
- [ ] Seed with historical data if available
- [ ] Monitor LLM performance post-deployment

---

## Viral Prediction Checklist

### Database Setup
- [ ] Create `prediction_history` table (mention_id, virality_6h, 12h, 24h, escalation_level, predicted_at)
- [ ] Create index on (tenant_id, predicted_at)

### Agent
- [ ] Create `PredictionAgent.java` with role RESEARCHER
- [ ] Define `CrisisPrediction` class (viralityScore6h, 12h, 24h, escalationLevel, recommendedAction)

### Service
- [ ] Create `PredictionService.java`
- [ ] Implement `predictVirality(mention)` → calls PredictionAgent
- [ ] Implement `calculateVelocity()` (retweets per hour trend)
- [ ] Implement `calculateSentimentTrend()` (% negative mentions over time)
- [ ] Implement `savePredictionHistory()` (audit trail)
- [ ] Implement `getPredictionAccuracy()` (calibrate agent over time)

### Integration
- [ ] Call `predictionService.predictVirality()` in `MentionProcessingService.runPipeline()` for NEGATIVE or CRITICAL mentions
- [ ] If viralityScore24h > 75: auto-escalate to P1, call `sendCrisisAlert()`
- [ ] Auto-escalate to `priority="P1"`, `isViral=true`

### Alerts
- [ ] Create `NotificationService` (send Slack/Teams/SMS alerts)
- [ ] Implement `sendCrisisAlert()` (format message with prediction details)
- [ ] Broadcast via WebSocket: `{ type: "alert.predicted_crisis", data: prediction }`

### Frontend
- [ ] Listen to WebSocket for `alert.predicted_crisis` events
- [ ] Show toast notification with crisis warning
- [ ] Add "Alerts" tab showing predicted crises
- [ ] Chart: virality score vs. actual outcome (24h later)

### Testing
- [ ] Unit test: calculateVelocity with mock mention data
- [ ] Test: PredictionAgent returns valid JSON structure
- [ ] Integration test: mention with followers=50K + negative sentiment + velocity > 5 → prediction > 70
- [ ] Accuracy test: compare predicted scores vs. actual outcomes after 24h

### Deployment
- [ ] Test with historical data (backtest prediction accuracy)
- [ ] Monitor false positive rate first week
- [ ] Tune thresholds (currently 75% virality = crisis) based on false positives

---

## Multi-Channel Orchestration Checklist

### Interfaces & Connectors
- [ ] Create `ChannelConnector` interface (postReply, likePost, deleteReply, getName)
- [ ] Implement `TwitterChannelConnector` (uses Twitter API v2)
- [ ] Implement `FacebookChannelConnector` (uses Graph API)
- [ ] Implement `LinkedInChannelConnector` (uses LinkedIn API)
- [ ] Implement `InstagramChannelConnector` (uses IG API or DM fallback)

### Service
- [ ] Create `ChannelConnectorFactory.java` (registry pattern)
- [ ] Register connectors by platform in config
- [ ] Add connector selection logic in `MentionProcessingService`

### Configuration
- [ ] Add tenant config fields for each platform API keys (encrypted)
- [ ] Add tenant feature flag: `enable_multi_channel` (default false)
- [ ] Add channel preference: which platforms to reply on (enum set)

### API
- [ ] Update `MentionController.approveReply()` to accept `{ replyText, channels: ["TWITTER", "FACEBOOK"] }`
- [ ] New endpoint: `POST /api/replies/batch` (post to multiple channels at once)
- [ ] New endpoint: `POST /api/replies/{id}/post` (actually post the reply)
- [ ] Track which channels reply was posted to in reply history

### Database
- [ ] Add `channel` column to replies
- [ ] Add `channels_posted` array column (TWITTER, FACEBOOK, etc.)
- [ ] Add `external_post_id` (mapping to platform-specific ID)

### Frontend
- [ ] Reply editor: show checkbox "Post to:" with checkboxes for each platform
- [ ] Schedule post time (option: post in 1h, 24h, or now)
- [ ] Show status: "Posted to Twitter" ✓, "Posted to Facebook" ✓, "Pending: LinkedIn" ⏳
- [ ] Batch reply button: "Reply to all 5 selected mentions → Twitter, Facebook"

### Testing
- [ ] Unit test each connector (mock API responses)
- [ ] Integration test: approve reply → posts to Twitter (with mock token)
- [ ] Test: permission checking (user has credentials for Twitter, should fail for Zendesk)
- [ ] Test: staggered posting (3 mentions spaced 5 minutes apart to avoid spam filters)

### Deployment
- [ ] Test with sandbox API keys first (Twitter, Facebook, LinkedIn)
- [ ] Verify rate limits per platform
- [ ] Monitor for API errors (handle gracefully)

---

## Competitive Intelligence Checklist

### Configuration
- [ ] Add competitor handle list to `tenant_configs` (comma-separated or JSON)
- [ ] Add `competitor_platforms` config (which competitors to track)
- [ ] Add feature flag: `enable_competitive_intel` (default false)

### Ingestion
- [ ] Update `TwitterIngestionService` to also ingest competitor mentions
- [ ] Filter by: handle in [your_handle, competitor1, competitor2, ...]
- [ ] Tag mentions with `is_competitor_mention=true` flag

### Analytics
- [ ] New service: `CompetitiveAnalyticsService`
- [ ] Endpoint: `GET /api/analytics/competitors?brands=@Brand1,@Brand2`
- [ ] Return comparison data:
  - Sentiment distribution (pie chart: Your=65% positive, Competitor1=55% positive)
  - Mention volume trend (line chart: 24h data)
  - Share-of-voice (% of total mentions in category)
  - Topic breakdown (your top topics vs theirs)
  - Average sentiment score comparison
  - NPS-style rating per brand

### Dashboard
- [ ] New tab: "Competitive Intelligence"
- [ ] Widget: "Your Brand vs Top Competitors"
- [ ] Charts:
  - Sentiment comparison (side-by-side)
  - Volume over time
  - Topic heatmap (what topics each brand covers)
- [ ] Table: top complaints per competitor (opportunities to differentiate)

### Testing
- [ ] Unit test: sentiment calculation across 3 brands
- [ ] Test: share-of-voice math (100 total mentions, you=40, competitor1=35, competitor2=25)
- [ ] Integration test: ingest 10 competitor mentions, verify they show in competitive dashboard

### Deployment
- [ ] Test with real Twitter API (mock competitor data)
- [ ] Verify data refresh timing (real-time vs batch)

---

## Advanced Filtering Checklist

### Query Builder
- [ ] Create `MentionQuery` class (DSL representation)
- [ ] Parse filters: sentiment, priority, urgency, topic, followers, keywords, date range
- [ ] Support operators: AND, OR, NOT, (), comparisons (>, <, ==, >=, <=, contains)

### API
- [ ] New endpoint: `GET /api/mentions/search?q=...`
- [ ] Query format: JSON or URL-encoded
  - Example: `?q={sentiment:"NEGATIVE",urgency:"CRITICAL",followers_gt:10000}`
  - Example: `?q={(sentiment:"NEGATIVE" AND urgency:"HIGH") OR followers_gt:100000}`

### Database
- [ ] Create `saved_searches` table (user, tenant, query, name, created_at)
- [ ] Update `mentions` table indexes for fast filtering

### Frontend
- [ ] Visual query builder (drag-and-drop filters)
- [ ] Autocomplete suggestions for: sentiment, topic, team
- [ ] "Save Search" button → prompt for name → run on schedule
- [ ] Saved searches list with "Run", "Edit", "Delete" buttons

### Notifications
- [ ] Schedule saved searches (daily, hourly)
- [ ] Email alerts: "Your saved search 'High-Risk Mentions' found 5 new matches"

### Testing
- [ ] Test: complex query with AND/OR/NOT
- [ ] Test: performance with 1M mentions, complex filter
- [ ] Test: saved search runs on schedule correctly

### Deployment
- [ ] Add database indexes for filtered columns (sentiment, priority, followers)
- [ ] Monitor query performance

---

## Knowledge Base Integration Checklist

### Database
- [ ] Create `knowledge_base_articles` table (title, content, category, compliance_level, tags)
- [ ] Create `article_feedback` table (helpful/not helpful votes)
- [ ] Index on tags and compliance_level

### Admin UI
- [ ] New section: "Knowledge Base"
- [ ] CRUD for articles (create, edit, delete)
- [ ] Tagging system (BILLING, FRAUD, FEATURE_REQUEST, BUG, etc.)
- [ ] Compliance level selector (PUBLIC, INTERNAL, ESCALATION_ONLY)

### Search Agent
- [ ] Create `KnowledgeSearchAgent` (RESEARCHER role)
- [ ] Given mention, find top-3 relevant KB articles
- [ ] Return article IDs, titles, similarity scores

### Integration
- [ ] In `ReplyAgent`, fetch KB articles before generating reply
- [ ] Enhance prompt: "Here are relevant help articles: [Article 1: ..., Article 2: ...]"
- [ ] Include KB links in AI-generated reply

### API
- [ ] New endpoint: `GET /api/knowledge-base` (list articles)
- [ ] New endpoint: `GET /api/knowledge-base/search?mention_id=abc`
- [ ] New endpoint: `POST /api/knowledge-base` (create article)

### Testing
- [ ] Test: KnowledgeSearchAgent finds correct articles for mention
- [ ] Test: reply includes KB links
- [ ] Test: compliance level filtering (don't expose ESCALATION_ONLY to user)

---

## Workflow Automation Checklist

### Data Model
- [ ] Create `workflow_rules` table (tenant, name, conditions JSONB, actions JSONB, enabled)
- [ ] Create `workflow_executions` table (rule_id, mention_id, executed_at, result JSONB)

### Rule Engine
- [ ] Create `RuleEvaluator` class (parse & evaluate conditions)
- [ ] Create `ActionExecutor` class (execute actions: route, ticket, notify, webhook)
- [ ] Create `WorkflowEngine` service (orchestrate)

### Supported Conditions
- [ ] `sentiment == NEGATIVE | POSITIVE | NEUTRAL`
- [ ] `urgency == LOW | MEDIUM | HIGH | CRITICAL`
- [ ] `topic == PAYMENT_FAILURE | APP_CRASH | ...`
- [ ] `followers > 10000`
- [ ] `is_viral == true | false`
- [ ] `keywords_match("fraud", "scam")`

### Supported Actions
- [ ] `escalate_to_team(team_name)`
- [ ] `create_ticket(priority="P1", category="FRAUD")`
- [ ] `send_notification(channel="SLACK", message="...")`
- [ ] `send_email(to="manager@company.com", subject="...")`
- [ ] `assign_reviewer(user_id="...")`
- [ ] `tag_mention(tags=["FRAUD", "URGENT"])`
- [ ] `run_webhook(url="https://...", payload={})`
- [ ] `sql_query("UPDATE mentions SET ... WHERE ...")`
- [ ] `call_external_api(service="TWILIO", action="send_sms", to="+1234567890")`

### Frontend
- [ ] Visual workflow builder (nodes for IF/THEN)
- [ ] Condition builder (dropdown selectors)
- [ ] Action builder (select action type, fill in parameters)
- [ ] Test rule button (dry-run on sample mention)

### Integration
- [ ] In `MentionProcessingService.runPipeline()`, after all agents run:
  - Get all active rules for tenant
  - Evaluate each rule against current mention
  - Execute matching actions

### Testing
- [ ] Unit test: RuleEvaluator(sentiment==NEGATIVE AND followers > 5000)
- [ ] Test: ActionExecutor creates ticket with correct fields
- [ ] Integration test: mention → rule triggered → ticket created + Slack notified

---

## Deployment Checklist (All Features)

### Pre-Deployment
- [ ] Code review by 2+ senior devs
- [ ] Unit tests: >80% coverage
- [ ] Integration tests: happy path + edge cases
- [ ] Database migration tested with production backup
- [ ] Feature flags configured (all new features behind flags)
- [ ] Monitoring/alerting set up (Prometheus, Grafana, PagerDuty)
- [ ] Rollback plan documented

### Staging
- [ ] Deploy to staging environment
- [ ] Run smoke tests (login, ingest mention, reply, analytics)
- [ ] Load test (simulate 10x production traffic)
- [ ] Security scan (OWASP, SQL injection, XSS)
- [ ] Performance baseline (response time < 200ms)

### Production
- [ ] Blue-green deployment (no downtime)
- [ ] Canary: 10% traffic → 50% → 100%
- [ ] Monitor: error rate, latency, database performance
- [ ] Rollback plan on standby (rollback script tested)
- [ ] Post-deployment: verify feature works end-to-end

### Documentation
- [ ] User guide (how to use new feature)
- [ ] Admin guide (configuration, troubleshooting)
- [ ] API documentation (OpenAPI/Swagger)
- [ ] Internal runbook (how to handle common issues)

---

**Print this checklist, check items off, and track progress!**

