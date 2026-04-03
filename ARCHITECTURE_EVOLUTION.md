# Sentinel AI — Architecture Evolution Roadmap

## Current State (Today)

```
┌─────────────────────────────────────────────────────────────┐
│                  SENTINEL AI v1.0 (Single Tenant)           │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  Frontend: React 18 + Recharts                        │  │
│  │  - Dashboard (Overview, Mentions, Analytics, Tickets) │  │
│  │  - Reply Queue (manual approval)                      │  │
│  │  - Test Ingestion                                     │  │
│  └──────────────────────────────────────────────────────┘  │
│                           ↓                                  │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  API: Spring Boot 3 REST                              │  │
│  │  - /api/mentions (GET, POST)                         │  │
│  │  - /api/analytics (GET)                              │  │
│  │  - /api/tickets (GET, POST)                          │  │
│  │  - WebSocket: /ws/mentions                           │  │
│  └──────────────────────────────────────────────────────┘  │
│                           ↓                                  │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  7 AI Agents (SquadOS)                                │  │
│  │  ├─ MonitorAgent (orchestrator)                      │  │
│  │  ├─ SentimentAgent (analysis)                        │  │
│  │  ├─ EscalationAgent (priority)                       │  │
│  │  ├─ ReplyAgent (generation)                          │  │
│  │  ├─ ComplianceAgent (review)                         │  │
│  │  ├─ TicketAgent (CRM)                                │  │
│  │  └─ TrendAgent (pattern detection)                   │  │
│  └──────────────────────────────────────────────────────┘  │
│                           ↓                                  │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  Data Layer                                           │  │
│  │  ├─ PostgreSQL (mentions, users, tickets)            │  │
│  │  ├─ Redis (session, traces)                          │  │
│  │  └─ In-Memory Processing                             │  │
│  └──────────────────────────────────────────────────────┘  │
│                           ↓                                  │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  Integrations                                         │  │
│  │  ├─ Twitter API v2 (ingest, no post yet)             │  │
│  │  ├─ Zendesk / Jira / Freshdesk (post tickets)        │  │
│  │  ├─ Ollama / OpenAI / Anthropic / Gemini (LLM)       │  │
│  │  └─ Mock ingestion (for testing)                     │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                              │
└─────────────────────────────────────────────────────────────┘

Capabilities:
✓ Monitor mentions on multiple platforms (Twitter, Facebook, Instagram, LinkedIn)
✓ Multi-dimensional sentiment analysis
✓ AI auto-reply generation with compliance review
✓ CRM ticket creation
✓ Real-time analytics dashboard
✓ Configurable brand handle & tone
✓ Multiple LLM provider support

Limitations:
✗ Single tenant only (can't serve multiple brands on same instance)
✗ Basic analytics (no trend prediction)
✗ Can't reply on platforms (monitoring only)
✗ No learning from feedback (agents don't improve)
✗ Fixed pipeline (no if-then workflow rules)
✗ No multi-language support
✗ No competitive benchmarking
```

---

## After Phase 1: Foundation (Month 1-2)

```
┌────────────────────────────────────────────────────────────────┐
│         SENTINEL AI v2.0 (Multi-Tenant + Intelligence)         │
├────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │  NEW: Multi-Tenant Architecture                         │  │
│  │  ├─ TenantContext + Interceptor (JWT-based isolation)  │  │
│  │  ├─ Tenant-specific config (handle, tone, LLM)         │  │
│  │  ├─ AdminController (tenant CRUD, billing)             │  │
│  │  └─ TenantSwitcher UI                                  │  │
│  └─────────────────────────────────────────────────────────┘  │
│                           ↓                                    │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │  Frontend Enhanced                                       │  │
│  │  ├─ Advanced filtering & search (visual query builder)  │  │
│  │  ├─ Saved searches + scheduled alerts                  │  │
│  │  └─ Better error handling & resilience                 │  │
│  └─────────────────────────────────────────────────────────┘  │
│                           ↓                                    │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │  NEW: Error Recovery & Resilience                       │  │
│  │  ├─ Dead Letter Queue (DLQ) for failed processing      │  │
│  │  ├─ Circuit breaker for external APIs                  │  │
│  │  ├─ Exponential backoff + retry logic                  │  │
│  │  └─ Fallback LLM providers                             │  │
│  └─────────────────────────────────────────────────────────┘  │
│                           ↓                                    │
│  Database Enhanced:                                           │
│  ├─ NEW: tenants, tenant_configs, tenant_users tables       │
│  ├─ All tables: +tenant_id column + indexes                 │
│  ├─ NEW: search_queries, saved_searches tables              │
│  └─ NEW: dlq_items table (failed mentions for replay)        │
│                                                                 │
└────────────────────────────────────────────────────────────────┘

New Capabilities:
✓ Multi-tenant SaaS (serve 100s of brands)
✓ Advanced filtering (complex queries, full-text search)
✓ Saved searches + alerts
✓ Zero-loss processing (DLQ + replay)
✓ Better stability under load

Ready For: Phase 2 (feedback loop, predictions)
```

---

## After Phase 2: Intelligence (Month 2-3)

```
┌────────────────────────────────────────────────────────────────┐
│         SENTINEL AI v3.0 (Smart Learning + Prediction)         │
├────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │  NEW: Feedback Loop & Agent Learning                    │  │
│  │  ├─ ReplyFeedbackService (approve/reject tracking)     │  │
│  │  ├─ TrainingDataService (examples for few-shot)        │  │
│  │  ├─ Enhanced prompts with top-N approved replies       │  │
│  │  ├─ AgentPerformanceMetrics (approval rate, trends)    │  │
│  │  └─ Export for OpenAI fine-tuning                      │  │
│  └─────────────────────────────────────────────────────────┘  │
│                           ↓                                    │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │  NEW: Viral Prediction & Crisis Alerts                  │  │
│  │  ├─ PredictionAgent (RESEARCHER role)                  │  │
│  │  ├─ Virality scoring (6h, 12h, 24h predictions)        │  │
│  │  ├─ Auto-escalate high-risk mentions to P1             │  │
│  │  ├─ SMS/Slack/Email alerts for predicted crises        │  │
│  │  ├─ Accuracy tracking (calibrate predictions)          │  │
│  │  └─ WebSocket: alert.predicted_crisis event            │  │
│  └─────────────────────────────────────────────────────────┘  │
│                           ↓                                    │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │  NEW: Sentiment Evolution Tracking                      │  │
│  │  ├─ Thread mentions with replies together              │  │
│  │  ├─ Conversation lifecycle (sentiment over time)        │  │
│  │  ├─ Measure if replies improve sentiment               │  │
│  │  └─ ConversationSummaryAgent                           │  │
│  └─────────────────────────────────────────────────────────┘  │
│                           ↓                                    │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │  NEW: Performance Metrics Dashboard                      │  │
│  │  ├─ Response time SLA tracking                          │  │
│  │  ├─ Agent performance scoring                           │  │
│  │  ├─ Sentiment recovery rate (% conversations improved)  │  │
│  │  ├─ Resolution time metrics                             │  │
│  │  └─ Team performance comparison                         │  │
│  └─────────────────────────────────────────────────────────┘  │
│                           ↓                                    │
│  Database Enhanced:                                           │
│  ├─ NEW: reply_feedback, training_examples tables            │
│  ├─ NEW: agent_performance_metrics table                     │
│  ├─ NEW: prediction_history table                            │
│  ├─ NEW: conversation_threads table                          │
│  └─ NEW: performance_snapshots table (time-series)           │
│                                                                 │
└────────────────────────────────────────────────────────────────┘

New Capabilities:
✓ Agents improve over time (30-40% fewer rejections after month 1)
✓ Predict viral trends 24h in advance
✓ Proactive crisis alerts before peak
✓ Measure reply effectiveness (sentiment improvement)
✓ Data-driven team optimization

Impact:
→ 40-50% faster response time
→ 60-70% improved sentiment recovery
→ 3-4x more efficient analyst team

Ready For: Phase 3 (multi-channel, competitive intel)
```

---

## After Phase 3: Expansion (Month 3-4)

```
┌────────────────────────────────────────────────────────────────┐
│    SENTINEL AI v4.0 (Omnichannel + Market Intelligence)        │
├────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │  NEW: Multi-Channel Orchestration                        │  │
│  │  ├─ ChannelConnector interface (Twitter, FB, LinkedIn)  │  │
│  │  ├─ Post replies on all platforms from one dashboard    │  │
│  │  ├─ Batch scheduling (stagger across channels)          │  │
│  │  ├─ Per-channel approval workflows                      │  │
│  │  └─ Platform-specific reply formatting                  │  │
│  └─────────────────────────────────────────────────────────┘  │
│                           ↓                                    │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │  NEW: Competitive Intelligence                          │  │
│  │  ├─ Monitor competitor handles                          │  │
│  │  ├─ Sentiment benchmarking (Your Brand vs Competitors)  │  │
│  │  ├─ Share-of-voice metric                               │  │
│  │  ├─ Topic gap analysis (opportunities to cover)         │  │
│  │  ├─ Complaint patterns comparison                       │  │
│  │  └─ CompetitiveAnalyticsService                         │  │
│  └─────────────────────────────────────────────────────────┘  │
│                           ↓                                    │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │  Enhanced Dashboard                                      │  │
│  │  ├─ New tab: Multi-Channel Manager                      │  │
│  │  ├─ New tab: Competitive Intelligence                   │  │
│  │  ├─ Channel status indicators                           │  │
│  │  ├─ Competitive comparison charts                       │  │
│  │  └─ Topic heatmaps (what to talk about)                │  │
│  └─────────────────────────────────────────────────────────┘  │
│                           ↓                                    │
│  Database Enhanced:                                           │
│  ├─ NEW: channel_replies, channel_connectors tables          │
│  ├─ NEW: competitor_brands, competitor_mentions tables       │
│  ├─ NEW: competitive_analytics_snapshots table              │
│  └─ Replies: +channels_posted array column                  │
│                                                                 │
└────────────────────────────────────────────────────────────────┘

New Capabilities:
✓ Reply on Twitter, Facebook, LinkedIn, Instagram at once
✓ Cross-platform campaign management
✓ Benchmark vs competitors (sentiment, volume, topics)
✓ Identify market opportunities (topic gaps)
✓ Share-of-voice tracking

Impact:
→ 80% faster multi-channel response
→ Market intelligence for product & marketing teams
→ Proactive competitive positioning
```

---

## After Phase 4: Automation (Month 4-6)

```
┌────────────────────────────────────────────────────────────────┐
│     SENTINEL AI v5.0 (Full Automation + Global Scale)         │
├────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │  NEW: Workflow Automation Engine                         │  │
│  │  ├─ Visual if-then rule builder                         │  │
│  │  ├─ Condition evaluation (sentiment, urgency, etc.)     │  │
│  │  ├─ Actions: escalate, ticket, notify, webhook, SQL    │  │
│  │  ├─ Audit trail of all rule executions                 │  │
│  │  └─ DRY-run / test mode                                 │  │
│  └─────────────────────────────────────────────────────────┘  │
│                           ↓                                    │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │  NEW: Knowledge Base Integration                         │  │
│  │  ├─ Admin: CRUD for help articles (FAQs, guides)        │  │
│  │  ├─ Auto-retrieve relevant KB on mention                │  │
│  │  ├─ Include KB links in AI replies                      │  │
│  │  ├─ Compliance tagging (public vs internal)             │  │
│  │  └─ Helpfulness tracking (votes on articles)            │  │
│  └─────────────────────────────────────────────────────────┘  │
│                           ↓                                    │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │  NEW: Multi-Language Support                             │  │
│  │  ├─ Per-region tone configuration                       │  │
│  │  ├─ Cultural sensitivity checking                       │  │
│  │  ├─ CulturalContextAgent (avoid cultural missteps)      │  │
│  │  └─ Auto-translate for global audiences                 │  │
│  └─────────────────────────────────────────────────────────┘  │
│                           ↓                                    │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │  NEW: VIP/Influencer Recognition                         │  │
│  │  ├─ VIP registry (executives, journalists, 100K+ users) │  │
│  │  ├─ Auto-VIP detection                                  │  │
│  │  ├─ Dedicated review queue for VIPs                     │  │
│  │  ├─ VIP dashboard (key opinion leaders)                 │  │
│  │  └─ Auto-escalate VIP mentions to P1                    │  │
│  └─────────────────────────────────────────────────────────┘  │
│                           ↓                                    │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │  NEW: Proactive Community Engagement                     │  │
│  │  ├─ Trending topic detection                            │  │
│  │  ├─ Engagement suggestions ("join 50 conversations")    │  │
│  │  ├─ Post calendar (schedule thought leadership)         │  │
│  │  ├─ Community manager assistant                         │  │
│  │  └─ CommunityAgent (engagement recommendations)         │  │
│  └─────────────────────────────────────────────────────────┘  │
│                           ↓                                    │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │  NEW: Export & Reporting                                 │  │
│  │  ├─ PDF/Excel monthly reports                           │  │
│  │  ├─ Scheduled email digests (daily/weekly/monthly)      │  │
│  │  ├─ Custom report builder                               │  │
│  │  ├─ Data export (CSV, JSON, Parquet for BI)            │  │
│  │  └─ Compliance audit logs for regulators                │  │
│  └─────────────────────────────────────────────────────────┘  │
│                           ↓                                    │
│  Database Enhanced:                                           │
│  ├─ NEW: workflow_rules, workflow_executions tables          │
│  ├─ NEW: knowledge_base_articles, article_feedback tables    │
│  ├─ NEW: vip_users table                                     │
│  ├─ NEW: scheduled_reports table                             │
│  ├─ NEW: reports_generated table (audit)                     │
│  └─ Mentions: +language, +is_influencer columns              │
│                                                                 │
└────────────────────────────────────────────────────────────────┘

New Capabilities:
✓ 60% of triage automated via workflow rules
✓ AI replies reference internal docs (50% fewer escalations)
✓ Support 50+ languages with cultural awareness
✓ VIP/influencer special handling
✓ Community building assistance
✓ Executive reporting & compliance ready

Result: Enterprise-grade platform, ready to scale globally

Performance Improvement:
→ 3-4x more mentions handled per analyst
→ 60-70% fewer manual reviews
→ 80% faster crisis response (multi-channel + prediction)
→ 10-15% higher customer satisfaction
```

---

## Component Dependency Graph

```
                    ┌─── Multi-Tenant Architecture ───┐
                    │   (Foundation for all)           │
                    └────────────────┬──────────────────┘
                                     │
                ┌────────────────────┼────────────────────┐
                │                    │                    │
                ↓                    ↓                    ↓
        ┌──────────────┐    ┌──────────────┐    ┌──────────────┐
        │ Feedback     │    │ Viral        │    │ Multi-Channel│
        │ Loop & Learn │    │ Prediction   │    │ Orchestration
        └──────────────┘    └──────────────┘    └──────────────┘
                │                    │                    │
                │                    │                    │
                ├────────────────────┼────────────────────┤
                │                    │                    │
                ↓                    ↓                    ↓
        ┌──────────────┐    ┌──────────────┐    ┌──────────────┐
        │ Agent        │    │ Sentiment    │    │ Competitive  │
        │ Performance  │    │ Evolution    │    │ Intelligence │
        └──────────────┘    └──────────────┘    └──────────────┘
                │                    │                    │
                └────────────────────┼────────────────────┘
                                     │
                ┌────────────────────┼────────────────────┐
                │                    │                    │
                ↓                    ↓                    ↓
        ┌──────────────┐    ┌──────────────┐    ┌──────────────┐
        │ Workflow     │    │ Knowledge    │    │ Multi-        │
        │ Automation   │    │ Base         │    │ Language      │
        └──────────────┘    └──────────────┘    └──────────────┘
                │                    │                    │
                └────────────────────┼────────────────────┘
                                     │
                ┌────────────────────┼────────────────────┐
                │                    │                    │
                ↓                    ↓                    ↓
        ┌──────────────┐    ┌──────────────┐    ┌──────────────┐
        │ VIP/         │    │ Proactive    │    │ Export &     │
        │ Influencer   │    │ Community    │    │ Reporting    │
        │ Recognition  │    │ Engagement   │    │              │
        └──────────────┘    └──────────────┘    └──────────────┘


All depend on:
- Advanced Filtering (foundation for querying)
- Error Recovery (stability)
- Performance Metrics (measurement)
```

---

## Tech Stack Evolution

| Component | v1.0 | v2.0 | v3.0 | v4.0 | v5.0 |
|-----------|------|------|------|------|------|
| **Frontend** | React 18 | ++ Tenant Switcher | ++ Agent Perf | ++ Multi-Ch | ++ Workflow UI |
| **API** | Spring Boot 3 | ++ TenantInterceptor | ++ Prediction | ++ Channel | ++ Automation |
| **Agents** | 7 baseline | — | ++ Prediction | — | ++ Community, Cultural |
| **Database** | PostgreSQL | + Tenant tables | + Feedback tables | + Competitor | + KB, Rules |
| **Services** | 3 core | + Resilience | + Prediction | + Competitive | + Automation, KB |
| **Integrations** | 4 (LLM) | — | + SMS, Slack | + Multi-channel | + Webhooks, BI |
| **Infrastructure** | Single instance | — | — | Load balancer | Horizontal scaling |
| **Monitoring** | Basic logs | + Errors | + Metrics | + Performance | + Observability |

---

## Revenue Opportunity Timeline

```
Month 1-2: Multi-Tenant Ready
  ├─ Launch "Sentinel SaaS" tier
  ├─ Price: $99-999/month per brand
  └─ TAM: 50K brands actively monitoring social media

Month 2-3: Intelligence Premium
  ├─ Upsell: Viral Prediction, Feedback Loop ($49/month)
  └─ Upsell: Competitive Intelligence ($199/month)

Month 3-4: Omnichannel Premium
  ├─ Upsell: Multi-Channel Posting (+$99/month)
  └─ Upsell: Advanced Workflows (+$49/month)

Month 4-6: Enterprise Suite
  ├─ Upsell: KB Integration, Multi-Language (+$99/month)
  ├─ Upsell: VIP Recognition, Reporting (+$49/month)
  └─ Enterprise Tier: All features + dedicated support

Projected MRR After 6 Months:
- 100 customers @ $500 avg = $50K MRR
- 500 customers @ $400 avg = $200K MRR (growth)
- 2000 customers @ $300 avg = $600K MRR (at scale)
```

---

**Last Updated:** April 4, 2026  
**Created by:** Architecture Analysis  
**Next Review:** After Phase 1 completion

