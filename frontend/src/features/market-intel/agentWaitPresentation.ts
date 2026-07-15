const FALLBACK_MESSAGES: Record<string, string> = {
  LLM_NOT_CONFIGURED: '模型尚未配置，已自动展示规则解读。',
  LLM_TIMEOUT: '模型在 60 秒内未完成，已自动展示规则解读。',
  INVALID_MODEL_OUTPUT: '模型输出格式无效，已自动展示规则解读。',
  OUTPUT_REJECTED_BY_GATE: '模型结论未通过证据门禁，已自动展示规则解读。',
  INSUFFICIENT_FACTOR_COVERAGE: '有效因子维度不足，未调用模型。',
  EXECUTOR_REJECTED: '分析任务暂未被执行，请稍后重试。',
  UNKNOWN: 'Agent 执行异常，本次未生成模型解读，请稍后重试。'
};

export function agentWaitMessage(elapsedSeconds: number) {
  if (elapsedSeconds < 10) return '正在整理因子与资金证据…';
  if (elapsedSeconds < 30) return '模型正在分析资金行为，请稍候…';
  return '模型响应较慢，仍在继续；超时后会自动展示规则解读。';
}

export function agentWaitButtonLabel(elapsedSeconds: number) {
  return `Agent 解读中 · ${elapsedSeconds}s`;
}

export function capitalAgentStatusMessage(reason: string) {
  return FALLBACK_MESSAGES[reason] ?? `解读已降级：${reason}`;
}
