package com.finscope.service.attribution;

import com.finscope.common.util.StringUtils;
import com.finscope.domain.instrument.Instrument;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import com.finscope.rpc.quote.PythonTradingCalendarClient;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
@Slf4j
public class AttributionResearchPlanFactory {
    @Autowired
    private PythonTradingCalendarClient tradingCalendarClient;

    public AttributionResearchPlan create(Instrument instrument, Double changePct, LocalDate reportDate) {
        String name = StringUtils.firstNonBlank(instrument.getName(), instrument.getCode());
        String direction = changePct == null ? "涨跌幅未知" : changePct == 0 ? "持平" : changePct < 0 ? "下跌" : "上涨";
        String date = reportDate == null ? "日期未提供" : reportDate.toString();
        AttributionResearchPlan plan = new AttributionResearchPlan();
        LocalDate startDate = evidenceStartDate(instrument, reportDate);
        plan.setEvidenceStartDate(startDate == null ? null : startDate.toString());
        plan.setObjective("解释" + name + "在" + date + direction + "的主要驱动");
        List<AttributionResearchPlan.Track> tracks = new ArrayList<AttributionResearchPlan.Track>();
        if ("FUND".equalsIgnoreCase(instrument.getType())) {
            tracks.add(track("FUND_EXPOSURE", "找到基金重仓行业或核心暴露的当日变化", 2, name + " 基金 重仓行业 目标交易日" + direction));
        } else if ("SECTOR".equalsIgnoreCase(instrument.getType())) {
            tracks.add(track("COMPANY", "找到板块龙头或成分股催化", 2, name + " 板块 龙头股 目标交易日" + direction));
        } else {
            tracks.add(track("COMPANY", "找到公司公告、经营或治理层面的直接事件", 2, name + " 目标交易日" + direction + " 公司公告 经营消息"));
        }
        tracks.add(track("INDUSTRY", "找到行业景气、产业链或竞争格局变化", 2, name + " 所属行业 产业链 目标交易日动态"));
        tracks.add(track("MACRO", "找到政策、宏观或监管环境变化", 1, name + " 政策 宏观 监管 相关消息"));
        tracks.add(track("MARKET", "判断板块相对走势和资金情绪", 1, name + " 所属板块 目标交易日走势 资金情绪"));
        tracks.add(track("COUNTER", "寻找不能支持主叙事的反证或缺证信息", 1, name + " 目标交易日" + direction + " 原因 反证 市场情绪"));
        for (AttributionResearchPlan.Track track : tracks) {
            track.setQueries(Arrays.asList((startDate == null ? date : startDate + " 至 " + date) + " " + track.getQueries().get(0)));
        }
        plan.setTracks(tracks);
        return plan;
    }

    private LocalDate evidenceStartDate(Instrument instrument, LocalDate reportDate) {
        if (reportDate == null) {
            return null;
        }
        LocalDate start = reportDate.minusDays(3);
        boolean mainlandStock = "STOCK".equalsIgnoreCase(instrument.getType())
                && instrument.getCode() != null && instrument.getCode().matches("[0-9]{6}");
        if (!mainlandStock && !"SECTOR".equalsIgnoreCase(instrument.getType())) {
            return start;
        }
        try {
            LocalDate previous = tradingCalendarClient.previousSession(reportDate);
            if (previous != null && previous.isBefore(start)) {
                return previous;
            }
        } catch (RuntimeException ex) {
            log.warn("归因休市日历不可用，保留三自然日窗口 code={} date={} errorType={}",
                    instrument.getCode(), reportDate, ex.getClass().getSimpleName());
        }
        return start;
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
