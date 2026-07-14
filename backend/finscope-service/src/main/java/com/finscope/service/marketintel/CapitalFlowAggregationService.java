package com.finscope.service.marketintel;

import com.finscope.domain.marketintel.CapitalFlowPoint;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CapitalFlowAggregationService {
    public List<CapitalFlowPoint> aggregate(List<CapitalFlowPoint> points,int windowMinutes){
        if(windowMinutes<=0)throw new IllegalArgumentException("windowMinutes must be positive");
        Map<LocalDateTime,List<CapitalFlowPoint>> groups=new LinkedHashMap<LocalDateTime,List<CapitalFlowPoint>>();
        for(CapitalFlowPoint point:points){LocalDateTime time=point.getObservedAt();LocalDateTime bucket=time.withMinute((time.getMinute()/windowMinutes)*windowMinutes).withSecond(0).withNano(0);
            groups.computeIfAbsent(bucket,key->new ArrayList<CapitalFlowPoint>()).add(point);}
        List<CapitalFlowPoint> result=new ArrayList<CapitalFlowPoint>();
        for(Map.Entry<LocalDateTime,List<CapitalFlowPoint>> entry:groups.entrySet()){
            List<CapitalFlowPoint> values=entry.getValue();CapitalFlowPoint first=values.get(0),last=values.get(values.size()-1);CapitalFlowPoint aggregate=new CapitalFlowPoint();
            aggregate.setInstrumentId(first.getInstrumentId());aggregate.setProviderCode(first.getProviderCode());aggregate.setGranularity("MINUTE_"+windowMinutes);
            aggregate.setDataDate(entry.getKey().toLocalDate());aggregate.setObservedAt(entry.getKey());aggregate.setPrice(last.getPrice());
            aggregate.setTradeVolume(sum(values,"volume"));aggregate.setIntervalTradeAmount(sum(values,"amount"));aggregate.setMainNetInflow(sum(values,"main"));
            aggregate.setSuperLargeNetInflow(sum(values,"super"));aggregate.setLargeNetInflow(sum(values,"large"));aggregate.setMediumNetInflow(sum(values,"medium"));aggregate.setSmallNetInflow(sum(values,"small"));
            aggregate.setTurnoverRate(last.getTurnoverRate());aggregate.setVolumeRatio(last.getVolumeRatio());aggregate.setRetrievedAt(last.getRetrievedAt());
            aggregate.setPayloadHash(last.getPayloadHash());aggregate.setQualityStatus(values.stream().allMatch(v->"COMPLETE".equals(v.getQualityStatus()))?"COMPLETE":"PARTIAL");
            aggregate.setCalculationVersion("AGGREGATED_"+windowMinutes+"M");result.add(aggregate);
        }return result;
    }
    private BigDecimal sum(List<CapitalFlowPoint> values,String field){BigDecimal total=BigDecimal.ZERO;boolean present=false;for(CapitalFlowPoint p:values){BigDecimal value;
        switch(field){case"volume":value=p.getTradeVolume();break;case"amount":value=p.getIntervalTradeAmount();break;case"main":value=p.getMainNetInflow();break;case"super":value=p.getSuperLargeNetInflow();break;case"large":value=p.getLargeNetInflow();break;case"medium":value=p.getMediumNetInflow();break;default:value=p.getSmallNetInflow();}
        if(value!=null){total=total.add(value);present=true;}}return present?total:null;}
}
