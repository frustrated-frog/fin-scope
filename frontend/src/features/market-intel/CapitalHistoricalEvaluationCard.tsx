import type { CapitalBehaviorEvaluation, CapitalSignalEvaluation } from './marketIntelTypes';

const evaluationStatusLabels: Record<CapitalSignalEvaluation['evaluationStatus'], string> = {
  UNTESTED: '样本不足',
  EXPLORATORY: '探索性统计',
  VALIDATED: '已验证',
  INVALIDATED: '已失效'
};

const stabilityLabels: Record<CapitalSignalEvaluation['stabilityStatus'], string> = {
  INSUFFICIENT_SAMPLE: '稳定性待积累',
  CONSISTENT: '前后样本方向一致',
  MIXED: '前后样本表现分化'
};

const decayLabels: Record<NonNullable<CapitalSignalEvaluation['decayStatus']>, string> = {
  INSUFFICIENT_SAMPLE: '衰减待积累',
  BASELINE: '短周期基准',
  PERSISTENT: '跨周期保持',
  DECAYING: '跨周期衰减',
  REVERSING: '跨周期反转'
};

function percent(value?: number | null, signed = false) {
  if (value == null) return '--';
  const amount = value * 100;
  return `${signed && amount > 0 ? '+' : ''}${amount.toFixed(2)}%`;
}

function SignalEvaluationRow({
  signal,
  historyReliable
}: {
  signal: CapitalSignalEvaluation;
  historyReliable: boolean;
}) {
  const publishable = historyReliable && ['EXPLORATORY', 'VALIDATED'].includes(signal.evaluationStatus)
    && signal.sampleCount >= 5;
  const maturity = Math.min(100, (signal.sampleCount / 5) * 100);
  return (
    <article className={`market-intel-evaluation-row status-${signal.evaluationStatus.toLowerCase()}`}>
      <header>
        <div>
          <strong>{signal.signalLabel}</strong>
          <span>{signal.horizonDays} 个交易日后</span>
        </div>
        <small>{evaluationStatusLabels[signal.evaluationStatus]}</small>
      </header>
      <div className="market-intel-evaluation-maturity" aria-label={`有效样本 ${signal.sampleCount} 个，展示门槛 5 个`}>
        <span style={{ width: `${maturity}%` }} />
      </div>
      <div className="market-intel-evaluation-sample">
        <span>有效样本 {signal.sampleCount}</span>
        <span>{stabilityLabels[signal.stabilityStatus]}</span>
        {signal.decayStatus && <span>{decayLabels[signal.decayStatus]}</span>}
        {signal.lastEventDate && <span>最近事件 {signal.lastEventDate}</span>}
      </div>
      {publishable ? (
        <dl className="market-intel-evaluation-stats">
          <div><dt>超额平均收益</dt><dd>{percent(signal.excessAverageReturn, true)}</dd></div>
          <div><dt>超额中位收益</dt><dd>{percent(signal.excessMedianReturn, true)}</dd></div>
          <div><dt>信号平均收益</dt><dd>{percent(signal.averageReturn, true)}</dd></div>
          <div><dt>无条件平均基线</dt><dd>{percent(signal.baselineAverageReturn, true)}</dd></div>
          <div><dt>正收益占比</dt><dd>{percent(signal.positiveRate)}</dd></div>
          <div><dt>平均有利 / 不利波动</dt><dd>{percent(signal.averageMfe, true)} / {percent(signal.averageMae, true)}</dd></div>
        </dl>
      ) : (
        <p className="market-intel-evaluation-withheld">
          {signal.evaluationStatus === 'INVALIDATED'
            ? '该统计已失效，暂不展示收益比例'
            : !historyReliable
              ? '数据质量未通过，暂不展示收益比例'
            : '样本不足，暂不展示收益比例'}
        </p>
      )}
    </article>
  );
}

export function CapitalHistoricalEvaluationCard({
  evaluation,
  currentSignalTypes = []
}: {
  evaluation: CapitalBehaviorEvaluation | null;
  currentSignalTypes?: string[];
}) {
  const currentSignals = new Set(currentSignalTypes);
  const visibleSignals = evaluation?.signals.filter((signal) => (
    currentSignals.size === 0 || currentSignals.has(signal.signalType)
  )) ?? [];
  const historyReliable = evaluation
    ? (evaluation.historyQualityStatus
        ? evaluation.historyQualityStatus === 'RELIABLE'
        : evaluation.status === 'AVAILABLE')
    : false;
  return (
    <section className="market-intel-evaluation" aria-labelledby="capital-evaluation-heading">
      <header>
        <div>
          <p className="market-intel-kicker">Event study · 严格前缀回放</p>
          <h3 id="capital-evaluation-heading">历史表现校验</h3>
        </div>
        {evaluation && <span className="market-intel-version">{evaluation.evaluationVersion}</span>}
      </header>
      {!evaluation ? (
        <div className="market-intel-evaluation-empty">
          <strong>当前快照尚无历史评价</strong>
          <p>刷新资金数据后生成；行情与规则解读仍可正常使用。</p>
        </div>
      ) : (
        <>
          <div className={`market-intel-evaluation-quality ${historyReliable ? 'reliable' : 'unreliable'}`}>
            <strong>{historyReliable ? '历史质量通过' : '历史质量未通过'}</strong>
            <span>价格覆盖 {percent(evaluation.priceCoverageRate)} · 成交额覆盖 {percent(evaluation.amountCoverageRate)}</span>
          </div>
          <div className="market-intel-evaluation-scope">
            <div><span>日线窗口</span><strong>{evaluation.dailySampleCount} 个交易日</strong></div>
            <div><span>成熟事件标签</span><strong>{evaluation.evaluableEventCount} 个</strong></div>
            <div><span>标签覆盖</span><strong>{percent(evaluation.coverageRate)}</strong></div>
          </div>
          <div className="market-intel-evaluation-list">
            {visibleSignals.length > 0
              ? visibleSignals.map((signal) => (
                <SignalEvaluationRow
                  key={`${signal.signalType}-${signal.horizonDays}`}
                  signal={signal}
                  historyReliable={historyReliable}
                />
              ))
              : <p className="market-intel-evaluation-withheld">当前信号还没有已成熟的历史事件样本。</p>}
          </div>
          {evaluation.dataGaps.length > 0 && (
            <details className="market-intel-evaluation-gaps">
              <summary>数据边界与缺口（{evaluation.dataGaps.length}）</summary>
              <ul>{evaluation.dataGaps.map((gap) => <li key={gap}>{gap}</li>)}</ul>
            </details>
          )}
          <p className="market-intel-evaluation-boundary">历史统计仅描述样本，不代表未来表现。</p>
        </>
      )}
    </section>
  );
}
