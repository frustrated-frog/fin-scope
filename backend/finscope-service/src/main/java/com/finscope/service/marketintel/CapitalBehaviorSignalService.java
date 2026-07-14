package com.finscope.service.marketintel;

import com.finscope.domain.marketintel.CapitalBehaviorSignal;
import com.finscope.domain.marketintel.CapitalFlowPoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CapitalBehaviorSignalService {
    private final CapitalSignalPolicy policy;
    public CapitalBehaviorSignalService(){this(CapitalSignalPolicy.v1());}
    @Autowired public CapitalBehaviorSignalService(CapitalSignalPolicy policy){this.policy=policy;}
    public List<CapitalBehaviorSignal> detect(List<CapitalFlowPoint> facts){
        List<CapitalFlowPoint> daily=facts.stream().filter(v->"DAY_1".equals(v.getGranularity())).sorted(Comparator.comparing(CapitalFlowPoint::getObservedAt)).collect(Collectors.toList());
        List<CapitalBehaviorSignal> result=new ArrayList<CapitalBehaviorSignal>();if(daily.size()>=2){CapitalFlowPoint previous=daily.get(daily.size()-2),latest=daily.get(daily.size()-1);
            if(positive(previous.getIntervalTradeAmount())&&latest.getIntervalTradeAmount()!=null){BigDecimal ratio=latest.getIntervalTradeAmount().divide(previous.getIntervalTradeAmount(),4,RoundingMode.HALF_UP);
                if(ratio.compareTo(policy.getAmountExpansionRatio())>=0&&negative(latest.getMainNetInflow()))result.add(signal("AMOUNT_EXPANSION_WITH_OUTFLOW",latest,ratio,policy.getAmountExpansionRatio(),"2d"));
                if(ratio.compareTo(policy.getLowAmountRatio())<=0&&positive(latest.getMainNetInflow()))result.add(signal("LOW_AMOUNT_INFLOW",latest,ratio,policy.getLowAmountRatio(),"2d"));}
            if(previous.getPrice()!=null&&latest.getPrice()!=null&&latest.getMainNetInflow()!=null){BigDecimal priceChange=latest.getPrice().subtract(previous.getPrice());
                if((positive(priceChange)&&negative(latest.getMainNetInflow()))||(negative(priceChange)&&positive(latest.getMainNetInflow())))result.add(simple("PRICE_FLOW_DIVERGENCE",latest,"2d"));}}
        detectLateSession(facts,result);return result;
    }
    private void detectLateSession(List<CapitalFlowPoint> facts,List<CapitalBehaviorSignal> result){List<CapitalFlowPoint> minute=facts.stream().filter(v->v.getGranularity()!=null&&v.getGranularity().startsWith("MINUTE_")).sorted(Comparator.comparing(CapitalFlowPoint::getObservedAt)).collect(Collectors.toList());
        if(minute.size()<4)return;int cut=(int)Math.floor(minute.size()*0.75);BigDecimal early=sumFlow(minute.subList(0,cut)),late=sumFlow(minute.subList(cut,minute.size()));
        if(early.signum()!=0&&late.signum()!=0&&early.signum()!=late.signum())result.add(simple("LATE_SESSION_FLOW_SHIFT",minute.get(minute.size()-1),"session"));}
    private CapitalBehaviorSignal signal(String type,CapitalFlowPoint p,BigDecimal actual,BigDecimal threshold,String window){CapitalBehaviorSignal s=simple(type,p,window);Map<String,BigDecimal>a=new LinkedHashMap<String,BigDecimal>();a.put("amountRatio",actual);s.setActualValues(a);Map<String,BigDecimal>t=new LinkedHashMap<String,BigDecimal>();t.put("amountRatio",threshold);s.setThresholds(t);return s;}
    private CapitalBehaviorSignal simple(String type,CapitalFlowPoint p,String window){CapitalBehaviorSignal s=CapitalBehaviorSignal.of(type,policy.version(),Arrays.asList(p.metricRef("intervalTradeAmount"),p.metricRef("mainNetInflow"),p.metricRef("price")));s.setWindow(window);return s;}
    private BigDecimal sumFlow(List<CapitalFlowPoint> values){return values.stream().map(CapitalFlowPoint::getMainNetInflow).filter(v->v!=null).reduce(BigDecimal.ZERO,BigDecimal::add);}
    private boolean positive(BigDecimal v){return v!=null&&v.signum()>0;}private boolean negative(BigDecimal v){return v!=null&&v.signum()<0;}
}
