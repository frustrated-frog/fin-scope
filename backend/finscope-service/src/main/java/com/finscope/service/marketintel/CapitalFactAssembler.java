package com.finscope.service.marketintel;

import com.finscope.domain.marketintel.CapitalBehaviorSnapshot;
import com.finscope.domain.marketintel.CapitalFlowPoint;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class CapitalFactAssembler {
    public List<String> assemble(CapitalBehaviorSnapshot snapshot){List<String> facts=new ArrayList<String>();for(CapitalFlowPoint point:snapshot.getFacts()){
        StringBuilder text=new StringBuilder(point.getObservedAt()+" "+point.getGranularity());
        if(point.getIntervalTradeAmount()!=null)text.append("，成交金额 ").append(money(point.getIntervalTradeAmount()));
        if(point.getMainNetInflow()!=null)text.append("，主力净").append(point.getMainNetInflow().signum()>=0?"流入 ":"流出 ").append(money(point.getMainNetInflow().abs()));
        if(point.getTurnoverRate()!=null)text.append("，换手率 ").append(point.getTurnoverRate()).append('%');facts.add(text.toString());}return facts;}
    private String money(BigDecimal value){BigDecimal unit=value.abs().compareTo(new BigDecimal("100000000"))>=0?new BigDecimal("100000000"):new BigDecimal("10000");return value.divide(unit,2,RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()+(unit.intValue()==10000?" 万元":" 亿元");}
}
