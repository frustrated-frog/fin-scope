package com.finscope.service.marketintel;

import com.finscope.domain.marketintel.CapitalBehaviorSignal;
import com.finscope.domain.marketintel.CapitalBehaviorSnapshot;
import com.finscope.domain.marketintel.CapitalFlowPoint;
import com.finscope.rpc.marketintel.JdkFinanceHttpClient;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CapitalBehaviorSnapshotFactory {
    public CapitalBehaviorSnapshot create(Long instrumentId,List<CapitalFlowPoint> facts,List<CapitalBehaviorSignal> signals){
        return create(instrumentId,facts,signals,Collections.emptyList());
    }
    public CapitalBehaviorSnapshot create(Long instrumentId,List<CapitalFlowPoint> facts,List<CapitalBehaviorSignal> signals,List<String> warnings){
        List<CapitalFlowPoint> sorted=facts.stream().sorted(Comparator.comparing(CapitalFlowPoint::getObservedAt).thenComparing(v->v.getId()==null?Long.MAX_VALUE:v.getId())).collect(Collectors.toList());
        LocalDateTime asOf=sorted.isEmpty()?LocalDateTime.now():sorted.get(sorted.size()-1).getObservedAt();StringBuilder canonical=new StringBuilder(instrumentId+"|"+asOf);
        for(CapitalFlowPoint p:sorted)canonical.append('|').append(p.getId()).append(':').append(p.getPayloadHash()).append(':').append(p.getCalculationVersion());
        for(CapitalBehaviorSignal s:signals)canonical.append('|').append(s.getType()).append(':')
                .append(s.getVersion()).append(':').append(s.getRuleVersion()).append(':')
                .append(s.getFactorRefs()).append(':').append(s.getMetricRefs()).append(':')
                .append(s.getActualValues()).append(':').append(s.getThresholds());
        List<String> normalizedWarnings=new ArrayList<String>(warnings==null?Collections.emptyList():warnings);Collections.sort(normalizedWarnings);
        for(String warning:normalizedWarnings)canonical.append("|warning:").append(warning);
        CapitalBehaviorSnapshot snapshot=CapitalBehaviorSnapshot.of(instrumentId,asOf,sorted,signals,JdkFinanceHttpClient.sha256(canonical.toString()));
        snapshot.setWarnings(normalizedWarnings);snapshot.setQualityStatus(normalizedWarnings.isEmpty()?"COMPLETE":"PARTIAL");return snapshot;
    }
}
