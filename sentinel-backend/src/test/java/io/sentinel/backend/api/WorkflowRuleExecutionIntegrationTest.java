package io.sentinel.backend.api;

import com.sun.net.httpserver.HttpServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sentinel.backend.repository.MentionEntity;
import io.sentinel.backend.repository.MentionRepository;
import io.sentinel.backend.repository.WorkflowExecutionRepository;
import io.sentinel.backend.repository.WorkflowExecutionStepRepository;
import io.sentinel.backend.repository.WorkflowRuleActionRepository;
import io.sentinel.backend.repository.WorkflowRuleConditionRepository;
import io.sentinel.backend.repository.WorkflowRuleRepository;
import io.sentinel.backend.security.JwtService;
import io.sentinel.backend.security.Role;
import io.sentinel.backend.security.UserEntity;
import io.sentinel.backend.security.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "sentinel.mock.enabled=false",
    "spring.task.scheduling.enabled=false"
})
@AutoConfigureMockMvc
class WorkflowRuleExecutionIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwtService;
    @Autowired private UserRepository userRepo;
    @Autowired private MentionRepository mentionRepo;
    @Autowired private WorkflowRuleRepository ruleRepo;
    @Autowired private WorkflowRuleConditionRepository conditionRepo;
    @Autowired private WorkflowRuleActionRepository actionRepo;
    @Autowired private WorkflowExecutionRepository executionRepo;
    @Autowired private WorkflowExecutionStepRepository executionStepRepo;

    private String tenantAToken;
    private String tenantBToken;

    @BeforeEach
    void setUp() {
        executionStepRepo.deleteAll();
        executionRepo.deleteAll();
        actionRepo.deleteAll();
        conditionRepo.deleteAll();
        ruleRepo.deleteAll();
        mentionRepo.deleteAll();
        userRepo.deleteAll();

        UserEntity tenantAUser = saveUser("workflow_admin_a", "tenant-a", Role.ADMIN);
        UserEntity tenantBUser = saveUser("workflow_admin_b", "tenant-b", Role.ADMIN);
        tenantAToken = jwtService.generate(tenantAUser);
        tenantBToken = jwtService.generate(tenantBUser);

        MentionEntity mention = new MentionEntity();
        mention.id = "WF-MENTION-A";
        mention.tenantId = "tenant-a";
        mention.text = "Customer reports fraud transaction";
        mention.platform = "TWITTER";
        mention.handle = "@tenantA";
        mention.urgency = "CRITICAL";
        mention.priority = "P3";
        mention.sentimentLabel = "NEGATIVE";
        mention.authorFollowers = 15000;
        mention.replyStatus = "PENDING";
        mention.processingStatus = "NEW";
        mention.postedAt = Instant.now();
        mentionRepo.save(mention);
    }

    @Test
    void dryRunEvaluatesRuleWithoutSideEffectsAndIsTenantScoped() throws Exception {
        String createRulePayload = """
            {
              "name": "Critical mentions escalate",
              "priority": 10,
              "conditions": [
                {"field": "urgency", "operator": "EQUALS", "value": "CRITICAL", "position": 1}
              ],
              "actions": [
                {"type": "escalate", "payload": {"priority": "P1"}, "position": 1}
              ]
            }
            """;

        mockMvc.perform(post("/api/workflows/rules")
                .header("Authorization", "Bearer " + tenantAToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRulePayload))
            .andExpect(status().isOk());

        String dryRunRaw = mockMvc.perform(post("/api/workflows/evaluate/dry-run")
                .header("Authorization", "Bearer " + tenantAToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mentionId\":\"WF-MENTION-A\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        JsonNode dryRun = mapper.readTree(dryRunRaw);
        assertThat(dryRun.get("dryRun").asBoolean()).isTrue();
        assertThat(dryRun.get("matchedRules").isArray()).isTrue();
        assertThat(dryRun.get("matchedRules").size()).isGreaterThanOrEqualTo(1);

        MentionEntity mentionAfter = mentionRepo.findById("WF-MENTION-A").orElseThrow();
        assertThat(mentionAfter.priority).isEqualTo("P3");

        mockMvc.perform(post("/api/workflows/evaluate/dry-run")
                .header("Authorization", "Bearer " + tenantBToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mentionId\":\"WF-MENTION-A\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void executeAppliesActionsAndAppendsKbCitation() throws Exception {
        String kbCreateRaw = mockMvc.perform(post("/api/admin/kb/articles")
                .header("Authorization", "Bearer " + tenantAToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "Fraud escalation SOP",
                      "content": "Escalate critical fraud mentions to fraud desk immediately.",
                      "visibility": "INTERNAL"
                    }
                    """))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String articleId = mapper.readTree(kbCreateRaw).get("id").asText();

        String createRulePayload = """
            {
              "name": "Critical escalation and KB",
              "priority": 20,
              "conditions": [
                {"field": "urgency", "operator": "EQUALS", "value": "CRITICAL", "position": 1}
              ],
              "actions": [
                {"type": "escalate", "payload": {"priority": "P1", "urgency": "CRITICAL"}, "position": 1},
                {"type": "assign", "payload": {"team": "FRAUD_DESK"}, "position": 2},
                {"type": "attach_kb_article", "payload": {"articleId": "%s"}, "position": 3}
              ]
            }
            """.formatted(articleId);

        mockMvc.perform(post("/api/workflows/rules")
                .header("Authorization", "Bearer " + tenantAToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRulePayload))
            .andExpect(status().isOk());

        String executeRaw = mockMvc.perform(post("/api/workflows/execute")
                .header("Authorization", "Bearer " + tenantAToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mentionId\":\"WF-MENTION-A\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        JsonNode execute = mapper.readTree(executeRaw);
        assertThat(execute.get("dryRun").asBoolean()).isFalse();
        assertThat(execute.get("appliedRules").size()).isGreaterThanOrEqualTo(1);

        MentionEntity mentionAfter = mentionRepo.findById("WF-MENTION-A").orElseThrow();
        assertThat(mentionAfter.priority).isEqualTo("P1");
        assertThat(mentionAfter.assignedTeam).isEqualTo("FRAUD_DESK");
        assertThat(mentionAfter.replyText).contains("KB:");
        assertThat(mentionAfter.replyText).contains(articleId);
    }

    @Test
    void executeNotifyWebhookSendsHttpRequest() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>("");
        AtomicReference<String> idempotencyKey = new AtomicReference<>("");
        CountDownLatch latch = new CountDownLatch(1);

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/workflow-hook", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            idempotencyKey.set(exchange.getRequestHeaders().getFirst("X-Idempotency-Key"));
            byte[] ok = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, ok.length);
            exchange.getResponseBody().write(ok);
            exchange.close();
            latch.countDown();
        });
        server.start();

        try {
            int port = server.getAddress().getPort();
            String createRulePayload = """
                {
                  "name": "Notify critical mention",
                  "priority": 5,
                  "conditions": [
                    {"field": "urgency", "operator": "EQUALS", "value": "CRITICAL", "position": 1}
                  ],
                  "actions": [
                    {"type": "notify_webhook", "payload": {"url": "http://localhost:%d/workflow-hook", "maxAttempts": 1}, "position": 1}
                  ]
                }
                """.formatted(port);

            mockMvc.perform(post("/api/workflows/rules")
                    .header("Authorization", "Bearer " + tenantAToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createRulePayload))
                .andExpect(status().isOk());

            mockMvc.perform(post("/api/workflows/execute")
                    .header("Authorization", "Bearer " + tenantAToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"mentionId\":\"WF-MENTION-A\"}"))
                .andExpect(status().isOk());

            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(requestBody.get()).contains("WF-MENTION-A");
            assertThat(idempotencyKey.get()).isNotBlank();
        } finally {
            server.stop(0);
        }
    }

    private UserEntity saveUser(String username, String tenantId, Role role) {
        UserEntity u = new UserEntity();
        u.id = UUID.randomUUID().toString();
        u.username = username;
        u.email = username + "@example.com";
        u.passwordHash = "x";
        u.fullName = username;
        u.role = role;
        u.tenantId = tenantId;
        return userRepo.save(u);
    }
}

