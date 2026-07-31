package com.finscope.rpc.quant.catalog;

import com.finscope.domain.quant.catalog.QuantStrategyCatalogEntry;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class AwesomeTradingMarkdownParser {
    private static final String REPOSITORY = "https://github.com/paperswithbacktest/awesome-systematic-trading/";
    private static final Pattern LINK = Pattern.compile("\\[[^\\]]*]\\(([^)]+)\\)");

    public List<QuantStrategyCatalogEntry> parse(String markdown) {
        List<QuantStrategyCatalogEntry> values = new ArrayList<QuantStrategyCatalogEntry>();
        boolean equities = false;
        String[] lines = (markdown == null ? "" : markdown).split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) {
                if ("## 股票".equals(trimmed)) {
                    equities = true;
                    continue;
                }
                if (equities) break;
            }
            if (!equities || !trimmed.startsWith("|") || trimmed.contains("---") || trimmed.contains("夏普比率")) {
                continue;
            }
            List<String> columns = columns(trimmed);
            if (columns.size() != 6 || clean(columns.get(0)).isEmpty()) continue;
            values.add(entry(columns));
        }
        if (values.isEmpty()) throw new IllegalArgumentException("上游股票策略目录为空或格式已变化");
        return values;
    }

    private QuantStrategyCatalogEntry entry(List<String> columns) {
        QuantStrategyCatalogEntry value = new QuantStrategyCatalogEntry();
        value.setTitle(clean(columns.get(0)));
        value.setReportedSharpe(number(columns.get(1), false));
        value.setReportedVolatility(number(columns.get(2), true));
        value.setRebalanceCadence(clean(columns.get(3)));
        value.setImplementationUrl(link(columns.get(4), true));
        value.setPaperUrl(link(columns.get(5), false));
        value.setExternalKey(value.getImplementationUrl() == null
                ? "title:" + value.getTitle().toLowerCase(Locale.ROOT)
                : value.getImplementationUrl());
        return value;
    }

    private List<String> columns(String line) {
        String body = line.substring(1, line.endsWith("|") ? line.length() - 1 : line.length());
        String[] split = body.split("\\|", -1);
        List<String> result = new ArrayList<String>();
        for (String item : split) result.add(item.trim());
        return result;
    }

    private String clean(String value) {
        return value == null ? "" : value.replace("`", "").trim();
    }

    private Double number(String raw, boolean percent) {
        String value = clean(raw).replace("%", "");
        if (value.isEmpty() || "N/A".equalsIgnoreCase(value)) return null;
        double parsed = Double.parseDouble(value);
        return percent ? parsed / 100d : parsed;
    }

    private String link(String markdown, boolean implementation) {
        Matcher matcher = LINK.matcher(markdown == null ? "" : markdown);
        if (!matcher.find()) return null;
        String value = matcher.group(1).trim();
        if (!implementation || value.startsWith("http://") || value.startsWith("https://")) return value;
        URI resolved = URI.create(REPOSITORY).resolve(value);
        String path = resolved.getPath();
        if (path != null && path.startsWith("/paperswithbacktest/awesome-systematic-trading/static/")) {
            return REPOSITORY + "blob/main/" + path.substring("/paperswithbacktest/awesome-systematic-trading/".length());
        }
        return resolved.toString();
    }
}
