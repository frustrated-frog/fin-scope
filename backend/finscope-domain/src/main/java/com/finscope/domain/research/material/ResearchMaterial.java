package com.finscope.domain.research.material;

import com.finscope.common.enums.research.ResearchMaterialType;

import java.time.LocalDateTime;

/** 可被研究 Agent 引用的标准化外部资料。 */
public class ResearchMaterial {
    private ResearchMaterialType materialType;
    private String externalId;
    private String stockCode;
    private String title;
    private String content;
    private String url;
    private LocalDateTime publishedAt;
    private String providerCode;
    private String providerFamily;
    private String sourceTier;

    public ResearchMaterialType getMaterialType() { return materialType; }
    public void setMaterialType(ResearchMaterialType materialType) { this.materialType = materialType; }
    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }
    public String getStockCode() { return stockCode; }
    public void setStockCode(String stockCode) { this.stockCode = stockCode; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
    public String getProviderCode() { return providerCode; }
    public void setProviderCode(String providerCode) { this.providerCode = providerCode; }
    public String getProviderFamily() { return providerFamily; }
    public void setProviderFamily(String providerFamily) { this.providerFamily = providerFamily; }
    public String getSourceTier() { return sourceTier; }
    public void setSourceTier(String sourceTier) { this.sourceTier = sourceTier; }
}
