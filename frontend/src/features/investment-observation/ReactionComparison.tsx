import type { ReactionSample } from './reactionTypes';
import { eventLabels, signed, statusLabels } from './reactionTypes';
import { ReactionChart } from './ReactionChart';

const colors = ['var(--reaction-ink)', 'var(--reaction-teal)', '#936445', '#756994'];
export function ReactionComparison({ samples, onRemove }: { samples: ReactionSample[]; onRemove: (id: number) => void }) {
  const complete = samples.filter(sample => sample.calculation?.windows.find(window => window.sessions === 5)?.status === 'READY').length;
  return <section className="reaction-comparison" aria-label="同类案例对照">
    <header><div><span>同类案例对照</span><h4>{samples[0]?.eventType ? eventLabels[samples[0].eventType] : '选择事件样本'}</h4></div><p>{samples.length} 个样本 · 五日窗口完成 {complete} 个</p></header>
    <p className="reaction-note">最多对照 4 条同类型路径，按事件时点对齐。不同股票或同一事件的样本不视为独立统计证据；历史补录单独标记。</p>
    <ReactionChart relative series={samples.map((sample, index) => ({
      label: `${sample.instrumentName || sample.instrumentCode} · ${sample.publishedAt?.slice(0, 10)} · ${sample.title}${sample.historicalBackfill ? ' · 补录' : ''}`,
      points: sample.calculation?.points || [], metric: 'relativeReturnPp', color: colors[index]
    }))} />
    <div className="reaction-comparison-list">{samples.map((sample, index) => <article key={sample.id}>
      <div><strong style={{ color: colors[index] }}>{sample.instrumentName} · {sample.title}</strong><small>{sample.publishedAt?.slice(0, 10)}{sample.historicalBackfill && ' · 历史补录'}</small></div>
      <p>{[1, 3, 5].map(size => {
        const window = sample.calculation?.windows.find(value => value.sessions === size);
        return <span key={size}>前{size}日 <b>{window?.status === 'READY' ? signed(window.relativeReturnPp, ' pp') : window ? statusLabels[window.status] : '未计算'}</b></span>;
      })}</p>
      <button aria-label={`移除对照：${sample.title}`} onClick={() => onRemove(sample.id)}>移除</button>
    </article>)}</div>
    {samples.length < 2 && <p className="reaction-note">从反应矩阵再加入一个同类型样本，比较路径差异。</p>}
  </section>;
}
