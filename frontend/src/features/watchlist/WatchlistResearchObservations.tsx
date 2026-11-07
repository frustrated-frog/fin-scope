import { useState, type ReactNode } from 'react';
import { formatResearchNumber as number, type ResearchFinding, type ResearchSnapshot, type StockStrength, type StrengthReturnPoint } from './researchScanTypes';

type PlottableStock = StockStrength & { beta: number; fitQuality: number; excessReturnPp: number };
type StockLink = (code: string, name?: string) => ReactNode;

function isPlottable(stock: StockStrength): stock is PlottableStock {
  return Number.isFinite(stock.beta) && Number.isFinite(stock.fitQuality) && Number.isFinite(stock.excessReturnPp);
}

function isStrengthFinding(finding: ResearchFinding) {
  return finding.kind === 'OUTPERFORMING' || finding.kind === 'UNDERPERFORMING';
}

export function WatchlistResearchObservations({ snapshot, stockLink, stockName }: {
  snapshot: ResearchSnapshot;
  stockLink: StockLink;
  stockName: (code: string, fallback?: string) => string;
}) {
  const [selectedCode, setSelectedCode] = useState<string>();
  const stocks = snapshot.strengths.filter(isPlottable)
    .sort((left, right) => Math.abs(right.excessReturnPp) - Math.abs(left.excessReturnPp));
  const strengthFindings = new Map(snapshot.findings.filter(isStrengthFinding)
    .map((finding) => [finding.codes[0], finding]));
  const selected = stocks.find((stock) => stock.code === selectedCode) ?? stocks[0];
  const remaining = snapshot.findings.filter((finding) => !isStrengthFinding(finding)
    || !stocks.some((stock) => stock.code === finding.codes[0]));

  return <>
    {selected && <div className="research-observation-chart">
      <StrengthScatter stocks={stocks} selected={selected.code} onSelect={setSelectedCode} stockName={stockName} />
      <StrengthDetail stock={selected} finding={strengthFindings.get(selected.code)} stockLink={stockLink} />
    </div>}
    {remaining.length > 0 && <section aria-label={selected ? '其他观察' : '研究观察'}>
      {selected && <h4 className="research-observation-subtitle">其他观察</h4>}
      <ol className="research-finding-list">
        {remaining.slice(0, 12).map((finding) => <li key={finding.key}>
          <header><strong>{finding.title}</strong>{finding.newlyObserved && <span className="research-new">新增观察</span>}</header>
          <div className="research-finding-links">{finding.codes.map((code) => <span key={code}>{stockLink(code)}</span>)}</div>
          <ul>{finding.evidence.map((evidence, index) => <li key={index}>{evidence}</li>)}</ul>
        </li>)}
        {remaining.length > 12 && <li className="research-scan-empty">另有 {remaining.length - 12} 项观察，可在各分类查看。</li>}
      </ol>
    </section>}
    {!selected && !remaining.length && <p className="research-scan-empty">本次没有触发研究观察。可以在各分类查看已计算的强弱、关系与增长指标。</p>}
  </>;
}

function axis(values: number[], reference: number, minimumStep: number) {
  const min = Math.min(reference, ...values);
  const max = Math.max(reference, ...values);
  const rawStep = Math.max(minimumStep, (max - min) / 6);
  const magnitude = 10 ** Math.floor(Math.log10(rawStep));
  const normalized = rawStep / magnitude;
  const step = (normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 5 ? 5 : 10) * magnitude;
  // Allow room for markers around both extremes, including a reference line on the edge.
  const first = Math.floor(min / step - 0.14);
  const last = Math.ceil(max / step + 0.14);
  return {
    min: first * step,
    max: last * step,
    ticks: Array.from({ length: last - first + 1 }, (_, index) => (first + index) * step),
  };
}

function StrengthScatter({ stocks, selected, onSelect, stockName }: {
  stocks: PlottableStock[];
  selected: string;
  onSelect: (code: string) => void;
  stockName: (code: string, fallback?: string) => string;
}) {
  const left = 58, right = 694, top = 18, bottom = 238;
  const beta = axis(stocks.map((stock) => stock.beta), 1, 0.1);
  const deviation = axis(stocks.map((stock) => stock.excessReturnPp), 0, 2);
  const x = (value: number) => left + (value - beta.min) / (beta.max - beta.min) * (right - left);
  const y = (value: number) => bottom - (value - deviation.min) / (deviation.max - deviation.min) * (bottom - top);
  const points = stocks.map((stock) => ({
    stock,
    name: stockName(stock.code, stock.name),
    cx: x(stock.beta),
    cy: y(stock.excessReturnPp),
    radius: Math.sqrt(25 + 250 * Math.max(0, Math.min(1, stock.fitQuality))),
  }));
  const selection = points.find(({ stock }) => stock.code === selected);
  const labels = selection ? [selection] : [];
  const positivePoints = points.filter(({ stock }) => stock.excessReturnPp > 0);
  for (const point of [...points.slice(0, 6), ...positivePoints.slice(0, 2)]) {
    if (!labels.some((label) => Math.abs(label.cx - point.cx) < 80 && Math.abs(label.cy - point.cy) < 19)) {
      labels.push(point);
    }
  }

  return <figure className="research-strength-plot" aria-label="股票强弱分布">
    <select className="research-strength-select" aria-label="选择股票查看强弱详情" value={selected}
      onChange={(event) => onSelect(event.target.value)}>
      {points.map(({ stock, name }) => <option key={stock.code} value={stock.code}>{name} · {stock.code}</option>)}
    </select>
    <svg viewBox="0 0 720 280" aria-label="Beta 与基线偏离气泡图">
      <rect className="research-plot-positive-zone" x={left} y={top} width={right - left} height={y(0) - top} />
      <rect className="research-plot-negative-zone" x={left} y={y(0)} width={right - left} height={bottom - y(0)} />
      {deviation.ticks.map((value) => <g key={value}>
        <line className={value === 0 ? 'research-plot-reference' : 'research-plot-grid'} x1={left} x2={right} y1={y(value)} y2={y(value)} />
        <text x={left - 10} y={y(value)} dy="0.34em" textAnchor="end">{value > 0 ? '+' : ''}{value}</text>
      </g>)}
      {beta.ticks.map((value) => <text key={value} x={x(value)} y={bottom + 16} textAnchor="middle">{value.toFixed(1)}</text>)}
      <line className="research-plot-reference research-plot-beta" x1={x(1)} x2={x(1)} y1={top} y2={bottom} />
      <text x={x(1) + 5} y={top + 12}>β = 1</text>
      <text className="research-plot-zone-label is-positive" x={right - 9} y={top + 14} textAnchor="end">高弹性 · 跑赢</text>
      <text className="research-plot-zone-label is-negative" x={right - 9} y={bottom - 9} textAnchor="end">高弹性 · 跑输</text>
      <text className="research-plot-zone-label is-negative" x={left + 9} y={bottom - 9}>低弹性 · 跑输</text>
      <text x={(left + right) / 2} y="274" textAnchor="middle">Beta（相对市场弹性）→</text>
      <text transform="translate(15 128) rotate(-90)" textAnchor="middle">偏离基线（百分点）</text>
      {[...points].reverse().map(({ stock, name, cx, cy, radius }) => <g
        key={stock.code} className="research-strength-bubble" data-direction={direction(stock.excessReturnPp)}
        role="button" tabIndex={0} aria-pressed={stock.code === selected}
        aria-label={`选择 ${name} · Beta ${number(stock.beta)} · 偏离 ${number(stock.excessReturnPp, ' 个百分点', true)}`}
        onClick={() => onSelect(stock.code)}
        onKeyDown={(event) => {
          if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            onSelect(stock.code);
          }
        }}>
        <title>{name} · 拟合度 {Math.round(stock.fitQuality * 100)}%</title>
        <circle className="research-bubble-hit" cx={cx} cy={cy} r={Math.max(radius, 10)} />
        <circle className="research-bubble-disc" cx={cx} cy={cy} r={radius} />
        <circle className="research-bubble-center" cx={cx} cy={cy} r="2.2" />
      </g>)}
      {selection && <circle className="research-bubble-selection" cx={selection.cx} cy={selection.cy} r={selection.radius + 4} />}
      {labels.map(({ stock, name, cx, cy, radius }) => <text key={stock.code}
        className={stock.code === selected ? 'research-bubble-label is-selected' : 'research-bubble-label'}
        x={cx > right - 86 ? cx - radius - 6 : cx + radius + 6} y={cy} dy="0.34em"
        textAnchor={cx > right - 86 ? 'end' : 'start'}>{name}</text>)}
    </svg>
    <figcaption>气泡大小表示拟合度。选择气泡可查看个股详情。</figcaption>
  </figure>;
}

function direction(value: number) {
  return value > 0 ? 'positive' : value < 0 ? 'negative' : 'flat';
}

function StrengthDetail({ stock, finding, stockLink }: {
  stock: PlottableStock;
  finding?: ResearchFinding;
  stockLink: StockLink;
}) {
  const judgment = finding
    ? (finding.kind === 'OUTPERFORMING' ? '持续强于市场基线' : '持续弱于市场基线')
    : (stock.excessReturnPp > 0 ? '高于市场基线' : stock.excessReturnPp < 0 ? '低于市场基线' : '与市场基线持平');
  const metrics = [
    ['实际', number(stock.actualReturnPct, '%', true)],
    ['统计基线', number(stock.baselineReturnPct, '%', true)],
    ['高于基线', `${stock.positiveResidualDays}/${stock.evaluationSamples} 日`],
    ['Beta', number(stock.beta)],
    ['拟合度', `${Math.round(stock.fitQuality * 100)}%`],
    ['训练样本', `${stock.trainingSamples} 日`],
  ];
  return <section className="research-strength-detail" aria-label="所选股票强弱详情" data-direction={direction(stock.excessReturnPp)}>
    <header>
      <div className="research-strength-title">
        {stockLink(stock.code, stock.name)}<span>{judgment}</span>
        {finding?.newlyObserved && <span className="research-new">新增观察</span>}
      </div>
      {stock.evaluatedFrom && stock.evaluatedThrough && <p>{stock.evaluatedFrom} → {stock.evaluatedThrough}</p>}
    </header>
    <p className="research-strength-deviation"><strong>{number(stock.excessReturnPp, '', true)}</strong> 个百分点偏离</p>
    <dl className="research-strength-metrics">
      {metrics.map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}
    </dl>
    <StrengthReturnChart path={stock.returnPath} />
  </section>;
}

function StrengthReturnChart({ path }: { path?: StrengthReturnPoint[] }) {
  if (!path || path.length < 2) {
    return <p className="research-return-missing">此扫描未保存逐日曲线。重新扫描后可在新结果中查看。</p>;
  }

  const values = path.flatMap(({ actualReturnPct, baselineReturnPct }) => [actualReturnPct, baselineReturnPct]);
  const min = Math.min(0, ...values);
  const max = Math.max(0, ...values);
  const span = max - min || 1;
  const left = 8, right = 244, top = 14, bottom = 104;
  const x = (index: number) => left + index / (path.length - 1) * (right - left);
  const y = (value: number) => bottom - (value - min) / span * (bottom - top);
  const coordinates = (key: 'actualReturnPct' | 'baselineReturnPct') => path.map((point, index) => `${x(index)},${y(point[key])}`);
  const actual = coordinates('actualReturnPct');
  const baseline = coordinates('baselineReturnPct');
  const end = path[path.length - 1];
  const actualEndY = y(end.actualReturnPct);
  const baselineEndY = y(end.baselineReturnPct);
  const labelOffset = Math.abs(actualEndY - baselineEndY) < 12 ? (actualEndY <= baselineEndY ? -7 : 7) : 0;

  return <figure className="research-strength-returns">
    <svg viewBox="0 0 300 128" role="img" aria-label="实际累计收益与统计基线对比">
      <line className="research-plot-grid" x1={left} x2={right} y1={y(0)} y2={y(0)} />
      <polygon className="research-return-area" points={[...actual, ...[...baseline].reverse()].join(' ')} />
      <polyline className="research-return-baseline" points={baseline.join(' ')} />
      <polyline className="research-return-actual" points={actual.join(' ')} />
      <circle className="research-return-actual-dot" cx={right} cy={actualEndY} r="2.8" />
      <circle className="research-return-baseline-dot" cx={right} cy={baselineEndY} r="2.8" />
      <text className="research-return-actual-label" x={right + 6} y={actualEndY + labelOffset} dy="0.34em">{number(end.actualReturnPct, '%', true)}</text>
      <text x={right + 6} y={baselineEndY - labelOffset} dy="0.34em">{number(end.baselineReturnPct, '%', true)}</text>
      {[0, Math.floor(path.length / 2), path.length - 1].filter((index, position, indices) => indices.indexOf(index) === position)
        .map((index) => <text key={index} x={x(index)} y="124" textAnchor={index === 0 ? 'start' : index === path.length - 1 ? 'end' : 'middle'}>
          {path[index].date.slice(5)}
        </text>)}
    </svg>
    <figcaption><span>实际累计收益</span><span>统计基线</span></figcaption>
  </figure>;
}
