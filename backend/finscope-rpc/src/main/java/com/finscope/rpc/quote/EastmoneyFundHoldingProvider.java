package com.finscope.rpc.quote;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.instrument.FundHoldingDisclosure;
import com.finscope.domain.instrument.FundStockHolding;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 天天基金 F10 最近一期股票投资明细 Provider。 */
@Component
public class EastmoneyFundHoldingProvider implements FundHoldingProvider {
    private static final String ENDPOINT =
            "https://fundf10.eastmoney.com/FundArchivesDatas.aspx";
    private static final int TIMEOUT_MS = 2500;
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    private static final Pattern FUND_CODE = Pattern.compile("^\\d{6}$");
    private static final Pattern STOCK_CODE = Pattern.compile("^\\d{6}$");
    private static final Pattern DISCLOSURE_DATE =
            Pattern.compile("截止至[：:]\\s*(\\d{4}-\\d{2}-\\d{2})");
    private static final Pattern NO_DISCLOSURE_YEARS =
            Pattern.compile("arryear\\s*:\\s*\\[\\s*\\]");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FundDataRequester requester;
    private final Clock clock;

    @Autowired
    public EastmoneyFundHoldingProvider(QuoteHttpTransport transport) {
        this(url -> transport.get("EASTMONEY_FUND_HOLDINGS", URI.create(url),
                TIMEOUT_MS, MAX_RESPONSE_BYTES,
                Collections.singletonMap("Referer", "https://fundf10.eastmoney.com/"),
                StandardCharsets.UTF_8), Clock.systemDefaultZone());
    }

    EastmoneyFundHoldingProvider(FundDataRequester requester) {
        this(requester, Clock.systemDefaultZone());
    }

    EastmoneyFundHoldingProvider(FundDataRequester requester, Clock clock) {
        this.requester = requester;
        this.clock = clock;
    }

    @Override
    public FundHoldingDisclosure fetch(String fundCode) {
        String normalizedCode = normalizeCode(fundCode);
        try {
            return parse(normalizedCode, requester.get(buildUrl(normalizedCode)));
        } catch (ProviderContractException error) {
            throw error;
        } catch (Exception error) {
            throw drift("基金持仓请求或解析失败", error);
        }
    }

    private FundHoldingDisclosure parse(String fundCode, String payload) {
        String content = embeddedContent(payload);
        if (content.trim().isEmpty()) {
            if (!NO_DISCLOSURE_YEARS.matcher(payload).find()) {
                throw drift("基金持仓内容为空且披露年度状态不明确");
            }
            return new FundHoldingDisclosure(fundCode, "", null,
                    LocalDateTime.now(clock), Collections.emptyList());
        }
        Document document = Jsoup.parse(content);
        Element header = document.selectFirst(".boxitem h4.t");
        Element table = document.selectFirst("table.tzxq");
        if (header == null || table == null) {
            throw drift("基金持仓响应缺少标题或持仓表格");
        }
        Element fundLink = header.selectFirst("label.left a");
        String fundName = fundLink == null ? "" : fundLink.text().trim();
        Matcher dateMatcher = DISCLOSURE_DATE.matcher(header.text());
        if (fundName.isEmpty() || !dateMatcher.find()) {
            throw drift("基金持仓响应缺少基金名称或披露日期");
        }
        LocalDate disclosureDate;
        try {
            disclosureDate = LocalDate.parse(dateMatcher.group(1));
        } catch (RuntimeException error) {
            throw drift("基金持仓披露日期无效", error);
        }

        List<FundStockHolding> holdings = new ArrayList<FundStockHolding>();
        for (Element row : table.select("tbody tr")) {
            Elements cells = row.select("td");
            if (cells.isEmpty()) {
                continue;
            }
            if (cells.size() < 9) {
                throw drift("基金持仓表格列数不足");
            }
            holdings.add(parseHolding(cells));
        }
        return new FundHoldingDisclosure(fundCode, fundName, disclosureDate,
                LocalDateTime.now(clock), holdings);
    }

    private FundStockHolding parseHolding(Elements cells) {
        int rank = requiredPositiveInt(cells.get(0).text(), "持仓排名");
        String stockCode = cells.get(1).text().trim();
        String stockName = cells.get(2).text().trim();
        if (!STOCK_CODE.matcher(stockCode).matches() || stockName.isEmpty()) {
            throw drift("基金持仓股票代码或名称无效");
        }
        double weightPct = requiredNonNegativeNumber(
                cells.get(6).text().replace("%", ""), "持仓权重");
        Double shares = optionalNonNegativeNumber(cells.get(7).text(), "持股数");
        Double marketValue = optionalNonNegativeNumber(cells.get(8).text(), "持仓市值");
        return new FundStockHolding(rank, stockCode, stockName, weightPct, shares, marketValue);
    }

    private String embeddedContent(String payload) {
        if (payload == null) {
            throw drift("基金持仓响应为空");
        }
        int contentIndex = payload.indexOf("content");
        int colonIndex = contentIndex < 0 ? -1 : payload.indexOf(':', contentIndex);
        int quoteIndex = colonIndex < 0 ? -1 : payload.indexOf('"', colonIndex);
        if (quoteIndex < 0) {
            throw drift("基金持仓响应缺少 content");
        }
        try (JsonParser parser = objectMapper.getFactory().createParser(payload.substring(quoteIndex))) {
            if (parser.nextToken() != JsonToken.VALUE_STRING) {
                throw drift("基金持仓 content 不是字符串");
            }
            return parser.getValueAsString();
        } catch (ProviderContractException error) {
            throw error;
        } catch (Exception error) {
            throw drift("基金持仓 content 解码失败", error);
        }
    }

    private String buildUrl(String fundCode) {
        return ENDPOINT + "?type=jjcc&code=" + fundCode
                + "&topline=10&year=&month=";
    }

    private String normalizeCode(String fundCode) {
        String normalized = fundCode == null ? "" : fundCode.trim();
        if (!FUND_CODE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("fund code must contain exactly six digits");
        }
        return normalized;
    }

    private int requiredPositiveInt(String text, String label) {
        try {
            int value = Integer.parseInt(cleanNumber(text));
            if (value <= 0) {
                throw drift(label + "必须为正整数");
            }
            return value;
        } catch (ProviderContractException error) {
            throw error;
        } catch (RuntimeException error) {
            throw drift(label + "无效", error);
        }
    }

    private double requiredNonNegativeNumber(String text, String label) {
        Double value = parseNumber(text, label, false);
        if (value == null) {
            throw drift(label + "缺失");
        }
        return value;
    }

    private Double optionalNonNegativeNumber(String text, String label) {
        return parseNumber(text, label, true);
    }

    private Double parseNumber(String text, String label, boolean optional) {
        String normalized = cleanNumber(text);
        if (normalized.isEmpty() || "--".equals(normalized)) {
            if (optional) {
                return null;
            }
            throw drift(label + "缺失");
        }
        try {
            double value = Double.parseDouble(normalized);
            if (!Double.isFinite(value) || value < 0.0d) {
                throw drift(label + "超出有效范围");
            }
            return value;
        } catch (ProviderContractException error) {
            throw error;
        } catch (RuntimeException error) {
            throw drift(label + "无效", error);
        }
    }

    private String cleanNumber(String text) {
        return text == null ? "" : text.trim().replace(",", "");
    }

    private ProviderContractException drift(String message) {
        return new ProviderContractException("FUND_HOLDING_CONTRACT_DRIFT", message, true);
    }

    private ProviderContractException drift(String message, Throwable cause) {
        return new ProviderContractException(
                "FUND_HOLDING_CONTRACT_DRIFT", message, true, cause);
    }
}
