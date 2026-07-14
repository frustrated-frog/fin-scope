package com.finscope.service.marketintel;

import com.finscope.domain.marketintel.CapitalBehaviorSignal;
import com.finscope.domain.marketintel.CapitalFlowPoint;
import com.finscope.domain.marketintel.CapitalRuleExplanation;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class CapitalRuleExplanationService {
    public CapitalRuleExplanation explain(List<CapitalFlowPoint> facts,List<CapitalBehaviorSignal> signals){
        CapitalRuleExplanation value=new CapitalRuleExplanation();value.setRuleVersion("capital-rules-v1");List<CapitalRuleExplanation.Item> items=new ArrayList<CapitalRuleExplanation.Item>();
        for(CapitalBehaviorSignal signal:signals){CapitalRuleExplanation.Item item=new CapitalRuleExplanation.Item();item.setLevel("OBSERVATION");item.setMetricRefs(signal.getMetricRefs());item.setText(text(signal.getType()));items.add(item);}
        if(items.isEmpty()){CapitalRuleExplanation.Item item=new CapitalRuleExplanation.Item();item.setLevel("NEUTRAL");item.setText("当前资金与成交数据未触发显著异常规则，建议继续观察连续性。");item.setMetricRefs(new ArrayList<String>());items.add(item);}
        value.setItems(items);value.setSummary(summary(signals));value.setDataGaps(Arrays.asList("缺少 Level-2 逐笔委托与成交，无法确认拆单或隐藏订单。","资金数据来自公开口径，不能等同于机构真实意图。"));return value;
    }
    private String summary(List<CapitalBehaviorSignal> signals){if(has(signals,"AMOUNT_EXPANSION_WITH_OUTFLOW"))return "成交明显放大，但主力净流向为负，说明放量同时资金承接存在分歧。";
        if(has(signals,"LOW_AMOUNT_INFLOW"))return "成交相对收缩但主力净流向为正，资金改善尚缺少成交配合。";
        if(has(signals,"PRICE_FLOW_DIVERGENCE"))return "价格方向与主力净流向出现背离，需要结合后续成交连续性判断。";return "资金面暂未出现规则定义的显著异常。";}
    private boolean has(List<CapitalBehaviorSignal>s,String type){return s.stream().anyMatch(v->type.equals(v.getType()));}
    private String text(String type){switch(type){case"AMOUNT_EXPANSION_WITH_OUTFLOW":return "成交金额较上一交易日明显放大，同时主力净流出，放量并未形成一致资金流入。";case"LOW_AMOUNT_INFLOW":return "成交金额偏低但主力净流入，当前改善力度仍需更多成交确认。";case"PRICE_FLOW_DIVERGENCE":return "价格变化与主力净流向方向相反，量价资金出现背离。";case"LATE_SESSION_FLOW_SHIFT":return "尾盘资金方向相较盘中发生反转，短时行为出现变化。";default:return "检测到资金行为变化。";}}
}
