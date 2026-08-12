package com.finscope.domain.industrychain;

import java.time.LocalDateTime;

/** 一次产业链异步生成或刷新运行。 */
public class IndustryChainRevision {
    private Long id;
    private Long chainId;
    private String status;
    private String stage;
    private String message;
    private String errorCode;
    private boolean retryable;
    private LocalDateTime createdAt;
    private String leaseToken;
    private LocalDateTime leaseUpdatedAt;
    private LocalDateTime completedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getChainId() { return chainId; }
    public void setChainId(Long chainId) { this.chainId = chainId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public boolean isRetryable() { return retryable; }
    public void setRetryable(boolean retryable) { this.retryable = retryable; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getLeaseToken() { return leaseToken; }
    public void setLeaseToken(String leaseToken) { this.leaseToken = leaseToken; }
    public LocalDateTime getLeaseUpdatedAt() { return leaseUpdatedAt; }
    public void setLeaseUpdatedAt(LocalDateTime leaseUpdatedAt) { this.leaseUpdatedAt = leaseUpdatedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
}
