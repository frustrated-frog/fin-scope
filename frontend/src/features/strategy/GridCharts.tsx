import { useId, useState } from 'react';
import { allocations, frequencies } from './ClassicStrategies';
import type { GridRow } from './ParameterGrid';

const colors = ['#77cbd6', '#e6ac76', '#b398da', '#84c8a0', '#dc8fa1', '#8eadd9'];
const percent = (n: number) => (n * 100).toFixed(1) + '%';

function scale(values: number[], length: number) {
  const lo = Math.min(...values, 0),
    hi = Math.max(...values, 0);
  const range = hi - lo || 0.01;
  const min = lo - range * 0.07,
    max = hi + range * 0.07;
  return { position: (n: number) => ((n - min) * length) / (max - min), ticks: [min, (min + max) / 2, max] };
}

export function GridCharts({ rows }: { rows: GridRow[] }) {
  const [mode, setMode] = useState('风险与收益');
  const [selected, setSelected] = useState<number>();
  const id = useId();
  const series = rows.filter((r) => r.state === 'DONE' && r.net != null && r.analytics);
  if (!series.length) {
    return null;
  }
  const active = series.find((r) => r.id === selected) ?? series[0];
  const line = mode === '净值对比',
    cost = mode === '换手与费用';
  const dates = [...new Set(series.flatMap((r) => r.analytics!.closes.map((c) => c.day)))].sort();
  const dateIndex = new Map(dates.map((date, index) => [date, index]));
  const points = series.flatMap((r, i) => {
    const x = cost ? r.analytics!.turnover : r.analytics!.drawdown;
    const y = cost ? r.analytics!.feeRate : r.net;
    return x != null && y != null ? [{ x, y, id: r.id, i }] : [];
  });
  const sx = scale(line ? [0, Math.max(dates.length - 1, 1)] : points.map((v) => v.x), 530);
  const sy = scale(
    line ? series.flatMap((r) => r.analytics!.closes.map((p) => p.net)) : points.map((v) => v.y),
    148,
  );
  const x = (v: number) => 65 + sx.position(v),
    y = (v: number) => 166 - sy.position(v);
  const eligible = line ? dates.length > 0 : points.length > 0;
  return (
    <section className="pg-charts">
      <header>
        <h4>配置对比</h4>
        <div className="att-switch">
          {['风险与收益', '换手与费用', '净值对比'].map((m) => (
            <button key={m} aria-pressed={m === mode} onClick={() => setMode(m)}>
              {m}
            </button>
          ))}
        </div>
      </header>
      <div className="pg-chart-layout">
        <div>
          <p>{line ? '每日收盘净值 · 初始值 1' : cost ? '纵轴 · 费用占初始本金' : '纵轴 · 扣费收益率'}</p>
          {eligible ? (
            <svg viewBox="0 0 625 200" role="img" aria-labelledby={id}>
              <title id={id}>
                {mode} · {series.length} 组已完成的回测
              </title>
              {sy.ticks.map((t) => (
                <g key={t}>
                  <path d={`M65 ${y(t)}h530`} />
                  <text x="58" y={y(t) + 4} textAnchor="end">
                    {line ? (1 + t).toFixed(3) : percent(t)}
                  </text>
                </g>
              ))}
              {line
                ? [dates[0], ...(dates.length > 1 ? [dates[dates.length - 1]] : [])].map((d) => (
                    <text key={d} x={x(dateIndex.get(d)!)} y="190" textAnchor="middle">
                      {d.slice(5)}
                    </text>
                  ))
                : sx.ticks.map((t) => (
                    <text key={t} x={x(t)} y="190" textAnchor="middle">
                      {percent(t)}
                    </text>
                  ))}
              {line
                ? series.map((r, i) => {
                    let previous = -2;
                    const path = r
                      .analytics!.closes.map((c) => {
                        const index = dateIndex.get(c.day)!;
                        const command = index === previous + 1 ? 'L' : 'M';
                        previous = index;
                        return command + x(index) + ' ' + y(c.net);
                      })
                      .join(' ');
                    return (
                      <path
                        key={r.id}
                        d={path}
                        style={{
                          stroke: colors[i % colors.length],
                          strokeWidth: r.id === active.id ? 2.8 : 1.4,
                          opacity: r.id === active.id ? 1 : 0.66,
                        }}
                      />
                    );
                  })
                : points.map((p) => (
                    <circle
                      key={p.id}
                      cx={x(p.x)}
                      cy={y(p.y)}
                      r={p.id === active.id ? 6.5 : 4.5}
                      fill={colors[p.i % colors.length]}
                      stroke={p.id === active.id ? '#e2eff1' : 'none'}
                      onClick={() => setSelected(p.id)}
                    >
                      <title>
                        #{series[p.i].version} · {percent(p.x)} / {percent(p.y)}
                      </title>
                    </circle>
                  ))}
            </svg>
          ) : (
            <p>这一批尚无可绘制的观测。</p>
          )}
          <small>{line ? '交易日期' : cost ? '横轴 · 区间双边换手率' : '横轴 · 最大收盘回撤'}</small>
        </div>
        <aside>
          <label>
            查看配置
            <select value={active.id} onChange={(e) => setSelected(Number(e.target.value))}>
              {series.map((r) => (
                <option key={r.id} value={r.id}>
                  #{r.version} · {frequencies[r.rebalance]} · {r.holdings} 只
                </option>
              ))}
            </select>
          </label>
          <strong>{percent(active.net!)}</strong>
          <span>扣费收益 · {allocations[active.allocation]}</span>
          <p>
            最大收盘回撤 {active.analytics!.drawdown != null ? percent(active.analytics!.drawdown) : '—'}
            <br />
            成交 {active.fills ?? '—'} 笔 · 费用 ¥ {active.fees?.toFixed(2) ?? '—'}
          </p>
        </aside>
      </div>
    </section>
  );
}
