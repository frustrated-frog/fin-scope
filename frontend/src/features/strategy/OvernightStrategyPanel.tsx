import { useEffect, useState, type FormEvent } from 'react';
import { api } from '../../shared/api/client';
import { HoldingAnalysisDrawer, type HoldingAnalysis } from './HoldingAnalysisDrawer';
import type { OvernightMode, OvernightPosition, OvernightReport } from './overnightTypes';
import './OvernightStrategyPanel.css';
import { OvernightAuditPanel } from './OvernightAuditPanel';
import { OvernightTargetEvidence } from './OvernightTargetEvidence';
import { OvernightJointEvidence } from './OvernightJointPanel';
import { OvernightDirectionPanel } from './OvernightDirectionPanel';
import { OvernightAutomationPanel } from './OvernightAutomationPanel';

const modeLabels = { TAIL_ENTRY: '尾盘入场', AFTER_CLOSE_HOLDING: '盘后持仓' };
const statusLabels: Record<string, string> = {
  WATCH: '研究观察，尚无实盘优势证据', BEFORE_CUTOFF: '尚未到决策时刻',
  DATA_UNAVAILABLE: '分钟数据未就绪', INSUFFICIENT_DATA: '历史样本不足',
  CALENDAR_UNAVAILABLE: '交易日历未覆盖', PENDING: '等待次日行情',
  PARTIAL: '部分时点已到期', SETTLED: '四个时点已结算', ENTRY_UNVERIFIED: '入场价格无法验证',
};
const targetLabels: Record<string, string> = { OPEN: '次日开盘', '10:00': '次日 10:00', '14:30': '次日 14:30', CLOSE: '次日收盘' };
const pct = (value?: number | null) => value == null ? '—' : `${(value * 100).toFixed(1)}%`;
const today = () => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai' }).format(new Date());

export function OvernightStrategyPanel() {
  const [auditRevision, setAuditRevision] = useState(0);
  const [mode, setMode] = useState<OvernightMode>('TAIL_ENTRY');
  const [code, setCode] = useState('');
  const [signalDate, setSignalDate] = useState(today);
  const [cutoff, setCutoff] = useState('14:30');
  const [costBps, setCostBps] = useState(20);
  const [positions, setPositions] = useState<OvernightPosition[]>([]);
  const [records, setRecords] = useState<OvernightReport[]>([]);
  const [report, setReport] = useState<OvernightReport>();
  const [error, setError] = useState('');
  const [historyError, setHistoryError] = useState('');
  const [busy, setBusy] = useState('');
  const [analysis, setAnalysis] = useState<HoldingAnalysis>();
  const [analysisOpen, setAnalysisOpen] = useState(false);
  const position = positions.find(item => item.instrumentCode === code);

  useEffect(() => {
    let active = true;
    let pending = false;
    async function loadHistory() {
      if (pending) {
        return;
      }
      pending = true;
      try {
        const value = await api<OvernightReport[]>('/api/quant/overnight/history');
        if (active) {
          setRecords(value);
          setHistoryError('');
        }
      } catch (reason) {
        if (active) {
          setHistoryError(`档案读取失败：${reason instanceof Error ? reason.message : '连接异常'}。页面会自动重试。`);
        }
      } finally {
        pending = false;
      }
    }
    void loadHistory();
    const timer = window.setInterval(() => { void loadHistory(); }, 30000);
    api<{ positions: OvernightPosition[] }>('/api/strategy/stock-account').then(value => {
      if (active) {
        setPositions(value.positions.filter(item => item.quantity > 0));
      }
    }).catch(error => { if (active) {
        setError(`持仓读取失败：${error.message}`);
      } });
    return () => { active = false; window.clearInterval(timer); };
  }, []);

  function chooseMode(value: OvernightMode) {
    setMode(value); setReport(undefined); setError('');
    setCostBps(value === 'TAIL_ENTRY' ? 20 : 10);
    if (value === 'AFTER_CLOSE_HOLDING') {
      setCode(positions[0]?.instrumentCode ?? '');
    }
  }

  async function generate(event: FormEvent) {
    event.preventDefault(); setBusy('generate'); setError(''); setReport(undefined);
    try {
      const value = await api<OvernightReport>('/api/quant/overnight/generate', { method: 'POST', body: JSON.stringify({
        instrumentCode: code, signalDate, mode, cutoff: mode === 'TAIL_ENTRY' ? cutoff : '15:00', costBps,
      }) });
      setReport(value);
      setAuditRevision(value => value + 1);
      if (value.id) {
        setRecords(previous => [value, ...previous.filter(item => item.id !== value.id)]);
        setHistoryError('');
      }
    } catch (error) { setError(error instanceof Error ? error.message : '预测生成失败'); }
    finally { setBusy(''); }
  }

  async function settle() {
    setBusy('settle'); setHistoryError('');
    try {
      setRecords(await api<OvernightReport[]>('/api/quant/overnight/settle', { method: 'POST' }));
      setAuditRevision(value => value + 1);
    }
    catch (error) { setHistoryError(error instanceof Error ? error.message : '结算失败'); }
    finally { setBusy(''); }
  }

  async function existingAnalysis() {
    setBusy('analysis'); setError('');
    try {
      setAnalysis(await api<HoldingAnalysis>(`/api/strategy/stock-positions/${encodeURIComponent(code)}/analysis`));
      setAnalysisOpen(true);
    } catch (error) { setError(error instanceof Error ? error.message : '持仓分析不可用'); }
    finally { setBusy(''); }
  }

  return <section className="overnight-workbench" aria-label="尾盘与盘后独立策略">
    <header className="overnight-title"><div><span>隔夜策略 · 自动研究</span><h3>尾盘找机会，盘后看持仓</h3><p>自动筛选、到点判断、次日核验。每一次研究都保留当时的数据边界。</p></div><small>上海交易时间</small></header>
    <div className="overnight-mode" role="group" aria-label="研究场景">
      <button type="button" aria-pressed={mode === 'TAIL_ENTRY'} onClick={() => chooseMode('TAIL_ENTRY')} disabled={!!busy}><b>尾盘入场</b><span>现在买入，次日是否有净收益？</span></button>
      <button type="button" aria-pressed={mode === 'AFTER_CLOSE_HOLDING'} onClick={() => chooseMode('AFTER_CLOSE_HOLDING')} disabled={!!busy}><b>盘后持仓</b><span>已经持有，比较明天的持有时长</span></button>
    </div>
    <ol className="overnight-clock" aria-label="决策与执行时间轴">
      <li data-active={mode === 'TAIL_ENTRY'}><time>14:30 / 14:45</time><b>尾盘自动判断</b><span>提前扫描候选，到点截断数据</span></li>
      <li data-active={mode === 'AFTER_CLOSE_HOLDING'}><time>15:10</time><b>盘后持仓更新</b><span>使用 15:00 收盘数据，读取真实账本</span></li>
      <li><time>T+1</time><b>次日分时评测</b><span>开盘 / 10:00 / 14:30 / 收盘</span></li>
    </ol>
    <OvernightAutomationPanel mode={mode} records={records} renderReport={item => <ResearchResult report={item} />} />
    {historyError && <p role="alert" className="overnight-error">{historyError}</p>}
    <details className="overnight-manual"><summary>单股补充研究与历史复盘</summary>
    <div className="overnight-layout"><form className="overnight-input" onSubmit={generate}>
      <h4>{modeLabels[mode]}设置</h4>
      {mode === 'TAIL_ENTRY' ? <label>研究股票<input value={code} onChange={event => { setCode(event.target.value); setReport(undefined); }} placeholder="例如 605058 或 605058.SH" required disabled={!!busy} /></label>
        : <label>真实持仓<select value={code} onChange={event => { setCode(event.target.value); setReport(undefined); }} required disabled={!!busy}>
          <option value="">选择账本中的股票</option>{positions.map(item => <option key={item.instrumentCode} value={item.instrumentCode}>{item.instrumentName} · {item.instrumentCode}</option>)}</select></label>}
      {mode === 'AFTER_CLOSE_HOLDING' && !positions.length && <p className="overnight-empty">暂无可研判的持仓。请先到“Compound 长期投资工作台 → 真实持仓”记录买入。</p>}
      {position && mode === 'AFTER_CLOSE_HOLDING' && <div className="overnight-position"><span>账本成本 ¥{position.averageCost.toFixed(2)}</span><span>{position.quantity} 股 · 建仓 {position.openedOn ?? '未记录'}</span></div>}
      <label>信号日期<input type="date" value={signalDate} onChange={event => { setSignalDate(event.target.value); setReport(undefined); }} required disabled={!!busy} /></label>
      <label>数据截止时刻<select value={mode === 'TAIL_ENTRY' ? cutoff : '15:00'} onChange={event => { setCutoff(event.target.value); setReport(undefined); }} disabled={!!busy || mode === 'AFTER_CLOSE_HOLDING'}>
        <option value="14:30">14:30</option><option value="14:45">14:45</option><option value="14:50">14:50</option>{mode === 'AFTER_CLOSE_HOLDING' && <option value="15:00">15:00 · 收盘</option>}</select></label>
      <label>{mode === 'TAIL_ENTRY' ? '买卖成本及滑点假设' : '后续卖出成本及滑点假设'}<div className="overnight-unit"><input type="number" min="0" max="200" step="1" value={costBps} onChange={event => { setCostBps(Number(event.target.value)); setReport(undefined); }} required disabled={!!busy} /><span>基点</span></div><small>10 基点 = 0.1%，请按你的实际费用调整。</small></label>
      <button className="overnight-primary" type="submit" disabled={!!busy || !code}>{busy === 'generate' ? '读取分钟数据并计算…' : `生成${modeLabels[mode]}研究`}</button>
      {mode === 'AFTER_CLOSE_HOLDING' && <button type="button" onClick={existingAnalysis} disabled={!!busy || !code}>查看现有持仓分析</button>}
      <small>历史补跑单独标记；只有形成有效研究结果才冻结入档，不覆盖同条件下的第一次判断。</small>
    </form><div className="overnight-result" aria-live="polite">
      {error && <p role="alert" className="overnight-error">{error}</p>}
      {report ? <ResearchResult report={report} /> : <div className="overnight-waiting"><span>{mode === 'TAIL_ENTRY' ? '买入之前' : '持有之后'}</span><h4>{mode === 'TAIL_ENTRY' ? '让预测对应你能参与的那段涨跌' : '从真实持仓出发，比较明天的选择'}</h4><p>{mode === 'TAIL_ENTRY' ? '按截止时刻截断分钟线，分别研究次日不同退出时点。没有合格分钟数据时，保留空缺，不借用收盘结果。' : '成本与数量直接读取账本。新盘后判断单独保存，尾盘入场时的判断仍然保留。'}</p><div>本地模型 · 4 个退出时点 · 独立冻结记录</div></div>}
    </div></div></details>
    <details className="overnight-manual"><summary>全历史验证与自定义观察名单</summary><OvernightAuditPanel mode={mode} revision={auditRevision} /></details>
    <details className="overnight-manual"><summary>历史档案与次日复盘（{records.length}）</summary>
    <section className="overnight-history" aria-label="两类预测独立档案"><header><div><h4>判断留痕与次日复盘</h4><p>展示最近 50 份档案；验收统计覆盖全部历史。每次最多更新 3 只股票，服务也会自动轮换核验。</p></div><button type="button" onClick={settle} disabled={!!busy || !records.length}>{busy === 'settle' ? '读取到期行情…' : '更新到期结果'}</button></header>
      {!records.length && !historyError && <p className="overnight-empty">还没有冻结记录。完成第一份具备足够分钟历史的研究后，原始预测会保存在这里。</p>}
      {records.map(item => <details key={item.id}><summary><span className="overnight-badge">{modeLabels[item.mode]}</span><b>{item.instrumentCode}</b><span>{item.signalDate} {item.cutoff}</span><span>{item.evidenceKind === 'FORWARD' ? '当时生成' : '历史回顾'}</span><span>{statusLabels[item.outcome?.status ?? 'PENDING'] ?? '等待结算'}</span></summary><ResearchResult report={item} /></details>)}
    </section></details>
    {analysisOpen && <HoldingAnalysisDrawer analysis={analysis} loading={false} targetName={position?.instrumentName ?? code} targetCode={code} onClose={() => setAnalysisOpen(false)} />}
  </section>;
}

export function ResearchResult({ report }: { report: OvernightReport }) {
  const resultStatus = report.closeDirection?.upProbability != null && report.status === 'INSUFFICIENT_DATA'
    ? '涨跌预测已生成 · 收益样本不足' : statusLabels[report.status] ?? report.status;
  return <article><header className="overnight-result-head"><div><span>{modeLabels[report.mode]} · {report.instrumentCode}</span><h4>{resultStatus}</h4></div><b className="overnight-badge">{report.evidenceKind === 'FORWARD' ? '当时生成' : '历史回顾'}</b></header>
    <dl className="overnight-evidence"><div><dt>数据截止</dt><dd>{report.dataThrough.replace('T', ' ')}</dd></div><div><dt>预测对应日期</dt><dd>{report.targetDate ?? '日历未覆盖'}</dd></div><div><dt>截止时点参考价</dt><dd>{report.referencePrice ? `¥${report.referencePrice.toFixed(2)}` : '暂无完整行情'}</dd></div><div><dt>费用假设</dt><dd>{report.costBps} 基点</dd></div></dl>
    <p className="overnight-note">{report.mode === 'TAIL_ENTRY' ? '参考价不是承诺买入价；收益目标对应决策后 5 分钟的入场价格代理。' : `持仓成本 ¥${report.costBasis?.toFixed(2)}；预期收益相对当日收盘，另列相对成本的收益。`}</p>
    {report.jointResearch && <p className="overnight-note">联合预测：{report.jointResearch.reason}</p>}
    <OvernightDirectionPanel report={report} />
    <div className="overnight-targets">{report.targets.map(target => {
      const actual = report.outcome?.targets.find(item => item.target === target.target);
      const baseline = target.probabilitySource === 'HISTORICAL_BASELINE';
      const joint = target.probabilitySource === 'JOINT_MODEL';
      return <section key={target.target}><h5>{targetLabels[target.target]}</h5><strong>{pct(target.upProbability)}</strong><span>{joint ? '联合模型盈利概率 · 前瞻对照通过' : baseline ? '历史基线参考 · 已扣假设成本' : target.calibrationStatus === 'FITTED' ? '校准后盈利概率 · 已扣假设成本' : '扣除假设成本后盈利的概率'}</span>
        {baseline && <p>模型尚未通过历史对照，当前采用过去的盈利比例，不代表已发现上涨优势。</p>}
        {target.status === 'INSUFFICIENT_DATA' ? <><p>可用历史 {target.sampleCount} 例，至少需要 {target.minimumSamples ?? 60} 例</p>{target.missingSamples != null && <p>还需积累 {target.missingSamples} 个有效收益样本。</p>}{target.missingValidationSamples != null && <p>独立验证还缺 {target.missingValidationSamples} 例，暂不输出概率。</p>}</> : <><dl><div><dt>{baseline ? '历史平均净收益' : '预期净收益'}</dt><dd>{pct(target.expectedNetReturn)}</dd></div>{!baseline && <div><dt>{joint ? '80% 校准残差范围' : target.reliability ? '80% 校准误差范围' : '历史验证误差区间'}</dt><dd>{pct(target.lowerNetReturn)} ～ {pct(target.upperNetReturn)}</dd></div>}{target.costBasisReturn != null && <div><dt>{baseline ? '历史均值对应成本收益' : '相对持仓成本'}</dt><dd>{pct(target.costBasisReturn)}</dd></div>}</dl>{!joint && <><small>历史 {target.sampleCount} 例 · 顺序验证 {target.validationCount} 例</small><small>模型概率误差 Brier {target.brierScore?.toFixed(3)} / 基线 {target.baselineBrier?.toFixed(3)}（越低越好）</small></>}<OvernightTargetEvidence target={target} /></>}
        <OvernightJointEvidence target={target} />
        {actual && <p className="overnight-actual">实际价格路径净收益 {pct(actual.actualNetReturn)}</p>}
      </section>;
    })}</div>
    {(report.outcome?.warnings ?? []).map(warning => <p role="alert" className="overnight-error" key={warning}>{warning}</p>)}
    {report.warnings.map(warning => <p className="overnight-note" key={warning}>{warning}</p>)}
    <small>生成于 {report.generatedAt.replace('T', ' ')} · {report.id ? '原始记录已冻结' : '尚未形成可冻结的预测'} · {report.modelVersion}</small>
  </article>;
}
