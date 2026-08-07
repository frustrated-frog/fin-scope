package com.finscope.service.intake;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.dao.task.TaskRepository;
import com.finscope.domain.intake.FetchBatch;
import com.finscope.domain.task.AsyncTask;
import com.finscope.domain.task.TaskPhase;
import com.finscope.service.task.TaskProgressEvent;
import com.finscope.service.task.TaskProgressPublisher;
import com.finscope.service.task.TaskView;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.UUID;
import java.util.concurrent.Executor;
import com.finscope.common.exception.BizErrorCode;

/** 将耗时的信息源抓取放入已有的持久化任务与 SSE 通道，页面可随时恢复任务状态。 */
@Service
public class IntakeFetchTaskService {
    private static final String TASK_TYPE = "SOURCE_INTAKE_FETCH";

    @Resource private TaskRepository taskRepository;
    @Resource private IntakeService intakeService;
    @Resource private TaskProgressPublisher taskProgressPublisher;
    @Resource(name = "ingestTaskExecutor") private Executor executor;

    public TaskView submit(Long sourceId) {
        if (sourceId == null) throw new BusinessException(BizErrorCode.SOURCE_ID_REQUIRED);
        String taskId = UUID.randomUUID().toString();
        AsyncTask task = AsyncTask.queued(taskId, TASK_TYPE, "source:" + sourceId);
        task.setMessage("等待抓取信息源");
        taskRepository.create(task);
        executor.execute(() -> run(taskId, sourceId));
        return TaskView.from(task, null);
    }

    private void run(String taskId, Long sourceId) {
        try {
            FetchBatch batch = intakeService.intakeFetch(sourceId, "MANUAL", null,
                    phase -> progress(taskId, phase, messageFor(phase)));
            if ("FAILED".equals(batch.getStatus())) throw new IllegalStateException(batch.getErrorMessage());
            String message = "候选池已更新：" + batch.getCandidateCount() + " 条候选";
            taskRepository.complete(taskId, null, message);
            publish(taskId, "COMPLETED", TaskPhase.COMPLETED, message, null, true);
        } catch (Exception ex) {
            String message = ex.getMessage() == null || ex.getMessage().trim().isEmpty() ? "信息源摄入失败" : ex.getMessage();
            taskRepository.fail(taskId, message);
            publish(taskId, "FAILED", TaskPhase.FAILED, message, message, true);
        }
    }

    private String messageFor(TaskPhase phase) {
        if (phase == TaskPhase.FETCHING) return "正在抓取信息源";
        if (phase == TaskPhase.PARSING) return "正在筛选候选内容";
        if (phase == TaskPhase.LLM) return "正在进行 Agent 预审与批次汇总";
        if (phase == TaskPhase.PERSISTING) return "正在写入文章库";
        return "正在处理信息源";
    }

    private void progress(String taskId, TaskPhase phase, String message) {
        if (phase == TaskPhase.FETCHING) taskRepository.markRunning(taskId, phase, message);
        else taskRepository.updatePhase(taskId, phase, message);
        publish(taskId, "RUNNING", phase, message, null, false);
    }

    private void publish(String taskId, String status, TaskPhase phase, String message, String error, boolean terminal) {
        TaskView view = new TaskView();
        view.setTaskId(taskId); view.setType(TASK_TYPE); view.setStatus(status); view.setPhase(phase.name());
        view.setMessage(message); view.setErrorMessage(error);
        try { taskProgressPublisher.publish(taskId, TaskProgressEvent.phase(view)); }
        finally { if (terminal) taskProgressPublisher.complete(taskId); }
    }
}
