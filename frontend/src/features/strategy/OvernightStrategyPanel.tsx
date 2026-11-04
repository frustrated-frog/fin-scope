import { useEffect, useState, type FormEvent } from 'react';
import { api } from '../../shared/api/client';
import { HoldingAnalysisDrawer, type HoldingAnalysis } from './HoldingAnalysisDrawer';
import type { OvernightMode, OvernightPosition, OvernightReport } from './overnightTypes';
import './OvernightStrategyPanel.css';
import { OvernightAuditPanel } from './OvernightAuditPanel';
import { OvernightDirectionPanel } from './OvernightDirectionPanel';
import { OvernightAutomationPanel } from './OvernightAutomationPanel';
import { OvernightSheet } from './OvernightSheet';
import { OvernightReview } from './OvernightReview';
import './OvernightWorkspace.css';

const modeLabels = { TAIL_ENTRY: '尾盘入场', AFTER_CLOSE_HOLDING: '盘后持仓' };
const statusLabels: Record<string, string> = {
  WATCH: '研究观察，尚无实盘优势证据', BEFORE_CUTOFF: '尚未到决策时刻',
  DATA_UNAVAILABLE: '分钟数据未就绪', INSUFFICIENT_DATA: '历史样本不足',
  CALENDAR_UNAVAILABLE: '交易日历未覆盖', PENDING: '等待次日行情',
  PARTIAL: '部分时点已到期', SETTLED: '四个时点已结算', ENTRY_UNVERIFIED: '入场价格无法验证',
};
const today = () => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai' }).format(new Date());

export function OvernightStrategyPanel() {
  const [view, setView] = useState<'today' | 'performance' | 'history'>('today');
  const [tool, setTool] = useState<'manual' | 'watchlist'>();
  const [selectedReport, setSelectedReport] = useState<OvernightReport>();
  const [auditRevision, setAuditRevision] = useState(0);
  const [mode, setMode] = useState<OvernightMode>('TAIL_ENTRY');
  const [code, setCode] = useState('');
  const [signalDate, setSignalDate] = useState(today);
  const [cutoff, setCutoff] = useState('14:30');
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
    if (value === 'AFTER_CLOSE_HOLDING') {
      setCode(positions[0]?.instrumentCode ?? '');
    }
  }

  async function generate(event: FormEvent) {
    event.preventDefault(); setBusy('generate'); setError(''); setReport(undefined);
    try {
      const value = await api<OvernightReport>('/api/quant/overnight/generate', { method: 'POST', body: JSON.stringify({
        instrumentCode: code, signalDate, mode, cutoff: mode === 'TAIL_ENTRY' ? cutoff : '15:00',
        costBps: mode === 'TAIL_ENTRY' ? 20 : 10,
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
    <header className="overnight-title"><div><span>OVERNIGHT / 隔夜研究</span><h3>把今天的判断，交给明天验证</h3><p>尾盘自动发现 · 盘后跟踪持仓 · 次日真实复盘</p></div>
      <div className="overnight-tools"><button type="button" onClick={() => setTool('manual')}>研究工具 ↗</button>
      {mode === 'TAIL_ENTRY' && <button type="button" onClick={() => setTool('watchlist')}>我的关注 ↗</button>}</div></header>
    <div className="overnight-mode" role="group" aria-label="研究场景">
      <button type="button" aria-pressed={mode === 'TAIL_ENTRY'} onClick={() => chooseMode('TAIL_ENTRY')} disabled={!!busy}><b>尾盘入场</b><span>14:30 后，发现明日偏涨股票</span></button>
      <button type="button" aria-pressed={mode === 'AFTER_CLOSE_HOLDING'} onClick={() => chooseMode('AFTER_CLOSE_HOLDING')} disabled={!!busy}><b>盘后持仓</b><span>已经持有，判断明日走势</span></button>
    </div>
    <nav className="overnight-navigation" aria-label="隔夜工作区">
      {([['today', '今日研判'], ['performance', '预测表现'], ['history', '历史复盘']] as const).map(([key, label]) => <button key={key} type="button" aria-current={view === key ? 'page' : undefined} onClick={() => setView(key)}>{label}</button>)}
      <span>上海交易时间 / T → T+1</span>
    </nav>
    <div hidden={view === 'history'}><OvernightAutomationPanel mode={mode} records={records} revision={auditRevision} view={view === 'performance' ? 'performance' : 'today'} renderReport={item => <ResearchResult report={item} />} /></div>
    {historyError && <p role="alert" className="overnight-error">{historyError}</p>}
    {view === 'history' && <OvernightReview mode={mode} records={records} busy={!!busy} onSettle={() => void settle()} onSelect={setSelectedReport} />}
    {selectedReport && <OvernightSheet title={`${selectedReport.instrumentCode} · ${selectedReport.signalDate}`} onClose={() => setSelectedReport(undefined)}><ResearchResult report={selectedReport} /></OvernightSheet>}
    {tool === 'watchlist' && <OvernightSheet title="我的关注" onClose={() => setTool(undefined)}><OvernightAuditPanel mode={mode} revision={auditRevision} view="watchlist" /></OvernightSheet>}
    {tool === 'manual' && <OvernightSheet title="单股研究与历史回顾" onClose={() => setTool(undefined)}>
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
      <button className="overnight-primary" type="submit" disabled={!!busy || !code}>{busy === 'generate' ? '读取分钟数据并计算…' : `生成${modeLabels[mode]}研究`}</button>
      {mode === 'AFTER_CLOSE_HOLDING' && <button type="button" onClick={existingAnalysis} disabled={!!busy || !code}>查看现有持仓分析</button>}
      <small>历史补跑单独标记；只有形成有效研究结果才冻结入档，不覆盖同条件下的第一次判断。</small>
    </form><div className="overnight-result" aria-live="polite">
      {error && <p role="alert" className="overnight-error">{error}</p>}
      {report ? <ResearchResult report={report} /> : <div className="overnight-waiting"><span>{mode === 'TAIL_ENTRY' ? '买入之前' : '持有之后'}</span><h4>{mode === 'TAIL_ENTRY' ? '让预测对应你能参与的那段涨跌' : '从真实持仓出发，比较明天的选择'}</h4><p>{mode === 'TAIL_ENTRY' ? '只使用截止时刻已知的数据，判断次日收盘能否高于当时参考价。没有合格数据时，保留空缺。' : '自动读取账本中的股票，判断次日收盘相对今日收盘的涨跌。盘后与尾盘记录分别保留。'}</p><div>本地模型 · 次日涨跌 · 独立冻结记录</div></div>}
    </div></div></OvernightSheet>}
    {analysisOpen && <HoldingAnalysisDrawer analysis={analysis} loading={false} targetName={position?.instrumentName ?? code} targetCode={code} onClose={() => setAnalysisOpen(false)} />}
  </section>;
}

export function ResearchResult({ report }: { report: OvernightReport }) {
  const resultStatus = report.closeDirection?.upProbability != null
    ? '次日涨跌判断已冻结' : statusLabels[report.status] ?? report.status;
  const referencePrice = report.closeDirection?.referencePrice ?? report.referencePrice;
  return <article><header className="overnight-result-head"><div><span>{modeLabels[report.mode]} · {report.instrumentCode}</span><h4>{resultStatus}</h4></div><b className="overnight-badge">{report.evidenceKind === 'FORWARD' ? '当时生成' : '历史回顾'}</b></header>
    <dl className="overnight-evidence"><div><dt>数据截止</dt><dd>{report.dataThrough.replace('T', ' ')}</dd></div><div><dt>预测对应日期</dt><dd>{report.targetDate ?? '日历未覆盖'}</dd></div><div><dt>截止时点参考价</dt><dd>{referencePrice != null ? `¥${referencePrice.toFixed(2)}` : '暂无完整行情'}</dd></div></dl>
    <OvernightDirectionPanel report={report} />
    {!report.closeDirection && <p className="overnight-note">这份记录没有可用的次日涨跌预测。</p>}
    {(report.outcome?.warnings ?? []).map(warning => <p role="alert" className="overnight-error" key={warning}>{warning}</p>)}
    {!!report.warnings.length && <details><summary>数据与研究说明</summary>{report.warnings.map(warning => <p className="overnight-note" key={warning}>{warning}</p>)}</details>}
    <small>生成于 {report.generatedAt.replace('T', ' ')} · {report.id ? '原始记录已冻结' : '尚未形成可冻结的预测'}</small>
  </article>;
}
