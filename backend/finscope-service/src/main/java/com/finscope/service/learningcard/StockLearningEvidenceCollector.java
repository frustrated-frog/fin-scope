package com.finscope.service.learningcard;

import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.learningcard.StockLearningCardEvidence;
import com.finscope.service.search.evidence.SearchDepth;
import com.finscope.service.search.evidence.SearchEvidence;
import com.finscope.service.search.evidence.SearchEvidenceContentService;
import com.finscope.service.search.evidence.SearchEvidenceGateway;
import com.finscope.service.search.evidence.SearchEvidenceRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.net.URI;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** 搜索只是基线的补充；先验证标的相关性，再获取正文，拒绝用其他公司的材料凑数量。 */
@Service
@Slf4j
public class StockLearningEvidenceCollector {
    @Resource
    private SearchEvidenceGateway search;
    @Resource
    private SearchEvidenceContentService content;

    public List<StockLearningCardEvidence> collect(Instrument instrument, String dimension,
                                                  List<StockLearningCardEvidence> baseline) {
        List<StockLearningCardEvidence> result = new ArrayList<>();
        for (StockLearningCardEvidence item : baseline) {
            result.add(copy(item));
        }
        String query = "\"" + instrument.getName() + "\" \"" + instrument.getCode() + "\" "
                + StockLearningFramework.queryFor(dimension, "", "").strip();
        try {
            var batch = search.search(new SearchEvidenceRequest(query, SearchDepth.DEEP, 8, 12, "cn", "zh", 25_000));
            if (batch.isAllProvidersFailed() && result.isEmpty()) {
                throw new IllegalStateException("公开资料搜索暂不可用，且没有本公司基线资料");
            }
            Set<String> urls = new HashSet<>();
            for (StockLearningCardEvidence seed : result) {
                urls.add(seed.getUrl());
            }
            int fullTextReads = 0;
            for (SearchEvidence hit : batch.getEvidence()) {
                if (result.size() >= 8) {
                    break;
                }
                if (!usableUrl(hit.getUrl()) || !related(instrument, hit.getTitle(), hit.getUrl(), hit.getContent())
                        || !urls.add(hit.getUrl())) {
                    continue;
                }
                String body = mentions(instrument, hit.getContent()) ? text(hit.getContent()) : "";
                String origin = "SEARCH_SNIPPET";
                try {
                    var acquired = content.acquire(hit, query, instrument.getName(), fullTextReads++ < 2);
                    // 正文提取可能重定向到其他公司或丢失主体，不能覆盖已核对的本公司摘要。
                    if (mentions(instrument, acquired.getContent())) {
                        body = text(acquired.getContent());
                        origin = acquired.getContentOrigin();
                    }
                } catch (RuntimeException error) {
                    log.warn("股票学习卡单条资料读取失败 code={} dimension={} errorType={}",
                            instrument.getCode(), dimension, error.getClass().getSimpleName());
                }
                if (body.isBlank()) {
                    continue;
                }
                var item = new StockLearningCardEvidence("", text(hit.getTitle()), text(hit.getUrl()),
                        text(hit.getSourceDomain()), text(hit.getPublishedAt()), body);
                item.setContentOrigin(origin);
                result.add(item);
            }
        } catch (RuntimeException error) {
            if (result.isEmpty()) {
                throw error;
            }
            log.warn("股票学习卡搜索补充失败，使用已校验基线 code={} dimension={} errorType={}",
                    instrument.getCode(), dimension, error.getClass().getSimpleName());
        }
        for (int index = 0; index < result.size(); index++) {
            result.get(index).setEvidenceCode("E" + (index + 1));
        }
        return result;
    }

    private boolean related(Instrument instrument, String title, String url, String snippet) {
        return mentions(instrument, title) || hasCode(instrument, url) || mentions(instrument, snippet);
    }

    private boolean usableUrl(String value) {
        try {
            URI uri = URI.create(text(value));
            return uri.getScheme() != null && Set.of("http", "https").contains(uri.getScheme().toLowerCase(Locale.ROOT))
                    && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    private boolean mentions(Instrument instrument, String value) {
        String normalized = Normalizer.normalize(text(value), Normalizer.Form.NFKC).replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        String name = Normalizer.normalize(text(instrument.getName()), Normalizer.Form.NFKC).replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        if (!name.isBlank() && normalized.contains(name)) {
            return true;
        }
        String code = Pattern.quote(text(instrument.getCode()));
        // 正文中的普通六位金额不是证券身份，必须有代码标签或交易所格式。
        return Pattern.compile("(?:证券|股票)(?:代码|代号)?[\\p{P}]*" + code + "(?![0-9])|(?:SH|SZ|BJ)"
                + code + "(?![0-9])|(?<![0-9])" + code + "\\.(?:SH|SZ|BJ)|\\(" + code + "\\)")
                .matcher(normalized).find();
    }

    private boolean hasCode(Instrument instrument, String value) {
        return instrument.getCode() != null && Pattern.compile("(?<![0-9])" + Pattern.quote(instrument.getCode())
                + "(?![0-9])").matcher(text(value)).find();
    }

    private StockLearningCardEvidence copy(StockLearningCardEvidence item) {
        var copy = new StockLearningCardEvidence("", item.getTitle(), item.getUrl(), item.getSource(),
                item.getPublishedAt(), item.content());
        copy.setContentOrigin(item.getContentOrigin());
        return copy;
    }

    private String text(String value) {
        return value == null ? "" : value.strip();
    }
}
