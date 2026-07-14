package com.finscope.service.marketintel;

import com.finscope.dao.marketintel.CapitalBehaviorSnapshotRepository;
import com.finscope.dao.marketintel.CapitalFlowRepository;
import com.finscope.dao.marketintel.CapitalInterpretationRepository;
import com.finscope.dao.marketintel.MarketIntelRefreshRunRepository;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.marketintel.CapitalBehaviorSignal;
import com.finscope.domain.marketintel.CapitalBehaviorSnapshot;
import com.finscope.domain.marketintel.CapitalFlowPoint;
import com.finscope.domain.marketintel.CapitalInterpretation;
import com.finscope.domain.marketintel.CapitalRuleExplanation;
import com.finscope.domain.marketintel.MarketIntelRefreshRun;
import com.finscope.domain.marketintel.MarketIntelRefreshStep;
import com.finscope.rpc.marketintel.CapitalFlowData;
import com.finscope.rpc.marketintel.CapitalFlowProvider;
import com.finscope.rpc.marketintel.ProviderContractException;
import com.finscope.rpc.marketintel.ProviderRequestGuard;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.Executor;

@Service
public class MarketIntelRefreshCoordinator {
    private final MarketIntelCapitalService capital;private final List<CapitalFlowProvider> providers;private final ProviderRequestGuard guard;
    private final CapitalFlowRepository flows;private final CapitalBehaviorSignalService signalService;private final CapitalBehaviorSnapshotFactory snapshotsFactory;
    private final CapitalBehaviorSnapshotRepository snapshots;private final CapitalRuleExplanationService ruleService;private final CapitalInterpretationRepository interpretations;
    private final CapitalFactAssembler facts;private final MarketIntelRefreshRunRepository runs;
    @Resource(name="marketIntelRefreshExecutor") private Executor executor;
    public MarketIntelRefreshCoordinator(MarketIntelCapitalService capital,List<CapitalFlowProvider> providers,ProviderRequestGuard guard,CapitalFlowRepository flows,CapitalBehaviorSignalService signalService,CapitalBehaviorSnapshotFactory snapshotsFactory,CapitalBehaviorSnapshotRepository snapshots,CapitalRuleExplanationService ruleService,CapitalInterpretationRepository interpretations,CapitalFactAssembler facts,MarketIntelRefreshRunRepository runs){this.capital=capital;this.providers=providers;this.guard=guard;this.flows=flows;this.signalService=signalService;this.snapshotsFactory=snapshotsFactory;this.snapshots=snapshots;this.ruleService=ruleService;this.interpretations=interpretations;this.facts=facts;this.runs=runs;}
    public MarketIntelRefreshRun requestRefresh(Long instrumentId){Instrument instrument=capital.stock(instrumentId);MarketIntelRefreshRun run=runs.createRun(instrumentId,"MANUAL");
        try{executor.execute(()->refresh(run,instrument));}catch(RuntimeException e){runs.finishRun(run.getId(),MarketIntelRefreshRun.Status.FAILED,0,1);throw e;}return run;}
    private void refresh(MarketIntelRefreshRun run,Instrument instrument){CapitalFlowProvider provider=providers.stream().filter(v->v.supports(instrument)).findFirst().orElse(null);if(provider==null){runs.finishRun(run.getId(),MarketIntelRefreshRun.Status.FAILED,0,1);return;}
        MarketIntelRefreshStep step=runs.createStep(run.getId(),"CAPITAL_FLOW",provider.providerCode(),1);runs.updateStep(step.getId(),MarketIntelRefreshStep.Status.RUNNING,0,null,null);
        try{CapitalFlowData data=guard.execute(provider.providerCode(),()->provider.fetch(instrument,LocalDate.now()));List<CapitalFlowPoint> points=data.allPoints();if(points.isEmpty()){runs.updateStep(step.getId(),MarketIntelRefreshStep.Status.EMPTY,0,null,null);runs.finishRun(run.getId(),MarketIntelRefreshRun.Status.PARTIAL,0,0);return;}
            List<String> warnings=new ArrayList<String>(new LinkedHashSet<String>(data.getWarnings()));
            if(points.stream().anyMatch(point->!"COMPLETE".equals(point.getQualityStatus()))&&!warnings.contains("部分时间点行情未与资金流对齐"))warnings.add("部分时间点行情未与资金流对齐");
            flows.saveAll(points);List<CapitalBehaviorSignal> signals=signalService.detect(points);CapitalBehaviorSnapshot snapshot=snapshots.save(snapshotsFactory.create(instrument.getId(),points,signals,warnings));persistRule(snapshot,ruleService.explain(points,signals));
            boolean partial=!warnings.isEmpty();String warningMessage=partial?String.join("；",warnings):null;
            runs.updateStep(step.getId(),MarketIntelRefreshStep.Status.SUCCEEDED,points.size(),partial?"PARTIAL_DATA":null,warningMessage);
            runs.finishRun(run.getId(),partial?MarketIntelRefreshRun.Status.PARTIAL:MarketIntelRefreshRun.Status.SUCCEEDED,1,0);
        }catch(ProviderContractException e){runs.updateStep(step.getId(),MarketIntelRefreshStep.Status.FAILED,0,e.getErrorType(),e.getMessage());runs.finishRun(run.getId(),MarketIntelRefreshRun.Status.FAILED,0,1);
        }catch(Exception e){runs.updateStep(step.getId(),MarketIntelRefreshStep.Status.FAILED,0,"INTERNAL_ERROR",e.getMessage());runs.finishRun(run.getId(),MarketIntelRefreshRun.Status.FAILED,0,1);}}
    private void persistRule(CapitalBehaviorSnapshot snapshot,CapitalRuleExplanation rule){if(interpretations.findByAction(snapshot.getId(),"RULE",snapshot.getFingerprint()).isPresent())return;CapitalInterpretation value=new CapitalInterpretation();value.setInstrumentId(snapshot.getInstrumentId());value.setSnapshotId(snapshot.getId());value.setInterpretationType("RULE");value.setStatus("SUCCEEDED");value.setPlainSummary(rule.getSummary());value.setFacts(facts.assemble(snapshot));value.setHypotheses(Collections.emptyList());value.setDataGaps(rule.getDataGaps());value.setObservationPoints(Collections.singletonList("观察后续资金与成交是否延续当前组合。"));value.setDisclaimer("规则解释仅用于研究，不构成投资建议。");value.setRuleVersion(rule.getRuleVersion());value.setInputHash(snapshot.getFingerprint());interpretations.save(value);}
}
