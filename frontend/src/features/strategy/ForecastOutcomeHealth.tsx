import { CSSProperties } from 'react';
import { ForecastModelHealth, SingleStockForecastRun } from './quantTypes';

const percent = (value?: number, digits = 1) => value == null ? '—' : `${(value * 100).toFixed(digits)}%`;
const signedPercent = (value?: number) => value == null ? '—' : `${value >= 0 ? '+' : ''}${(value * 100).toFixed(1)}%`;
const decimal = (value?: number, digits = 3) => value == null ? '—' : value.toFixed(digits);
const price = (value?: number) => value == null ? '—' : `¥${value.toLocaleString('zh-CN', { maximumFractionDigits: 3 })}`;

const healthCopy: Record<ForecastModelHealth['status'], { label: string; short: string }> = {
  INSUFFICIENT_EVIDENCE: { label: '健康证据积累中', short: '积累中' },
  HEALTHY: { label: '方向门禁开放', short: '健康' },
  WATCH: { label: '进入谨慎观察', short: '观察' },
  PAUSED: { label: '方向输出已暂停', short: '暂停' }
};

function outcomeResult(run: SingleStockForecastRun) {
  if (run.maturityStatus === 'UNAVAILABLE') return { title: '本次无法验证', detail: run.outcome?.note || '缺少对应交易日行情，未纳入健康统计。' };
  if (run.maturityStatus !== 'MATURED') return {
    title: '等待到期行情', detail: `需要信号日后第 ${run.horizonDays ?? run.report?.horizonDays ?? 20} 个交易日的退出开盘价；系统在再次进入策略工作台时自动结算。`
  };
  if (run.report?.decision === 'ABSTAIN'
      && (run.report.modelDecision === 'UP' || run.report.modelDecision === 'DOWN')) {
    return run.outcome?.correct
      ? { title: '影子方向命中', detail: '当时健康门禁未向用户输出方向；系统保留模型原始方向作影子验证，本次与实际方向一致。' }
      : { title: '影子方向偏离', detail: '当时健康门禁未向用户输出方向；系统保留模型原始方向作影子验证，本次与实际方向不一致。' };
  }
  if (run.report?.decision === 'ABSTAIN' || run.outcome?.correct == null) return { title: '本次当时弃权', detail: '保留真实收益用于概率评分，但不把弃权记录算作方向命中或偏离。' };
  return run.outcome.correct
    ? { title: '本次命中', detail: '当时给出的选择性方向与扣除冻结交易成本后的实际方向一致。' }
    : { title: '本次偏离', detail: '当时方向与扣除冻结交易成本后的实际方向不一致，已计入健康门禁。' };
}

export function ForecastOutcomeHealth({ run }: { run: SingleStockForecastRun }) {
  const health = run.modelHealth;
  const outcome = run.outcome;
  const result = outcomeResult(run);
  const activeStatus = health?.status ?? 'INSUFFICIENT_EVIDENCE';
  const hasHealthEvidence = (health?.sampleCount ?? 0) > 0;
  const statuses: ForecastModelHealth['status'][] = ['INSUFFICIENT_EVIDENCE', 'WATCH', 'HEALTHY', 'PAUSED'];

  return <section className="forecast-paper-section forecast-live-proof" data-health={activeStatus}>
    <header className="forecast-live-proof-head">
      <div><span>FORWARD OUTCOME / MODEL VITALS</span><h4>真实到期验证与模型健康</h4><p>只使用预测发生后才出现的行情评价当时结论；同股票、同周期、同模型版本滚动统计，最多保留最近 20 次。</p></div>
      <div className="forecast-health-seal"><i /><span>{healthCopy[activeStatus].short}</span><strong>{healthCopy[activeStatus].label}</strong></div>
    </header>

    <div className="forecast-health-rail" aria-label={`模型健康状态：${healthCopy[activeStatus].label}`}>
      {statuses.map(status => <div key={status} data-current={status === activeStatus}><i /><span>{healthCopy[status].short}</span></div>)}
    </div>

    <div className="forecast-live-proof-body">
      <article className="forecast-outcome-docket" data-result={run.maturityStatus === 'MATURED' ? (outcome?.correct === false ? 'miss' : 'settled') : 'pending'}>
        <header><span>RUN #{run.id} / OUTCOME</span><strong>{result.title}</strong><small>{run.maturityStatus === 'MATURED' ? `结算于 ${outcome?.settledAt?.replace('T', ' ').slice(0, 16) || '—'}` : maturityLabel(run.maturityStatus)}</small></header>
        <div className="forecast-outcome-route">
          <div><span>T+1 入场</span><strong>{price(outcome?.entryOpen)}</strong><small>{outcome?.entryDate || '等待交易日确认'}</small></div>
          <i aria-hidden="true">→</i>
          <div><span>T+{(run.horizonDays ?? run.report?.horizonDays ?? 20) + 1} 退出</span><strong>{price(outcome?.exitOpen)}</strong><small>{outcome?.exitDate || '尚未到期'}</small></div>
          <div className="forecast-outcome-return"><span>扣费后实际收益</span><strong>{signedPercent(outcome?.actualNetReturn)}</strong><small>{outcome?.actualDirection === 'UP' ? '实际上涨' : outcome?.actualDirection === 'DOWN' ? '实际下跌' : '方向待确认'} · {outcome?.sourceCode || '行情源待确认'}</small></div>
        </div>
        <p>{result.detail}</p>
      </article>

      <aside className="forecast-health-vitals">
        <div className="forecast-vital-primary"><span>概率误差 / BRIER</span><strong>{decimal(hasHealthEvidence ? health?.brierScore : undefined)}</strong><small>0.5 无信息基准 {decimal(hasHealthEvidence ? health?.baselineBrierScore : undefined)}</small><i style={{ '--health-score': `${hasHealthEvidence ? Math.max(0, Math.min(100, (1 - (health?.brierScore ?? .25) / .5) * 100)) : 0}%` } as CSSProperties} /></div>
        <dl>
          <div><dt>到期样本</dt><dd>{health?.sampleCount ?? 0}<small>/ 20</small></dd></div>
          <div><dt>方向覆盖</dt><dd>{percent(health?.coverage)}</dd><small>{health?.coveredCount ?? 0} 次表态 · {health?.abstainedCount ?? 0} 次弃权</small></div>
          <div><dt>覆盖后命中</dt><dd>{percent(health?.coveredAccuracy)}</dd><small>弃权不计入方向命中率</small></div>
          <div><dt>Log Loss</dt><dd>{decimal(health?.logLoss)}</dd><small>越低越好，错误自信惩罚更重</small></div>
          <div><dt>实际上涨占比</dt><dd>{percent(health?.observedUpRate)}</dd><small>仅描述当前验证窗口</small></div>
        </dl>
        <footer><strong>{health?.conclusion || '至少需要 8 次同口径到期结果，当前不会因小样本自动暂停方向输出。'}</strong><small>{health?.firstAsOfDate && health?.lastAsOfDate ? `验证窗口 ${health.firstAsOfDate} → ${health.lastAsOfDate}` : '验证窗口尚未形成'} · {run.modelVersion}</small></footer>
      </aside>
    </div>
  </section>;
}

function maturityLabel(status?: SingleStockForecastRun['maturityStatus']) {
  if (status === 'UNAVAILABLE') return '行情不可用';
  if (status === 'MATURED') return '已完成结算';
  return '待未来行情';
}
