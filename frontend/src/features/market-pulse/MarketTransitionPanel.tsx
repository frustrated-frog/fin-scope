import { useEffect, useMemo, useState } from 'react';

import type {
  MarketStateTrajectoryPoint,
  MarketTransitionDecision,
  StockDiscoveryMarketContext
} from './marketPulseTypes';
import './MarketTransitionPanel.css';

type Props = {
  decision: MarketTransitionDecision;
  onOpenStockDiscovery?: (context: StockDiscoveryMarketContext) => void;
};

const stateLabels: Record<MarketStateTrajectoryPoint['state'], string> = {
  REPAIR: '修复试探',
  EXPANSION: '扩散进攻',
  ROTATION: '震荡轮动',
  RISK_RELEASE: '风险释放'
};

const postureLabels: Record<StockDiscoveryMarketContext['riskPosture'], string> = {
  OFFENSIVE: '进攻观察', BALANCED: '均衡试错', DEFENSIVE: '防守优先'
};

const chaseLabels: Record<StockDiscoveryMarketContext['chasePolicy'], string> = {
  CONFIRMATION_ALLOWED: '确认后参与', PULLBACK_ONLY: '只等回撤确认', NO_CHASING: '不追高'
};

function pointPosition(point: MarketStateTrajectoryPoint) {
  return {
    x: 72 + point.participation / 100 * 644,
    y: 354 - point.riskAppetite / 100 * 306
  };
}

function trajectoryPath(points: MarketStateTrajectoryPoint[]) {
  return points.map((point, index) => {
    const position = pointPosition(point);
    return `${index ? 'L' : 'M'} ${position.x.toFixed(1)} ${position.y.toFixed(1)}`;
  }).join(' ');
}

function TransitionTrajectory({ points }: { points: MarketStateTrajectoryPoint[] }) {
  const [selectedIndex, setSelectedIndex] = useState(Math.max(0, points.length - 1));
  useEffect(() => {
    setSelectedIndex(Math.max(0, points.length - 1));
  }, [points.length]);
  const selected = points[Math.min(selectedIndex, Math.max(0, points.length - 1))];
  const path = useMemo(() => trajectoryPath(points), [points]);
  return (
    <figure className="market-transition-trajectory">
      <figcaption>
        <div><span>10D STATE PATH</span><h3>十日状态迁移</h3></div>
        <p>横轴观察参与度，纵轴观察风险偏好；方向比单日位置更重要。</p>
      </figcaption>
      {points.length ? <>
        <div className="market-transition-chart">
          <svg viewBox="0 0 760 400" role="img" aria-label="十日市场状态迁移轨迹">
            <defs>
              <linearGradient id="marketTransitionPath" x1="0" x2="1" y1="1" y2="0">
                <stop offset="0" stopColor="var(--mp-green)" />
                <stop offset=".55" stopColor="var(--mp-blue)" />
                <stop offset="1" stopColor="var(--mp-red)" />
              </linearGradient>
              <marker id="marketTransitionArrow" markerHeight="7" markerWidth="7" orient="auto" refX="5" refY="3.5">
                <path d="M0,0 L0,7 L6,3.5 z" className="market-transition-arrow" />
              </marker>
            </defs>
            <rect x="72" y="48" width="322" height="153" className="quadrant repair" />
            <rect x="394" y="48" width="322" height="153" className="quadrant expansion" />
            <rect x="72" y="201" width="322" height="153" className="quadrant release" />
            <rect x="394" y="201" width="322" height="153" className="quadrant rotation" />
            <line x1="394" x2="394" y1="48" y2="354" className="axis" />
            <line x1="72" x2="716" y1="201" y2="201" className="axis" />
            <text x="90" y="76" className="quadrant-label repair">修复试探</text>
            <text x="698" y="76" textAnchor="end" className="quadrant-label expansion">扩散进攻</text>
            <text x="90" y="334" className="quadrant-label release">风险释放</text>
            <text x="698" y="334" textAnchor="end" className="quadrant-label rotation">震荡轮动</text>
            <text x="394" y="390" textAnchor="middle" className="axis-label">市场参与度 →</text>
            <text x="22" y="201" textAnchor="middle" className="axis-label axis-y">风险偏好 →</text>
            <path d={path} className="trajectory-path" markerEnd="url(#marketTransitionArrow)" />
            {points.map((point, index) => {
              const position = pointPosition(point);
              const isCurrent = index === points.length - 1;
              const isSelected = index === selectedIndex;
              return <g key={`${point.businessDate}-${index}`} className={isCurrent ? 'is-current' : ''}>
                {isCurrent && <circle cx={position.x} cy={position.y} r="15" className="trajectory-pulse" />}
                <circle cx={position.x} cy={position.y} r={isSelected ? 7 : 5} className={isSelected ? 'trajectory-point selected' : 'trajectory-point'} />
                {(isCurrent || points.length <= 5) && <text x={position.x} y={position.y - 13} textAnchor="middle" className="trajectory-date">{point.businessDate.slice(5)}</text>}
              </g>;
            })}
          </svg>
        </div>
        <input type="range" min="0" max={points.length - 1} value={Math.min(selectedIndex, points.length - 1)}
          aria-label="选择状态迁移日期" onChange={event => setSelectedIndex(Number(event.target.value))} />
        {selected && <div className="market-transition-selected">
          <time>{selected.businessDate}</time><strong>{stateLabels[selected.state]}</strong>
          <span><small>参与度</small><b>{selected.participation}</b></span>
          <span><small>风险偏好</small><b>{selected.riskAppetite}</b></span>
        </div>}
      </> : <div className="market-transition-chart-empty"><strong>迁移轨迹正在积累</strong><p>至少获得两个完整交易日后再绘制方向。</p></div>}
    </figure>
  );
}

export function MarketTransitionPanel({ decision, onOpenStockDiscovery }: Props) {
  const context = decision.discoveryContext;
  const preferred = context.preferredSectors.join(' · ') || '等待行业轮动确认';
  const avoided = context.avoidSectors.join(' · ') || '当前没有强制回避方向';
  return (
    <section className="market-transition-cockpit" data-transition={decision.transition.code}>
      <header className="market-transition-thesis">
        <div className="market-transition-title">
          <span>MARKET STRUCTURE / DECISION LAYER</span>
          <h3>市场转折雷达</h3>
          <h2>{decision.transition.label}</h2>
          <p>{decision.transition.summary}</p>
        </div>
        <div className="market-transition-strength" aria-label={`转折强度 ${decision.transition.strength} 分`}>
          <div><span>转折强度</span><strong>{decision.transition.strength}</strong><small>/ 100</small></div>
          <i><b style={{ width: `${decision.transition.strength}%` }} /></i>
          <em>{decision.transition.code.replace(/_/g, ' ')}</em>
        </div>
        <ul className="market-transition-drivers">
          {decision.transition.drivers.map(item => <li key={item}>{item}</li>)}
        </ul>
      </header>

      <div className="market-transition-gauges" aria-label="市场结构仪表">
        {decision.gauges.map(item => <article key={item.code} data-code={item.code} data-available={item.available || undefined}>
          <header><span>{item.label}</span><b>{item.available ? item.score : '—'}</b></header>
          <i aria-hidden="true"><b style={{ width: `${item.score}%` }} /></i>
          <strong>{item.status}</strong>
          <p>{item.detail}</p>
        </article>)}
      </div>

      <div className="market-transition-main">
        <TransitionTrajectory points={decision.trajectory} />
        <section className="market-transition-scenarios">
          <header><span>NEXT SESSION PLAYBOOK</span><h3>下一交易日情景</h3><p>不是预测唯一结果，而是提前写清盘面走向与对应动作。</p></header>
          <div>{decision.scenarios.map((scenario, index) => <article key={scenario.code} data-testid="market-scenario" data-emphasis={scenario.emphasis}>
            <div className="scenario-rank"><span>0{index + 1}</span><i /></div>
            <div className="scenario-body">
              <header><div>{scenario.emphasis === 'PRIMARY' && <em>当前主路径</em>}<h4>{scenario.title}</h4></div><strong>{scenario.matchScore}<small>/100</small></strong></header>
              <ul>{scenario.triggers.map(item => <li key={item}>{item}</li>)}</ul>
              <p><span>研究姿态</span>{scenario.posture}</p>
            </div>
          </article>)}</div>
        </section>
      </div>

      <section className="market-transition-handoff" aria-label="市场环境与股票发现联动">
        <div className="market-transition-handoff-copy">
          <span>CONTEXT HAND-OFF</span><h3>把市场环境带入股票发现</h3>
          <p>这里只传递风险姿态、行业方向和追高约束；个股候选仍由股票发现独立计算。</p>
        </div>
        <dl>
          <div><dt>风险姿态</dt><dd>{postureLabels[context.riskPosture]}</dd></div>
          <div><dt>优先研究</dt><dd>{preferred}</dd></div>
          <div><dt>谨慎方向</dt><dd>{avoided}</dd></div>
          <div><dt>参与纪律</dt><dd>{chaseLabels[context.chasePolicy]}</dd></div>
        </dl>
        <button type="button" onClick={() => onOpenStockDiscovery?.(context)}>带着当前环境进入股票发现</button>
      </section>
    </section>
  );
}
