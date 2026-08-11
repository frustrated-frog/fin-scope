package com.finscope.domain.industrychain;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 随图谱修订发布的产业研究内容聚合。 */
@Data
public class IndustryChainResearchContent {
    private Overview overview = new Overview();
    private List<StageProfile> stageProfiles = new ArrayList<StageProfile>();
    private List<CompanyProfile> companyProfiles = new ArrayList<CompanyProfile>();
    private List<NodeProfile> nodeProfiles = new ArrayList<NodeProfile>();

    @Data
    public static class Overview {
        private String lifecycle;
        private String prosperity;
        private String supplyDemand;
        private String cycleType;
        private List<String> demandDrivers = new ArrayList<String>();
        private List<String> supplyDrivers = new ArrayList<String>();
        private List<String> keyVariables = new ArrayList<String>();
        private List<String> bottlenecks = new ArrayList<String>();
        private List<String> overcapacityRisks = new ArrayList<String>();
        private List<String> trendTags = new ArrayList<String>();
    }

    @Data
    public static class StageProfile {
        private String nodeKey;
        private String roleSummary;
        private String businessModel;
        private String costStructure;
        private String valueCapture;
        private String bottleneck;
        private String prosperity;
        private String supplyDemand;
        private String lifecycle;
        private List<String> profitDrivers = new ArrayList<String>();
        private List<String> barriers = new ArrayList<String>();
        private List<String> coreMetrics = new ArrayList<String>();
        private List<String> risks = new ArrayList<String>();
        private List<String> keyVariables = new ArrayList<String>();
        private List<String> trendTags = new ArrayList<String>();
    }

    @Data
    public static class CompanyProfile {
        private String nodeKey;
        private String industryPosition;
        private List<String> coreProducts = new ArrayList<String>();
        private List<String> downstreamMarkets = new ArrayList<String>();
        private List<String> competitiveAdvantages = new ArrayList<String>();
        private List<String> keyVariables = new ArrayList<String>();
    }

    @Data
    public static class NodeProfile {
        private String nodeKey;
        private String definition;
        private String function;
        private List<String> inputs = new ArrayList<String>();
        private List<String> outputs = new ArrayList<String>();
        private List<String> costDrivers = new ArrayList<String>();
        private List<String> valueDrivers = new ArrayList<String>();
        private List<String> barriers = new ArrayList<String>();
        private List<String> coreMetrics = new ArrayList<String>();
        private List<String> risks = new ArrayList<String>();
        private String maturity;
        private String valueLevel;
        private String bottleneckLevel;
        private String localizationLevel;
    }
}
