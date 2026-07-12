package com.finscope.service.attribution;

import com.finscope.common.util.StringUtils;
import com.finscope.domain.instrument.Instrument;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class AttributionResearchPlanFactory {
    public AttributionResearchPlan create(Instrument instrument, Double changePct) {
        String name = StringUtils.firstNonBlank(instrument.getName(), instrument.getCode());
        String direction = changePct != null && changePct < 0 ? "下跌" : "上涨";
        AttributionResearchPlan plan = new AttributionResearchPlan();
        plan.setObjective("解释" + name + "当日" + direction + "的主要驱动");
        List<AttributionResearchPlan.Track> tracks = new ArrayList<AttributionResearchPlan.Track>();
        if ("FUND".equalsIgnoreCase(instrument.getType())) {
            tracks.add(track("FUND_EXPOSURE", "找到基金重仓行业或核心暴露的当日变化", 2, name + " 基金 重仓行业 今日" + direction));
        } else if ("SECTOR".equalsIgnoreCase(instrument.getType())) {
            tracks.add(track("COMPANY", "找到板块龙头或成分股催化", 2, name + " 板块 龙头股 今日" + direction));
        } else {
            tracks.add(track("COMPANY", "找到公司公告、经营或治理层面的直接事件", 2, name + " 今日" + direction + " 公司公告 经营消息"));
        }
        tracks.add(track("INDUSTRY", "找到行业景气、产业链或竞争格局变化", 2, name + " 所属行业 产业链 今日动态"));
        tracks.add(track("MACRO", "找到政策、宏观或监管环境变化", 1, name + " 政策 宏观 监管 最新消息"));
        tracks.add(track("MARKET", "判断板块相对走势和资金情绪", 1, name + " 所属板块 今日走势 资金情绪"));
        tracks.add(track("COUNTER", "寻找不能支持主叙事的反证或缺证信息", 1, name + " 今日" + direction + " 原因 反证 市场情绪"));
        plan.setTracks(tracks);
        return plan;
    }

    private AttributionResearchPlan.Track track(String code, String criteria, int maxQueries, String query) {
        AttributionResearchPlan.Track track = new AttributionResearchPlan.Track();
        track.setCode(code);
        track.setSuccessCriteria(criteria);
        track.setMaxQueries(maxQueries);
        track.setQueries(Arrays.asList(query));
        return track;
    }
}
