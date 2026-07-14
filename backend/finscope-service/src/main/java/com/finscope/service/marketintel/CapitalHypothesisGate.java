package com.finscope.service.marketintel;

import com.finscope.domain.marketintel.CapitalBehaviorSnapshot;
import com.finscope.domain.marketintel.CapitalFlowPoint;
import com.finscope.domain.marketintel.CapitalHypothesis;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class CapitalHypothesisGate {
    private static final String[] METRICS={"price","tradeVolume","intervalTradeAmount","cumulativeTradeAmount","turnoverRate","volumeRatio","mainNetInflow","superLargeNetInflow","largeNetInflow","mediumNetInflow","smallNetInflow"};
    public List<CapitalHypothesis> apply(CapitalBehaviorSnapshot snapshot,List<CapitalHypothesis> hypotheses){
        Set<String> allowed=new HashSet<String>();for(CapitalFlowPoint fact:snapshot.getFacts())if(fact.getId()!=null)for(String metric:METRICS)allowed.add(fact.metricRef(metric));
        List<CapitalHypothesis> accepted=new ArrayList<CapitalHypothesis>();for(CapitalHypothesis value:hypotheses){if(value.getSupportingMetricRefs().isEmpty()||!allowed.containsAll(value.getSupportingMetricRefs()))continue;
            value.setConfidence(cap(value.getType(),value.getConfidence(),snapshot));List<String> counter=new ArrayList<String>(value.getCounterEvidence());
            if("HIDDEN_FLOW".equals(value.getType())||"ORDER_SPLITTING".equals(value.getType())){counter.add("缺少 Level-2 逐笔委托/成交，公开净流向只能支持低置信度行为假设。");List<String> gaps=new ArrayList<String>(value.getDataGaps());gaps.add("Level-2 逐笔委托与成交");value.setDataGaps(gaps);}
            value.setCounterEvidence(counter);accepted.add(value);}return accepted;
    }
    private String cap(String type,String requested,CapitalBehaviorSnapshot snapshot){if("HIDDEN_FLOW".equals(type)||"ORDER_SPLITTING".equals(type))return "LOW";
        long days=snapshot.getFacts().stream().map(v->v.getObservedAt().toLocalDate()).distinct().count();if(("ACCUMULATION".equals(type)||"DISTRIBUTION".equals(type))&&days<2)return "LOW";
        return "LOW".equalsIgnoreCase(requested)?"LOW":"MID";}
}
