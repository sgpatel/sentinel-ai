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
class KnowledgeBaseRetrievalIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwtService;
    @Autowired private UserRepository userRepo;
    @Autowired private KnowledgeBaseArticleRepository articleRepo;
    @Autowired private KnowledgeBaseArticleTagRepository tagRepo;

    private String tenantAToken;
    private String tenantBToken;

    @BeforeEach
    void setUp() {
        tagRepo.deleteAll();
        articleRepo.deleteAll();
        userRepo.deleteAll();

        UserEntity tenantAUser = saveUser("kb_admin_a", "tenant-a", Role.ADMIN);
        UserEntity tenantBUser = saveUser("kb_admin_b", "tenant-b", Role.ADMIN);
        tenantAToken = jwtService.generate(tenantAUser);
        tenantBToken = jwtService.generate(tenantBUser);
    }

    @Test
    void kbCrudAndSearchAreTenantScoped() throws Exception {
        String createPayload = """
            {
              "title": "Refund delay troubleshooting",
              "content": "Steps for handling delayed refund complaints.",
              "visibility": "INTERNAL",
              "tags": ["refund", "billing"]
            }
            """;

        String createdRaw = mockMvc.perform(post("/api/admin/kb/articles")
                .header("Authorization", "Bearer " + tenantAToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createPayload))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String createdId = mapper.readTree(createdRaw).get("id").asText();

        String listRaw = mockMvc.perform(get("/api/admin/kb/articles")
                .header("Authorization", "Bearer " + tenantAToken)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        JsonNode tenantAList = mapper.readTree(listRaw);
        assertThat(tenantAList.isArray()).isTrue();
        assertThat(tenantAList.size()).isEqualTo(1);
        assertThat(tenantAList.get(0).get("id").asText()).isEqualTo(createdId);

        String searchRaw = mockMvc.perform(get("/api/admin/kb/search")
                .param("q", "refund")
                .header("Authorization", "Bearer " + tenantAToken)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        JsonNode searchResults = mapper.readTree(searchRaw);
        assertThat(searchResults.size()).isEqualTo(1);
        assertThat(searchResults.get(0).get("title").asText()).contains("Refund");

        String tenantBListRaw = mockMvc.perform(get("/api/admin/kb/articles")
                .header("Authorization", "Bearer " + tenantBToken)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        JsonNode tenantBList = mapper.readTree(tenantBListRaw);
        assertThat(tenantBList.size()).isEqualTo(0);
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

