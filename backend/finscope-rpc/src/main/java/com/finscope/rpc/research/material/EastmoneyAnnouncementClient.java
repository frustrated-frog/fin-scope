package com.finscope.rpc.research.material;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.enums.research.ResearchMaterialType;
import com.finscope.common.enums.research.AnnouncementContentIssue;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.research.material.ResearchMaterial;
import com.finscope.rpc.acquisition.AcquisitionRequest;
import com.finscope.rpc.acquisition.AcquisitionRuntime;
import com.finscope.rpc.marketintel.ProviderContractException;
import com.finscope.rpc.research.ResearchSourceReader;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.text.Normalizer;
import java.util.regex.Pattern;

/** 全市场公告列表分页；实际公开时间与文件落款日期分别保存。 */
@Component
public class EastmoneyAnnouncementClient {
    private static final String PROVIDER_CODE = com.finscope.common.constant.ContentConstants.PROVIDER_EASTMONEY_ANNOUNCEMENT;
    private static final int PAGE_SIZE = 100;
    private static final Pattern SECURITY_CODE = Pattern.compile("(?:证券|股票)(?:代码|代号)[\\s\\p{Z}:()]*([0-9]+)");
    @Resource
    private AcquisitionRuntime runtime;
    @Resource
    private ObjectMapper json;
    @Resource
    private ResearchSourceReader sourceReader;
    @Resource
    private CompanyBusinessProfileClient profiles;

    public CompanyAnnouncementPage page(LocalDateTime since, LocalDateTime until, int page, CompanyAnnouncementEnrichmentContext context) {
        if (page < 1 || since == null || until == null || since.isAfter(until) || context == null) {
            throw new IllegalArgumentException("公告分页参数无效");
        }
        // 公告落款可晚于上架时间一天，查询包含下一日，最终以 display_time 过滤。
        String url = "https://np-anotice-stock.eastmoney.com/api/security/ann?sr=-1&ann_type=A&client_source=web"
                + "&page_size=" + PAGE_SIZE + "&page_index=" + page + "&begin_time=" + since.toLocalDate()
                + "&end_time=" + until.toLocalDate().plusDays(1);
        JsonNode data = read(url).path("data");
        JsonNode rows = data.path("list");
        if (!rows.isArray() || !data.path("total_hits").canConvertToInt()) {
            throw invalid("公司公告列表结构无效");
        }
        var materials = new ArrayList<ResearchMaterial>();
        for (JsonNode row : rows) {
            JsonNode issuer = row.path("codes").path(0);
            String code = issuer.path("stock_code").asText("");
            String id = row.path("art_code").asText("");
            String title = row.path("title").asText("").strip();
            LocalDateTime published = publishedAt(row.path("display_time").asText(""));
            if (!code.matches("[603489][0-9]{5}") || !id.matches("AN[0-9]+") || title.isBlank()
                    || published == null || published.isBefore(since) || published.isAfter(until)) {
                continue;
            }
            ResearchMaterial value = new ResearchMaterial();
            value.setMaterialType(ResearchMaterialType.ANNOUNCEMENT);
            value.setProviderCode(PROVIDER_CODE);
            value.setProviderFamily("EASTMONEY");
            value.setSourceTier("T1");
            value.setExternalId(id);
            value.setStockCode(code);
            value.setTitle(title);
            value.setPublishedAt(published);
            value.setUrl("https://data.eastmoney.com/notices/detail/" + code + "/" + id + ".html");
            value.setContent("公告日期：" + row.path("notice_date").asText("") + "\n" + title);
            context.register(value, market(code));
            materials.add(value);
        }
        return new CompanyAnnouncementPage(materials, (long) page * PAGE_SIZE < data.path("total_hits").asLong());
    }

    public void enrich(ResearchMaterial material, CompanyAnnouncementEnrichmentContext context) {
        if (material == null) {
            throw new IllegalArgumentException("公告材料不能为空");
        }
        String id = material.getExternalId();
        if (id == null || !id.matches("AN[0-9]+") || material.getStockCode() == null
                || !material.getStockCode().matches("[603489][0-9]{5}")) {
            throw new IllegalArgumentException("公告编号无效");
        }
        if (context == null || !context.matches(material) || !PROVIDER_CODE.equals(material.getProviderCode())) {
            throw contentError(AnnouncementContentIssue.LISTING_MISMATCH, "公告编号、证券代码或来源链接与本轮列表不一致", false);
        }
        // 直接读取公司公告 PDF，复用统一采集、体积限制与 PDF 提取能力。
        String pdfUrl = "https://pdf.dfcfw.com/pdf/H2_" + id + "_1.pdf";
        com.finscope.domain.research.ResearchSourceDocument document;
        try {
            document = sourceReader.read(pdfUrl);
        } catch (RuntimeException error) {
            throw new ProviderContractException(AnnouncementContentIssue.BODY_READ_FAILED.name(), "公告 PDF 下载或文本抽取失败", true, error);
        }
        if (document == null || document.getBody() == null || document.getBody().isBlank()) {
            throw contentError(AnnouncementContentIssue.EMPTY_BODY, "公告 PDF 正文为空", true);
        }
        if (!"PDF".equals(document.getContentType()) || !"FETCHED".equals(document.getFetchStatus())
                || !pdfUrl.equals(document.getFinalUrl())) {
            throw contentError(AnnouncementContentIssue.UNEXPECTED_DOCUMENT, "公告原文类型、抓取状态或公告链接不符", true);
        }
        String body = document.getBody();
        verifyIssuer(material, context, body);
        material.setContent(material.getContent() + "\n公告原文：\n" + body);
    }

    private void verifyIssuer(ResearchMaterial material, CompanyAnnouncementEnrichmentContext context, String body) {
        String canonical = Normalizer.normalize(body, Normalizer.Form.NFKC).replaceAll("[\\u200B\\uFEFF]+", "");
        String normalized = normalize(body);
        // 只把抬头明确标注的证券/股票代码当作发行人身份，邮编、电话号码、正文引用不作替代。
        var codes = SECURITY_CODE.matcher(canonical.substring(0, Math.min(200, canonical.length())));
        boolean declared = false;
        while (codes.find()) {
            declared = true;
            if (material.getStockCode().equals(codes.group(1))) {
                return;
            }
        }
        if (declared) {
            throw contentError(AnnouncementContentIssue.ISSUER_MISMATCH, "公告抬头证券代码与列表关联证券不符", false);
        }
        String name = context.issuerName(material.getStockCode());
        if (name == null) {
            var issuer = new Instrument();
            issuer.setCode(material.getStockCode());
            issuer.setMarket(context.market(material.getExternalId()));
            try {
                name = profiles.fetch(issuer).getCompanyName();
            } catch (RuntimeException error) {
                throw new ProviderContractException(AnnouncementContentIssue.ISSUER_LOOKUP_FAILED.name(), "公告发行人公司全称查询失败", true, error);
            }
            String fullName = name == null ? "" : normalize(name);
            if (fullName.length() < 8 || !(fullName.endsWith("有限公司") || fullName.endsWith("股份公司"))) {
                throw contentError(AnnouncementContentIssue.ISSUER_UNCONFIRMED, "公司资料未提供可核验的公司全称", false);
            }
            context.rememberIssuer(material.getStockCode(), name);
        }
        String heading = normalized.substring(0, Math.min(800, normalized.length()));
        if (!heading.contains(normalize(name))) {
            throw contentError(AnnouncementContentIssue.ISSUER_UNCONFIRMED, "公告未标注证券代码，且抬头未匹配关联公司的全称", false);
        }
    }

    private String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).replaceAll("[\\s\\p{Z}\\u200B\\uFEFF]+", "");
    }

    private String market(String code) {
        if (code.startsWith("4") || code.startsWith("8") || code.startsWith("92")) {
            return "BJ";
        }
        if (code.startsWith("6") || code.startsWith("900")) {
            return "SH";
        }
        return "SZ";
    }

    private ProviderContractException contentError(AnnouncementContentIssue issue, String message, boolean retryable) {
        return new ProviderContractException(issue.name(), message, retryable);
    }

    private JsonNode read(String url) {
        var request = AcquisitionRequest.get(URI.create(url)).purpose(PROVIDER_CODE)
                .header("Referer", "https://data.eastmoney.com/")
                .connectTimeoutMs(3000).readTimeoutMs(5000).deadlineMs(7000)
                .maxResponseBytes(4 * 1024 * 1024).maxRetries(0).build();
        var response = runtime.fetch(request);
        try {
            JsonNode root = json.readTree(response.getBodyText());
            if (root == null || root.path("success").asInt() != 1) {
                throw invalid("公司公告来源返回失败");
            }
            return root;
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            throw invalid("公司公告响应无法解析");
        }
    }

    private LocalDateTime publishedAt(String value) {
        if (value.length() < 19) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.substring(0, 19), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (DateTimeParseException error) {
            return null;
        }
    }

    private ProviderContractException invalid(String message) {
        return new ProviderContractException("INVALID_RESPONSE", message, false);
    }
}
