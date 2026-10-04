import type { OvernightAutomationState } from './overnightTypes';

export function OvernightHistoryStatus({ history }: { history: OvernightAutomationState['history'] }) {
  if (!history) {
    return null;
  }
  const ready = history.coverage.filter(row => row.completeDays >= history.desiredDays).length;
  const running = history.jobs.find(job => job.status === 'RUNNING');
  const failed = history.jobs.filter(job => job.status === 'FAILED');
  return <details className="overnight-auto-log overnight-history-status">
    <summary><b>历史样本自动补齐</b><span>{running ? `正在获取 ${running.instrumentCode}` : `${ready} 只已覆盖 ${history.desiredDays} 个完整交易日`}</span></summary>
    <p>自动为公共股票池、候选股和持仓补充近一年 5 分钟行情，无需 Key 或手动设置。公共池持续更新；尾盘决策窗口暂停补数。</p>
    <p>完整交易日只表示行情覆盖；通过交易日历、成交条件和独立验证后，才会计入模型样本。</p>
    {history.coverage.slice(0, 20).map(row => <div key={row.instrumentCode}>
      <span>{row.instrumentCode}</span><b>{row.completeDays} 个完整交易日</b>
      <p>{row.firstDate} 至 {row.lastDate} · {row.barCount.toLocaleString()} 根分钟线</p>
    </div>)}
    {failed.slice(0, 5).map(job => <div key={job.key}><span>{job.instrumentCode}</span><b>补数暂未完成</b><p>{job.reason}</p></div>)}
    {!history.coverage.length && !history.jobs.length && <p>后台将从已有日线缓存建立公共股票池，无需先填写名单或持仓。</p>}
    <p>实时分钟：东方财富 · 历史补数：BaoStock 未复权。历史补数不计作前瞻预测成绩。</p>
  </details>;
}
