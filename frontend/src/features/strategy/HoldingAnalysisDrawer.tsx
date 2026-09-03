export type HoldingAnalysis = {
  instrumentCode: string;
  instrumentName?: string;
  entryDate: string;
  firstObservedDate?: string;
  asOfDate?: string;
  holdingCalendarDays: number;
  observedTradingDays: number;
  costBasis: number;
  latestPrice: number;
  quantity: number;
  totalCost: number;
  marketValue: number;
  unrealizedProfit: number;
  holdingReturn: number;
  maximumFavorableExcursion: number;
  maximumAdverseExcursion: number;
  maximumDrawdown: number;
  maximumDrawdownDays: number;
  annualizedVolatility: number;
  qualityStatus: string;
  sourceCode: string;
  method: string;
  warnings: string[];
  forecast?: {
    runId: number;
    asOfDate: string;
    horizonDays: number;
    status: string;
    upProbability?: number;
    p10?: number;
    p50?: number;
    p90?: number;
    modelVersion: string;
  };
  series: Array<{ tradeDate: string; close: number; returnSinceEntry: number; drawdown: number }>;
};

type Props = {
  analysis?: HoldingAnalysis;
  loading: boolean;
  targetName: string;
  targetCode: string;
  onClose: () => void;
};

export function HoldingAnalysisDrawer({ analysis, loading, targetName, targetCode, onClose }: Props) {
  return <div className="holding-analysis-overlay" onMouseDown={event => {
    if (event.target === event.currentTarget) onClose();
  }}>
    <aside className="holding-analysis-drawer" aria-label={`${targetName}持仓量化分析`}>
      <header className="holding-analysis-header">
        <div><span>POSITION PATH · REAL ACCOUNT</span><h3>{analysis?.instrumentName || targetName}</h3><p>{analysis?.instrumentCode || targetCode} · 真实账本驱动</p></div>
        <div className="holding-analysis-header-result">
          {analysis ? <strong className={analysis.holdingReturn >= 0 ? 'up' : 'down'}>{percent(analysis.holdingReturn)}</strong> : null}
          <button type="button" aria-label="关闭持仓量化分析" onClick={onClose}>×</button>
        </div>
      </header>

      {loading ? <div className="holding-analysis-loading"><i /><b>正在重建持仓路径</b><span>读取缓存日线并核对最新预测证据…</span></div> : null}
      {!loading && analysis ? <div className="holding-analysis-body">
        <section className="holding-path-card">
          <div className="holding-section-title"><div><span>REALIZED PATH</span><h4>持仓收益路径</h4></div><small>{analysis.entryDate} → {analysis.asOfDate || '当前'}</small></div>
          {analysis.series.length > 1 ? <HoldingPathChart analysis={analysis} /> : <div className="holding-path-unavailable">历史路径暂不可用，当前盈亏仍以真实账本和原始行情计算。</div>}
          <div className="holding-path-caption"><span><i className="cost" />成本基准 0%</span><span><i className="path" />持仓收益</span><em>{analysis.observedTradingDays || '—'} 个交易日样本</em></div>
        </section>

        <section className="holding-analysis-summary">
          <div className="holding-position-facts">
            <div><small>持仓成本</small><b>{money(analysis.totalCost)}</b><span>{analysis.quantity} 股 × ¥{fixed(analysis.costBasis)}</span></div>
            <div><small>当前市值</small><b>{money(analysis.marketValue)}</b><span>现价 ¥{fixed(analysis.latestPrice)}</span></div>
            <div><small>浮动盈亏</small><b className={analysis.unrealizedProfit >= 0 ? 'up' : 'down'}>{signedMoney(analysis.unrealizedProfit)}</b><span>持有 {analysis.holdingCalendarDays} 个自然日</span></div>
          </div>
          <div className="holding-risk-grid">
            <RiskMetric label="最大有利波动" value={percent(analysis.maximumFavorableExcursion)} note="持仓中曾达到的最好位置" tone="positive" />
            <RiskMetric label="最大不利波动" value={percent(analysis.maximumAdverseExcursion)} note="持仓中曾承受的最差位置" tone="negative" />
            <RiskMetric label="最大回撤" value={percent(analysis.maximumDrawdown)} note="阶段高点至随后低点" tone="negative" />
            <RiskMetric label="回撤持续" value={`${analysis.maximumDrawdownDays} 天`} note="最大回撤对应自然日跨度" />
            <RiskMetric label="年化波动率" value={percent(analysis.annualizedVolatility)} note="基于持仓期间日收益估算" />
            <RiskMetric label="数据覆盖" value={qualityLabel(analysis.qualityStatus)} note={`${analysis.sourceCode} · ${analysis.firstObservedDate || '无历史'}`} />
          </div>
        </section>

        <ForecastEvidence analysis={analysis} />

        {analysis.warnings.length ? <section className="holding-analysis-notes"><b>数据口径</b>{analysis.warnings.map(item => <p key={item}>{item}</p>)}</section> : null}
      </div> : null}
    </aside>
  </div>;
}

function HoldingPathChart({ analysis }: { analysis: HoldingAnalysis }) {
  const series = sampleSeries(analysis.series, 260);
  const values = series.map(item => item.returnSinceEntry);
  const rawMin = Math.min(0, ...values);
  const rawMax = Math.max(0, ...values);
  const spread = Math.max(.02, rawMax - rawMin);
  const min = rawMin - spread * .12;
  const max = rawMax + spread * .12;
  const left = 62; const right = 886; const top = 28; const bottom = 254;
  const x = (index: number) => left + (right - left) * index / Math.max(1, series.length - 1);
  const y = (value: number) => top + (max - value) / (max - min) * (bottom - top);
  const path = series.map((item, index) => `${index ? 'L' : 'M'} ${x(index).toFixed(2)} ${y(item.returnSinceEntry).toFixed(2)}`).join(' ');
  const zeroY = y(0);
  const area = `${path} L ${right} ${zeroY.toFixed(2)} L ${left} ${zeroY.toFixed(2)} Z`;
  const ticks = [max, (max + min) / 2, min];
  const positive = analysis.holdingReturn >= 0;
  return <svg className="holding-path-chart" viewBox="0 0 930 300" role="img" aria-label="持仓期间收益率曲线">
    <defs><linearGradient id="holdingPathFill" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor={positive ? '#52e0ae' : '#ff7878'} stopOpacity=".24" /><stop offset="1" stopColor={positive ? '#52e0ae' : '#ff7878'} stopOpacity="0" /></linearGradient></defs>
    {ticks.map(value => <g key={value}><line x1={left} y1={y(value)} x2={right} y2={y(value)} className="holding-chart-grid" /><text x={left - 12} y={y(value) + 4} textAnchor="end">{percent(value)}</text></g>)}
    <line x1={left} y1={zeroY} x2={right} y2={zeroY} className="holding-chart-zero" />
    <path d={area} fill="url(#holdingPathFill)" />
    <path d={path} className={positive ? 'holding-chart-line positive' : 'holding-chart-line negative'} />
    <circle cx={left} cy={y(series[0].returnSinceEntry)} r="5" className="holding-chart-entry" />
    <circle cx={right} cy={y(series[series.length - 1].returnSinceEntry)} r="5" className="holding-chart-last" />
    <text x={left} y="286">{series[0].tradeDate}</text><text x={right} y="286" textAnchor="end">{series[series.length - 1].tradeDate}</text>
  </svg>;
}

function ForecastEvidence({ analysis }: { analysis: HoldingAnalysis }) {
  const forecast = analysis.forecast;
  return <section className="holding-forecast-card">
    <div className="holding-section-title"><div><span>FORWARD EVIDENCE</span><h4>模型冻结证据</h4></div><small>{forecast ? `${forecast.asOfDate} · ${forecast.horizonDays} 日` : '尚无可复用预测'}</small></div>
    {forecast ? <>
      <div className="holding-forecast-grid">
        <div className="probability"><small>未来上涨概率</small><strong>{forecast.upProbability == null ? '—' : percent(forecast.upProbability)}</strong><span>{forecast.status}</span></div>
        <div><small>P10 下行</small><b>{forecast.p10 == null ? '—' : percent(forecast.p10)}</b></div>
        <div><small>P50 中位</small><b>{forecast.p50 == null ? '—' : percent(forecast.p50)}</b></div>
        <div><small>P90 上行</small><b>{forecast.p90 == null ? '—' : percent(forecast.p90)}</b></div>
      </div>
      <footer><span>这是当前持仓诊断证据，不冒充历史买入依据。</span><code>RUN #{forecast.runId} · {forecast.modelVersion}</code></footer>
    </> : <div className="holding-forecast-empty">请先在单股预测页生成该股票的预测，系统会自动关联最新冻结结果。</div>}
  </section>;
}

function RiskMetric({ label, value, note, tone }: { label: string; value: string; note: string; tone?: string }) {
  return <article data-tone={tone}><small>{label}</small><b>{value}</b><span>{note}</span></article>;
}

function sampleSeries<T>(values: T[], limit: number) {
  if (values.length <= limit) return values;
  const step = (values.length - 1) / (limit - 1);
  return Array.from({ length: limit }, (_, index) => values[Math.round(index * step)]);
}

function money(value: number) { return `¥${Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`; }
function signedMoney(value: number) { return `${value >= 0 ? '+' : '-'}${money(Math.abs(value))}`; }
function percent(value: number) { return `${value >= 0 ? '' : '-'}${(Math.abs(value) * 100).toFixed(2)}%`; }
function fixed(value: number) { return Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 3 }); }
function qualityLabel(value: string) {
  if (value === 'COMPLETE') return '完整';
  if (value === 'PARTIAL_HISTORY') return '部分';
  return '不可用';
}
