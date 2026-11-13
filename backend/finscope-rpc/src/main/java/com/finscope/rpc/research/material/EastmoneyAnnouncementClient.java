package com.finscope.rpc.research.material;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.enums.research.ResearchMaterialType;
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

/** 全市场公告列表分页；实际公开时间与文件落款日期分别保存。 */
@Component
public class EastmoneyAnnouncementClient {
    private static final String PROVIDER_CODE = com.finscope.common.constant.ContentConstants.PROVIDER_EASTMONEY_ANNOUNCEMENT;
    private static final int PAGE_SIZE = 100;
    @Resource
    private AcquisitionRuntime runtime;
    @Resource
    private ObjectMapper json;
    @Resource
    private ResearchSourceReader sourceReader;

    public CompanyAnnouncementPage page(LocalDateTime since, LocalDateTime until, int page) {
        if (page < 1 || since == null || until == null || since.isAfter(until)) {
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
            materials.add(value);
        }
        return new CompanyAnnouncementPage(materials, (long) page * PAGE_SIZE < data.path("total_hits").asLong());
    }

    public void enrich(ResearchMaterial material) {
        String id = material.getExternalId();
        if (id == null || !id.matches("AN[0-9]+") || material.getStockCode() == null
                || !material.getStockCode().matches("[603489][0-9]{5}")) {
            throw new IllegalArgumentException("公告编号无效");
        }
        // 直接读取公司公告 PDF，复用统一采集、体积限制与 PDF 提取能力。
        var document = sourceReader.read("https://pdf.dfcfw.com/pdf/H2_" + id + "_1.pdf");
        String body = document.getBody();
        if (body == null || body.isBlank() || !body.replaceAll("\\s", "").contains(material.getStockCode())) {
            throw invalid("公司公告原文缺失或证券代码不符");
        }
        material.setContent(material.getContent() + "\n公告原文：\n" + body);
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
