import { fundamentalValue, fundamentalChange } from './fundamentalResearchTypes';
import type { FundamentalWatchComparison } from './fundamentalResearchTypes';

export function FundamentalComparisonView({ comparison, archived = false }: { comparison?: FundamentalWatchComparison; archived?: boolean }) {
  if (!comparison) {
    return null;
  }
  if (comparison.state !== 'UPDATED') {
    return <p className="fr-comparison-waiting">{comparison.message}</p>;
  }
  return <section className="fr-comparison" aria-label="后续财报对照">
    <div className="fr-comparison-heading"><strong>{archived ? '本次复核依据' : comparison.reviewed ? '此份披露已复核' : '新财报待复核'}</strong><span>{comparison.baseline?.periodEnd} → {comparison.current?.periodEnd}</span></div>
    <dl>{comparison.changes.map(metric => <div key={metric.conceptCode}>
      <dt>{metric.label}</dt><dd>
        <div><span>原基线</span><strong>{fundamentalValue(metric.baseline, metric.unit)}</strong></div>
        <div><span>新披露</span><strong>{fundamentalValue(metric.current, metric.unit)}</strong></div>
        <div><span>变化</span><strong>{fundamentalChange(metric.change, metric.unit)}</strong></div>
      </dd>
    </div>)}</dl>
    <small>同期间、合并口径 · {comparison.current?.sourceCode} · {comparison.current?.currency} · 可比 {comparison.changes.length} 项</small>
  </section>;
}
