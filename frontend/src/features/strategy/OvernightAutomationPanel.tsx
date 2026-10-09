import { useEffect, useState, type ReactNode } from 'react';
import { api } from '../../shared/api/client';
import type { OvernightAutomationState, OvernightMode, OvernightReport } from './overnightTypes';
import { OvernightHistoryStatus } from './OvernightHistoryStatus';
import { OvernightJointPanel, OvernightJointRanking } from './OvernightJointPanel';
import './OvernightAutomationPanel.css';
import { OvernightSheet } from './OvernightSheet';
import { OvernightPerformance } from './OvernightPerformance';

const states: Record<string, string> = {
  COMPLETED: '研究完成', RUNNING: '正在处理', FAILED: '执行失败', MISSED: '错过窗口',
  PARTIAL: '部分完成', INTERRUPTED: '运行中断', EMPTY: '暂无合格候选', SKIPPED: '条件不满足',
  WATCH: '研究已冻结', INSUFFICIENT_DATA: '样本积累中', DATA_UNAVAILABLE: '分钟行情不足',
  CALENDAR_UNAVAILABLE: '日历未覆盖', BEFORE_CUTOFF: '等待决策时刻',
};
const formatTime = (value?: string) => value ? value.slice(0, 16).replace('T', ' ') : '待确认';

export function OvernightAutomationPanel({ mode, records, renderReport, view = 'today', revision = 0 }: {
  view?: 'today' | 'performance'; revision?: number;
  mode: OvernightMode; records: OvernightReport[]; renderReport: (report: OvernightReport) => ReactNode;
}) {
  const [state, setState] = useState<OvernightAutomationState>();
  const [error, setError] = useState('');
  const [tool, setTool] = useState<'runtime' | 'research'>();
  const [selected, setSelected] = useState<string>();
  useEffect(() => { setSelected(undefined); }, [mode]);
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

  const selectedResult = results.find(({ job, result }) => `${job.key}-${result.instrumentCode}` === selected);
  const dataThrough = results.map(({ result }) => records.find(item => item.id === result.reportId)?.dataThrough)
    .filter((value): value is string => !!value).sort().slice(-1)[0];

  return <section className="overnight-auto" aria-label="自动发现与持仓研判">
    <div className="overnight-command-bar">
      <span className="overnight-auto-status" data-active={!!state?.enabled && healthy && !error}>{headline}</span>
      <div><button type="button" onClick={() => setTool('runtime')}>运行状态 ↗</button>
      <button type="button" onClick={() => setTool('research')}>模型研究 ↗</button></div>
    </div>
    {error && <p role="alert" className="overnight-auto-notice">{error}。连接恢复后自动刷新；下方保留上次读取的记录。</p>}
    {view === 'performance' ? <OvernightPerformance state={state?.joint} mode={mode} revision={revision} /> : <>
    <div className="overnight-session">
      <div><span className="overnight-auto-kicker">{mode === 'TAIL_ENTRY' ? '收盘之前 · 自动发现' : '收盘之后 · 真实持仓'}</span>
        <h4>{mode === 'TAIL_ENTRY' ? '为下一交易日，提前看一步' : '从已有持仓，判断下一步'}</h4>
        <p>{mode === 'TAIL_ENTRY' ? '候选按决策窗口留档，次日自动核验。入选观察不等于看涨。' : '自动读取账本，分别观察次日涨跌与四个退出时点。'}</p></div>
      <div className="overnight-session-time"><span>{mode === 'TAIL_ENTRY' ? '下一判断窗口' : '盘后研判窗口'}</span>
        <strong>{mode === 'TAIL_ENTRY' ? state?.nextTailAt?.slice(11, 16) ?? '—' : '15:10'}</strong>
        <small>{mode === 'TAIL_ENTRY' ? state?.nextTailAt?.slice(0, 10) ?? '等待交易日历' : '数据截止 15:00 · 上海时间'}</small></div>
    </div>
    {state && !state.calendarAvailable && <p className="overnight-auto-notice">已核验的交易日历未覆盖下一窗口，自动研究暂不推断日期。</p>}
    {state?.calendarAvailable && !state.tradingDay && <p className="overnight-auto-notice">当前为非交易日，下一交易日将自动恢复扫描与持仓研判。</p>}
    {mode === 'AFTER_CLOSE_HOLDING' && state && !state.ledgerFresh && <p className="overnight-auto-notice">账本尚未同步或已过期。收到新快照后自动研判。</p>}
    {mode === 'AFTER_CLOSE_HOLDING' && state?.holdingStatus === 'EMPTY' && <p className="overnight-auto-notice">账本当前没有未平仓股票。记入真实买入后，盘后会自动纳入研判。</p>}
    {mode === 'AFTER_CLOSE_HOLDING' && state?.holdingStatus === 'WINDOW_CLOSED' && <p className="overnight-auto-notice">今日盘后窗口已结束。未完成的持仓将在下一交易日继续处理。</p>}
    <div className="overnight-auto-section-title"><h5>{mode === 'TAIL_ENTRY' ? '隔夜观察候选' : '持仓研判'} <em>{results.length}</em></h5><span>{latestDay ? `最近窗口 ${latestDay} · 行情截止 ${formatTime(dataThrough)}` : '等待首个交易窗口'}</span></div>
    {!results.length && <div className="overnight-quiet-state">
      <span className="overnight-window-mark" aria-hidden="true">T → T+1</span>
      <h4>{!state ? error ? '暂时无法读取研究状态' : '正在读取研究状态' : problem ? states[problem.status] : pool?.candidates?.length ? '候选已准备，等待决策窗口' : latest.some(job => job.status === 'EMPTY') ? '本轮暂无合格候选' : '等待自动研究结果'}</h4>
      <p>{problem ? `${problem.cutoff} ${states[problem.status]}：${problem.reason ?? '请查看运行状态'}` : mode === 'TAIL_ENTRY' ? '14:20 / 14:40 扫描候选，14:30 / 14:45 冻结判断。保持服务运行即可，无需手动选股。' : '交易日 15:10 起自动研判真实持仓，成本与数量来自账本。'}</p>
      {!!pool?.candidates?.length && <small>{pool.candidates.map(item => item.instrumentName || item.instrumentCode).join(' · ')}</small>}
    </div>}
    <div className="overnight-candidate-list">{results.map(({ job, result }) => {
      const candidate = job.candidates?.find(item => item.instrumentCode === result.instrumentCode);
      const report = records.find(item => item.id === result.reportId);
      const direction = report?.closeDirection;
      const target = report?.targets.find(item => item.target === '10:00');
      const probability = direction?.upProbability;
      return <button type="button" className="overnight-candidate" key={`${job.key}-${result.instrumentCode}`}
        onClick={() => setSelected(`${job.key}-${result.instrumentCode}`)}>
        <span className="overnight-candidate-name"><b>{candidate?.instrumentName ?? job.instrumentName ?? result.instrumentCode}</b><small>{result.instrumentCode} · {job.cutoff}</small></span>
        <span><small>次日收盘上涨概率</small><strong>{probability == null ? '—' : `${(probability * 100).toFixed(1)}%`}</strong><small>{direction?.selectedModel === 'PRIOR' ? '历史比例参考' : probability == null ? '等待有效预测' : direction?.validated ? '已通过前瞻对照' : '模型判断 · 验证中'}</small></span>
        <span><small>次日 10:00 净收益参考</small><strong>{target?.expectedNetReturn == null ? '—' : `${(target.expectedNetReturn * 100).toFixed(2)}%`}</strong><small>{target?.probabilitySource === 'HISTORICAL_BASELINE' ? '历史平均 · 已扣假设成本' : '已扣假设成本'}</small></span>
        <span className="overnight-candidate-state"><small>{states[result.status] ?? result.status}</small><small>{target?.lowerNetReturn == null ? '风险区间待验证' : `收益区间下界 ${(target.lowerNetReturn * 100).toFixed(1)}%`}</small><b>查看依据 ↗</b></span>
      </button>;
    })}</div>
    </>}
    {selectedResult && <OvernightSheet title={`${selectedResult.job.candidates?.find(item => item.instrumentCode === selectedResult.result.instrumentCode)?.instrumentName ?? selectedResult.job.instrumentName ?? selectedResult.result.instrumentCode} · ${selectedResult.job.cutoff}`} onClose={() => setSelected(undefined)}>
      <p>数据窗口 {selectedResult.job.signalDate} {selectedResult.job.cutoff} · 入选观察不等于看涨。</p>
      {selectedResult.result.reason && <p>{selectedResult.result.reason}</p>}
      {(() => {
        const report = records.find(item => item.id === selectedResult.result.reportId);
        return report ? renderReport(report) : <p>{selectedResult.result.warnings?.join('；') || '完整档案尚未出现在最近 50 份记录中；请稍后刷新或查看历史复盘。'}</p>;
      })()}
    </OvernightSheet>}
    {tool && <OvernightSheet title={tool === 'runtime' ? '运行状态' : '模型研究'} onClose={() => setTool(undefined)}>
      {tool === 'research' ? <><OvernightJointPanel state={state?.joint} mode={mode} />
        {!state?.joint && <p>模型信息尚未就绪，连接恢复后自动更新。</p>}
        {mode === 'TAIL_ENTRY' && research.map(job => <OvernightJointRanking key={job.key} job={job} />)}
        <OvernightHistoryStatus history={state?.history} /></> : <>
        <p>{headline} · 最近心跳 {formatTime(state?.heartbeat?.lastTickAt)}</p>
        <p>心跳代表任务服务状态，不代表行情新鲜度。页面每 30 秒自动更新。</p>
        {state?.heartbeat?.error && <p role="alert">{state.heartbeat.error}</p>}
        <div className="overnight-run-list">{latest.map(job => <article key={job.key}><header><b>{job.cutoff} · {job.phase === 'DISCOVER' ? '市场扫描' : job.instrumentName ?? '自动研判'}</b><span>{states[job.status] ?? job.status}</span></header>
          <p>{job.reason ?? '暂无补充信息'}</p>{job.scope && <small>{job.scope} · 读取 {job.observedCount} 只 / 时间有效 {job.freshCount} 只</small>}</article>)}</div>
        {!latest.length && <p>当前场景暂无运行记录。</p>}
      </>}
    </OvernightSheet>}
  </section>;
}
