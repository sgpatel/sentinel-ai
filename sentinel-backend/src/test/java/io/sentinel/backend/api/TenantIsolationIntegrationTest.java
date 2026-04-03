package io.sentinel.backend.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sentinel.backend.repository.MentionEntity;
import io.sentinel.backend.repository.MentionDlqEntity;
import io.sentinel.backend.repository.MentionDlqRepository;
import io.sentinel.backend.repository.MentionRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "sentinel.mock.enabled=false",
    "spring.task.scheduling.enabled=false"
})
@AutoConfigureMockMvc
class TenantIsolationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private MentionRepository mentionRepo;
    @Autowired private MentionDlqRepository dlqRepo;
    @Autowired private UserRepository userRepo;
    @Autowired private JwtService jwtService;
    @Autowired private ObjectMapper mapper;

    private String tenantAToken;
    private String tenantBToken;

    @BeforeEach
    void setUp() {
        mentionRepo.deleteAll();
        dlqRepo.deleteAll();
        userRepo.deleteAll();

        UserEntity tenantAUser = saveUser("admin_tenant_a", "tenant-a", Role.ADMIN);
        UserEntity tenantBUser = saveUser("admin_tenant_b", "tenant-b", Role.ADMIN);

        tenantAToken = jwtService.generate(tenantAUser);
        tenantBToken = jwtService.generate(tenantBUser);

        mentionRepo.save(newMention("A-MENTION-1", "tenant-a", "Tenant A mention", "PENDING"));
        mentionRepo.save(newMention("B-MENTION-1", "tenant-b", "Tenant B mention", "PENDING"));
    }

    @Test
    void listMentionsReturnsOnlyCurrentTenantData() throws Exception {
        String raw = mockMvc.perform(get("/api/mentions")
                .header("Authorization", "Bearer " + tenantAToken)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        JsonNode arr = mapper.readTree(raw);
        assertThat(arr.isArray()).isTrue();
        assertThat(arr).hasSize(1);
        assertThat(arr.get(0).get("id").asText()).isEqualTo("A-MENTION-1");
    }

    @Test
    void getMentionReturns404ForOtherTenantData() throws Exception {
        mockMvc.perform(get("/api/mentions/B-MENTION-1")
                .header("Authorization", "Bearer " + tenantAToken)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());
    }

    @Test
    void pendingRepliesAreTenantScoped() throws Exception {
        String raw = mockMvc.perform(get("/api/pending-replies")
                .header("Authorization", "Bearer " + tenantBToken)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        JsonNode arr = mapper.readTree(raw);
        assertThat(arr).hasSize(1);
        assertThat(arr.get(0).get("id").asText()).isEqualTo("B-MENTION-1");
    }

    @Test
    void crossTenantApproveIsBlocked() throws Exception {
        mockMvc.perform(post("/api/mentions/B-MENTION-1/reply/approve")
                .header("Authorization", "Bearer " + tenantAToken)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());

        MentionEntity target = mentionRepo.findById("B-MENTION-1").orElseThrow();
        assertThat(target.replyStatus).isEqualTo("PENDING");
    }

    @Test
    void crossTenantRejectIsBlocked() throws Exception {
        mockMvc.perform(post("/api/mentions/A-MENTION-1/reply/reject")
                .header("Authorization", "Bearer " + tenantBToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"revisedReply\":\"updated\"}")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());

        MentionEntity target = mentionRepo.findById("A-MENTION-1").orElseThrow();
        assertThat(target.replyStatus).isEqualTo("PENDING");
    }

    @Test
    void adminCanSwitchTenantViaHeader() throws Exception {
        String raw = mockMvc.perform(get("/api/mentions")
                .header("Authorization", "Bearer " + tenantAToken)
                .header("X-Tenant-Id", "tenant-b")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        JsonNode arr = mapper.readTree(raw);
        assertThat(arr).hasSize(1);
        assertThat(arr.get(0).get("id").asText()).isEqualTo("B-MENTION-1");
    }

    @Test
    void searchMentionsIsTenantScoped() throws Exception {
        String raw = mockMvc.perform(get("/api/mentions/search")
                .param("q", "Tenant")
                .header("Authorization", "Bearer " + tenantAToken)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        JsonNode body = mapper.readTree(raw);
        JsonNode content = body.get("content");
        assertThat(content).hasSize(1);
        assertThat(content.get(0).get("id").asText()).isEqualTo("A-MENTION-1");
    }

    @Test
    void searchMentionsSupportsSentimentAndFollowerFilters() throws Exception {
        MentionEntity extra = newMention("A-MENTION-2", "tenant-a", "Needs escalation", "PENDING");
        extra.sentimentLabel = "POSITIVE";
        extra.authorFollowers = 50_000;
        mentionRepo.save(extra);

        String raw = mockMvc.perform(get("/api/mentions/search")
                .param("sentiment", "POSITIVE")
                .param("minFollowers", "10000")
                .header("Authorization", "Bearer " + tenantAToken)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        JsonNode body = mapper.readTree(raw);
        JsonNode content = body.get("content");
        assertThat(content).hasSize(1);
        assertThat(content.get(0).get("id").asText()).isEqualTo("A-MENTION-2");
    }

    @Test
    void savedSearchesAreScopedByTenantAndUser() throws Exception {
        mockMvc.perform(post("/api/saved-searches")
                .header("Authorization", "Bearer " + tenantAToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"A Search\",\"queryJson\":\"{\\\"sentiment\\\":\\\"NEGATIVE\\\"}\"}"))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/saved-searches")
                .header("Authorization", "Bearer " + tenantBToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"B Search\",\"queryJson\":\"{\\\"priority\\\":\\\"P1\\\"}\"}"))
            .andExpect(status().isOk());

        String rawA = mockMvc.perform(get("/api/saved-searches")
                .header("Authorization", "Bearer " + tenantAToken)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        JsonNode listA = mapper.readTree(rawA);
        assertThat(listA).hasSize(1);
        assertThat(listA.get(0).get("name").asText()).isEqualTo("A Search");

        String rawB = mockMvc.perform(get("/api/saved-searches")
                .header("Authorization", "Bearer " + tenantBToken)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        JsonNode listB = mapper.readTree(rawB);
        assertThat(listB).hasSize(1);
        assertThat(listB.get(0).get("name").asText()).isEqualTo("B Search");
    }

    @Test
    void savedSearchUpdateDeleteBlockedAcrossTenants() throws Exception {
        String createdRaw = mockMvc.perform(post("/api/saved-searches")
                .header("Authorization", "Bearer " + tenantAToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"A Search\",\"queryJson\":\"{\\\"q\\\":\\\"foo\\\"}\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(createdRaw).get("id").asText();

        mockMvc.perform(put("/api/saved-searches/" + id)
                .header("Authorization", "Bearer " + tenantBToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Hacked\"}"))
            .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/saved-searches/" + id)
                .header("Authorization", "Bearer " + tenantBToken))
            .andExpect(status().isNotFound());

        String ownRaw = mockMvc.perform(put("/api/saved-searches/" + id)
                .header("Authorization", "Bearer " + tenantAToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"A Search Updated\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(ownRaw).get("name").asText()).isEqualTo("A Search Updated");
    }

    @Test
    void dlqReplayIsTenantScoped() throws Exception {
        MentionDlqEntity row = new MentionDlqEntity();
        row.mentionId = "A-MENTION-1";
        row.tenantId = "tenant-a";
        row.status = "NEW";
        row.failureStage = "PIPELINE";
        row.errorMessage = "boom";
        row = dlqRepo.save(row);

        mockMvc.perform(post("/api/admin/dlq/" + row.id + "/replay")
                .header("Authorization", "Bearer " + tenantBToken))
            .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/admin/dlq/" + row.id + "/replay")
                .header("Authorization", "Bearer " + tenantAToken))
            .andExpect(status().isOk());
    }

    @Test
    void dlqBatchReplayRespectsTenantContext() throws Exception {
        MentionDlqEntity a = new MentionDlqEntity();
        a.mentionId = "A-MENTION-1";
        a.tenantId = "tenant-a";
        a.status = "NEW";
        a.failureStage = "PIPELINE";
        a.errorMessage = "a";
        dlqRepo.save(a);

        MentionDlqEntity b = new MentionDlqEntity();
        b.mentionId = "B-MENTION-1";
        b.tenantId = "tenant-b";
        b.status = "NEW";
        b.failureStage = "PIPELINE";
        b.errorMessage = "b";
        dlqRepo.save(b);

        String raw = mockMvc.perform(post("/api/admin/dlq/replay")
                .param("limit", "10")
                .header("Authorization", "Bearer " + tenantAToken)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        JsonNode body = mapper.readTree(raw);
        assertThat(body.get("replayed").asInt()).isEqualTo(1);

        MentionDlqEntity updatedA = dlqRepo.findById(a.id).orElseThrow();
        MentionDlqEntity updatedB = dlqRepo.findById(b.id).orElseThrow();
        assertThat(updatedA.status).isEqualTo("REQUEUED");
        assertThat(updatedB.status).isEqualTo("NEW");
    }

    @Test
    void reliabilityMetricsAreTenantScopedAndSwitchableForAdmin() throws Exception {
        MentionDlqEntity a = new MentionDlqEntity();
        a.mentionId = "A-MENTION-1";
        a.tenantId = "tenant-a";
        a.status = "NEW";
        a.failureStage = "PIPELINE";
        a.errorMessage = "a";
        dlqRepo.save(a);

        MentionDlqEntity b = new MentionDlqEntity();
        b.mentionId = "B-MENTION-1";
        b.tenantId = "tenant-b";
        b.status = "FAILED";
        b.failureStage = "PIPELINE";
        b.errorMessage = "b";
        dlqRepo.save(b);

        String rawA = mockMvc.perform(get("/api/admin/reliability/metrics")
                .header("Authorization", "Bearer " + tenantAToken)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        JsonNode metricsA = mapper.readTree(rawA);
        assertThat(metricsA.get("tenantId").asText()).isEqualTo("tenant-a");
        assertThat(metricsA.get("dlq").get("total").asLong()).isEqualTo(1);
        assertThat(metricsA.get("dlq").get("new").asLong()).isEqualTo(1);

        String rawB = mockMvc.perform(get("/api/admin/reliability/metrics")
                .header("Authorization", "Bearer " + tenantAToken)
                .header("X-Tenant-Id", "tenant-b")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        JsonNode metricsB = mapper.readTree(rawB);
        assertThat(metricsB.get("tenantId").asText()).isEqualTo("tenant-b");
        assertThat(metricsB.get("dlq").get("total").asLong()).isEqualTo(1);
        assertThat(metricsB.get("dlq").get("failed").asLong()).isEqualTo(1);
    }

    private UserEntity saveUser(String username, String tenantId, Role role) {
        UserEntity u = new UserEntity();
        u.id = UUID.randomUUID().toString();
        u.username = username;
        u.email = username + "@example.com";
        u.passwordHash = "noop";
        u.role = role;
        u.tenantId = tenantId;
        u.active = true;
        u.createdAt = Instant.now();
        u.updatedAt = Instant.now();
        return userRepo.save(u);
    }

    private MentionEntity newMention(String id, String tenantId, String text, String replyStatus) {
        MentionEntity m = new MentionEntity();
        m.id = id;
        m.tenantId = tenantId;
        m.platform = "TWITTER";
        m.handle = "@YourHandleName";
        m.authorUsername = "tester";
        m.authorName = "Tester";
        m.text = text;
        m.postedAt = Instant.now();
        m.ingestedAt = Instant.now();
        m.processingStatus = "DONE";
        m.replyStatus = replyStatus;
        m.sentimentLabel = "NEGATIVE";
        m.priority = "P2";
        return m;
    }
}

