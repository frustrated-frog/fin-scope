package com.finscope.service.investmentobservation;

import com.finscope.common.enums.investmentobservation.InvestmentObservationChangeType;
import com.finscope.common.enums.investmentobservation.InvestmentObservationDisposition;
import com.finscope.common.enums.investmentobservation.InvestmentObservationSourceType;
import com.finscope.common.enums.investmentobservation.InvestmentObservationStage;
import com.finscope.common.enums.investmentobservation.InvestmentObservationSubjectType;
import com.finscope.domain.investmentobservation.InvestmentObservation;
import com.finscope.domain.investmentobservation.InvestmentObservationScoreDimension;
import com.finscope.domain.radar.RadarEvent;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class InvestmentObservationScoringService {

    public InvestmentObservation score(RadarEvent event) {
        InvestmentObservation value = new InvestmentObservation();
        value.setSourceType(InvestmentObservationSourceType.RADAR_EVENT);
        value.setSourceId(event.getId());
        value.setTitle(text(event.getCanonicalTitle(), "待确认的市场变化"));
        value.setSummary(text(event.getSummary(), "当前只有初步线索，需要等待更多事实。"));
        value.setSubjectType(subjectType(event.getCategoryCode()));
        value.setSubjectName(value.getTitle());
        value.setChangeType(changeType(value.getTitle() + " " + value.getSummary()));
        value.setDisposition(InvestmentObservationDisposition.ACTIVE);
        value.setSupportingEvidenceCount(Math.max(event.getEvidenceCount(), event.getSignalCount()));
        value.setIndependentSourceCount(Math.max(event.getEvidenceSourceCount(), event.getSourceCount()));
        value.setOpposingEvidenceCount(opposingEvidenceCount(event));
        value.setWhyItMatters(whyItMatters(value.getSubjectType(), value.getChangeType()));
        value.setUncertainty(text(event.getUncertainty(), uncertainty(event)));
        value.setNextValidation(text(event.getNextObservation(), "等待第二个独立来源、公告或可量化经营数据确认"));
        value.setLastSourceFingerprint(fingerprint(event));

        List<InvestmentObservationScoreDimension> dimensions = dimensions(event);
        value.setScoreDimensions(dimensions);
        int total = 0;
        for (InvestmentObservationScoreDimension dimension : dimensions) {
            total += dimension.getScore();
        }
        value.setScore(total);
        value.setStage(stage(total));
        value.setEvidenceInsufficient(total < 70 || value.getIndependentSourceCount() < 2
                || blank(event.getNextObservation()));
        return value;
    }

    public void applyFocusFloor(List<InvestmentObservation> values) {
        boolean hasFocus = false;
        for (InvestmentObservation value : values) {
            if (value.getStage() == InvestmentObservationStage.FOCUS) {
                hasFocus = true;
                break;
            }
        }
        if (hasFocus) {
            return;
        }
        List<InvestmentObservation> eligible = new ArrayList<InvestmentObservation>();
        for (InvestmentObservation value : values) {
            if (value.getStage() == InvestmentObservationStage.TRACKING && value.getScore() >= 50) {
                eligible.add(value);
            }
        }
        eligible.sort(Comparator.comparingInt(InvestmentObservation::getScore).reversed());
        int limit = Math.min(3, eligible.size());
        for (int index = 0; index < limit; index++) {
            InvestmentObservation value = eligible.get(index);
            value.setStage(InvestmentObservationStage.FOCUS);
            value.setEvidenceInsufficient(true);
            value.setUncertainty(join(value.getUncertainty(), "当前作为相对优先对象展示，但尚未达到高置信证据门槛"));
        }
    }

    private List<InvestmentObservationScoreDimension> dimensions(RadarEvent event) {
        List<InvestmentObservationScoreDimension> values = new ArrayList<InvestmentObservationScoreDimension>();
        int change = bounded(Math.round(event.getHotspotScore() * 0.20F), 20);
        values.add(dimension("CHANGE", "变化强度", change, 20,
                change >= 14 ? "事件热度显示变化正在扩散" : "变化强度仍需更多事实确认"));
        int trust = bounded(Math.round(event.getConfidenceScore() * 0.20F), 20);
        values.add(dimension("TRUST", "证据可信度", trust, 20,
                trust >= 14 ? "雷达置信度较高" : "当前来源置信度一般"));
        int independent = independentScore(Math.max(event.getEvidenceSourceCount(), event.getSourceCount()));
        values.add(dimension("INDEPENDENCE", "独立来源", independent, 15,
                independent >= 12 ? "已有多个独立来源交叉确认" : "独立来源仍然有限"));
        int persistence = persistenceScore(event.getHotspotLifecycleState());
        values.add(dimension("PERSISTENCE", "变化持续性", persistence, 15,
                persistence >= 12 ? "变化仍在增强或持续" : "尚未形成稳定的跨日趋势"));
        int mechanism = mechanismScore(event);
        values.add(dimension("MECHANISM", "投资机制", mechanism, 15,
                mechanism >= 12 ? "已有结构化证据解释潜在传导" : "影响机制仍需进一步拆解"));
        int verifiability = blank(event.getNextObservation()) ? 2 : 15;
        values.add(dimension("VERIFIABILITY", "可验证性", verifiability, 15,
                verifiability == 15 ? "存在明确的下一验证点" : "暂缺可操作的验证指标"));
        return values;
    }

    private InvestmentObservationScoreDimension dimension(String code, String label, int score, int max,
                                                           String explanation) {
        InvestmentObservationScoreDimension value = new InvestmentObservationScoreDimension();
        value.setCode(code);
        value.setLabel(label);
        value.setScore(score);
        value.setMaxScore(max);
        value.setExplanation(explanation);
        return value;
    }

    private int independentScore(int sources) {
        if (sources >= 3) {
            return 15;
        }
        if (sources == 2) {
            return 11;
        }
        if (sources == 1) {
            return 5;
        }
        return 0;
    }

    private int persistenceScore(String lifecycle) {
        if ("RISING".equalsIgnoreCase(lifecycle)) {
            return 15;
        }
        if ("STABLE".equalsIgnoreCase(lifecycle)) {
            return 10;
        }
        if ("NEW".equalsIgnoreCase(lifecycle)) {
            return 7;
        }
        if ("FADING".equalsIgnoreCase(lifecycle)) {
            return 3;
        }
        return 4;
    }

    private int mechanismScore(RadarEvent event) {
        if ("READY".equalsIgnoreCase(event.getEvidenceStatus()) && !blank(event.getEvidenceSummary())) {
            return 15;
        }
        if (!blank(event.getEvidenceSummary())) {
            return 10;
        }
        if (!blank(event.getScoreExplanation()) || !blank(event.getHotspotExplanation())) {
            return 6;
        }
        return 3;
    }

    private InvestmentObservationStage stage(int score) {
        if (score >= 70) {
            return InvestmentObservationStage.FOCUS;
        }
        if (score >= 50) {
            return InvestmentObservationStage.TRACKING;
        }
        return InvestmentObservationStage.LEARNING;
    }

    private InvestmentObservationSubjectType subjectType(String category) {
        if ("COMPANY".equalsIgnoreCase(category)) {
            return InvestmentObservationSubjectType.COMPANY;
        }
        if ("INDUSTRY".equalsIgnoreCase(category)) {
            return InvestmentObservationSubjectType.INDUSTRY;
        }
        return InvestmentObservationSubjectType.EVENT;
    }

    private InvestmentObservationChangeType changeType(String source) {
        String value = source == null ? "" : source.toLowerCase(Locale.ROOT);
        if (contains(value, "订单", "合同", "中标")) {
            return InvestmentObservationChangeType.ORDER;
        }
        if (contains(value, "价格", "涨价", "降价")) {
            return InvestmentObservationChangeType.PRICE;
        }
        if (contains(value, "政策", "监管", "补贴")) {
            return InvestmentObservationChangeType.POLICY;
        }
        if (contains(value, "业绩", "利润", "收入", "财报")) {
            return InvestmentObservationChangeType.EARNINGS;
        }
        if (contains(value, "竞争", "份额", "格局")) {
            return InvestmentObservationChangeType.COMPETITION;
        }
        if (contains(value, "资金", "资本开支", "融资")) {
            return InvestmentObservationChangeType.CAPITAL;
        }
        return InvestmentObservationChangeType.OTHER;
    }

    private boolean contains(String value, String... tokens) {
        for (String token : tokens) {
            if (value.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private String whyItMatters(InvestmentObservationSubjectType subjectType,
                                InvestmentObservationChangeType changeType) {
        if (changeType == InvestmentObservationChangeType.ORDER || changeType == InvestmentObservationChangeType.EARNINGS) {
            return "这类变化可能通过收入、利润与预期差影响公司或产业链判断";
        }
        if (changeType == InvestmentObservationChangeType.PRICE || changeType == InvestmentObservationChangeType.CAPITAL) {
            return "这类变化可能反映供需、投入强度或盈利能力正在发生调整";
        }
        if (subjectType == InvestmentObservationSubjectType.INDUSTRY) {
            return "行业层面的连续变化可能影响上下游需求和竞争格局";
        }
        return "该事件具有持续验证价值，但目前不构成交易结论";
    }

    private String uncertainty(RadarEvent event) {
        if (Math.max(event.getEvidenceSourceCount(), event.getSourceCount()) < 2) {
            return "尚缺第二个独立来源确认";
        }
        if (blank(event.getEvidenceSummary())) {
            return "尚缺结构化证据解释变化机制";
        }
        return "历史与当前证据不能证明未来结果";
    }

    private int opposingEvidenceCount(RadarEvent event) {
        String warning = text(event.getEvidenceWarning(), "");
        return blank(warning) ? 0 : 1;
    }

    private String fingerprint(RadarEvent event) {
        if (!blank(event.getEvidenceFingerprint())) {
            return event.getEvidenceFingerprint();
        }
        return text(event.getEventKey(), "event-" + event.getId()) + ":" + event.getHotspotScore()
                + ":" + event.getConfidenceScore() + ":" + event.getSourceCount();
    }

    private int bounded(int value, int max) {
        return Math.max(0, Math.min(max, value));
    }

    private String text(String value, String fallback) {
        return blank(value) ? fallback : value.trim();
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String join(String first, String second) {
        return blank(first) ? second : first + "；" + second;
    }
}
