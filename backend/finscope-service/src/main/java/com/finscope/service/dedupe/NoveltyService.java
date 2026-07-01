package com.finscope.service.dedupe;

import com.finscope.dao.article.ArticleRepository;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

@Service
public class NoveltyService {
    @Resource
    private FingerprintService fingerprintService;
    @Resource
    private ArticleRepository articleRepository;

    public NoveltyDecision decide(String urlFingerprint, String title, long bodySimhash) {
        List<ArticleRepository.ArticleRecord> records = articleRepository.findRecentRecords(500);
        for (ArticleRepository.ArticleRecord record : records) {
            if (urlFingerprint.equals(record.getUrlFingerprint())) {
                return new NoveltyDecision("DUPLICATE", "URL 已在文章池中出现，重复文章 ID: " + record.getId());
            }
            if (fingerprintService.titleSimilarity(title, record.getTitle()) >= 0.72) {
                return new NoveltyDecision("DUPLICATE", "标题与历史文章相似，重复文章 ID: " + record.getId());
            }
            if (fingerprintService.hammingDistance(bodySimhash, record.getBodySimhash()) <= 10) {
                return new NoveltyDecision("FOLLOW_UP", "正文指纹接近历史文章，作为旧事件后续进入观察");
            }
        }
        return new NoveltyDecision("NEW", "首次进入信息流");
    }

    public static class NoveltyDecision {
        private final String type;
        private final String reason;

        public NoveltyDecision(String type, String reason) {
            this.type = type;
            this.reason = reason;
        }

        public String getType() {
            return type;
        }

        public String getReason() {
            return reason;
        }
    }
}
