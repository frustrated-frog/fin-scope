import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { api } from '../../shared/api/client';
import type { CapturePlan, CaptureState, OvernightMode, OvernightValidation } from './overnightTypes';
import './OvernightAuditPanel.css';

const names: Record<string, string> = { OPEN: '次日开盘', '10:00': '次日 10:00', '14:30': '次日 14:30', CLOSE: '次日收盘' };
const states: Record<string, string> = { RUNNING: '采集中', COMPLETED: '采集结束', MISSED: '错过窗口', INTERRUPTED: '采集中断',
  WATCH: '预测已冻结', INSUFFICIENT_DATA: '训练样本不足', DATA_UNAVAILABLE: '分钟行情缺失', FAILED: '采集失败',
  PENDING: '等待行情', PARTIAL: '部分到期', SETTLED: '全部到期', ENTRY_UNVERIFIED: '入场无法验证' };
const percent = (value: number | null) => value == null ? '—' : `${(value * 100).toFixed(2)}%`;
const decimal = (value: number | null) => value == null ? '—' : value.toFixed(3);

export function OvernightAuditPanel({ mode, revision }: { mode: OvernightMode; revision: number }) {
  const [capture, setCapture] = useState<CaptureState>();
  const [validation, setValidation] = useState<OvernightValidation>();
  const [codes, setCodes] = useState('');
  const [cost, setCost] = useState(20);
  const [enabled, setEnabled] = useState(false);
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState('');
  const [evidence, setEvidence] = useState('FORWARD');
  const load = useCallback(async (resetForm = false) => {
    try {
      const [state, summary] = await Promise.all([
        api<CaptureState>('/api/quant/overnight/capture'), api<OvernightValidation>('/api/quant/overnight/validation'),
      ]);
      if (!state.plan || !Array.isArray(summary.groups)) {
        throw new Error('服务尚未提供新版留档接口，请重启 Java 和 Python');
      }
      setCapture(state); setValidation(summary); setError('');
      if (resetForm) {
        setCodes(state.plan.instrumentCodes.join(', ')); setCost(state.plan.costBps); setEnabled(state.plan.enabled);
      }
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '验收信息读取失败');
    }
  }, []);
  useEffect(() => { void load(true); }, [load]);
  useEffect(() => {
    if (revision > 0) {
      void load();
    }
  }, [revision, load]);
  useEffect(() => {
    const timer = window.setInterval(() => { void load(); }, 30000);
    return () => window.clearInterval(timer);
  }, [load]);

  async function save(event: FormEvent) {
    event.preventDefault(); setSaving(true); setError(''); setSaved('');
    try {
      const plan = await api<CapturePlan>('/api/quant/overnight/capture', { method: 'POST', body: JSON.stringify({
        enabled, instrumentCodes: codes.split(/[\s,，;；]+/).filter(Boolean), costBps: cost,
      }) });
      setCodes(plan.instrumentCodes.join(', ')); setSaved(plan.enabled ? '计划已保存，从后续完整窗口开始留档。' : '自动留档已暂停，已有档案继续核验。');
      await load();
    } catch (reason) { setError(reason instanceof Error ? reason.message : '计划保存失败'); }
    finally { setSaving(false); }
  }
  const groups = validation?.groups.filter(group => group.mode === mode && group.evidenceKind === evidence) ?? [];
  return <section className="overnight-audit" aria-label="自动留档与全历史验收">
    <header className="overnight-audit-heading"><div><span>记录当时，再看次日</span><h4>预测验证台</h4></div><button type="button" onClick={() => void load()}>刷新验收</button></header>
    {error && <p role="alert" className="overnight-error">{error}</p>}
    {mode === 'TAIL_ENTRY' && <div className="overnight-capture-grid">
      <form className="overnight-capture-form" onSubmit={save}>
        <h5>尾盘自动留档 <span>{capture?.plan.enabled ? '已启用' : '未启用'}</span></h5>
        <p>交易日 <b>14:30 / 14:45</b> 采集观察名单。请保持 Python 服务运行；错过窗口只记漏跑。</p>
        <label>自动观察名单<textarea aria-label="自动观察名单" value={codes} onChange={event => setCodes(event.target.value)} placeholder="605058.SH, 000001.SZ" rows={2} disabled={saving || !capture} /><small>最多 10 只沪深股票，用逗号或换行分隔。与手动研究股票独立。</small></label>
        <div className="overnight-capture-controls"><label>自动留档成本（基点）<input type="number" min={0} max={200} step={1} value={cost} onChange={event => setCost(Number(event.target.value))} disabled={saving || !capture} required /></label><label className="overnight-capture-toggle"><input type="checkbox" checked={enabled} onChange={event => setEnabled(event.target.checked)} disabled={saving || !capture} />启用自动留档</label></div>
        <button type="submit" disabled={saving || !capture}>{saving ? '保存中…' : '保存留档计划'}</button>
        {saved && <p role="status">{saved}</p>}
        {capture && !capture.calendarAvailable && <p className="overnight-error">当前交易日历未覆盖，自动预测暂停推断。</p>}
      </form>
      <section className="overnight-capture-log" aria-label="最近留档窗口"><h5>最近留档窗口</h5>
        {!capture?.runs.length && <p>尚无留档窗口。保存并启用计划后，从下一次窗口开始积累；历史日期不会补造预测。</p>}
        {capture?.runs.slice(0, 6).map(run => <details key={`${run.signalDate}-${run.cutoff}`}><summary><time>{run.signalDate} <b>{run.cutoff}</b></time><span data-state={run.status}>{states[run.status] ?? run.status}</span></summary>
          <p>{run.instrumentCodes.length} 只观察股票 · {run.costBps} 基点{run.reason ? ` · ${run.reason}` : ''}</p>
          {run.results.map(result => <p key={result.instrumentCode}><b>{result.instrumentCode}</b> · {states[result.status] ?? result.status}{result.evidenceKind === 'RETROSPECTIVE' ? ' · 超时完成，仅列历史回顾' : ''}{result.reason ? ` · ${result.reason}` : ''}{result.warnings?.map(warning => <small key={warning}>{warning}</small>)}</p>)}
        </details>)}
        {capture && <small>服务时间 {capture.serverTime.slice(0, 19).replace('T', ' ')}（上海） · 每 30 秒刷新</small>}
      </section>
    </div>}
    <section className="overnight-validation" aria-label="隔夜全历史验收">
      <div className="overnight-validation-heading"><div><h5>{mode === 'TAIL_ENTRY' ? '尾盘入场' : '盘后持仓'} · 全历史验收</h5><p>共 {validation?.recordCount ?? '—'} 份冻结档案；按场景、时点、费用和模型版本分开统计。</p></div><label>验收记录范围<select value={evidence} onChange={event => setEvidence(event.target.value)}><option value="FORWARD">当时生成</option><option value="RETROSPECTIVE">历史回顾</option></select></label></div>
      {!groups.length && <p className="overnight-audit-empty">该场景尚无{evidence === 'FORWARD' ? '当时生成' : '历史回顾'}档案。概率、收益和命中率保持空缺，等待真实记录到期。</p>}
      {groups.map(group => <article className="overnight-validation-group" key={`${group.modelVersion}-${group.cutoff}-${group.costBps}`}>
        <header><b>{group.cutoff} · {group.costBps} 基点</b><code>{group.modelVersion}</code><span>{group.recordCount} 份档案</span></header>
        <p>{Object.entries(group.statuses).map(([name, count]) => `${states[name] ?? name} ${count}`).join(' · ')}</p>
        {!group.targets.length && <p>尚无具备预测概率和到期行情的配对样本。</p>}
        {group.targets.length > 0 && <div className="overnight-audit-table"><table><caption>按目标交易日等权，收益已扣除冻结成本假设</caption><thead><tr><th>退出时点</th><th>记录 / 交易日</th><th>方向准确率</th><th>概率误差 / 基准</th><th>全部参与</th><th>概率 ≥ 50% 参与</th></tr></thead><tbody>{group.targets.map(target => <tr key={target.target}><th>{names[target.target]}</th><td>{target.count} / {target.days}<small>{target.days < 60 ? '初期观察' : '仍需持续验证'}</small></td><td>{percent(target.accuracy)}</td><td>{decimal(target.pairedBrier)} / {decimal(target.baselineBrier)}<small>配对 {target.baselineCount} 条 · 越低越好</small></td><td>{percent(target.meanNetReturn)}</td><td>{percent(target.selectedNetReturn)}<small>参与 {target.selectedCount} 条</small></td></tr>)}</tbody></table></div>}
        <details><summary>概率分组与缺失原因</summary>{group.targets.map(target => <div key={target.target}><h6>{names[target.target]} · 概率可靠性</h6><div className="overnight-calibration">{target.bins.map(bin => <div key={bin.lower}><b>{Math.round(bin.lower * 100)}–{Math.round(bin.upper * 100)}%</b><span>预测 {percent(bin.predicted)}</span><span>实际盈利 {percent(bin.actual)}</span><small>{bin.count} 条 / {bin.days} 天</small></div>)}</div></div>)}{Object.entries(group.missingReasons).map(([reason, count]) => <p key={reason}>{reasonLabel(reason)} · {count} 条</p>)}</details>
      </article>)}
      <p className="overnight-audit-method">概率基准是预测时冻结的历史盈利比例，旧记录缺失时不追补。两种参与方式使用同一观察名单、日期和费用；概率不足 50% 时不参与，当日全部不参与记零收益。收益为价格代理，尚未证明可以实际成交。</p>
      <details className="overnight-audit-method"><summary>验收口径与限制</summary>{validation?.limitations.map(text => <p key={text}>{text}</p>)}</details>
    </section>
  </section>;
}

function reasonLabel(reason: string) {
  const labels: Record<string, string> = { ENTRY_DATA_MISSING: '入场分钟行情缺失', CALENDAR_UNAVAILABLE: '交易日历未覆盖', ENTRY_NOT_EXECUTABLE: '入场区间无成交额或为一字价格' };
  if (labels[reason]) {
    return labels[reason];
  }
  if (reason.startsWith('PREDICTION:')) {
    const status = reason.slice('PREDICTION:'.length);
    return `预测未形成：${states[status] ?? status}`;
  }
  const marker = reason.lastIndexOf(':');
  const target = reason.slice(0, marker);
  return `${names[target] ?? target}：${reason.endsWith('EXIT_UNVERIFIED') ? '退出成交无法验证' : '行情未到期或缺失'}`;
}
