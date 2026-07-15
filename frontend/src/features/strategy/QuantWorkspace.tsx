import { FormEvent, useEffect, useMemo, useState } from 'react';
import { api } from '../../shared/api/client';
import { FactorGuide } from './FactorGuide';
import { QuantDataset, QuantDatasetQuality, QuantExperiment, QuantFactorAnalysis, QuantStrategyDraft, QuantStrategySpec, QuantStrategyVersion, ResearchFactorDefinition } from './quantTypes';

type Pane = 'laboratory' | 'factors' | 'experiments';
type Toast = (message: string, type?: 'success' | 'error' | 'info') => void;
const statusText: Record<string, string> = { EMPTY: '等待数据', QUALITY_PENDING: '等待质量门禁', BLOCKED: '质量阻断', READY: '质量通过', QUEUED: '排队中', RUNNING: '计算中', SUCCEEDED: '已完成', FAILED: '失败' };

function percent(value: number) { return `${(value * 100).toFixed(2)}%`; }
function parseSpec(value: QuantStrategyVersion): QuantStrategySpec | null {
  try { return JSON.parse(value.specJson) as QuantStrategySpec; } catch { return null; }
}

function EquityChart({ experiment }: { experiment?: QuantExperiment }) {
  const points = experiment?.result?.equityCurve ?? [];
  if (points.length < 2) return <div className="quant-chart-empty"><span>Equity trace</span><strong>等待实验曲线</strong><p>确认策略并启动实验后，这里会显示策略净值与基准轨迹。</p></div>;
  const values = points.flatMap(item => [item.portfolioNav, item.benchmarkNav]);
  const min = Math.min(...values); const max = Math.max(...values); const range = max - min || 1;
  const path = (key: 'portfolioNav' | 'benchmarkNav') => points.map((item, index) => {
    const x = (index / (points.length - 1)) * 1000;
    const y = 250 - ((item[key] - min) / range) * 210;
    return `${index ? 'L' : 'M'}${x.toFixed(1)},${y.toFixed(1)}`;
  }).join(' ');
  const tablePoints = points.filter((_, index) => index === 0 || index === points.length - 1 || index % 20 === 0);
  return <div className="quant-chart" aria-label="策略与基准净值曲线">
    <div className="quant-chart-head"><span>Equity trace</span><div><i className="strategy-line" />策略净值 <i className="benchmark-line" />等权基准</div></div>
    <svg viewBox="0 0 1000 280" role="img" aria-labelledby="quant-equity-title quant-equity-desc"><title id="quant-equity-title">策略与等权基准净值曲线</title><desc id="quant-equity-desc">展示回测区间内策略净值和时点股票池等权基准净值的变化。</desc><defs><linearGradient id="quantFill" x1="0" x2="0" y1="0" y2="1"><stop offset="0" stopColor="#31b7cf" stopOpacity=".28"/><stop offset="1" stopColor="#31b7cf" stopOpacity="0"/></linearGradient></defs><path className="quant-gridline" d="M0 40H1000M0 110H1000M0 180H1000M0 250H1000"/><path className="quant-benchmark-path" d={path('benchmarkNav')}/><path className="quant-strategy-path" d={path('portfolioNav')}/></svg>
    <details className="quant-curve-data"><summary>查看净值数据表</summary><div className="quant-table-wrap"><table><caption>每 20 个交易日抽样，含首尾日期</caption><thead><tr><th>日期</th><th>策略净值</th><th>等权基准</th><th>回撤</th></tr></thead><tbody>{tablePoints.map(point => <tr key={point.tradeDate}><td>{point.tradeDate}</td><td>{point.portfolioNav.toFixed(4)}</td><td>{point.benchmarkNav.toFixed(4)}</td><td>{percent(point.drawdown)}</td></tr>)}</tbody></table></div></details>
  </div>;
}

export function QuantWorkspace({ addToast, setMessage }: { addToast: Toast; setMessage: (message: string) => void }) {
  const [pane, setPane] = useState<Pane>('laboratory');
  const [datasets, setDatasets] = useState<QuantDataset[]>([]);
  const [researchFactors, setResearchFactors] = useState<ResearchFactorDefinition[]>([]);
  const [datasetQuality, setDatasetQuality] = useState<QuantDatasetQuality>();
  const [selectedFactorCode, setSelectedFactorCode] = useState<string>();
  const [strategies, setStrategies] = useState<QuantStrategyVersion[]>([]);
  const [experiments, setExperiments] = useState<QuantExperiment[]>([]);
  const [selectedDatasetId, setSelectedDatasetId] = useState<number | null>(null);
  const [selectedExperimentId, setSelectedExperimentId] = useState<number | null>(null);
  const [experimentDetail, setExperimentDetail] = useState<QuantExperiment>();
  const [draft, setDraft] = useState<QuantStrategyDraft>();
  const [factorAnalyses, setFactorAnalyses] = useState<Record<string, QuantFactorAnalysis>>({});
  const [prompt, setPrompt] = useState('选择盈利收益率、ROE、低负债与20日动量，构建一套偏稳健的质量价值策略；每20个交易日调仓，重视回撤与交易成本。');
  const [busy, setBusy] = useState<string>();

  async function load() {
    const [datasetValues, researchFactorValues, strategyValues, experimentValues] = await Promise.all([
      api<QuantDataset[]>('/api/quant/datasets'),
      api<ResearchFactorDefinition[]>('/api/factor-research/factors').catch(() => []),
      api<QuantStrategyVersion[]>('/api/quant/strategies'), api<QuantExperiment[]>('/api/quant/experiments')
    ]);
    const safeResearchFactors = Array.isArray(researchFactorValues) ? researchFactorValues : [];
    setDatasets(datasetValues); setResearchFactors(safeResearchFactors); setStrategies(strategyValues); setExperiments(experimentValues);
    setSelectedFactorCode(current => current ?? safeResearchFactors[0]?.identity.code);
    setSelectedDatasetId(current => current ?? datasetValues.find(item => item.status === 'READY')?.id ?? null);
    setSelectedExperimentId(current => current ?? experimentValues[0]?.id ?? null);
    setMessage('量化研究环境已同步');
  }

  useEffect(() => { load().catch(error => addToast(error instanceof Error ? error.message : '量化工作台加载失败', 'error')); }, []);
  useEffect(() => {
    setFactorAnalyses({});
    if (!selectedDatasetId) { setDatasetQuality(undefined); return; }
    let cancelled = false;
    api<QuantDatasetQuality>(`/api/quant/datasets/${selectedDatasetId}/quality`)
      .then(value => { if (!cancelled) setDatasetQuality(Array.isArray(value?.availableFactors) ? value : { ...value, availableFactors: [] }); })
      .catch(() => { if (!cancelled) setDatasetQuality(undefined); });
    return () => { cancelled = true; };
  }, [selectedDatasetId]);
  useEffect(() => {
    if (!selectedExperimentId) { setExperimentDetail(undefined); return; }
    let cancelled = false;
    const refresh = async () => {
      try {
        const value = await api<QuantExperiment>(`/api/quant/experiments/${selectedExperimentId}`);
        if (!cancelled) setExperimentDetail(value);
      } catch (error) { if (!cancelled) addToast(error instanceof Error ? error.message : '实验详情加载失败', 'error'); }
    };
    refresh(); const active = experiments.find(item => item.id === selectedExperimentId);
    const timer = active && ['QUEUED', 'RUNNING'].includes(active.status) ? window.setInterval(async () => { await refresh(); await load(); }, 1200) : undefined;
    return () => { cancelled = true; if (timer) window.clearInterval(timer); };
  }, [selectedExperimentId, experiments.find(item => item.id === selectedExperimentId)?.status]);

  async function createLearningDataset() {
    setBusy('dataset');
    try { const value = await api<QuantDataset>('/api/quant/datasets/learning-sample', { method: 'POST', body: JSON.stringify({ name: `A股多因子学习样本 ${datasets.length + 1}` }) }); await load(); setSelectedDatasetId(value.id); addToast('学习数据集已建立，质量门禁通过', 'success'); }
    catch (error) { addToast(error instanceof Error ? error.message : '学习数据创建失败', 'error'); }
    finally { setBusy(undefined); }
  }

  async function generateDraft(event: FormEvent) {
    event.preventDefault(); if (!selectedDatasetId) return;
    setBusy('agent'); setDraft(undefined);
    try { const value = await api<QuantStrategyDraft>('/api/quant/strategy-drafts', { method: 'POST', body: JSON.stringify({ datasetId: selectedDatasetId, prompt }) }); setDraft(value); addToast(value.status === 'VALIDATED' ? 'Agent 草案已通过结构与因子校验' : 'Agent 草案未通过严格校验，已保留失败记录', value.status === 'VALIDATED' ? 'success' : 'error'); }
    catch (error) { addToast(error instanceof Error ? error.message : 'Agent 草案生成失败', 'error'); }
    finally { setBusy(undefined); }
  }

  async function confirmDraft() {
    if (!draft) return; setBusy('confirm');
    try { await api<QuantStrategyVersion>(`/api/quant/strategy-drafts/${draft.id}/confirm`, { method: 'POST' }); await load(); setDraft(undefined); addToast('策略版本已锁定，可启动实验', 'success'); }
    catch (error) { addToast(error instanceof Error ? error.message : '策略确认失败', 'error'); }
    finally { setBusy(undefined); }
  }

  async function runExperiment(strategyVersionId: number) {
    setBusy(`run-${strategyVersionId}`);
    try { const value = await api<QuantExperiment>('/api/quant/experiments', { method: 'POST', body: JSON.stringify({ strategyVersionId }) }); await load(); setSelectedExperimentId(value.id); setPane('experiments'); addToast('实验已进入受控计算队列', 'success'); }
    catch (error) { addToast(error instanceof Error ? error.message : '实验启动失败', 'error'); }
    finally { setBusy(undefined); }
  }

  async function interpret() {
    if (!selectedExperimentId) return; setBusy('interpret');
    try { const value = await api<QuantExperiment>(`/api/quant/experiments/${selectedExperimentId}/interpretations`, { method: 'POST' }); setExperimentDetail(value); addToast('Agent 已生成结构化解读', 'success'); }
    catch (error) { addToast(error instanceof Error ? error.message : '结果解读失败', 'error'); }
    finally { setBusy(undefined); }
  }
  async function analyzeFactor(code: string) {
    if (!selectedDatasetId) return; setBusy(`factor-${code}`);
    try { const value = await api<QuantFactorAnalysis>(`/api/quant/factors/${code}/analysis?datasetId=${selectedDatasetId}`); setFactorAnalyses(current => ({ ...current, [`${value.datasetId}:${code}`]: value })); addToast(`${code} 因子诊断完成`, 'success'); }
    catch (error) { addToast(error instanceof Error ? error.message : '因子诊断失败', 'error'); }
    finally { setBusy(undefined); }
  }

  const selectedDataset = datasets.find(item => item.id === selectedDatasetId);
  const selectedMetrics = experimentDetail?.result?.metrics;
  const selectedVersion = strategies.find(item => item.id === experimentDetail?.strategyVersionId);
  const selectedSpec = selectedVersion ? parseSpec(selectedVersion) : null;
  const latestPositionDate = (experimentDetail?.result?.positions ?? []).reduce((latest, item) => item.tradeDate > latest ? item.tradeDate : latest, '');
  const latestPositions = (experimentDetail?.result?.positions ?? []).filter(item => item.tradeDate === latestPositionDate);
  const draftSpec = draft?.spec;
  const availableFactors = useMemo(() => new Set(datasetQuality?.availableFactors ?? []), [datasetQuality]);
  const selectedFactorAnalysis = selectedDatasetId && selectedFactorCode
    ? factorAnalyses[`${selectedDatasetId}:${selectedFactorCode}`]
    : undefined;

  return <section className="quant-workspace">
    <header className="quant-hero">
      <div className="quant-hero-copy"><p className="quant-eyebrow">FinScope Quant · Research protocol</p><h3>把想法压进一条<br/><em>可复现的实验链</em></h3><p>数据快照、因子假设、T+1 执行与结果解读各自留痕。Agent 可以起草，但不会替你确认或偷偷运行。</p></div>
      <div className="quant-protocol" aria-label="实验协议"><span>DATA</span><i/><span>FACTOR</span><i/><span>SPEC</span><i/><span>RUN</span><i/><span>READ</span></div>
    </header>

    <nav className="quant-panes" aria-label="量化工作台页面">
      {([['laboratory','策略实验室'],['factors','因子观测站'],['experiments','实验档案']] as Array<[Pane,string]>).map(([id,label]) => <button type="button" aria-current={pane === id ? 'page' : undefined} key={id} className={pane === id ? 'active' : ''} onClick={() => setPane(id)}>{label}<small>{id === 'laboratory' ? strategies.length : id === 'factors' ? researchFactors.length : experiments.length}</small></button>)}
    </nav>

    {pane === 'laboratory' && <div className="quant-lab-grid">
      <aside className="quant-dataset-panel quant-panel"><div className="quant-panel-title"><span>01 / DATASET</span><h4>研究样本</h4></div>
        {datasets.length === 0 ? <div className="quant-empty"><strong>先建立一份学习样本</strong><p>30 个虚拟标的、320 个交易日，适合验证完整流程，不代表真实市场。</p></div> : <div className="quant-dataset-list">{datasets.map(item => <button type="button" className={selectedDatasetId === item.id ? 'active' : ''} onClick={() => setSelectedDatasetId(item.id)} key={item.id}><span><i data-status={item.status}/>{item.name}</span><small>{item.dataKind === 'LEARNING_SAMPLE' ? '虚拟学习数据' : '真实数据'} · {statusText[item.status] ?? item.status}</small></button>)}</div>}
        <button type="button" className="quant-action secondary" onClick={createLearningDataset} disabled={busy === 'dataset'}>{busy === 'dataset' ? '正在生成 9,600 条行情…' : '＋ 新建学习样本'}</button>
        {selectedDataset && <dl className="quant-dataset-meta"><div><dt>区间</dt><dd>{selectedDataset.startDate ?? '—'} → {selectedDataset.endDate ?? '—'}</dd></div><div><dt>指纹</dt><dd>{selectedDataset.fingerprint?.slice(0, 12) ?? '等待生成'}</dd></div><div><dt>性质</dt><dd>{selectedDataset.dataKind === 'LEARNING_SAMPLE' ? '虚拟 / 不可用于实盘结论' : '真实数据'}</dd></div></dl>}
      </aside>
      <main className="quant-agent-panel quant-panel"><div className="quant-panel-title"><span>02 / AGENT DRAFT</span><h4>用自然语言描述假设</h4><p>Agent 只能从登记因子中组装受限策略 DSL。</p></div>
        <form onSubmit={generateDraft}><textarea aria-label="策略研究假设" rows={6} value={prompt} onChange={event => setPrompt(event.target.value)} /><div className="quant-agent-actions"><span>{selectedDataset ? `绑定数据集 #${selectedDataset.id}` : '请先选择可用数据集'}</span><button className="quant-action" disabled={!selectedDataset || busy === 'agent'}>{busy === 'agent' ? 'Agent 校验中…' : '生成策略草案'}</button></div></form>
        {draft?.status === 'FAILED' && <article className="quant-draft"><header><div><span>REJECTED DRAFT</span><h4>草案未通过研究协议</h4></div><b>不可确认</b></header><ul>{draft.validationIssues.map(issue => <li key={issue}>{issue}</li>)}</ul><p>失败响应已留痕，但不会形成策略版本或自动运行。</p></article>}
        {draftSpec && draft.status === 'VALIDATED' && <article className="quant-draft"><header><div><span>VALIDATED DRAFT</span><h4>{draftSpec.name}</h4></div><b>等待你的确认</b></header><p>{draftSpec.investmentHypothesis}</p><div className="quant-factor-weights">{draftSpec.factors.map(item => <div key={item.code}><span>{item.code}</span><i><b style={{ width: `${item.weight * 100}%` }}/></i><strong>{(item.weight * 100).toFixed(0)}%</strong></div>)}</div><footer><small>T 日收盘信号 · T+1 开盘成交 · Top {draftSpec.portfolio.topN} 等权 · 每 {draftSpec.portfolio.rebalanceEvery} 日调仓</small><button type="button" className="quant-action confirm" onClick={confirmDraft} disabled={busy === 'confirm'}>确认并锁定版本</button></footer></article>}
      </main>
      <aside className="quant-versions-panel quant-panel"><div className="quant-panel-title"><span>03 / VERSIONS</span><h4>可运行策略</h4></div>{strategies.length === 0 ? <div className="quant-empty compact"><strong>暂无已确认版本</strong><p>草案必须由你确认后才能进入实验队列。</p></div> : <div className="quant-version-list">{strategies.map(item => { const spec = parseSpec(item); return <article key={item.id}><header><span>v{item.version}</span><b>{item.name}</b></header><p>{spec?.riskBoundary ?? '已锁定策略边界'}</p><small>{item.engineVersion} · {item.strategyFingerprint.slice(0,8)}</small><button type="button" onClick={() => runExperiment(item.id)} disabled={busy === `run-${item.id}`}>{busy === `run-${item.id}` ? '正在入队…' : '启动实验'}</button></article>; })}</div>}</aside>
    </div>}

    {pane === 'factors' && <div className="quant-factor-page">
      <header><span>FACTOR MANUAL / {researchFactors.length}</span><h4>先看懂，再验证</h4><p>每个因子都有明确公式、可获得时间和误读边界；诊断评价同日股票池排序，不预测单只股票。</p></header>
      <FactorGuide
        definitions={researchFactors}
        selectedCode={selectedFactorCode}
        onSelect={setSelectedFactorCode}
        selectedDataset={selectedDataset}
        availableFactors={availableFactors}
        analysis={selectedFactorAnalysis}
        busy={Boolean(selectedFactorCode && busy === `factor-${selectedFactorCode}`)}
        onAnalyze={analyzeFactor}
      />
    </div>}

    {pane === 'experiments' && <div className="quant-experiment-grid"><aside className="quant-run-list quant-panel"><div className="quant-panel-title"><span>RUN ARCHIVE</span><h4>实验批次</h4></div>{experiments.length === 0 ? <div className="quant-empty compact"><strong>暂无实验</strong><p>从已确认的策略版本启动第一轮回测。</p></div> : experiments.map(item => <button type="button" className={selectedExperimentId === item.id ? 'active' : ''} onClick={() => setSelectedExperimentId(item.id)} key={item.id}><i data-status={item.status}/><span><b>实验 #{item.id}</b><small>策略版本 #{item.strategyVersionId}</small></span><em>{statusText[item.status]}</em></button>)}</aside>
      <main className="quant-results">{experimentDetail && <section className="quant-readout"><header><div><span>DATA PROVENANCE</span><h4>{experimentDetail.datasetName ?? `数据集 #${experimentDetail.datasetId ?? '—'}`}</h4></div><b>{experimentDetail.dataKind === 'LEARNING_SAMPLE' ? '虚拟学习数据 · 不可用于实盘结论' : '真实研究数据'}</b></header><p>数据指纹 {experimentDetail.datasetFingerprint.slice(0, 16)} · 引擎 {experimentDetail.engineVersion}{selectedSpec ? ` · Top ${selectedSpec.portfolio.topN} · 每 ${selectedSpec.portfolio.rebalanceEvery} 日调仓` : ''}</p></section>}<div className="quant-metrics">{[['年化收益', selectedMetrics ? percent(selectedMetrics.annualizedReturn) : '—'],['最大回撤', selectedMetrics ? percent(selectedMetrics.maxDrawdown) : '—'],['Sharpe', selectedMetrics ? selectedMetrics.sharpeRatio.toFixed(2) : '—'],['超额收益', selectedMetrics ? percent(selectedMetrics.excessReturn) : '—']].map(([label,value]) => <div key={label}><span>{label}</span><strong>{value}</strong></div>)}</div><EquityChart experiment={experimentDetail}/>
        {experimentDetail?.result && <section className="quant-readout"><header><div><span>EXECUTION EVIDENCE</span><h4>年度表现、交易与异常留痕</h4></div><b>{experimentDetail.result.trades.length} 笔成交</b></header>{experimentDetail.result.warnings.length > 0 && <ul>{experimentDetail.result.warnings.map((warning, index) => <li key={`${index}-${warning}`}>{warning}</li>)}</ul>}<div className="quant-table-wrap"><table><caption>年度表现</caption><thead><tr><th>年度</th><th>策略</th><th>等权基准</th><th>超额</th><th>最大回撤</th></tr></thead><tbody>{experimentDetail.result.annualPerformance.map(year => <tr key={year.year}><td>{year.year}</td><td>{percent(year.portfolioReturn)}</td><td>{percent(year.benchmarkReturn)}</td><td>{percent(year.excessReturn)}</td><td>{percent(year.maxDrawdown)}</td></tr>)}</tbody></table></div><div className="quant-table-wrap"><table><caption>最近成交记录</caption><thead><tr><th>信号日</th><th>成交日</th><th>标的</th><th>方向</th><th>数量</th><th>价格</th><th>费用</th></tr></thead><tbody>{experimentDetail.result.trades.slice(-20).reverse().map((trade, index) => <tr key={`${trade.tradeDate}-${trade.instrumentCode}-${index}`}><td>{trade.signalDate}</td><td>{trade.tradeDate}</td><td>{trade.instrumentCode}</td><td>{trade.side}</td><td>{trade.quantity}</td><td>{trade.price.toFixed(2)}</td><td>{trade.fee.toFixed(2)}</td></tr>)}</tbody></table></div></section>}
        {latestPositions.length > 0 && <section className="quant-readout"><header><div><span>POSITION SNAPSHOT</span><h4>期末持仓 · {latestPositionDate}</h4></div><b>{latestPositions.length} 个标的</b></header><div className="quant-table-wrap"><table><caption>回测期末可复现持仓快照</caption><thead><tr><th>标的</th><th>数量</th><th>价格</th><th>市值</th><th>权重</th></tr></thead><tbody>{latestPositions.map(position => <tr key={position.instrumentCode}><td>{position.instrumentCode}</td><td>{position.quantity}</td><td>{position.price.toFixed(2)}</td><td>{position.marketValue.toFixed(2)}</td><td>{percent(position.weight)}</td></tr>)}</tbody></table></div></section>}
        <section className="quant-readout"><header><div><span>AGENT READOUT</span><h4>只解释结果，不替你下结论</h4></div><button type="button" className="quant-action" disabled={experimentDetail?.status !== 'SUCCEEDED' || busy === 'interpret'} onClick={interpret}>{busy === 'interpret' ? '正在归纳…' : '生成结果解读'}</button></header>{experimentDetail?.status === 'FAILED' ? <p className="quant-error">{experimentDetail.errorMessage}</p> : experimentDetail?.interpretation ? <pre>{JSON.stringify(JSON.parse(experimentDetail.interpretation), null, 2)}</pre> : <p>实验完成后，可让 Agent 从表现、风险与下一轮可验证实验三个角度生成结构化解读。</p>}</section>
      </main></div>}
  </section>;
}
