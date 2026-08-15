import type { ForecastAuditEngineMetrics, ForecastBacktestAudit } from './quantTypes';
import './BacktestAuditPanel.css';

const verdict = {
  PASS: { label: '两套账本一致', note: '成交时点与关键指标落在审计容差内' },
  WARNING: { label: '账本存在差异', note: '差异已定位到交易或指标，请结合明细阅读' },
  UNAVAILABLE: { label: '影子账本暂不可用', note: '原生预测和回测结果仍然有效，本次不使用独立佐证' }
} as const;

const mismatchLabels: Record<string, string> = {
  TRADE_COUNT: '交易数量', ENTRY_DATE: '入场日期', EXIT_DATE: '退出日期',
  RETURN: '收益', COST: '交易成本', MAX_DRAWDOWN: '最大回撤', SHARPE: 'Sharpe'
};

function pct(value: number) { return `${(value * 100).toFixed(1)}%`; }
function signedPct(value: number) { return `${value >= 0 ? '+' : ''}${(value * 100).toFixed(2)}%`; }

function EngineLedger({ metrics, title }: { metrics: ForecastAuditEngineMetrics; title: string }) {
  return <article className="backtest-engine-ledger">
    <header><span>{metrics.engine}</span><h5>{title}</h5></header>
    <dl>
      <div><dt>累计收益</dt><dd>{signedPct(metrics.totalReturn)}</dd></div>
      <div><dt>最大回撤</dt><dd>{pct(metrics.maxDrawdown)}</dd></div>
      <div><dt>Sharpe</dt><dd>{metrics.sharpeRatio.toFixed(2)}</dd></div>
      <div><dt>完整交易</dt><dd>{metrics.tradeCount}</dd></div>
      <div><dt>累计成本</dt><dd>{pct(metrics.totalCost)}</dd></div>
    </dl>
  </article>;
}

export function BacktestAuditPanel({ audit }: { audit: ForecastBacktestAudit }) {
  const state = verdict[audit.status];
  return <section className="forecast-paper-section backtest-audit-panel" data-status={audit.status}>
    <header className="forecast-section-head"><div><span>DIFFERENTIAL BACKTEST</span><h4>独立回测审计</h4></div><small>Backtesting.py 影子验证 · {audit.durationMs} ms</small></header>
    <div className="backtest-audit-verdict"><i aria-hidden="true" /><div><strong>{state.label}</strong><p>{state.note}</p></div><span>SHADOW / 不参与本期推荐门禁</span></div>
    <div className="backtest-ledger-track">
      <EngineLedger metrics={audit.primaryEngine} title="FinScope 原生账本" />
      <div className="backtest-agreement" aria-label="成交日期一致率"><span>ENTRY</span><strong>{pct(audit.entryDateAgreementRate)}</strong><i /><span>EXIT</span><strong>{pct(audit.exitDateAgreementRate)}</strong></div>
      {audit.shadowEngine ? <EngineLedger metrics={audit.shadowEngine} title="Backtesting.py 影子账本" /> : <article className="backtest-engine-ledger backtest-engine-missing"><span>SECOND LEDGER</span><strong>等待下一次独立复核</strong><p>本次不会影响原生量化结论。</p></article>}
    </div>
    <div className="backtest-delta-strip"><div><span>收益差</span><b>{pct(audit.returnDelta)}</b></div><div><span>回撤差</span><b>{pct(audit.maxDrawdownDelta)}</b></div><div><span>Sharpe 差</span><b>{audit.sharpeDelta.toFixed(3)}</b></div><div><span>成本差</span><b>{pct(audit.costDelta)}</b></div></div>
    {audit.mismatches.length > 0 && <div className="backtest-mismatches"><span>DIFFERENCE LEDGER</span>{audit.mismatches.map((item, index) => <article key={`${item.category}-${item.tradeIndex ?? index}`}><b>{item.tradeIndex ? `第 ${item.tradeIndex} 笔 · ` : ''}{mismatchLabels[item.category] ?? item.category}</b><p>{item.detail}</p>{(item.primaryValue != null || item.shadowValue != null) && <code>{String(item.primaryValue ?? '—')} → {String(item.shadowValue ?? '—')}</code>}</article>)}</div>}
    <footer>{audit.limitations.map(item => <p key={item}>{item}</p>)}</footer>
  </section>;
}
