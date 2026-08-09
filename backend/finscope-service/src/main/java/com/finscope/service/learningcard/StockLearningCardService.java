package com.finscope.service.learningcard;

import com.finscope.common.exception.BusinessConflictException;
import com.finscope.dao.learningcard.StockLearningCardRepository;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.learningcard.StockLearningCard;
import com.finscope.domain.learningcard.StockLearningCardClaim;
import com.finscope.domain.learningcard.StockLearningCardRun;
import com.finscope.domain.learningcard.StockLearningCardWatchItem;
import com.finscope.domain.research.ResearchMode;
import com.finscope.domain.research.ResearchRun;
import com.finscope.domain.research.ResearchThesis;
import com.finscope.service.research.ResearchService;
import com.finscope.service.research.ResearchThesisService;
import com.finscope.service.research.report.ResearchReportService;
import com.finscope.service.strategy.StrategyInstrumentResolver;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class StockLearningCardService {
    @Resource private StrategyInstrumentResolver instrumentResolver;
    @Resource private StockLearningCardRepository cards;
    @Resource private ResearchThesisService thesisService;
    @Resource private ResearchService researchService;
    @Resource private ResearchReportService reportService;

    public StockLearningCardRun start(String code) {
        Instrument instrument = instrumentResolver.resolve(code, "STOCK");
        StockLearningCard card = cards.findOrCreate(instrument.getId(), StockLearningFramework.CODE);
        StockLearningCardRun latest = cards.latest(card.getId()).orElse(null);
        if (latest != null && "RUNNING".equals(latest.getStatus())) throw new BusinessConflictException("该股票学习卡仍在生成中");
        ResearchThesis thesis = new ResearchThesis();
        thesis.setQuestion("请按空间、盈利模式、竞争格局、治理结构、定价观察和反方验证研究" + instrument.getName());
        thesis.setSubjectType("COMPANY"); thesis.setSubjectName(instrument.getName()); thesis.setSubjectCode(instrument.getCode());
        thesis = thesisService.create(thesis);
        ResearchRun research = researchService.createRun(thesis.getId(), LocalDate.now(), Collections.<String>emptyList(), ResearchMode.DEEP).getRun();
        StockLearningCardRun running = run(card, research.getId(), "RUNNING", null, "生成中", "PENDING", "CONTROLLED");
        return cards.appendRun(running, Collections.<StockLearningCardClaim>emptyList(), Collections.<StockLearningCardWatchItem>emptyList());
    }

    public StockLearningCardView get(String code) {
        Instrument instrument = instrumentResolver.resolve(code, "STOCK");
        StockLearningCard card = cards.findOrCreate(instrument.getId(), StockLearningFramework.CODE);
        StockLearningCardRun latest = cards.latest(card.getId()).orElse(null);
        if (latest != null && "RUNNING".equals(latest.getStatus())) latest = reconcile(card, latest);
        return new StockLearningCardView(card, latest);
    }

    private StockLearningCardRun reconcile(StockLearningCard card, StockLearningCardRun running) {
        ResearchRun research = researchService.detail(running.getResearchRunId());
        if ("RUNNING".equals(research.getStatus())) return running;
        String report = reportService.findByRunId(research.getId()).map(value -> value.getConclusion()).orElse(null);
        boolean available = report != null && !report.trim().isEmpty() && StockLearningFramework.isAllowedText(report);
        List<StockLearningCardClaim> claims = new ArrayList<StockLearningCardClaim>();
        int index = 1;
        for (String dimension : StockLearningFramework.dimensions()) {
            StockLearningCardClaim claim = new StockLearningCardClaim();
            claim.setDimensionCode(dimension);
            claim.setJudgment(available ? report : "证据不足，暂不形成判断");
            claim.setRationale(available ? "基于本次受控研究报告，仍需阅读关联证据。" : "本次公开研究未形成可用报告。");
            claim.setCounterargument("需继续寻找与当前判断相反的经营、竞争或治理证据。");
            claim.setUnknowns("当前公开证据未覆盖的变量应保持未知。");
            claim.setConfidence(available ? "LOW" : "LOW"); claim.setSortOrder(index++); claims.add(claim);
        }
        StockLearningCardWatchItem watch = new StockLearningCardWatchItem();
        watch.setMetric("下一次公开披露中的经营与财务变化"); watch.setBaseline("当前公开证据未覆盖");
        watch.setFrequency("下一次财报或重大公告"); watch.setUpgradeCondition("关键经营指标与研究判断同向改善");
        watch.setDowngradeCondition("关键经营指标与研究判断持续背离"); watch.setNextReviewAt(LocalDateTime.now().plusMonths(3)); watch.setSortOrder(1);
        StockLearningCardRun completed = run(card, research.getId(), available ? "READY" : "DEGRADED",
                available ? "CONTINUE_LEARNING" : "INSUFFICIENT_EVIDENCE", available ? report : "本次研究未形成足够公开证据", available ? "PARTIAL" : "MISSING", available ? "MODEL_ASSISTED" : "CONTROLLED");
        if (!available) completed.setWarningMessage("研究报告缺失或包含不适合学习卡展示的交易语言，已改为保守降级结论。");
        return cards.appendRun(completed, claims, Collections.singletonList(watch));
    }
    private StockLearningCardRun run(StockLearningCard card, Long researchRunId, String status, String conclusion, String summary, String completeness, String mode) {
        StockLearningCardRun value = new StockLearningCardRun(); value.setCardId(card.getId()); value.setResearchRunId(researchRunId);
        value.setFrameworkCode(StockLearningFramework.CODE); value.setStatus(status); value.setConclusionStatus(conclusion);
        value.setSummary(summary); value.setEvidenceCompleteness(completeness); value.setGenerationMode(mode); return value;
    }
    public static class StockLearningCardView {
        private final StockLearningCard card; private final StockLearningCardRun latestRun;
        public StockLearningCardView(StockLearningCard card, StockLearningCardRun latestRun) { this.card=card; this.latestRun=latestRun; }
        public StockLearningCard getCard() { return card; }
        public StockLearningCardRun getLatestRun() { return latestRun; }
    }
}
