import { useEffect, useState, type ReactNode } from 'react';
import { api } from '../../shared/api/client';
import type { OvernightAutomationState, OvernightMode, OvernightReport } from './overnightTypes';
import './OvernightAutomationPanel.css';

const states: Record<string, string> = {
  COMPLETED: '研究完成', RUNNING: '正在处理', FAILED: '执行失败', MISSED: '错过窗口',
  PARTIAL: '部分完成', INTERRUPTED: '运行中断', EMPTY: '暂无合格候选', SKIPPED: '条件不满足',
  WATCH: '研究已冻结', INSUFFICIENT_DATA: '样本积累中', DATA_UNAVAILABLE: '分钟行情不足',
  CALENDAR_UNAVAILABLE: '日历未覆盖', BEFORE_CUTOFF: '等待决策时刻',
};
const formatTime = (value?: string) => value ? value.slice(0, 16).replace('T', ' ') : '待确认';

export function OvernightAutomationPanel({ mode, records, renderReport }: {
  mode: OvernightMode; records: OvernightReport[]; renderReport: (report: OvernightReport) => ReactNode;
}) {
  const [state, setState] = useState<OvernightAutomationState>();
  const [error, setError] = useState('');
  useEffect(() => {
    let active = true;
    let pending = false;
    async function load() {
      if (pending) {
        return;
      }
      pending = true;
      try {
        const value = await api<OvernightAutomationState>('/api/quant/overnight/automation');
        if (!Array.isArray(value.jobs)) {
          throw new Error('请重启 Java 与 Python 服务以加载自动研究接口');
        }
        if (active) {
          setState(value);
          setError('');
        }
      } catch (reason) {
        if (active) {
          setError(reason instanceof Error ? reason.message : '自动运行状态读取失败');
        }
      } finally {
        pending = false;
      }
    }
    void load();
    const timer = window.setInterval(() => { void load(); }, 30000);
    return () => { active = false; window.clearInterval(timer); };
  }, []);

  const jobs = state?.jobs.filter(job => job.mode === mode) ?? [];
  const days = jobs.map(job => job.signalDate).sort();
  const latestDay = days[days.length - 1];
  const latest = jobs.filter(job => job.signalDate === latestDay);
  const research = latest.filter(job => job.phase === 'PREDICT');
  const pool = latest.find(job => job.phase === 'DISCOVER' && job.status === 'COMPLETED');
  const results = research.flatMap(job => (job.results ?? []).map(result => ({ job, result })));
  const problem = latest.find(job => ['FAILED', 'MISSED', 'INTERRUPTED', 'SKIPPED'].includes(job.status));
  const heartbeatAge = state?.heartbeat ? Date.parse(`${state.serverTime}+08:00`) - Date.parse(`${state.heartbeat.lastTickAt}+08:00`) : Infinity;
  const running = state?.jobs.some(job => job.status === 'RUNNING');
  const healthy = !!state?.heartbeat && !state.heartbeat.error && (heartbeatAge < 120000 || (running && heartbeatAge < 600000));
  const headline = error ? '自动状态暂不可用' : !state ? '正在读取自动状态' : !state.enabled ? '自动研究已暂停'
    : !healthy ? '等待自动任务恢复' : '自动研究已开启';

  return <section className="overnight-auto" aria-label="自动发现与持仓研判">
    <header className="overnight-auto-heading"><div><span className="overnight-auto-kicker">自动发现 · 独立留档</span><h4>{headline}</h4>
      <p>{mode === 'TAIL_ENTRY' ? '从当日成交活跃、走势较强的股票中自动筛选，到点生成隔夜研究。' : '收盘后自动读取真实持仓，比较次日开盘、10:00、14:30 与收盘四个退出时点。'}</p></div>
      <span className="overnight-auto-status" data-active={!!state?.enabled && healthy && !error}>{state?.enabled && healthy && !error ? '后台运行' : '待就绪'}</span></header>
    <div className="overnight-auto-facts">
      <div><span>{mode === 'TAIL_ENTRY' ? '下一次尾盘判断' : '账本同步状态'}</span><strong>{mode === 'TAIL_ENTRY' ? formatTime(state?.nextTailAt) : state?.ledgerFresh ? `${state.positionCount} 只真实持仓` : '等待账本同步'}</strong></div>
      <div><span>{mode === 'TAIL_ENTRY' ? '每个窗口研究上限' : '盘后研判窗口'}</span><strong>{mode === 'TAIL_ENTRY' ? `${state?.candidateLimit ?? '—'} 只` : '15:10—18:00'}</strong></div>
      <div><span>次日结果核验</span><strong>自动轮换更新</strong></div>
    </div>
    {error && <p role="alert" className="overnight-auto-notice">{error}。连接恢复后自动刷新；下方保留上次读取的记录。</p>}
    {state && !state.calendarAvailable && <p className="overnight-auto-notice">已核验的交易日历未覆盖下一窗口，自动研究暂不推断日期。</p>}
    {state?.calendarAvailable && !state.tradingDay && <p className="overnight-auto-notice">当前为非交易日，下一交易日将自动恢复扫描与持仓研判。</p>}
    {mode === 'AFTER_CLOSE_HOLDING' && state && !state.ledgerFresh && <p className="overnight-auto-notice">账本尚未同步或已过期。Java 服务每分钟同步一次；收到新快照后自动研判。</p>}
    {mode === 'AFTER_CLOSE_HOLDING' && state?.holdingStatus === 'EMPTY' && <p className="overnight-auto-empty">账本当前没有未平仓股票。记入真实买入后，盘后会自动纳入研判。</p>}
    {mode === 'AFTER_CLOSE_HOLDING' && state?.holdingStatus === 'WINDOW_CLOSED' && <p className="overnight-auto-notice">今日盘后自动窗口已结束；未完成的持仓等待下个交易日，历史补充研究可在下方进行。</p>}
    <div className="overnight-auto-section-title"><h5>{mode === 'TAIL_ENTRY' ? '自动发现的隔夜候选' : '自动生成的持仓研判'}</h5><span>{latestDay ? `最近记录 · ${latestDay}` : '等待首个交易窗口'}</span></div>
    {!results.length && problem && <p className="overnight-auto-notice">{problem.cutoff} {states[problem.status]}：{problem.reason ?? '查看运行记录了解详情'}</p>}
    {!results.length && <div className="overnight-auto-empty"><b>{pool?.candidates?.length ? `已准备 ${pool.candidates.length} 只候选，等待 ${pool.cutoff} 判断` : '暂未形成自动研究结果'}</b>
      <p>{mode === 'TAIL_ENTRY' ? '14:20 / 14:40 扫描候选，14:30 / 14:45 截断分钟数据并冻结研究。无需手动填写名单。' : '交易日 15:10 起处理真实持仓。成本、数量和建仓日直接来自账本。'}</p>
      {pool?.candidates?.length ? <p>{pool.candidates.map(item => `${item.instrumentName} ${item.instrumentCode}`).join(' · ')}</p> : null}</div>}
    <div className="overnight-auto-results">{results.map(({ job, result }) => {
      const candidate = job.candidates?.find(item => item.instrumentCode === result.instrumentCode);
      const report = records.find(item => item.id === result.reportId);
      return <details key={`${job.key}-${result.instrumentCode}`} className="overnight-auto-stock">
        <summary><div><b>{candidate?.instrumentName ?? job.instrumentName ?? result.instrumentCode}</b><small>{result.instrumentCode} · {job.cutoff}</small></div>
          <span data-state={result.status}>{states[result.status] ?? result.status}</span><span className="overnight-auto-detail-hint">查看依据</span></summary>
        {candidate?.changePct != null && <p className="overnight-auto-detail-note">筛选时涨幅 {candidate.changePct.toFixed(2)}% · 量比 {candidate.volumeRatio?.toFixed(2) ?? '—'}。筛选顺序不代表上涨概率。</p>}
        {result.reason && <p className="overnight-auto-detail-note">{result.reason}</p>}
        {report ? renderReport(report) : <p className="overnight-auto-detail-note">{result.warnings?.join('；') || '完整档案尚未出现在最近记录中，页面将自动刷新。'}</p>}
      </details>;
    })}</div>
    {latest.length > 0 && <details className="overnight-auto-log"><summary>运行记录与未生成原因 <span>{latest.length} 项</span></summary>
      {latest.map(job => <div key={job.key}><span>{job.cutoff} · {job.phase === 'DISCOVER' ? '市场扫描' : job.instrumentName ?? '自动研判'}</span><b>{states[job.status] ?? job.status}</b>
        {job.reason && <p>{job.reason}</p>}{job.scope && <p>{job.scope} · 读取 {job.observedCount} 只，时间有效 {job.freshCount} 只</p>}</div>)}</details>}
    <footer>保持服务运行即可自动执行；数据不足会列明原因，次日真实结果自动回填。{state?.heartbeat && <span>最近运行 {formatTime(state.heartbeat.lastTickAt)} · 页面每 30 秒刷新</span>}</footer>
  </section>;
}
