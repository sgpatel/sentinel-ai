package io.sentinel.backend.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sentinel.backend.repository.KnowledgeBaseArticleRepository;
import io.sentinel.backend.repository.KnowledgeBaseArticleTagRepository;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "sentinel.mock.enabled=false",
    "spring.task.scheduling.enabled=false"
})
@AutoConfigureMockMvc
class KnowledgeBaseComplianceIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwtService;
    @Autowired private UserRepository userRepo;
    @Autowired private KnowledgeBaseArticleRepository articleRepo;
    @Autowired private KnowledgeBaseArticleTagRepository tagRepo;

    private String tenantToken;

    @BeforeEach
    void setUp() {
        tagRepo.deleteAll();
        articleRepo.deleteAll();
        userRepo.deleteAll();

        UserEntity user = saveUser("kb_compliance_admin", "tenant-a", Role.ADMIN);
        tenantToken = jwtService.generate(user);
    }

    @Test
    void restrictedArticlesAreFilteredFromComplianceSearch() throws Exception {
        String restrictedPayload = """
            {
              "title": "Internal fraud runbook",
              "content": "Password reset escalation matrix for internal teams.",
              "visibility": "RESTRICTED"
            }
            """;
        String publicPayload = """
            {
              "title": "Password reset guide",
              "content": "How customers can reset account password safely.",
              "visibility": "PUBLIC"
            }
            """;

        String restrictedRaw = mockMvc.perform(post("/api/admin/kb/articles")
                .header("Authorization", "Bearer " + tenantToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(restrictedPayload))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String restrictedId = mapper.readTree(restrictedRaw).get("id").asText();

        String publicRaw = mockMvc.perform(post("/api/admin/kb/articles")
                .header("Authorization", "Bearer " + tenantToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(publicPayload))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String publicId = mapper.readTree(publicRaw).get("id").asText();

        String adminSearchRaw = mockMvc.perform(get("/api/admin/kb/search")
                .param("q", "password")
                .header("Authorization", "Bearer " + tenantToken)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        JsonNode adminResults = mapper.readTree(adminSearchRaw);
        assertThat(adminResults.size()).isEqualTo(2);

        String complianceSearchRaw = mockMvc.perform(get("/api/kb/search")
                .param("q", "password")
                .header("Authorization", "Bearer " + tenantToken)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        JsonNode complianceResults = mapper.readTree(complianceSearchRaw);
        assertThat(complianceResults.size()).isEqualTo(1);
        assertThat(complianceResults.get(0).get("id").asText()).isEqualTo(publicId);
        assertThat(complianceResults.get(0).get("id").asText()).isNotEqualTo(restrictedId);
        assertThat(complianceResults.get(0).get("visibility").asText()).isEqualTo("PUBLIC");
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

