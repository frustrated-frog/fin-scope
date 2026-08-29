import { CSSProperties, useMemo, useState } from 'react';

import type { SectorRotation } from './marketPulseTypes';

const stageLabels: Record<string, string> = {
  EMERGING: '萌芽',
  ACCELERATING: '加速',
  PERSISTENT: '持续',
  OVERHEATED: '过热',
  FADING: '退潮',
  REVERSING: '反转试探',
  WEAK: '弱势',
  INSUFFICIENT_DATA: '数据不足'
};

type Props = {
  sectors: SectorRotation[];
  onOpenStockDiscovery?: () => void;
};

type MapMode = 'heatmap' | 'rotation';

function signedPercent(value?: number) {
  if (value == null || !Number.isFinite(value)) {
    return '—';
  }
  return `${value > 0 ? '+' : ''}${value.toFixed(2)}%`;
}

function ratioPercent(value?: number) {
  if (value == null || !Number.isFinite(value)) {
    return '—';
  }
  return `${Math.round(value * 100)}%`;
}

function money(value?: number) {
  if (value == null || !Number.isFinite(value)) {
    return '—';
  }
  return `${value >= 0 ? '+' : ''}${(value / 100_000_000).toFixed(1)} 亿`;
}

function heatTone(value?: number) {
  if (value == null) {
    return 'neutral';
  }
  if (value >= 4) {
    return 'hot';
  }
  if (value > 0) {
    return 'warm';
  }
  if (value <= -4) {
    return 'cold';
  }
  if (value < 0) {
    return 'cool';
  }
  return 'neutral';
}

function acceleration(item: SectorRotation) {
  if (item.return1d == null) {
    return 0;
  }
  return item.return1d - (item.return5d ?? 0) / 5;
}

function relativeStrength(item: SectorRotation) {
  return item.excessReturn5d ?? item.return5d ?? 0;
}

export function SectorOpportunityMap({ sectors, onOpenStockDiscovery }: Props) {
  const allSorted = useMemo(
    () => [...sectors].sort((left, right) => right.rotationScore - left.rotationScore),
    [sectors]
  );
  const sorted = useMemo(() => {
    if (allSorted.length <= 24) {
      return allSorted;
    }
    const focus = [...allSorted.slice(0, 18), ...allSorted.slice(-6)];
    return focus.filter((item, index) => focus.findIndex(candidate => candidate.sectorCode === item.sectorCode) === index);
  }, [allSorted]);
  const [mode, setMode] = useState<MapMode>('heatmap');
  const [selectedCode, setSelectedCode] = useState<string>();
  const selected = sorted.find(item => item.sectorCode === selectedCode) ?? sorted[0];
  const maximumStrength = Math.max(1, ...sorted.map(item => Math.abs(relativeStrength(item))));
  const maximumAcceleration = Math.max(1, ...sorted.map(item => Math.abs(acceleration(item))));

  return (
    <section className="market-pulse-opportunity-map">
      <header>
        <div><span>SECTOR FIELD · {sorted.length} / {allSorted.length}</span><h3>行业机会地图</h3></div>
        <div className="market-pulse-map-switch" role="group" aria-label="行业地图模式">
          <button type="button" aria-pressed={mode === 'heatmap'} onClick={() => setMode('heatmap')}>行业热力</button>
          <button type="button" aria-pressed={mode === 'rotation'} onClick={() => setMode('rotation')}>轮动地图</button>
        </div>
      </header>

      <div className="market-pulse-map-layout">
        <div className="market-pulse-map-stage">
          {mode === 'heatmap' ? (
            <div className="market-pulse-heatmap" aria-label="行业五日热力图">
              {sorted.map((item, index) => (
                <button
                  type="button"
                  key={item.sectorCode}
                  aria-label={`${item.sectorName}，5日 ${signedPercent(item.return5d)}，轮动分 ${item.rotationScore}`}
                  aria-pressed={selected?.sectorCode === item.sectorCode}
                  className={`market-pulse-heat-tile is-${heatTone(item.return5d)} size-${index < 3 ? 'hero' : index < 9 ? 'wide' : 'small'}`}
                  onClick={() => setSelectedCode(item.sectorCode)}
                >
                  <span>{stageLabels[item.stage ?? ''] ?? '观察'}</span>
                  <strong>{item.sectorName}</strong>
                  <b>{signedPercent(item.return5d)}</b>
                  <small>宽度 {ratioPercent(item.breadthRatio)}</small>
                </button>
              ))}
              {!sorted.length && <p>行业行情暂不可用。</p>}
            </div>
          ) : (
            <div className="market-pulse-rotation-map" aria-label="行业相对强度轮动图">
              <span className="quadrant leading">领先</span>
              <span className="quadrant improving">改善</span>
              <span className="quadrant weakening">减弱</span>
              <span className="quadrant lagging">落后</span>
              <span className="axis-label axis-x">相对强度 →</span>
              <span className="axis-label axis-y">短期加速度 →</span>
              {sorted.map(item => {
                const strength = relativeStrength(item);
                const speed = acceleration(item);
                const style = {
                  '--point-x': `${50 + strength / maximumStrength * 42}%`,
                  '--point-y': `${50 - speed / maximumAcceleration * 42}%`,
                  '--point-size': `${Math.max(28, Math.min(48, 24 + item.rotationScore / 4))}px`
                } as CSSProperties;
                return (
                  <button
                    type="button"
                    key={item.sectorCode}
                    style={style}
                    aria-label={`${item.sectorName}，相对强度 ${signedPercent(strength)}，短期加速度 ${signedPercent(speed)}`}
                    aria-pressed={selected?.sectorCode === item.sectorCode}
                    onClick={() => setSelectedCode(item.sectorCode)}
                  >
                    <span>{item.sectorName}</span>
                  </button>
                );
              })}
            </div>
          )}
        </div>

        <aside className="market-pulse-sector-detail" role="region" aria-label="行业详情">
          {selected ? <>
            <header>
              <span className={`market-pulse-stage stage-${selected.stage?.toLowerCase()}`}>{stageLabels[selected.stage ?? ''] ?? '观察'}</span>
              <h4>{selected.sectorName}</h4>
              <p>{selected.explanations?.[0] ?? '正在形成行业节奏描述。'}</p>
            </header>
            <dl>
              <div><dt>1 日</dt><dd>{signedPercent(selected.return1d)}</dd></div>
              <div><dt>5 日</dt><dd>{signedPercent(selected.return5d)}</dd></div>
              <div><dt>20 日</dt><dd>{signedPercent(selected.return20d)}</dd></div>
              <div><dt>行业宽度</dt><dd>{ratioPercent(selected.breadthRatio)}</dd></div>
              <div><dt>资金净流入</dt><dd>{money(selected.mainNetInflow)}</dd></div>
              <div><dt>持续天数</dt><dd>{selected.persistenceDays ?? 0} 天</dd></div>
              <div><dt>拥挤度</dt><dd>{selected.crowdingScore ?? 0}</dd></div>
              <div><dt>轮动分</dt><dd>{selected.rotationScore}</dd></div>
            </dl>
            {(selected.explanations?.length ?? 0) > 1 && <ul>{selected.explanations?.slice(1, 4).map(item => <li key={item}>{item}</li>)}</ul>}
            <footer>
              <p>这里只解释行业状态，个股候选、模型排序与研究入口由股票发现统一管理。</p>
              <button type="button" onClick={onOpenStockDiscovery}>到股票发现筛选该方向</button>
            </footer>
          </> : <p className="market-pulse-inline-empty">行业行情暂不可用。</p>}
        </aside>
      </div>
    </section>
  );
}
