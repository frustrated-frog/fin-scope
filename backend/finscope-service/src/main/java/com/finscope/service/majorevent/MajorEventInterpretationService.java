package com.finscope.service.majorevent;

import com.finscope.common.enums.news.NewsInterpretationStatus;
import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.common.exception.ResourceNotFoundException;
import com.finscope.dao.agent.AgentRunRepository;
import com.finscope.dao.majorevent.MajorEventReadingRepository;
import com.finscope.dao.majorevent.MajorEventRepository;
import com.finscope.dao.news.NewsReportRepository;
import com.finscope.dao.radar.RadarRepository;
import com.finscope.domain.majorevent.MajorEvent;
import com.finscope.domain.news.NewsReport;
import com.finscope.domain.news.NewsReportInterpretation;
import com.finscope.domain.news.NewsReportInterpretationView;
import com.finscope.domain.news.NewsReportVersion;
import com.finscope.domain.radar.RadarEventInterpretation;
import com.finscope.service.news.NewsReportInterpretationAgent;
import com.finscope.service.news.NewsReportInterpretationService;
import com.finscope.service.news.NewsWorkbenchCapabilities;
import com.finscope.service.radar.RadarEventInterpretationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Service
@Slf4j
public class MajorEventInterpretationService {
    @Resource
    private MajorEventRepository events;
    @Resource
    private MajorEventReadingRepository archive;
    @Resource
    private NewsReportRepository reports;
    @Resource
    private NewsReportInterpretationService news;
    @Resource
    private RadarRepository radar;
    @Resource
    private RadarEventInterpretationService radarInterpretations;
    @Resource
    private MajorEventRadarReadings radarReadings;
    @Resource
    private NewsWorkbenchCapabilities capabilities;
    @Resource
    private NewsReportInterpretationAgent agent;
    @Resource
    private AgentRunRepository agentRuns;
    @Resource(name = "newsInterpretationExecutor")
    private Executor executor;

    public NewsReportInterpretationView reading(long id, boolean generate) {
        MajorEvent event = events.findById(id).orElseThrow(() -> new ResourceNotFoundException("大事记录已不存在"));
        if (!"NEWS_ITEM".equals(event.getOriginType()) && !"RADAR_EVENT".equals(event.getOriginType())) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "此记录不是新闻或雷达事件");
        }
        var saved = archive.find(id, System.currentTimeMillis());
        if (saved.isPresent()) {
            if (generate && saved.get().getStatus() == NewsInterpretationStatus.FAILED) {
                return snapshot(event, true);
            }
            return view(saved.get());
        }
        String key = event.getOriginKey();
        if ("NEWS_ITEM".equals(event.getOriginType())) {
            var version = reports.archivedVersion(key, event.getTitle(), event.getSummary(), event.getCreatedAt());
            if (version.isPresent()) {
                return generate ? news.request(key, version.get()) : news.get(key, version.get());
            }
        } else {
            long eventId = radar.archivedEventId(key);
            if (radarReadings.capture(key, eventId)) {
                return view(archive.find(id, System.currentTimeMillis()).orElse(null));
            }
            var detail = radar.findDetailSnapshot(eventId);
            if (detail.isPresent()) {
                var status = generate && capabilities.isInterpretationEnabled()
                        ? radarInterpretations.request(eventId) : radarInterpretations.current(detail.get()).orElse(null);
                return radarView(event, eventId, status);
            }
        }
        return snapshot(event, generate);
    }

    private NewsReportInterpretationView radarView(MajorEvent event, long eventId, RadarEventInterpretation status) {
        if (status == null || "UNAVAILABLE".equals(status.getStatus())) {
            return view(null);
        }
        if (status.getResult() != null) {
            radarReadings.retain(event.getOriginKey(), eventId, status.getResult());
            return view(archive.find(event.getId(), System.currentTimeMillis()).orElse(null));
        }
        var reading = new NewsReportInterpretation();
        reading.setContentVersion(1);
        reading.setStatus(NewsInterpretationStatus.valueOf(status.getStatus()));
        reading.setFailureMessage(status.getFailureMessage());
        return view(reading);
    }

    private NewsReportInterpretationView snapshot(MajorEvent event, boolean generate) {
        long id = event.getId();
        if (generate && capabilities.isInterpretationEnabled()) {
            String token = UUID.randomUUID().toString();
            if (archive.claim(id, token, System.currentTimeMillis())) {
                try {
                    executor.execute(() -> interpretSnapshot(event, token));
                } catch (RejectedExecutionException error) {
                    archive.finish(id, token, null);
                }
            }
        }
        return view(archive.find(id, System.currentTimeMillis()).orElse(null));
    }

    private void interpretSnapshot(MajorEvent event, String token) {
        if (!archive.start(event.getId(), token, System.currentTimeMillis())) {
            return;
        }
        long started = System.nanoTime();
        var report = new NewsReport();
        report.setSourceName(event.getSourceName());
        var input = new NewsReportVersion();
        input.setTitle(event.getTitle());
        input.setContent(event.getSummary());
        input.setVersion(1);
        NewsReportInterpretation.Result result = null;
        try {
            result = agent.interpret(report, input);
        } catch (Exception error) {
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("大事记解读生成失败 id={} errorType={}", event.getId(), error.getClass().getSimpleName());
        }
        archive.finish(event.getId(), token, result);
        try {
            agentRuns.record("major-event-interpretation", result == null ? "FAILED" : "SUCCESS",
                    "major-event:" + event.getId(), null, result == null ? "归档新闻解读生成失败" : null,
                    (System.nanoTime() - started) / 1_000_000L);
        } catch (RuntimeException error) {
            log.warn("大事记解读轨迹保存失败 id={} errorType={}", event.getId(), error.getClass().getSimpleName());
        }
    }

    private NewsReportInterpretationView view(NewsReportInterpretation reading) {
        var result = new NewsReportInterpretationView();
        result.setContentVersion(1);
        result.setAvailable(capabilities.isInterpretationEnabled());
        result.setCurrent(reading);
        return result;
    }
}
