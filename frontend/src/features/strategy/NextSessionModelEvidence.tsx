import type { DirectionEvaluation } from './quantTypes';

const percent = (value?: number) => value == null ? '—' : `${(value * 100).toFixed(1)}%`;

export function NextSessionModelEvidence({ audit, compact = false }: { audit?: DirectionEvaluation; compact?: boolean }) {
  const enhancement = audit?.trainingSelection?.enhancement;
  if (!enhancement || !audit) {
    return null;
  }
  const original = audit.comparisons.LOCAL_V3;
  return <div className="next-session-model-evidence" aria-label="近期模型与原版对照">
    <div className="next-session-model-heading"><strong>近期价量已纳入预测</strong><span>两路判断 · 各占 50%</span></div>
    <p>近期分支已学习至 {enhancement.recentTrainingThrough} 的结果，使用 {enhancement.recentTrainingCount} 个成熟样本。</p>
    {!compact && <dl className="next-session-model-components">
      <div><dt>原版分支 · 上涨概率</dt><dd>{percent(enhancement.incumbentProbability)}</dd></div>
      <div><dt>近期分支 · 上涨概率</dt><dd>{percent(enhancement.recentProbability)}</dd></div>
    </dl>}
    {original && <div className="next-session-comparison">
      <table><caption>同日期滚动对照 · {audit.dayCount} 个交易日</caption>
        <thead><tr><th scope="col">指标</th><th scope="col">原版</th><th scope="col">当前组合</th></tr></thead>
        <tbody><tr><th scope="row">方向准确率</th><td>{percent(original.accuracy)}</td><td>{percent(audit.accuracy)}</td></tr>
          <tr><th scope="row">概率误差 <small>越低越好</small></th><td>{original.brierScore.toFixed(4)}</td><td>{audit.brierScore.toFixed(4)}</td></tr></tbody>
      </table>
    </div>}
    <p className="next-session-model-verdict">{audit.eligible ? '本次滚动比较通过，后续继续按真实结果验证。' : '优势尚未通过完整验证，当前仅供观察。'}</p>
    {!compact && <p>涨跌幅与区间继续使用原版收益模型。两路判断不同不代表其中一路更准确，以后续冻结记录验证。</p>}
  </div>;
}
