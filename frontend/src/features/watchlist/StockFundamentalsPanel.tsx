import { useEffect, useRef, useState } from 'react';
import { api } from '../../shared/api/client';
import type { StockValuationView } from '../financials/financialTypes';
import { percent } from './StockMarketInsights';

type FinancialQuarter = {
  periodEnd: string;
  reportType?: string;
  currency: string;
  reportId?: number | null;
  sourceCode?: string;
  qualityStatus?: string;
  publishedAt?: string;
  revenue?: number | string | null;
  netProfitParent?: number | string | null;
  operatingCashFlow?: number | string | null;
  grossMarginPct?: number | string | null;
  dataGaps: string[];
};

type Fundamentals = {
  instrument: { id: number; code: string; name?: string; market?: string; type: string };
  quarters: FinancialQuarter[];
  valuation?: StockValuationView | null;
  peers?: { code: string; name?: string; observedDate: string; sourceCode: string; qualityStatus?: string; peTtm?: number | string | null; pbMrq?: number | string | null }[];
  peerComparisonNote?: string;
  warnings: string[];
  refreshedAt: string;
};

type TrendPoint = { label: string; value?: number; detail: string };

export function StockFundamentalsPanel({ code }: { code: string }) {
  const [view, setView] = useState<Fundamentals>();
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const version = useRef(0);

  function load(force: boolean) {
    const request = ++version.current;
    setLoading(true);
    setError('');
    void api<Fundamentals>(`/api/watchlist/stocks/${encodeURIComponent(code)}/fundamentals${force ? '?refresh=true' : ''}`)
      .then((result) => {
        if (version.current === request) {
          setView(result);
          setLoading(false);
        }
      }).catch((reason) => {
        if (version.current === request) {
          setError(reason instanceof Error ? reason.message : '财务与估值加载失败');
          setLoading(false);
        }
      });
  }

  useEffect(() => {
    setView(undefined);
    load(false);
    return () => {
      version.current += 1;
    };
  }, [code]);

  const quarters = view?.quarters ?? [];
  const currencies = [...new Set(quarters.filter((quarter) => quarter.reportId != null).map((quarter) => quarter.currency))];
  const currency = currencies.length === 1 ? currencies[0] : undefined;
  const financialSeries = [
    { field: 'revenue', title: '营业收入', unit: '亿' },
    { field: 'netProfitParent', title: '归母净利润', unit: '亿' },
    { field: 'operatingCashFlow', title: '经营现金流净额', unit: '亿' },
    { field: 'grossMarginPct', title: '毛利率', unit: '%' }
  ] as const;

  return <div className="stock-insights stock-fundamentals">
    <section className="stock-insight-section" aria-labelledby="stock-financial-title">
      <header><div><h3 id="stock-financial-title">单季度财务趋势</h3><p className="stock-insight-note">最近 8 个已结束季度 · 累计报表按相邻期差额派生单季度</p></div>
        <button type="button" className="ghost-button" disabled={loading} onClick={() => load(true)}>{loading ? '自动整理中…' : '刷新财务与估值'}</button>
      </header>
      {loading && <p className="stock-insight-empty" role="status">正在自动读取并补齐财务与估值数据，已有档案会优先复用…</p>}
      {error && <p className="stock-insight-empty" role="alert">{error}<button type="button" className="ghost-button" disabled={loading} onClick={() => load(false)}>重试</button></p>}
      {view && <>
        {currencies.length > 1 ? <p className="stock-insight-empty">报告币种不同，暂不跨币种绘制金额趋势。</p> : <div className="stock-financial-chart-grid">
          {financialSeries.map((series) => <TrendChart key={series.field} title={series.title} unit={`${series.unit}${series.unit === '%' ? '' : ` ${currency ?? 'CNY'}`}`}
            points={quarters.map((quarter) => ({
              label: quarter.periodEnd,
              value: numeric(quarter[series.field]) == null ? undefined : numeric(quarter[series.field])! / (series.unit === '%' ? 1 : 100_000_000),
              detail: `${quarter.periodEnd} · ${quarter.sourceCode || '暂无来源'} · ${quarter.currency} · ${quarter.publishedAt ? `披露 ${quarter.publishedAt}` : '披露日期未提供'}`
            }))} />)}
        </div>}
        <details className="stock-financial-details"><summary>报告来源与数据缺口</summary>
          <ul>{quarters.map((quarter) => <li key={quarter.periodEnd}><strong>{quarter.periodEnd}</strong> · {quarter.sourceCode || '尚无报告'} · {quarter.currency} · 质量 {quarter.qualityStatus || '未提供'}
            {quarter.publishedAt && <span> · 披露 {quarter.publishedAt}</span>}
            {(quarter.dataGaps ?? []).length > 0 && <p>{quarter.dataGaps.join('；')}</p>}
          </li>)}</ul>
        </details>
      </>}
    </section>
    {view && <>
      <ValuationHistory view={view.valuation} />
      <section className="stock-insight-section" aria-labelledby="stock-peer-title">
        <header><h3 id="stock-peer-title">本地同行估值样本</h3><span>同市场 · 同行业 · 同观测日与来源</span></header>
        <p className="stock-insight-note">{view.peerComparisonNote || '当前没有满足同日、同源口径的本地同行样本。'}</p>
        {(view.peers ?? []).length > 0 && <div className="stock-peer-table"><table>
          <thead><tr><th>公司</th><th>PE TTM</th><th>PB MRQ</th><th>观测日</th></tr></thead>
          <tbody>
            <tr><th scope="row">{view.instrument.name || code} · 当前标的</th><td>{numeric(view.valuation?.latest?.peTtm)?.toFixed(2) ?? '—'}</td><td>{numeric(view.valuation?.latest?.pbMrq)?.toFixed(2) ?? '—'}</td><td>{view.valuation?.latest?.observedDate ?? '—'}</td></tr>
            {view.peers!.map((peer) => <tr key={peer.code} title={`${peer.sourceCode} · ${peer.qualityStatus || '质量未提供'}`}>
              <th scope="row">{peer.name || peer.code} <small>{peer.code}</small></th><td>{numeric(peer.peTtm)?.toFixed(2) ?? '—'}</td><td>{numeric(peer.pbMrq)?.toFixed(2) ?? '—'}</td><td>{peer.observedDate}</td>
            </tr>)}
          </tbody>
        </table></div>}
      </section>
    </>}
    {view && <footer className="stock-insight-provenance"><span>整理时间 {view.refreshedAt}</span>
      {(view.warnings ?? []).length > 0 && <ul>{view.warnings.map((warning, index) => <li key={`${index}-${warning}`}>{warning}</li>)}</ul>}
    </footer>}
  </div>;
}

function ValuationHistory({ view }: { view?: StockValuationView | null }) {
  const history = [...(view?.history ?? [])].sort((a, b) => a.observedDate.localeCompare(b.observedDate));
  const fields = [
    { field: 'peTtm', code: 'PE_TTM', title: '市盈率 PE · TTM' },
    { field: 'pbMrq', code: 'PB_MRQ', title: '市净率 PB · MRQ' }
  ] as const;
  return <section className="stock-insight-section" aria-labelledby="stock-valuation-title">
    <header><h3 id="stock-valuation-title">估值历史位置</h3><span>真实本地快照 · {history.length} 个观测日</span></header>
    {history.length === 0 ? <p className="stock-insight-empty">尚无可用估值快照，历史将在成功获取后逐日积累。</p> : <>
      <div className="stock-financial-chart-grid">{fields.map((field) => {
        const metric = view?.metrics.find((item) => item.metricCode === field.code);
        return <article key={field.field} className="stock-valuation-chart">
          <TrendChart title={field.title} unit="倍" points={history.map((item) => ({ label: item.observedDate, value: numeric(item[field.field]), detail: `${item.observedDate} · ${item.sourceCode} · ${item.qualityStatus}` }))} />
          <p className="stock-insight-note">最新 {numeric(view?.latest?.[field.field])?.toFixed(2) ?? '—'} 倍 · 有效样本 {metric?.sampleCount5y ?? 0} 个</p>
          <p className="stock-insight-note">本地 3 年窗口分位 {metric?.historyStatus === 'READY' ? unsignedPercent(numeric(metric.percentile3y)) : '积累中'} · 5 年窗口分位 {metric?.historyStatus === 'READY' ? unsignedPercent(numeric(metric.percentile5y)) : '积累中'}</p>
        </article>;
      })}</div>
      <p className="stock-insight-note">分位基于各窗口内已积累的正值样本，至少需要 20 个有效样本；窗口长度不代表已覆盖完整 3 年或 5 年。</p>
      <p className="stock-insight-note">来源 {view?.latest?.sourceCode ?? '未提供'} · 观测日 {view?.latest?.observedDate ?? '未提供'} · 质量 {view?.latest?.qualityStatus ?? '未提供'}</p>
    </>}
    {(view?.warnings ?? []).map((warning, index) => <p key={`${index}-${warning}`} className="stock-insight-note">{warning}</p>)}
  </section>;
}

function TrendChart({ title, unit, points }: { title: string; unit: string; points: TrendPoint[] }) {
  const valid = points.flatMap((point) => point.value == null ? [] : [point.value]);
  const min = Math.min(0, ...valid);
  const max = Math.max(0, ...valid);
  const span = max - min || 1;
  const x = (index: number) => 48 + index / Math.max(1, points.length - 1) * 360;
  const y = (value: number) => 143 - (value - min) / span * 112;
  // 缺失期断开，不以零值连接成趋势。
  let continuous = false;
  const path = points.map((point, index) => {
    if (point.value == null) {
      continuous = false;
      return '';
    }
    const command = continuous ? 'L' : 'M';
    continuous = true;
    return `${command}${x(index)},${y(point.value)}`;
  }).join(' ');
  return <section className="stock-trend-chart"><header><h4>{title}</h4><small>{unit}</small></header>
    {valid.length === 0 ? <p className="stock-insight-empty">暂无足够的可比数据</p> : <svg viewBox="0 0 450 180" role="img" aria-label={`${title}历史趋势`}>
      <line className="stock-chart-grid" x1="48" x2="408" y1={y(0)} y2={y(0)} />
      <text x="2" y={y(max) + 4}>{max.toFixed(1)}</text><text x="2" y={y(min) + 4}>{min.toFixed(1)}</text>
      <path className="stock-curve is-stock" d={path} />
      {points.map((point, index) => point.value == null ? null : <circle key={`${point.label}-${index}`} cx={x(index)} cy={y(point.value)} r="3"><title>{`${point.detail} · ${point.value.toFixed(2)} ${unit}`}</title></circle>)}
      <text x="48" y="173">{points[0]?.label}</text><text x="408" y="173" textAnchor="end">{points[points.length - 1]?.label}</text>
    </svg>}
  </section>;
}

function numeric(value: number | string | null | undefined) {
  if (value == null || value === '') {
    return undefined;
  }
  const number = Number(value);
  return Number.isFinite(number) ? number : undefined;
}

function unsignedPercent(value?: number) {
  return value == null ? '—' : percent(value).replace(/^\+/, '');
}
