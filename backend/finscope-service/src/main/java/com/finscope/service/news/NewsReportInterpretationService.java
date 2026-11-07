package com.finscope.service.news;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.common.exception.ResourceNotFoundException;
import com.finscope.dao.agent.AgentRunRepository;
import com.finscope.dao.news.NewsReportInterpretationRepository;
import com.finscope.dao.news.NewsReportRepository;
import com.finscope.domain.news.NewsReport;
import com.finscope.domain.news.NewsReportInterpretation;
import com.finscope.domain.news.NewsReportInterpretationView;
import com.finscope.domain.news.NewsReportVersion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Service
@Slf4j
public class NewsReportInterpretationService {
    @Resource
    private NewsWorkbenchCapabilities capabilities;
    @Resource
    private NewsReportRepository reports;
    @Resource
    private NewsReportInterpretationRepository interpretations;
    @Resource
    private NewsReportInterpretationAgent agent;
    @Resource
    private AgentRunRepository agentRuns;
    @Resource(name = "newsInterpretationExecutor")
    private Executor executor;

    public NewsReportInterpretationView get(String id, int version) {
        requireReport(id, version);
        return view(id, version);
    }

    public NewsReportInterpretationView request(String id, int version) {
        NewsReport report = requireReport(id, version);
        if (!capabilities.isModelEnabled()) {
            return view(id, version);
        }
        NewsReportVersion input = inputVersion(report, version);
        String token = UUID.randomUUID().toString();
        if (interpretations.tryQueue(id, version, token, System.currentTimeMillis())) {
            try {
                executor.execute(() -> interpret(report, input, token));
            } catch (RejectedExecutionException error) {
                interpretations.finish(id, version, token, null, "解读任务繁忙，请稍后重试");
            }
        }
        return view(id, version);
    }

    private NewsReportInterpretationView view(String id, int version) {
        NewsReportInterpretationView result = new NewsReportInterpretationView();
        result.setAvailable(capabilities.isModelEnabled());
        result.setContentVersion(version);
        result.setCurrent(interpretations.find(id, version, System.currentTimeMillis()).orElse(null));
        if (result.getCurrent() == null || result.getCurrent().getResult() == null) {
            result.setPrevious(interpretations.latestSuccessBefore(id, version).orElse(null));
        }
        return result;
    }

    private NewsReport requireReport(String id, int version) {
        if (id == null || id.isBlank() || id.length() > 256 || version < 1) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID);
        }
        NewsReport report = reports.find(id).orElseThrow(() -> new ResourceNotFoundException("新闻不存在或已超过保留期限"));
        if (version > report.getContentVersion()) {
            throw new ResourceNotFoundException("新闻正文版本不存在");
        }
        return report;
    }

    private NewsReportVersion inputVersion(NewsReport report, int version) {
        if (version != report.getContentVersion()) {
            return reports.findVersion(report.getId(), version)
                    .orElseThrow(() -> new ResourceNotFoundException("新闻正文版本不存在"));
        }
        NewsReportVersion input = new NewsReportVersion();
        input.setReportId(report.getId());
        input.setVersion(version);
        input.setTitle(report.getTitle());
        input.setContent(report.getContent());
        return input;
    }

    private void interpret(NewsReport report, NewsReportVersion input, String token) {
        String id = report.getId();
        int version = input.getVersion();
        if (!interpretations.start(id, version, token)) {
            return;
        }
        long started = System.nanoTime();
        NewsReportInterpretation.Result result = null;
        String failure = null;
        try {
            result = agent.interpret(report, input);
        } catch (Exception error) {
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            failure = "AI 解读生成失败，请重试";
            log.warn("新闻解读失败 reportId={} version={} errorType={}", id, version, error.getClass().getSimpleName());
        }
        interpretations.finish(id, version, token, result, failure);
        try {
            agentRuns.record("news-report-interpretation", result == null ? "FAILED" : "SUCCESS",
                    id + " v" + version, null, failure, (System.nanoTime() - started) / 1_000_000L);
        } catch (RuntimeException error) {
            log.warn("新闻解读轨迹保存失败 reportId={} version={} errorType={}", id, version, error.getClass().getSimpleName());
        }
    }
}
