import type { OvernightJointState } from './overnightJointTypes';
import type { OvernightMode } from './overnightTypes';
import { OvernightAuditPanel } from './OvernightAuditPanel';

const percent = (value?: number | null) => value == null ? '—' : `${(value * 100).toFixed(1)}%`;

export function OvernightPerformance({ state, mode, revision }: { state?: OvernightJointState; mode: OvernightMode; revision: number }) {
  const groups = state?.closeDirectionForward?.groups.filter(group => group.mode === mode) ?? [];
  return <div className="overnight-performance">
    <header className="overnight-view-heading"><span>前瞻验证 / 涨跌方向</span><h4>预测是否经得起次日检验</h4><p>只统计当时冻结、次日到期的真实对照。不同决策时点分别看，不合并成一个漂亮的准确率。</p></header>
    <section aria-label="次日涨跌表现">
      <div className="overnight-auto-section-title"><h5>次日涨跌</h5><span>次日收盘相对当日收盘 · 未扣费</span></div>
      {!groups.length && <div className="overnight-quiet-state"><h4>等待第一批真实结果</h4><p>自动研究完成后，次日行情将回填。样本未到期时，准确率保持空缺。</p></div>}
      <div className="overnight-performance-grid">{groups.map(group => <article className="overnight-score" key={group.key}>
        <header><b>{group.cutoff} → 次日收盘</b><span>{group.eligible ? '通过前瞻对照' : group.status === 'CHECKPOINT_FAILED' ? '未通过对照' : group.status === 'MONITORING_DEGRADED' ? '近期退化' : '积累验证中'}</span></header>
        <div className="overnight-score-comparison"><div><small>方向命中率</small><strong>{percent(group.metrics?.accuracy)}</strong></div><span>对照</span><div><small>历史比例基准</small><strong>{percent(group.metrics?.comparisons.HISTORICAL_PRIOR?.accuracy)}</strong></div></div>
        <dl><div><dt>有效交易日</dt><dd>{group.dayCount} / {group.requiredDays}</dd></div><div><dt>可核验覆盖</dt><dd>{percent(group.coverage)}</dd></div><div><dt>涨 / 不涨平均识别率</dt><dd>{percent(group.metrics?.balancedAccuracy)}</dd></div></dl>
        <progress value={group.dayCount} max={Math.max(group.requiredDays, group.dayCount, 1)} aria-label={`${group.cutoff}有效交易日进度`} />
        <small>历史比例基准使用预测时的上涨比例；同一天多只股票仍计一个交易日。</small>
      </article>)}</div>
    </section>
    <section className="overnight-profit-performance" aria-label="扣费盈利表现"><div className="overnight-auto-section-title"><h5>扣费盈利</h5><span>分退出时点 · 使用冻结的成本假设</span></div>
      <OvernightAuditPanel mode={mode} revision={revision} view="validation" />
    </section>
  </div>;
}
