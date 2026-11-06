package com.finscope.service.research.quality;

import com.finscope.common.enums.research.ResearchFactKind;
import com.finscope.domain.research.ResearchRun;
import com.finscope.domain.research.ResearchThesis;
import com.finscope.domain.research.quality.ResearchTaskContract;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ResearchTaskContractBuilder {
    private static final Pattern DATE = Pattern.compile("(20\\d{2})[-年/](\\d{1,2})[-月/](\\d{1,2})日?");

    public ResearchTaskContract build(ResearchRun run, ResearchThesis thesis) {
        ResearchTaskContract contract = new ResearchTaskContract();
        contract.setResearchRunId(run.getId());
        contract.setQuestion(text(thesis.getQuestion()));
        contract.setSubject(text(thesis.getSubjectName()));
        contract.setSubjectType(thesis.getSubjectType());
        contract.setSubjectCode(text(thesis.getSubjectCode()));
        contract.getAliases().add(contract.getSubject());
        if (!contract.getSubjectCode().isBlank()) {
            contract.getAliases().add(contract.getSubjectCode());
        }
        String[][] aliases = {{"微软", "Microsoft", "MSFT"}, {"英伟达", "NVIDIA", "NVDA"},
                {"长鑫科技", "长鑫存储", "CXMT"}, {"亚马逊", "Amazon", "AMZN"}};
        for (String[] group : aliases) {
            if (Arrays.stream(group).anyMatch(name -> name.equalsIgnoreCase(contract.getSubject()))) {
                contract.getAliases().addAll(Arrays.asList(group));
            }
        }
        LocalDate asOf = run.getRunDate() != null ? run.getRunDate()
                : run.getCreatedAt() == null ? LocalDate.now() : run.getCreatedAt().toLocalDate();
        contract.setAsOfDate(asOf.toString());
        Matcher dates = DATE.matcher(contract.getQuestion());
        while (dates.find()) {
            String date = LocalDate.of(Integer.parseInt(dates.group(1)), Integer.parseInt(dates.group(2)),
                    Integer.parseInt(dates.group(3))).toString();
            if (contract.getDateFrom() == null) {
                contract.setDateFrom(date);
            }
            contract.setDateTo(date);
        }
        if (contract.getDateFrom() == null && contains(contract.getQuestion(), "今日", "今天", "昨日", "昨天")) {
            LocalDate date = contains(contract.getQuestion(), "昨日", "昨天") ? asOf.minusDays(1) : asOf;
            contract.setDateFrom(date.toString());
            contract.setDateTo(date.toString());
        }
        contract.setDateRequired(contract.getDateFrom() != null);
        contract.setDateResolution(contract.isDateRequired() ? "按问题日期及运行日期解析" : "未限定事件日期");
        String question = contract.getQuestion();
        contract.setEvent(contains(question, "大宗交易") ? "大宗交易"
                : contains(question, "财资中心") ? "财资中心"
                : contains(question, "回购") ? "回购"
                : contains(question, "减持") ? "减持"
                : contains(question, "涨超", "均涨", "股价上涨") ? "股价上涨" : "研究命题");
        contract.getRequiredFacts().add(ResearchFactKind.EVENT);
        if ("大宗交易".equals(contract.getEvent())) {
            contract.getRequiredFacts().addAll(Arrays.asList(ResearchFactKind.VOLUME, ResearchFactKind.AMOUNT,
                    ResearchFactKind.PRICE));
        }
        if ("股价上涨".equals(contract.getEvent())) {
            contract.getRequiredFacts().add(ResearchFactKind.CHANGE_RATE);
        }
        addFacet(contract, "空间", ResearchFactKind.MARKET_SPACE);
        addFacet(contract, "盈利模式", ResearchFactKind.PROFIT_MODEL);
        addFacet(contract, "竞争", ResearchFactKind.COMPETITION);
        addFacet(contract, "治理", ResearchFactKind.GOVERNANCE);
        addFacet(contract, "估值", ResearchFactKind.VALUATION);
        if (contains(question, "反方", "反证", "风险")) {
            contract.getRequiredFacts().add(ResearchFactKind.COUNTERCHECK);
        }
        contract.getCompletionCriteria().addAll(Arrays.asList("必需事实逐项有同主体、同事件的原文支持",
                "日期和统计口径可核对；发布时间不能替代事件日期", "转载合并后至少两个来源组",
                "同日期、同口径的数值冲突已解决；预算耗尽只能交付带缺口的报告"));
        return contract;
    }

    private void addFacet(ResearchTaskContract contract, String keyword, ResearchFactKind kind) {
        if (contract.getQuestion().contains(keyword)) {
            contract.getRequiredFacts().add(kind);
        }
    }

    private boolean contains(String value, String... terms) {
        return Arrays.stream(terms).anyMatch(value::contains);
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
