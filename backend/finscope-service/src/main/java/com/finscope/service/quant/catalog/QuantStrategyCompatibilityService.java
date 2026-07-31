package com.finscope.service.quant.catalog;

import com.finscope.domain.quant.catalog.QuantStrategyCatalogEntry;
import com.finscope.domain.quant.catalog.QuantStrategyCompatibility;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;

@Service
public class QuantStrategyCompatibilityService {
    public QuantStrategyCompatibility evaluate(QuantStrategyCatalogEntry entry) {
        String title = entry == null || entry.getTitle() == null ? "" : entry.getTitle();
        if (contains(title, "多空", "配对", "套利", "期权", "期货", "日内", "跨资产", "杠杆", "做空")) {
            return result("UNSUPPORTED", Collections.<String>emptyList(), Collections.<String>emptyList(),
                    "当前引擎只支持 A 股多头 Top-N 等权策略，不支持多空、衍生品、配对或日内执行。\n");
        }
        if (contains(title, "账面价值", "Book-to-Market", "book-to-market")) {
            return adaptable("BP", "使用披露时点账面市值比 BP 形成 A 股多头等权版本。");
        }
        if (contains(title, "短期反转")) {
            return adaptable("REVERSAL_5D", "使用 5 日反转因子形成 A 股多头等权版本。");
        }
        if (contains(title, "低波动")) {
            return adaptable("VOLATILITY_20D", "以现有 20日低波因子近似原策略的多年周频波动口径，结果不可视为原策略复现。");
        }
        if (contains(title, "动量", "势头", "动力")) {
            return adaptable("MOMENTUM_60D", "以现有 60日动量近似原策略；未实现跳过最近一个月的 12-1 动量口径。");
        }
        if (contains(title, "ROA")) return missing("ROA", "需要新增披露时点 ROA 因子后再验证。");
        if (contains(title, "资产增长")) return missing("ASSET_GROWTH", "需要新增资产增长因子后再验证。");
        if (contains(title, "应计")) return missing("ACCRUAL", "需要新增应计质量因子后再验证。");
        if (contains(title, "52周", "52 周")) return missing("HIGH_52W", "需要新增 52 周高点距离因子后再验证。");
        return result("NEEDS_FACTOR", Collections.<String>emptyList(), Collections.<String>emptyList(),
                "尚无确定性因子映射，需要人工拆解论文口径、数据可得性和执行边界。");
    }

    private QuantStrategyCompatibility adaptable(String factor, String note) {
        return result("ADAPTABLE", Arrays.asList(factor), Collections.<String>emptyList(), note);
    }

    private QuantStrategyCompatibility missing(String factor, String note) {
        return result("NEEDS_FACTOR", Collections.<String>emptyList(), Arrays.asList(factor), note);
    }

    private QuantStrategyCompatibility result(String status, java.util.List<String> mapped,
                                               java.util.List<String> missing, String note) {
        QuantStrategyCompatibility value = new QuantStrategyCompatibility();
        value.setStatus(status);
        value.setMappedFactors(mapped);
        value.setMissingFactors(missing);
        value.setAdaptationNote(note.trim());
        return value;
    }

    private boolean contains(String value, String... needles) {
        for (String needle : needles) if (value.contains(needle)) return true;
        return false;
    }
}
