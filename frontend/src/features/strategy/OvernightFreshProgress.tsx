import type { OvernightJointState } from './overnightJointTypes';
import type { OvernightMode } from './overnightTypes';

const protocol = 'overnight-fresh-direction-v1';
const labels = { FRESH: '最新样本训练', CALIBRATED: '滚动概率校准', INDEX: '增加指数走势' };
const percent = (value?: number) => value == null ? '—' : `${(value * 100).toFixed(1)}%`;

export function OvernightFreshProgress({ state, mode }: { state: OvernightJointState; mode: OvernightMode }) {
  const models = state.models.filter(model => model.profile.mode === mode && model.freshResearch?.protocol === protocol);
  if (!models.length) {
    return state.freshForward?.protocol === protocol
      ? <section className="overnight-context-comparison" aria-label="新训练方案对照">
        <p>新训练方案尚未就绪。后台将准备最新样本与指数走势对照，完成后在这里显示学习截止日和真实验证进度。</p>
      </section> : null;
  }
  return <section className="overnight-context-comparison" aria-label="新训练方案对照">
    <p>新训练方案自动留档，主判断保持原方案。下方仅显示实际发生的次日结果。</p>
    {models.map(model => {
      const research = model.freshResearch!;
      const audit = research.models.FRESH?.audit;
      return <div key={model.id}>
        <p><strong>{model.profile.cutoff}</strong> · {audit
          ? `新方案学习至 ${audit.trainingThrough.slice(0, 10)}，旧方案至 ${audit.legacyTrainingThrough.slice(0, 10)}`
          : '等待足够的训练历史'}</p>
        {Object.entries(labels).map(([group, label]) => {
          const trained = !!research.models[group === 'CALIBRATED' ? 'FRESH' : group];
          const forward = state.freshForward?.protocol === protocol
            ? state.freshForward.groups.find(row => row.mode === mode && row.cutoff === model.profile.cutoff && row.group === group) : undefined;
          return <div className="overnight-direction-summary-row" key={group}>
            <b>{label}</b><span>{!trained ? '数据积累中' : forward?.dayCount ? `${forward.dayCount} 个完整交易日` : '等待真实次日结果'}</span>
            {forward?.metrics && <small>新方案 / 配对基准命中率 {percent(forward.metrics.accuracy)} / {percent(forward.metrics.comparisons.MATCHED_BASELINE?.accuracy)}
              · 已配对 {forward.pairedCount} / {forward.scheduledCount} 条 · 尚未证明有效</small>}
            {!forward?.metrics && !!forward?.scheduledCount && <small>已配对 {forward.pairedCount} / {forward.scheduledCount} 条，尚无完整日期可统计；缺失记录仍保留。</small>}
          </div>;
        })}
      </div>;
    })}
    <p>三项改动分别检验。指数使用此前已完成的日线；没有有效历史的股票日不伪造输入，次日统计保留缺口。</p>
  </section>;
}
