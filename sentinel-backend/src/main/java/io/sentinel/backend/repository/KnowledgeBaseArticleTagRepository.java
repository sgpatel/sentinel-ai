package io.sentinel.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KnowledgeBaseArticleTagRepository extends JpaRepository<KnowledgeBaseArticleTagEntity, String> {
    List<KnowledgeBaseArticleTagEntity> findByArticleIdOrderByTagAsc(String articleId);
    void deleteByArticleId(String articleId);
}

