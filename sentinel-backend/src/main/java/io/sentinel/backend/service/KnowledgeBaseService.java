package io.sentinel.backend.service;

import io.sentinel.backend.config.TenantContext;
import io.sentinel.backend.repository.KnowledgeBaseArticleEntity;
import io.sentinel.backend.repository.KnowledgeBaseArticleRepository;
import io.sentinel.backend.repository.KnowledgeBaseArticleTagEntity;
import io.sentinel.backend.repository.KnowledgeBaseArticleTagRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class KnowledgeBaseService {

    private static final List<String> ALLOWED_VISIBILITY = List.of("PUBLIC", "INTERNAL", "RESTRICTED");

    private final KnowledgeBaseArticleRepository articleRepo;
    private final KnowledgeBaseArticleTagRepository tagRepo;

    public KnowledgeBaseService(KnowledgeBaseArticleRepository articleRepo,
                                KnowledgeBaseArticleTagRepository tagRepo) {
        this.articleRepo = articleRepo;
        this.tagRepo = tagRepo;
    }

    public Map<String, Object> createArticle(Map<String, Object> body) {
        String tenantId = TenantContext.getOrDefault();
        KnowledgeBaseArticleEntity article = new KnowledgeBaseArticleEntity();
        article.tenantId = tenantId;
        article.title = String.valueOf(body.getOrDefault("title", "")).trim();
        article.content = String.valueOf(body.getOrDefault("content", "")).trim();
        article.visibility = normalizeVisibility(String.valueOf(body.getOrDefault("visibility", "PUBLIC")));
        article.active = !Boolean.FALSE.equals(body.get("active"));
        if (article.title.isBlank() || article.content.isBlank()) {
            throw new IllegalArgumentException("title and content are required");
        }
        articleRepo.save(article);
        replaceTags(article.id, parseTags(body.get("tags")));
        return toDto(article);
    }

    public List<Map<String, Object>> listArticles() {
        String tenantId = TenantContext.getOrDefault();
        return articleRepo.findByTenantIdOrderByUpdatedAtDesc(tenantId).stream()
            .map(this::toDto)
            .toList();
    }

    public Map<String, Object> updateArticle(String id, Map<String, Object> body) {
        String tenantId = TenantContext.getOrDefault();
        KnowledgeBaseArticleEntity article = articleRepo.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("article not found"));

        if (body.containsKey("title")) {
            String title = String.valueOf(body.get("title")).trim();
            if (!title.isBlank()) article.title = title;
        }
        if (body.containsKey("content")) {
            String content = String.valueOf(body.get("content")).trim();
            if (!content.isBlank()) article.content = content;
        }
        if (body.containsKey("visibility")) {
            article.visibility = normalizeVisibility(String.valueOf(body.get("visibility")));
        }
        if (body.containsKey("active")) {
            article.active = Boolean.parseBoolean(String.valueOf(body.get("active")));
        }
        articleRepo.save(article);
        if (body.containsKey("tags")) {
            replaceTags(article.id, parseTags(body.get("tags")));
        }
        return toDto(article);
    }

    public void deleteArticle(String id) {
        String tenantId = TenantContext.getOrDefault();
        KnowledgeBaseArticleEntity article = articleRepo.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("article not found"));
        tagRepo.deleteByArticleId(article.id);
        articleRepo.delete(article);
    }

    public List<Map<String, Object>> adminSearch(String query) {
        String tenantId = TenantContext.getOrDefault();
        String q = nullToEmpty(query).toLowerCase(Locale.ROOT);
        return articleRepo.findByTenantIdAndActiveTrueOrderByUpdatedAtDesc(tenantId).stream()
            .filter(a -> contains(a.title, q) || contains(a.content, q))
            .map(this::toDto)
            .toList();
    }

    public List<Map<String, Object>> complianceSearch(String query) {
        String tenantId = TenantContext.getOrDefault();
        String q = nullToEmpty(query).toLowerCase(Locale.ROOT);
        return articleRepo.findByTenantIdAndActiveTrueOrderByUpdatedAtDesc(tenantId).stream()
            .filter(a -> !"RESTRICTED".equalsIgnoreCase(a.visibility))
            .filter(a -> contains(a.title, q) || contains(a.content, q))
            .map(this::toDto)
            .toList();
    }

    public List<Map<String, Object>> retrieveForReply(String query, int maxResults) {
        String tenantId = TenantContext.getOrDefault();
        String q = nullToEmpty(query).toLowerCase(Locale.ROOT).trim();
        int limit = Math.max(1, Math.min(5, maxResults));
        return articleRepo.findByTenantIdAndActiveTrueOrderByUpdatedAtDesc(tenantId).stream()
            .filter(a -> !"RESTRICTED".equalsIgnoreCase(a.visibility))
            .filter(a -> q.isBlank() || contains(a.title, q) || contains(a.content, q))
            .limit(limit)
            .map(a -> {
                Map<String, Object> dto = new LinkedHashMap<>();
                dto.put("id", a.id);
                dto.put("title", a.title);
                dto.put("visibility", a.visibility);
                dto.put("snippet", truncate(a.content, 220));
                dto.put("citation", formatCitation(a));
                return dto;
            })
            .toList();
    }

    public Optional<Map<String, Object>> findActiveArticleForAttachment(String articleId) {
        String tenantId = TenantContext.getOrDefault();
        return articleRepo.findByIdAndTenantIdAndActiveTrue(articleId, tenantId)
            .map(a -> {
                Map<String, Object> dto = new LinkedHashMap<>();
                dto.put("id", a.id);
                dto.put("title", a.title);
                dto.put("citation", formatCitation(a));
                dto.put("visibility", a.visibility);
                return dto;
            });
    }

    private Map<String, Object> toDto(KnowledgeBaseArticleEntity article) {
        List<String> tags = tagRepo.findByArticleIdOrderByTagAsc(article.id).stream()
            .map(t -> t.tag)
            .toList();
        return Map.of(
            "id", article.id,
            "title", article.title,
            "content", article.content,
            "visibility", article.visibility,
            "active", article.active,
            "tags", tags,
            "updatedAt", article.updatedAt != null ? article.updatedAt.toEpochMilli() : 0L
        );
    }

    private void replaceTags(String articleId, List<String> tags) {
        tagRepo.deleteByArticleId(articleId);
        for (String tag : tags) {
            KnowledgeBaseArticleTagEntity row = new KnowledgeBaseArticleTagEntity();
            row.articleId = articleId;
            row.tag = tag;
            tagRepo.save(row);
        }
    }

    private static List<String> parseTags(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        List<String> tags = new ArrayList<>();
        for (Object it : list) {
            if (it == null) continue;
            String tag = String.valueOf(it).trim();
            if (!tag.isBlank()) tags.add(tag);
        }
        return tags;
    }

    private static String normalizeVisibility(String value) {
        String normalized = nullToEmpty(value).trim().toUpperCase(Locale.ROOT);
        return ALLOWED_VISIBILITY.contains(normalized) ? normalized : "PUBLIC";
    }

    private static boolean contains(String text, String query) {
        return nullToEmpty(text).toLowerCase(Locale.ROOT).contains(query);
    }

    private static String formatCitation(KnowledgeBaseArticleEntity article) {
        return "KB:" + article.id + " (" + article.title + ")";
    }

    private static String truncate(String value, int maxLen) {
        String text = nullToEmpty(value);
        return text.length() <= maxLen ? text : text.substring(0, maxLen);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

