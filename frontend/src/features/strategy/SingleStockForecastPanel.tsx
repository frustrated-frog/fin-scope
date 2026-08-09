import { FormEvent, useEffect, useMemo, useState } from 'react';
import { api } from '../../shared/api/client';
import { ForecastConfidenceInterval, ForecastQualification, SingleStockForecast, SingleStockForecastRun } from './quantTypes';

type Toast = (message: string, type?: 'success' | 'error' | 'info') => void;

const statusCopy: Record<string, { label: string; tone: string }> = {
  ROBUST: { label: '稳健', tone: 'supported' },
  CONDITIONAL: { label: '条件有效', tone: 'caution' },
  NO_CLEAR_EDGE: { label: '无明显优势', tone: 'neutral' },
  INSUFFICIENT_DATA: { label: '数据不足', tone: 'muted' }
};

type ForecastHorizon = 1 | 5 | 20;

const horizons: Array<{ days: ForecastHorizon; label: string; role: string; note: string }> = [
  { days: 1, label: '1D', role: '极短期', note: '噪声最高，门槛更严' },
  { days: 5, label: '5D', role: '默认观察', note: '时效与稳定性平衡' },
  { days: 20, label: '20D', role: '趋势观察', note: '偏中短期趋势' }
];

const decisionCopy = {
  UP: { label: '偏向上涨', code: 'UP' },
  DOWN: { label: '偏向下跌', code: 'DOWN' },
  ABSTAIN: { label: '暂不判断', code: 'ABSTAIN' }
};

const maturityCopy = { PENDING: '待验证', MATURED: '已到期', UNAVAILABLE: '无法验证' };

const percent = (value?: number, digits = 1) => value == null ? '—' : `${(value * 100).toFixed(digits)}%`;
const signedPercent = (value?: number) => value == null ? '—' : `${value >= 0 ? '+' : ''}${(value * 100).toFixed(1)}%`;
const number = (value?: number, digits = 2) => value == null ? '—' : value.toFixed(digits);
const money = (value?: number) => value == null ? '—' : `¥${value.toLocaleString('zh-CN', { maximumFractionDigits: 0 })}`;
const interval = (value?: ForecastConfidenceInterval, format: (item?: number) => string = number) =>
  value?.status === 'AVAILABLE' && value.lower != null && value.upper != null
    ? `${format(value.lower)} — ${format(value.upper)}` : '区间不可用';

function curvePoints(values: number[], width = 720, height = 190) {
  if (!values.length) return '';
  const min = Math.min(...values); const max = Math.max(...values); const span = Math.max(max - min, .0001);
  return values.map((value, index) => `${index / Math.max(1, values.length - 1) * width},${height - (value - min) / span * height}`).join(' ');
}

function EquityChart({ report }: { report: SingleStockForecast }) {
  const sampled = useMemo(() => {
    const step = Math.max(1, Math.ceil(report.equityCurve.length / 240));
    return report.equityCurve.filter((_, index) => index % step === 0 || index === report.equityCurve.length - 1);
  }, [report.equityCurve]);
  if (sampled.length < 2) return <p className="forecast-no-chart">净值序列不足，暂不绘制收益曲线。</p>;
  const strategy = curvePoints(sampled.map(item => item.strategyNav));
  const benchmark = curvePoints(sampled.map(item => item.benchmarkNav));
  return <div className="forecast-equity-chart">
    <div className="forecast-chart-legend"><span data-series="strategy">概率策略</span><span data-series="benchmark">同股买入并持有</span><small>{sampled[0].tradeDate} — {sampled[sampled.length - 1].tradeDate}</small></div>
    <svg viewBox="0 0 720 190" role="img" aria-label={`概率策略累计收益 ${signedPercent(report.performance?.strategy.totalReturn)}，同股买入并持有累计收益 ${signedPercent(report.performance?.benchmark.totalReturn)}`} preserveAspectRatio="none">
      <path d="M0 47.5H720M0 95H720M0 142.5H720" />
      <polyline data-series="benchmark" points={benchmark} />
      <polyline data-series="strategy" points={strategy} />
    </svg>
  </div>;
}

function SectionHead({ eyebrow, title, aside }: { eyebrow: string; title: string; aside?: string }) {
  return <header className="forecast-section-head"><div><span>{eyebrow}</span><h4>{title}</h4></div>{aside && <small>{aside}</small>}</header>;
}

const qualificationCopy: Record<ForecastQualification['status'], string> = {
  QUALIFIED: '已通过资格检验', CONDITIONAL: '条件有效', FAILED: '未通过资格检验',
  INSUFFICIENT_DATA: '资格样本不足'
};

function CalibrationChart({ qualification }: { qualification: ForecastQualification }) {
  const bins = qualification.lockedTest.reliabilityBins;
  return <div className="forecast-calibration-figure">
    <div className="forecast-calibration-chart">
      <svg viewBox="0 0 240 240" role="img" aria-label="锁定测试概率可靠性图：横轴为预测概率，纵轴为实际上涨频率">
        <path className="calibration-grid" d="M28 28H222M28 76.5H222M28 125H222M28 173.5H222M28 222H222M28 28V222M76.5 28V222M125 28V222M173.5 28V222M222 28V222" />
        <path className="calibration-ideal" d="M28 222L222 28" />
        {bins.filter(item => item.count > 0 && item.meanProbability != null && item.observedUpRate != null).map(item =>
          <g key={`${item.lowerBound}-${item.upperBound}`}>
            <circle cx={28 + item.meanProbability! * 194} cy={222 - item.observedUpRate! * 194} r={5 + Math.min(6, item.count / 4)} />
            <title>{`${percent(item.meanProbability)} 预测，${percent(item.observedUpRate)} 实际，${item.count} 个样本`}</title>
          </g>)}
        <text x="125" y="238">预测概率</text><text x="5" y="125" transform="rotate(-90 5 125)">实际上涨率</text>
      </svg>
      <div><span><i />校准后观察点</span><span><i />理想校准线</span></div>
    </div>
    <div className="forecast-calibration-bins"><table aria-label="概率可靠性分箱"><thead><tr><th>概率区间</th><th>样本</th><th>平均预测</th><th>实际上涨</th><th>偏差</th></tr></thead><tbody>{bins.map(item => <tr key={`${item.lowerBound}-${item.upperBound}`}><td>{`${Math.round(item.lowerBound * 100)}–${Math.round(item.upperBound * 100)}%`}</td><td>{item.count}</td><td>{percent(item.meanProbability)}</td><td>{percent(item.observedUpRate)}</td><td>{percent(item.calibrationError)}</td></tr>)}</tbody></table></div>
  </div>;
}

function QualificationSection({ qualification, probabilityInterval }: { qualification: ForecastQualification; probabilityInterval?: ForecastConfidenceInterval }) {
  const locked = qualification.lockedTest;
  const calibrated = locked.calibratedMetrics;
  const intervals = qualification.confidenceIntervals;
  const contradictsQualification = qualification.status === 'QUALIFIED'
    && (calibrated.brierSkillScore <= 0 || calibrated.logLoss > locked.rawMetrics.logLoss);
  const displayedStatus: ForecastQualification['status'] = contradictsQualification ? 'FAILED' : qualification.status;
  const displayedReason = contradictsQualification
    ? '锁定测试的概率质量未达到当前资格门槛'
    : qualification.reason || '模型、校准器与锁定测试严格隔离';
  const slices = [
    ['开发训练', qualification.splitAudit.development],
    ['独立校准', qualification.splitAudit.calibration],
    ['锁定测试', qualification.splitAudit.lockedTest]
  ] as const;
  return <section className="forecast-paper-section forecast-qualification">
    <SectionHead eyebrow="MODEL QUALIFICATION / LOCKED" title="预测可信度与概率校准" aside={`${locked.calibratedMetrics.sampleCount} 个锁定独立锚点`} />
    <div className="forecast-qualification-stamp" data-status={displayedStatus}><div><span>QUALIFICATION</span><strong>{qualificationCopy[displayedStatus]}</strong><small>{displayedReason}</small></div><p>锁定测试</p><dl><div><dt>Brier Skill</dt><dd>{signedPercent(calibrated.brierSkillScore)}</dd><small>95% {interval(intervals.brierSkillScore, signedPercent)}</small></div><div><dt>Log Loss</dt><dd>{number(calibrated.logLoss, 3)}</dd><small>原始 {number(locked.rawMetrics.logLoss, 3)}</small></div><div><dt>ECE</dt><dd>{percent(calibrated.expectedCalibrationError)}</dd><small>原始 {percent(locked.rawMetrics.expectedCalibrationError)}</small></div><div><dt>方向命中率</dt><dd>{percent(calibrated.accuracy)}</dd><small>95% {interval(intervals.accuracy, percent)}</small></div></dl></div>
    <div className="forecast-qualification-body">
      <CalibrationChart qualification={qualification} />
      <aside className="forecast-method-ledger">
        <h5>证据链</h5>
        <div className="forecast-split-timeline">{slices.map(([label, item]) => <article key={label}><span>{label}</span><strong>{item.independentSampleCount} 个锚点</strong><small>{item.startDate} → {item.endDate}</small><p>{item.sampleCount} 个逐日样本 · {item.positiveCount} 个上涨标签{item.purgedCount > 0 ? ` · purge ${item.purgedCount}` : ''}</p></article>)}</div>
        <div className="forecast-method-note"><strong>{qualification.calibration.status === 'FITTED' ? 'Platt 校准已生效' : '校准未拟合，使用原始概率'}</strong><p>校准区 Log Loss：{number(qualification.calibration.rawLogLoss, 3)} → {number(qualification.calibration.calibratedLogLoss, 3)}。{qualification.splitAudit.rule}</p></div>
        <div className="forecast-quality-comparison"><table aria-label="锁定测试概率质量对照"><thead><tr><th>概率口径</th><th>Brier</th><th>Skill</th><th>Log Loss</th><th>ECE</th></tr></thead><tbody><tr><th>原始模型</th><td>{number(locked.rawMetrics.brierScore, 3)}</td><td>{signedPercent(locked.rawMetrics.brierSkillScore)}</td><td>{number(locked.rawMetrics.logLoss, 3)}</td><td>{percent(locked.rawMetrics.expectedCalibrationError)}</td></tr><tr><th>校准模型</th><td>{number(calibrated.brierScore, 3)}</td><td>{signedPercent(calibrated.brierSkillScore)}</td><td>{number(calibrated.logLoss, 3)}</td><td>{percent(calibrated.expectedCalibrationError)}</td></tr><tr><th>朴素基准</th><td>{number(locked.baselineMetrics.brierScore, 3)}</td><td>0.0%</td><td>{number(locked.baselineMetrics.logLoss, 3)}</td><td>{percent(locked.baselineMetrics.expectedCalibrationError)}</td></tr></tbody></table></div>
      </aside>
    </div>
    <div className="forecast-uncertainty-strip"><article><span>策略超额收益 95% 区间</span><strong>{interval(intervals.excessReturn, signedPercent)}</strong></article><article><span>策略 Sharpe 95% 区间</span><strong>{interval(intervals.sharpeRatio, item => number(item, 2))}</strong></article><article><span>朴素基准概率</span><strong>{percent(locked.baselineProbability)}</strong></article></div>
    <footer className="forecast-qualification-foot"><span>TRIAL {qualification.trial.trialId.slice(0, 16)}</span><p>{qualification.trial.featureVersion} · {qualification.trial.splitVersion} · seed {qualification.trial.randomSeed}</p><small>{probabilityInterval?.limitation || '概率区间不覆盖突发事件与市场结构变化'}</small></footer>
  </section>;
}

export function SingleStockForecastPanel({ addToast, setMessage }: {
  addToast: Toast; setMessage: (message: string) => void;
}) {
  const [code, setCode] = useState('');
  const [horizon, setHorizon] = useState<ForecastHorizon>(5);
  const [runs, setRuns] = useState<SingleStockForecastRun[]>([]);
  const [selected, setSelected] = useState<SingleStockForecastRun>();
  const [busy, setBusy] = useState(false);
  const [historyBusy, setHistoryBusy] = useState(true);

  useEffect(() => {
    let active = true;
    api<SingleStockForecastRun[]>('/api/quant/single-stock-forecasts?limit=50')
      .then(value => { if (active) setRuns(Array.isArray(value) ? value : []); })
      .catch(error => { if (active) addToast(error instanceof Error ? error.message : '预测历史加载失败', 'error'); })
      .finally(() => { if (active) setHistoryBusy(false); });
    return () => { active = false; };
  }, []);

  async function run(event: FormEvent) {
    event.preventDefault();
    const normalized = code.trim();
    if (!/^\d{6}$/.test(normalized)) { addToast('请输入六位 A 股代码', 'error'); return; }
    setBusy(true);
    try {
      const value = await api<SingleStockForecastRun>('/api/quant/single-stock-forecasts', {
        method: 'POST', body: JSON.stringify({ code: normalized, horizonDays: horizon })
      });
      setSelected(value); setRuns(current => [value, ...current]);
      setMessage(`${value.instrumentCode} · 完整研究已保存`);
      addToast(value.status === 'ROBUST' ? '研究完成，样本外证据相对稳健' : '研究完成并已写入历史', value.status === 'ROBUST' ? 'success' : 'info');
    } catch (error) { addToast(error instanceof Error ? error.message : '单股研究运行失败', 'error'); }
    finally { setBusy(false); }
  }

  async function openRun(item: SingleStockForecastRun) {
    if (selected?.id === item.id && selected.report) return;
    try {
      const value = await api<SingleStockForecastRun>(`/api/quant/single-stock-forecasts/${item.id}`);
      setSelected(value); setCode(value.instrumentCode.slice(0, 6));
      if (value.horizonDays === 1 || value.horizonDays === 5 || value.horizonDays === 20) setHorizon(value.horizonDays);
    } catch (error) { addToast(error instanceof Error ? error.message : '预测记录读取失败', 'error'); }
  }

  const report = selected?.report;
  const status = statusCopy[report?.status ?? selected?.status ?? ''] ?? { label: report?.status ?? '未知', tone: 'neutral' };
  const probabilityPosition = Math.max(2, Math.min(98, (report?.upProbability ?? .5) * 100));

  return <div className="single-forecast">
    <section className="single-forecast-intro">
      <div><p className="single-forecast-kicker">Single-name research ledger</p><h4>一次预测，<em>留下完整证据。</em></h4><p>用缓存的前复权日线完成滚动样本外验证，把概率、因子、同股基准、风险和当时持仓冻结成可复查记录。</p></div>
      <form className="single-forecast-form" onSubmit={run}>
        <label htmlFor="single-stock-code">股票代码</label>
        <div className="forecast-horizon-rail" role="group" aria-label="预测周期">
          {horizons.map(item => <button type="button" key={item.days} aria-pressed={horizon === item.days} onClick={() => setHorizon(item.days)} aria-label={`${item.days} 日预测周期`}><span>{item.label}</span><strong>{item.role}</strong><small>{item.note}</small></button>)}
        </div>
        <div><input id="single-stock-code" inputMode="numeric" maxLength={6} placeholder="例如 600519" value={code} onChange={event => setCode(event.target.value.replace(/\s/g, ''))} /><button type="submit" disabled={busy}>{busy ? '正在回看历史…' : '运行完整研究'}</button></div>
        <small>当前 {horizon} 日独立模型 · T+1 可执行口径 · 同股买入持有基准</small>
      </form>
    </section>

    <div className="forecast-ledger-layout">
      <aside className="forecast-history" aria-label="预测历史">
        <header><span>RUN ARCHIVE</span><h4>预测历史</h4><small>{runs.length} 条记录</small></header>
        {historyBusy && <p>正在读取历史…</p>}
        {!historyBusy && runs.length === 0 && <p>运行第一份研究后，它会永久留在这里。</p>}
        <div>{runs.map(item => <button type="button" key={item.id} aria-pressed={selected?.id === item.id} onClick={() => openRun(item)}>
          <span><b>{item.instrumentCode}</b><time>{item.createdAt?.replace('T', ' ').slice(0, 16)}</time></span>
          <strong>{percent(item.upProbability)}</strong><small>{item.horizonDays ?? item.report?.horizonDays ?? 20}D · {statusCopy[item.status]?.label ?? item.status}{item.maturityStatus ? ` · ${maturityCopy[item.maturityStatus]}` : ''}{item.sameDataAsPrevious ? ' · 同一数据' : ''}</small>
        </button>)}</div>
      </aside>

      <main className="forecast-report">
        {!report && <section className="single-forecast-empty"><span>RESEARCH GATE</span><strong>选择历史记录，或运行一次新研究</strong><p>这里不会全市场选股。每份报告只回答一只股票的概率模型是否在样本外优于同股买入并持有。</p><div><i />不可变记录 <i />因子解释 <i />允许拒绝结论</div></section>}

        {report?.status === 'INSUFFICIENT_DATA' && <section className="single-forecast-gate" role="status"><span>DATA GATE / SAVED</span><h4>数据不足</h4><p>{report.conclusion}</p><strong>已取得 {report.barCount} 根日线；本次不足结论也已保存。</strong><small>截止 {report.asOfDate} · {report.sourceCode}</small></section>}

        {report && report.status !== 'INSUFFICIENT_DATA' && report.upProbability != null && <>
          <section className="single-forecast-board" data-tone={status.tone}>
            <header><div><span>{report.instrumentCode}</span><small>运行 #{selected?.id} · {report.horizonDays}D 独立试验 · 数据截止 {report.asOfDate}</small></div><b>{status.label}</b></header>
            <div className="single-forecast-thesis"><div className="single-forecast-probability"><span>{report.qualification ? '校准后上涨概率' : `未来 ${report.horizonDays} 日净收益为正的概率`}</span><strong>{percent(report.upProbability)}</strong>{report.qualification && <><small className="forecast-probability-range">{interval(report.probabilityInterval, percent)}</small><small>原始模型 {percent(report.rawProbability)}</small></>}<small>未来 {report.horizonDays} 个交易日 · 概率不是买卖指令</small></div><div className="single-forecast-verdict"><span>SELECTIVE DECISION</span><div className="forecast-decision-stamp" data-decision={report.decision ?? 'ABSTAIN'}><small>{decisionCopy[report.decision ?? 'ABSTAIN'].code}</small><strong>{decisionCopy[report.decision ?? 'ABSTAIN'].label}</strong></div><p>{report.decisionReason || report.conclusion}</p><small>{report.conclusion}</small>{selected?.sameDataAsPrevious && <small>与上一条记录使用相同数据指纹</small>}</div></div>
            <div className="forecast-probability-tape"><div className="forecast-tape-labels"><span>下行证据</span><b>50% 中线</b><span>上行证据</span></div><div className="forecast-tape-track"><i /><i /><i /><b style={{ left: `${probabilityPosition}%` }} /></div><small style={{ left: `${probabilityPosition}%` }}>{percent(report.upProbability)}</small></div>
            <div className="forecast-return-band"><div><span>相近信号 P20</span><strong>{signedPercent(report.lowerNetReturn)}</strong></div><div><span>相近信号平均</span><strong>{signedPercent(report.expectedNetReturn)}</strong></div><div><span>相近信号 P80</span><strong>{signedPercent(report.upperNetReturn)}</strong></div></div>
            {report.selectiveValidation && <div className="forecast-selective-strip"><article><span>信号覆盖率</span><strong>{percent(report.selectiveValidation.coverage)}</strong><small>{report.selectiveValidation.coveredCount} / {report.selectiveValidation.sampleCount} 个锁定样本给出方向</small></article><article><span>覆盖后命中率</span><strong>{percent(report.selectiveValidation.coveredAccuracy)}</strong><small>只统计越过 {percent(report.selectiveValidation.lowerThreshold)} / {percent(report.selectiveValidation.upperThreshold)} 阈值的样本</small></article><article><span>弃权率</span><strong>{percent(report.selectiveValidation.abstainRate)}</strong><small>宁可少判断，也不强迫低置信度信号表态</small></article></div>}
          </section>

          {report.qualification
            ? <QualificationSection qualification={report.qualification} probabilityInterval={report.probabilityInterval} />
            : <section className="forecast-paper-section forecast-qualification-legacy"><SectionHead eyebrow="MODEL QUALIFICATION" title="预测可信度" /><p>该记录生成时尚未启用锁定资格检验</p><small>仍可查看当时的滚动样本外、同股基准和因子证据，但不能补写未来版本的校准结论。</small></section>}

          {report.performance && <section className="forecast-paper-section forecast-performance">
            <SectionHead eyebrow="PERFORMANCE / SAME STOCK" title="策略与同股买入并持有" aside={`入场阈值 ${percent(report.strategyPolicy.signalThreshold)} · 持有 ${report.strategyPolicy.holdingDays} 日`} />
            <EquityChart report={report} />
            <div className="forecast-scoreline"><article><span>策略累计收益</span><strong>{signedPercent(report.performance.strategy.totalReturn)}</strong></article><article><span>同股买入并持有</span><strong>{signedPercent(report.performance.benchmark.totalReturn)}</strong></article><article data-negative={report.performance.excessReturn < 0}><span>策略超额收益</span><strong>{signedPercent(report.performance.excessReturn)}</strong></article></div>
            <div className="forecast-metric-grid">
              <article><span>策略最大回撤</span><strong>{percent(report.performance.strategy.maxDrawdown)}</strong><small>{report.performance.strategy.maxDrawdownStartDate} → {report.performance.strategy.maxDrawdownTroughDate}</small></article>
              <article><span>最大回撤持续时间</span><strong>{report.performance.strategy.maxDrawdownDurationDays} 日</strong><small>{report.performance.strategy.maxDrawdownRecoveryDate ? `恢复于 ${report.performance.strategy.maxDrawdownRecoveryDate}` : '截至样本末仍未恢复'}</small></article>
              <article><span>Sharpe</span><strong>{number(report.performance.strategy.sharpeRatio)}</strong><small>基准 {number(report.performance.benchmark.sharpeRatio)}</small></article>
              <article><span>年化波动率</span><strong>{percent(report.performance.strategy.annualizedVolatility)}</strong><small>基准 {percent(report.performance.benchmark.annualizedVolatility)}</small></article>
              <article><span>盈利交易胜率</span><strong>{percent(report.performance.profitableTradeRate)}</strong><small>{report.performance.tradeCount} 次完整交易</small></article>
              <article><span>单边换手率</span><strong>{number(report.performance.turnover)}×</strong><small>累计成本 {percent(report.performance.totalCost)}</small></article>
              <article><span>持仓时间占比</span><strong>{percent(report.performance.holdingTimeRatio)}</strong><small>平均持有 {number(report.performance.averageHoldingDays, 1)} 日</small></article>
              <article><span>日胜率</span><strong>{percent(report.performance.strategy.dailyWinRate)}</strong><small>按策略净值日变化计算</small></article>
            </div>
          </section>}

          <section className="forecast-paper-section">
            <SectionHead eyebrow="FACTOR NOTEBOOK" title="因子知识与当前解释" aside="贡献解释模型，不证明因果" />
            <div className="forecast-factor-list">{report.factorExplanations.map(item => <article key={item.code} data-direction={item.contribution >= 0 ? 'positive' : 'negative'}><header><span>{item.category} · {item.code}</span><b>{item.direction}</b></header><h5>{item.name}</h5><p>{item.economicMeaning}</p><dl><div><dt>当前值</dt><dd>{number(item.currentValue, 4)}</dd></div><div><dt>历史位置</dt><dd>{percent(item.historicalPercentile)}</dd></div><div><dt>模型贡献</dt><dd>{item.contribution >= 0 ? '+' : ''}{number(item.contribution, 3)}</dd></div></dl><details><summary>公式与边界</summary><p>{item.formula} · {item.window}</p><p>{item.boundary}</p></details></article>)}</div>
          </section>

          {(report.inSample || report.outOfSample) && <section className="forecast-paper-section">
            <SectionHead eyebrow="VALIDATION SPLIT" title="样本内 / 样本外" aside={`${report.validation?.independentSampleCount ?? 0} 个非重叠独立锚点`} />
            <div className="forecast-split-grid">{report.inSample && <article><span>样本内 · 仅诊断</span><strong>{report.inSample.sampleCount} 个样本</strong><dl><div><dt>命中率</dt><dd>{percent(report.inSample.accuracy)}</dd></div><div><dt>Brier</dt><dd>{number(report.inSample.brierScore, 3)}</dd></div></dl><p>{report.inSample.evidenceRole}</p></article>}{report.outOfSample && <article><span>样本外 · 决定结论</span><strong>{report.outOfSample.sampleCount} 次顺序预测</strong><dl><div><dt>命中率</dt><dd>{percent(report.outOfSample.accuracy)}</dd></div><div><dt>模型 / 基准 Brier</dt><dd>{number(report.outOfSample.brierScore, 3)} / {number(report.outOfSample.baselineBrierScore, 3)}</dd></div></dl><p>{report.outOfSample.evidenceRole}</p></article>}</div>
          </section>}

          {report.parameterStability && <section className="forecast-paper-section">
            <SectionHead eyebrow="NEIGHBORHOOD TEST" title="相邻参数稳定性" aside={`${percent(report.parameterStability.positiveExcessRatio)} 邻域取得正超额`} />
            <div className="forecast-table-wrap"><table aria-label="相邻参数稳定性"><thead><tr><th>方案</th><th>阈值</th><th>持有</th><th>年化收益</th><th>同股超额</th><th>Sharpe</th><th>最大回撤</th><th>交易</th></tr></thead><tbody>{report.parameterStability.scenarios.map(item => <tr key={`${item.holdingDays}-${item.threshold}`} data-primary={item.primary}><td>{item.primary ? '主方案' : '相邻方案'}</td><td>{percent(item.threshold)}</td><td>{item.holdingDays} 日</td><td>{signedPercent(item.annualizedReturn)}</td><td>{signedPercent(item.excessReturn)}</td><td>{number(item.sharpeRatio)}</td><td>{percent(item.maxDrawdown)}</td><td>{item.tradeCount}</td></tr>)}</tbody></table></div>
          </section>}

          <div className="forecast-two-column">
            <section className="forecast-paper-section"><SectionHead eyebrow="CALENDAR" title="分年度表现" /><div className="forecast-table-wrap"><table aria-label="分年度表现"><thead><tr><th>年份</th><th>策略</th><th>同股持有</th><th>超额</th><th>回撤</th><th>交易</th></tr></thead><tbody>{report.annualPerformance.map(item => <tr key={item.year}><td>{item.year}</td><td>{signedPercent(item.strategyReturn)}</td><td>{signedPercent(item.benchmarkReturn)}</td><td>{signedPercent(item.excessReturn)}</td><td>{percent(item.maxDrawdown)}</td><td>{item.tradeCount}</td></tr>)}</tbody></table></div></section>
            <section className="forecast-paper-section"><SectionHead eyebrow="STOCK REGIMES" title="标的自身趋势阶段" /><div className="forecast-table-wrap"><table aria-label="标的自身趋势阶段"><thead><tr><th>阶段</th><th>样本日</th><th>策略</th><th>同股持有</th><th>Sharpe</th><th>持仓</th></tr></thead><tbody>{report.regimePerformance.map(item => <tr key={item.regime}><td>{item.label}</td><td>{item.sampleDays}</td><td>{signedPercent(item.strategyReturn)}</td><td>{signedPercent(item.benchmarkReturn)}</td><td>{number(item.sharpeRatio)}</td><td>{percent(item.holdingTimeRatio)}</td></tr>)}</tbody></table></div></section>
          </div>

          {selected?.holdingSnapshot && <section className="forecast-paper-section forecast-holding"><SectionHead eyebrow="PERSONAL CONTEXT / FROZEN" title="我的持仓快照" aside="不参与训练与概率计算" /><div className="forecast-holding-body"><div><span>{selected.holdingSnapshot.held ? selected.holdingSnapshot.instrumentName || report.instrumentCode : '当前未记录持仓'}</span><strong>{selected.holdingSnapshot.held ? money(selected.holdingSnapshot.estimatedMarketValue) : '—'}</strong><small>记录时估算市值</small></div><dl><div><dt>数量</dt><dd>{number(selected.holdingSnapshot.quantity, 2)}</dd></div><div><dt>平均成本</dt><dd>{money(selected.holdingSnapshot.averageCost)}</dd></div><div><dt>记录时收盘</dt><dd>{money(selected.holdingSnapshot.lastClose)}</dd></div><div><dt>未实现收益</dt><dd>{signedPercent(selected.holdingSnapshot.unrealizedReturn)}</dd></div></dl><p>{selected.holdingSnapshot.interpretation}</p></div></section>}

          {report.recentObservations.length > 0 && <section className="forecast-paper-section"><SectionHead eyebrow="OOS LEDGER" title="最近样本外预测" /><div className="forecast-table-wrap"><table aria-label="最近样本外预测"><thead><tr><th>信号日</th><th>当时概率</th><th>实际净收益</th><th>结果</th></tr></thead><tbody>{report.recentObservations.map(item => <tr key={item.signalDate}><td>{item.signalDate}</td><td>{percent(item.probability)}</td><td>{signedPercent(item.actualNetReturn)}</td><td>{item.correct ? '命中' : '偏离'}</td></tr>)}</tbody></table></div></section>}

          <footer className="forecast-provenance"><div><span>DATA TRACE</span><b>{report.barCount} 根 QFQ 日线 · {report.labeledSampleCount} 个标签 · {report.sourceCode}</b></div><code>{report.dataFingerprint.slice(0, 20)}</code><p>{report.modelVersion} · {report.reportSchemaVersion}</p>{report.warnings.map(warning => <p key={warning}>{warning}</p>)}</footer>
        </>}
      </main>
    </div>
  </div>;
}
