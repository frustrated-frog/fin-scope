import { useState } from 'react';
import { lifecycleLabel, type PaperVersion } from './PaperLab';
import { classicExperimentType } from './ClassicStrategies';
import './QuantAnalytics.css';

export function ResearchMap({versions, onExperiment, onAccount}: {
  versions: PaperVersion[];
  onExperiment: (id: number) => void;
  onAccount: (id: number) => void;
}) {
  const [selected, select] = useState<number>();
  const current = versions.find(v => v.id === selected) ?? versions[0];
  if (!current) {
    return null;
  }
  const { spec } = current;
  const recipe = spec.rule.kind === 'python' && spec.rule.recipe;
  return <section className="qa-surface qa-map" aria-label="研究路径">
    <header><h4>研究路径</h4><select aria-label="选择研究版本" value={current.id} onChange={event => select(Number(event.target.value))}>
      {versions.map(v => <option key={v.id} value={v.id}>#{v.id} · {v.spec.rule.name}</option>)}
    </select></header>
    <ol>
      <li><small>股票池</small><strong>{spec.source}</strong><span>冻结名单 · {spec.codes.length} 只</span></li>
      <li><small>策略版本 #{current.id}</small><strong>{spec.rule.name}</strong><span>{recipe ? classicExperimentType(recipe) : '五分钟决策'}</span></li>
      <li><small>历史实验</small><button onClick={() => onExperiment(current.id)}>查看回测分析 ↗</button><span>收益、回撤与成交</span></li>
      <li><small>模拟账户</small>{current.started ? <button onClick={() => onAccount(current.id)}>查看模拟组合 ↗</button> : <strong>尚未启动模拟</strong>}
        <span>{current.started ? lifecycleLabel(current) : '可从回测详情启动'}</span></li>
    </ol>
  </section>;
}
