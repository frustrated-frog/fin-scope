package com.finscope.service.agent;

import com.finscope.domain.agent.AgentActionFingerprint;
import com.finscope.domain.article.Article;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class ActionFingerprintService {

    public AgentActionFingerprint sourceFetch(Long sourceId) {
        String targetId = text(sourceId);
        return AgentActionFingerprint.of(
                "source-fetch",
                "source",
                targetId,
                "source-fetch:source:" + targetId,
                "");
    }

    public AgentActionFingerprint articleInterpret(Article article) {
        Long articleId = article == null ? null : article.getId();
        String inputHash = hash(article == null ? "" : article.getBody());
        String targetId = text(articleId);
        return AgentActionFingerprint.of(
                "article-interpret",
                "article",
                targetId,
                "article-interpret:article:" + targetId + ":" + inputHash,
                inputHash);
    }

    public AgentActionFingerprint evidenceExtract(Long eventId, Article article) {
        Long articleId = article == null ? null : article.getId();
        String inputHash = hash(article == null ? "" : article.getBody());
        String targetId = text(eventId) + ":" + text(articleId);
        return AgentActionFingerprint.of(
                "evidence-extract",
                "event-article",
                targetId,
                "evidence-extract:event-article:" + targetId + ":" + inputHash,
                inputHash);
    }

    private String text(Long value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (int index = 0; index < 8 && index < bytes.length; index++) {
                builder.append(String.format("%02x", bytes[index]));
            }
            return builder.toString();
        } catch (Exception ex) {
            return Integer.toHexString((value == null ? "" : value).hashCode());
        }
    }
}
