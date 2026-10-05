package com.finscope.service.financials;

import com.finscope.common.enums.financials.FinancialInterpretationChapter;
import com.finscope.domain.financials.FinancialEvidence;
import com.finscope.domain.financials.FinancialInterpretation;
import com.finscope.domain.financials.FinancialInterpretationSection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 无外部依赖的报告规则：确定章节材料、教学说明和降级内容。 */
public final class FinancialInterpretationReportFramework {
    public static final String VERSION = "financial-interpret-v5";

    private FinancialInterpretationReportFramework() {
    }

    public static List<FinancialInterpretationSection> plan(List<FinancialEvidence> evidence) {
        List<FinancialInterpretationSection> sections = new ArrayList<>();
        for (FinancialInterpretationChapter code : FinancialInterpretationChapter.values()) {
            FinancialInterpretationSection section = new FinancialInterpretationSection();
            section.setCode(code);
            section.setTitle(code.getLabel());
            section.setLearningExplanation(teaching(code));
            section.setCommonMisreading(misreading(code));
            for (FinancialEvidence item : evidence) {
                if (relevant(code, item) && !"DATA_GAP".equals(item.getType())) {
                    section.getRefs().add(item.getId());
                }
            }
            if (section.getRefs().isEmpty()) {
                section.setAssessment("INSUFFICIENT_EVIDENCE");
                section.getLimitations().add("当前材料不足以独立完成本章判断，需要补充原文或可比数据。");
            }
            if (code == FinancialInterpretationChapter.GROWTH_DRIVERS) {
                section.getLimitations().add("没有销量、价格、产品和地区拆分，增长来源只能提出待验证假设。");
            }
            if (code == FinancialInterpretationChapter.EARNINGS_QUALITY) {
                section.getLimitations().add("没有非经常性损益和重要会计附注，不能完整确认利润的可持续性。");
            }
            if (code == FinancialInterpretationChapter.CAPITAL_ALLOCATION) {
                section.getLimitations().add("缺少完整资本投入和分红回购资料，不计算未经核对的资本回报率。");
            }
            sections.add(section);
        }
        return sections;
    }

    public static void decorate(FinancialInterpretation.Result result, FinancialEvidencePacket packet) {
        if (!VERSION.equals(packet.getPromptVersion())) {
            return;
        }
        result.setReportVersion(VERSION);
        result.setReportScope(packet.getReportScope());
        List<FinancialInterpretationSection> ordered = new ArrayList<>();
        for (FinancialInterpretationSection planned : plan(packet.getModelEvidence())) {
            for (FinancialInterpretationSection section : result.getSections()) {
                if (section.getCode() == planned.getCode()) {
                    section.setTitle(planned.getTitle());
                    section.setLearningExplanation(planned.getLearningExplanation());
                    section.setCommonMisreading(planned.getCommonMisreading());
                    for (String limitation : planned.getLimitations()) {
                        if (!section.getLimitations().contains(limitation)) {
                            section.getLimitations().add(limitation);
                        }
                    }
                    ordered.add(section);
                }
            }
        }
        result.setSections(ordered);
    }

    public static List<FinancialInterpretationSection> fallback(FinancialEvidencePacket packet) {
        List<FinancialInterpretationSection> sections = plan(packet.getModelEvidence());
        for (FinancialInterpretationSection section : sections) {
            List<String> relevantRefs = new ArrayList<>(section.getRefs());
            section.getRefs().clear();
            section.setAssessment("INSUFFICIENT_EVIDENCE");
            section.setConfidence("LOW");
            section.setSummary("当前仅整理可核查事实，尚未形成完整的经营解释。请结合本章材料和后续验证继续阅读。");
            for (String id : relevantRefs) {
                FinancialEvidence item = packet.getEvidenceIndex().get(id);
                if (item != null && section.getFacts().size() < 4) {
                    FinancialInterpretation.Claim claim = new FinancialInterpretation.Claim();
                    claim.setClaim(item.getLabel() + (item.getValue() == null ? "" : "：" + item.getValue()
                            + (item.getUnit() == null ? "" : " " + item.getUnit())));
                    claim.setClaimType("FACT");
                    claim.getRefs().add(id);
                    section.getFacts().add(claim);
                    section.getRefs().add(id);
                }
            }
            section.getLimitations().add("模型解读不可用，原因解释、反证分析和观察清单尚待生成。");
        }
        return sections;
    }

    public static boolean relevant(FinancialInterpretationChapter code, FinancialEvidence item) {
        String id = item.getId() == null ? "" : item.getId();
        if (code == FinancialInterpretationChapter.BUSINESS_MODEL
                || code == FinancialInterpretationChapter.DISCLOSURE_RISKS) {
            return false;
        }
        List<String> concepts = switch (code) {
            case PERFORMANCE_TRENDS, GROWTH_DRIVERS -> Arrays.asList("REVENUE", "PROFIT", "CONTRACT_LIAB");
            case PROFITABILITY -> Arrays.asList("REVENUE", "COST", "MARGIN", "EXPENSE", "PROFIT");
            case EARNINGS_QUALITY -> Arrays.asList("PROFIT", "CASH", "IMPAIRMENT", "INVESTMENT_INCOME", "NON_OPERATING");
            case CASH_WORKING_CAPITAL -> Arrays.asList("CASH", "RECEIVABLE", "INVENTORY", "PAYABLE", "CONTRACT_LIAB", "CAPITAL_EXPENDITURE");
            case ASSET_QUALITY -> Arrays.asList("ASSET", "RECEIVABLE", "INVENTORY", "IMPAIRMENT", "GOODWILL", "CONSTRUCTION");
            case SOLVENCY_FINANCING -> Arrays.asList("CASH", "DEBT", "BORROW", "BONDS", "LIAB", "CURRENT_RATIO", "QUICK_RATIO", "FINANCE_EXPENSE");
            case CAPITAL_ALLOCATION -> Arrays.asList("CAPITAL_EXPENDITURE", "FIXED_ASSET", "CONSTRUCTION", "EQUITY", "FREE_CASH", "INVESTING_CASH");
            default -> List.of();
        };
        for (String concept : concepts) {
            if (id.contains(concept)) {
                return true;
            }
        }
        return false;
    }

    private static String teaching(FinancialInterpretationChapter code) {
        return switch (code) {
            case BUSINESS_MODEL -> "先理解收入来自什么产品、谁付钱、何时确认收入和收款，再看数字。商业模式决定应该重点观察哪些财务科目；仅有三张表不能确认客户结构或竞争优势。";
            case PERFORMANCE_TRENDS -> "同比与上年同期比较，环比与相邻季度比较。累计报表覆盖年初至报告期末，单季度反映当季表现，两者不能直接混比。连续趋势需要同口径的多个时点，还要考虑季节性。";
            case GROWTH_DRIVERS -> "收入变化可能来自销量、售价、产品组合、并购或汇率。净利润也受成本、费用及一次性损益影响。基期亏损或接近零时，增速不能直接代表持续增长能力。";
            case PROFITABILITY -> "毛利率观察收入扣除营业成本后的盈利空间；期间费用影响毛利能否转化为利润。这里的净利率按归母净利润优先口径计算，需核对少数股东损益，不能与其他口径直接比较。";
            case EARNINGS_QUALITY -> "利润是会计确认的经营结果，现金流是实际收付，两者时间可以不同。核查利润质量需要结合回款、减值、非经常性损益和会计政策，单期现金不足不直接证明利润失真。";
            case CASH_WORKING_CAPITAL -> "应收账款是尚未收回的销售款，存货是尚未销售的商品或生产投入，应付是尚未支付的采购款。自由现金流在本系统按经营现金流减资本开支计算，未覆盖的资本投入需另查附注。";
            case ASSET_QUALITY -> "资产质量关注资产能否收回、变现或持续创造收益。存货需看周转与减值，应收需看回款与账龄，商誉和在建工程需结合附注；余额本身不能确认资产已经受损。";
            case SOLVENCY_FINANCING -> "资产负债率描述负债占资产的比例；流动比率比较流动资产与流动负债，速动比率进一步扣除存货。货币资金不等于全部可动用现金，有息负债汇总也可能缺少租赁等项目，应核查受限资金和到期安排。";
            case CAPITAL_ALLOCATION -> "资本开支是为未来经营投入的资金，分红和回购是向股东返还资金。资本效率需要收益与投入资本匹配，ROE等指标通常需要平均余额；不能只用期末权益草率判断资本回报。";
            case DISCLOSURE_RISKS -> "已标记审计只说明报告属性，不代表已读过审计意见。审计结论、会计政策变化、关联交易、担保和诉讼需要核对原文。观察清单应写明要补什么材料、哪些变化会支持或推翻判断。";
        };
    }

    private static String misreading(FinancialInterpretationChapter code) {
        return switch (code) {
            case BUSINESS_MODEL -> "公司名称和股票代码不能替代业务资料，不能凭印象补写主营业务。";
            case PERFORMANCE_TRENDS -> "两个非相邻报告期不能证明逐季改善；年报与单季数据不能直接比较。";
            case GROWTH_DRIVERS -> "高增速可能受低基数影响；合同负债增加不能直接保证未来收入。";
            case PROFITABILITY -> "毛利率改善不一定带来净利润改善；研发费用增加也不直接证明研发失败。";
            case EARNINGS_QUALITY -> "经营现金流为负不等于财务造假，也不能仅凭净利润增长认定利润可持续。";
            case CASH_WORKING_CAPITAL -> "快速扩张也可能占用现金；应收、存货增长需要结合收入规模和结算时点。";
            case ASSET_QUALITY -> "存货增加可能是备货，也可能是积压，必须寻找周转、订单或减值证据。";
            case SOLVENCY_FINANCING -> "不同行业的负债结构不同，不能只凭一个比率给公司贴上安全或危险标签。";
            case CAPITAL_ALLOCATION -> "资本开支增加不自动意味着未来增长；高ROE也可能与权益减少有关。";
            case DISCLOSURE_RISKS -> "没有取得风险披露不代表没有风险；证据校验通过不等于所有原因解释都已证实。";
        };
    }
}
