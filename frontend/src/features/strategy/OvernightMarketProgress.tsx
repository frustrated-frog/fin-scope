import type { OvernightJointState } from './overnightJointTypes';
import type { OvernightMode } from './overnightTypes';

const labels = { MARKET: '市场整体强弱', SENTIMENT: '短线强弱与回落', INDUSTRY: '行业同行表现', RELATIVE: '相对指数的强弱与持续性' };
const percent = (value?: number) => value == null ? '—' : `${(value * 100).toFixed(1)}%`;

export function OvernightMarketProgress({ state, mode }: { state: OvernightJointState; mode: OvernightMode }) {
  const cutoffs = mode === 'TAIL_ENTRY' ? ['14:30', '14:45'] : ['15:00'];
  return <section aria-label="新增信息验证进度" className="overnight-context-comparison">
    <p>新增信息验证：先积累有效历史，再自动训练并记录次日判断。以下结果不会直接替换主预测。</p>
    {cutoffs.map(cutoff => {
      const training = state.marketAblation?.find(item => item.cutoff === cutoff);
      const latest = state.contextSnapshots?.find(item => item.cutoff === cutoff);
      const capture = latest?.relativeStrength?.protocol === 'overnight-relative-strength-v1' ? latest.relativeStrength : undefined;
      return <div key={cutoff}>
        {Object.entries(labels).map(([group, label]) => {
          const learned = training?.groups.find(item => item.group === group);
          const forward = state.marketForward?.groups.find(item => item.cutoff === cutoff && item.mode === mode && item.group === group);
          return <div className="overnight-direction-summary-row" key={group}>
            <b>{cutoff} · {label}</b>
            <span>{learned?.status === 'RESEARCH_ONLY' ? '模型已训练，积累真实对照' : '积累训练数据'}</span>
            <small>有效历史 {learned?.dayCount ?? 0} / {learned?.requiredDays ?? (group === 'RELATIVE' ? 164 : 143)} 日 · 完整次日对照 {forward?.dayCount ?? 0} / {forward?.requiredDays ?? 60} 日
              {forward && <> · 已配对 {forward.pairedCount} / {forward.scheduledCount} 条</>}
            </small>
            {group === 'RELATIVE' && <small>{capture
              ? `最近采集 ${latest!.signalDate} ${latest!.observedAt.slice(11, 16)} · 指数 ${capture.capturedIndices} / ${capture.expectedIndices} 个 · 已对齐 ${capture.pairedSymbols} 只股票`
              : '等待判断前的指数分钟数据；缺失或时间未对齐时不生成输入。'}</small>}
            {forward?.metrics && <small>增强 / 同样本基准命中率 {percent(forward.metrics.accuracy)} / {percent(forward.metrics.comparisons.MATCHED_BASELINE?.accuracy)} · 仅供研究</small>}
          </div>;
        })}
      </div>;
    })}
  </section>;
}
