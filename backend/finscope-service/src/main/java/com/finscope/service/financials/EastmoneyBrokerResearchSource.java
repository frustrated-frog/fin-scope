package com.finscope.service.financials;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.domain.financials.BrokerResearchCandidate;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.finscope.common.exception.BizErrorCode;

@Component
public class EastmoneyBrokerResearchSource implements BrokerResearchSource {
    static final String SOURCE_CODE = "EASTMONEY";
    private static final String CATALOG_HOST = "reportapi.eastmoney.com";
    private static final String PDF_HOST = "pdf.dfcfw.com";
    private static final int MAX_CATALOG_BYTES = 2 * 1024 * 1024;
    private static final int MAX_PDF_BYTES = 30 * 1024 * 1024;
    private final BrokerResearchHttpClient http;
    private final ObjectMapper json;

    public EastmoneyBrokerResearchSource(BrokerResearchHttpClient http, ObjectMapper json) {
        this.http = http;
        this.json = json;
    }

    @Override
    public String sourceCode() {
        return SOURCE_CODE;
    }

    @Override
    public List<BrokerResearchCandidate> list(String stockCode, LocalDate from,
                                               LocalDate to, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        String normalizedCode = normalizeStockCode(stockCode);
        URI uri = URI.create("https://" + CATALOG_HOST + "/report/list?"
                + "industryCode=*&pageSize=" + safeLimit
                + "&industry=*&rating=*&ratingChange=*"
                + "&beginTime=" + encode(from.toString())
                + "&endTime=" + encode(to.toString())
                + "&pageNo=1&fields=&qType=0&orgCode=&code=" + normalizedCode + "&rcode=");
        try {
            BrokerResearchHttpClient.Response response = http.get(
                    uri, catalogHeaders(), MAX_CATALOG_BYTES);
            requireSuccessful(response, CATALOG_HOST, false);
            JsonNode root = json.readTree(response.getBody());
            JsonNode data = root.path("data");
            if (!data.isArray()) {
                throw externalInvalid("公开研报目录格式异常");
            }
            List<BrokerResearchCandidate> result = new ArrayList<BrokerResearchCandidate>();
            for (JsonNode item : data) {
                if (result.size() >= safeLimit) break;
                BrokerResearchCandidate candidate = map(item, normalizedCode);
                if (candidate != null) result.add(candidate);
            }
            return result;
        } catch (BusinessException error) {
            throw error;
        } catch (Exception error) {
            throw new BusinessException(BizErrorCode.REPORT_DIRECTORY_UNAVAILABLE, error);
        }
    }

    @Override
    public byte[] download(BrokerResearchCandidate candidate) {
        if (candidate == null || !SOURCE_CODE.equals(candidate.getSourceCode())) {
            throw new BusinessException(BizErrorCode.REPORT_SOURCE_MISMATCH);
        }
        URI uri = requireAllowedUri(candidate.getSourceUrl(), PDF_HOST);
        try {
            BrokerResearchHttpClient.Response response = http.get(
                    uri, pdfHeaders(), MAX_PDF_BYTES);
            requireSuccessful(response, PDF_HOST, true);
            byte[] body = response.getBody();
            String type = response.getContentType() == null
                    ? "" : response.getContentType().toLowerCase(Locale.ROOT);
            if (!type.contains("application/pdf") || body.length < 5
                    || body[0] != '%' || body[1] != 'P' || body[2] != 'D'
                    || body[3] != 'F' || body[4] != '-') {
                throw externalInvalid("公开研报原文不是有效 PDF");
            }
            return body;
        } catch (BusinessException error) {
            throw error;
        } catch (Exception error) {
            throw new BusinessException(BizErrorCode.REPORT_DOWNLOAD_FAILED, error);
        }
    }

    private BrokerResearchCandidate map(JsonNode item, String expectedStockCode) {
        String externalId = text(item, "infoCode");
        String stockCode = text(item, "stockCode");
        String title = text(item, "title");
        if (externalId == null || title == null || !expectedStockCode.equals(stockCode)) {
            return null;
        }
        BrokerResearchCandidate value = new BrokerResearchCandidate();
        value.setSourceCode(SOURCE_CODE);
        value.setExternalId(externalId);
        value.setSourceUrl("https://" + PDF_HOST + "/pdf/H3_" + externalId + "_1.pdf");
        value.setStockCode(stockCode);
        value.setTitle(title);
        value.setInstitution(text(item, "orgSName"));
        value.setAnalyst(text(item, "researcher"));
        value.setRating(text(item, "emRatingName"));
        value.setReportType("COMPANY_RESEARCH");
        String date = text(item, "publishDate");
        if (date != null && date.length() >= 10) {
            try {
                value.setPublishedDate(LocalDate.parse(date.substring(0, 10)));
            } catch (RuntimeException ignored) {
                // A missing date must not discard an otherwise usable public report.
            }
        }
        if (item.path("attachPages").canConvertToInt()) {
            value.setPageCount(item.path("attachPages").intValue());
        }
        value.setAvailability("AVAILABLE");
        return value;
    }

    private void requireSuccessful(BrokerResearchHttpClient.Response response,
                                   String expectedHost, boolean pdf) {
        if (response.getStatus() < 200 || response.getStatus() >= 300) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                    pdf ? "公开研报原文暂时不可用" : "公开研报目录暂时不可用");
        }
        URI finalUri = response.getFinalUri();
        if (finalUri == null || !"https".equalsIgnoreCase(finalUri.getScheme())
                || !expectedHost.equalsIgnoreCase(finalUri.getHost())) {
            throw externalInvalid("公开研报来源跳转到了非允许地址");
        }
    }

    private URI requireAllowedUri(String value, String expectedHost) {
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || !expectedHost.equalsIgnoreCase(uri.getHost())) {
                throw new IllegalArgumentException();
            }
            return uri;
        } catch (RuntimeException error) {
            throw new BusinessException(BizErrorCode.REPORT_URL_NOT_ALLOWED);
        }
    }

    private String normalizeStockCode(String value) {
        if (value == null) {
            throw new BusinessException(BizErrorCode.STOCK_CODE_REQUIRED);
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?<!\\d)(\\d{6})(?!\\d)").matcher(value);
        if (!matcher.find()) {
            throw new BusinessException(BizErrorCode.AUTO_REPORT_A_SHARE_ONLY);
        }
        return matcher.group(1);
    }

    private String text(JsonNode item, String field) {
        String value = item.path(field).asText("").trim();
        return value.isEmpty() ? null : value;
    }

    private String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private Map<String, String> catalogHeaders() {
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Accept", "application/json");
        headers.put("Referer", "https://data.eastmoney.com/");
        headers.put("User-Agent", "Mozilla/5.0 (compatible; FinScope/1.0; local research tool)");
        return headers;
    }

    private Map<String, String> pdfHeaders() {
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Accept", "application/pdf");
        headers.put("Referer", "https://data.eastmoney.com/");
        headers.put("User-Agent", "Mozilla/5.0 (compatible; FinScope/1.0; local research tool)");
        return Collections.unmodifiableMap(headers);
    }

    private BusinessException externalInvalid(String message) {
        return new BusinessException(ErrorCode.EXTERNAL_RESPONSE_INVALID, message);
    }
}
