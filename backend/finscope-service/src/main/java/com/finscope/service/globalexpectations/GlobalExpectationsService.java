package com.finscope.service.globalexpectations;

import com.finscope.domain.globalexpectations.GlobalExpectationItem;
import com.finscope.rpc.polymarket.PolymarketPublicClient;
import com.finscope.rpc.polymarket.PolymarketPublicMarket;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** 将公开预测市场价格转换为待核验的研究观察，不输出交易指令。 */
@Service
public class GlobalExpectationsService {
    private static final int MAX_MARKETS = 30;

    @Resource
    private PolymarketPublicClient polymarketPublicClient;

    public List<GlobalExpectationItem> list() {
        try {
            List<GlobalExpectationItem> items = map(polymarketPublicClient.fetchActiveMarkets());
            if (!items.isEmpty()) {
                return items;
            }
        } catch (Exception ignored) {
            // 公共源短暂不可用时保留可解释的本地观察基线，页面不会因此中断。
        }
        return baseline();
    }

    private List<GlobalExpectationItem> map(List<PolymarketPublicMarket> markets) {
        List<GlobalExpectationItem> items = new ArrayList<GlobalExpectationItem>();
        for (PolymarketPublicMarket market : markets) {
            String theme = themeOf(market.getQuestion());
            if (theme == null || market.getYesProbability() == null) {
                continue;
            }
            items.add(item((long) items.size() + 1, theme, market.getQuestion(), market.getMarketUrl(),
                    market.getYesProbability(), percentagePoints(market.getOneDayPriceChange()), market.getVolume(),
                    market.getOpenInterest(), market.getEndDate()));
        }
        items.sort(Comparator.comparing(GlobalExpectationItem::getVolume,
                Comparator.nullsLast(Comparator.reverseOrder())));
        if (items.size() > MAX_MARKETS) {
            return new ArrayList<GlobalExpectationItem>(items.subList(0, MAX_MARKETS));
        }
        return items;
    }

    private GlobalExpectationItem item(Long id, String theme, String question, String marketUrl, Integer probability,
                                       Double change24h, Double volume, Double openInterest, String endDate) {
        GlobalExpectationItem item = new GlobalExpectationItem();
        item.setId(id);
        item.setTheme(theme);
        item.setQuestion(question);
        item.setMarketUrl(marketUrl);
        item.setProbability(probability);
        item.setChange24h(change24h);
        item.setVolume(volume);
        item.setOpenInterest(openInterest);
        item.setEndDate(endDate);
        item.setObservation(observation(theme));
        item.setStatus(change24h != null && Math.abs(change24h) >= 3D ? "SIGNAL" : "WATCHING");
        item.setObservedAt("刚刚");
        return item;
    }

    private String themeOf(String question) {
        String normalized = question == null ? "" : question.toLowerCase(Locale.ROOT);
        if (contains(normalized, "chip", "semiconductor", "ai", "export control", "taiwan")) {
            return "科技供应链";
        }
        if (contains(normalized, "oil", "crude", "gas", "opec", "energy", "uranium")) {
            return "能源资源";
        }
        if (contains(normalized, "fed", "federal reserve", "inflation", "interest rate", "recession")) {
            return "全球宏观";
        }
        if (contains(normalized, "china", "chinese", "beijing", "hong kong", "tariff")) {
            return "中国相关";
        }
        if (contains(normalized, "iran", "russia", "ukraine", "nato", "sanction", "trade war")) {
            return "中美关系";
        }
        return null;
    }

    private boolean contains(String value, String... keywords) {
        for (String keyword : keywords) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private Double percentagePoints(Double value) {
        return value == null ? null : Math.round(value * 1000D) / 10D;
    }

    private String observation(String theme) {
        if ("科技供应链".equals(theme)) {
            return "核验正式政策、限制范围与供应链实际传导。";
        }
        if ("能源资源".equals(theme)) {
            return "核验供给、航运、库存与主要产油国政策。";
        }
        if ("全球宏观".equals(theme)) {
            return "核验就业、通胀、流动性与央行正式表态。";
        }
        if ("中国相关".equals(theme)) {
            return "核验双边政策、官方数据与实际执行进度。";
        }
        return "核验相关政策、外交表态与可确认的后续事实。";
    }

    private List<GlobalExpectationItem> baseline() {
        List<GlobalExpectationItem> items = new ArrayList<GlobalExpectationItem>();
        items.add(item(1L, "科技供应链", "公共市场连接暂不可用：等待下一次 Polymarket 快照", "https://polymarket.com", 0, null, null, null, null));
        items.get(0).setStatus("BASELINE");
        items.get(0).setObservedAt("等待刷新");
        return items;
    }
}
