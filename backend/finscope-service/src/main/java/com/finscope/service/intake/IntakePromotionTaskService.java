package com.finscope.service.intake;

import com.finscope.dao.task.TaskRepository;
import com.finscope.domain.intake.PromoteIntakeCandidateResponse;
import com.finscope.domain.task.AsyncTask;
import com.finscope.domain.task.TaskPhase;
import com.finscope.service.task.TaskProgressEvent;
import com.finscope.service.task.TaskProgressPublisher;
import com.finscope.service.task.TaskView;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.UUID;
import java.util.concurrent.Executor;

@Service
public class IntakePromotionTaskService {
    @Resource private TaskRepository taskRepository;
    @Resource private IntakeService intakeService;
    @Resource private TaskProgressPublisher publisher;
    @Resource(name = "ingestTaskExecutor") private Executor executor;

    public TaskView submit(Long candidateId) {
        String id = UUID.randomUUID().toString();
        AsyncTask task = AsyncTask.queued(id, "INTAKE_PROMOTE", "candidate:" + candidateId);
        task.setMessage("等待入库"); taskRepository.create(task);
        executor.execute(() -> run(id, candidateId));
        return TaskView.from(task, null);
    }
    private void run(String taskId, Long candidateId) {
        try {
            PromoteIntakeCandidateResponse result = intakeService.promote(candidateId,
                    phase -> progress(taskId, phase));
            String message = result.getWorkflowStatus() == null || "SUCCESS".equals(result.getWorkflowStatus())
                    ? "文章已入库，研究工作包已生成" : result.getWorkflowSummary();
            taskRepository.complete(taskId, result.getArticleId(), message);
            terminal(taskId, "COMPLETED", message, null, result.getArticleId());
        } catch (Exception ex) {
            String message = ex.getMessage() == null ? "文章入库失败" : ex.getMessage();
            taskRepository.fail(taskId, message); terminal(taskId, "FAILED", message, message, null);
        }
    }
    private void progress(String id, TaskPhase phase) {
        String message = phase == TaskPhase.PERSISTING ? "正在写入文章库" : phase == TaskPhase.LLM ? "正在生成情报卡片和研究工作包" : "正在处理文章";
        if (phase == TaskPhase.FETCHING) taskRepository.markRunning(id, phase, message); else taskRepository.updatePhase(id, phase, message);
        publish(id, "RUNNING", phase, message, null, null, false);
    }
    private void terminal(String id, String status, String message, String error, Long articleId) { publish(id, status, "COMPLETED".equals(status) ? TaskPhase.COMPLETED : TaskPhase.FAILED, message, error, articleId, true); }
    private void publish(String id, String status, TaskPhase phase, String message, String error, Long articleId, boolean terminal) {
        TaskView view = new TaskView(); view.setTaskId(id); view.setStatus(status); view.setPhase(phase.name()); view.setMessage(message); view.setErrorMessage(error); view.setArticleId(articleId);
        try { publisher.publish(id, TaskProgressEvent.phase(view)); } finally { if (terminal) publisher.complete(id); }
    }
}
