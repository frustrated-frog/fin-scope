import { useEffect, useState } from 'react';
import { api } from '../../shared/api/client';
import type { MarketIndexQuote, ResourceState, View } from '../../shared/types';
import { core, price, stamp, type Snapshot } from '../globalMarkets/markets';
import { changeClass, formatPct } from '../watchlist/watchlistFormatters';
import './DashboardIndexBoard.css';

const mainland = [
  { id: '000001', name: '上证指数' },
  { id: '399001', name: '深证成指' },
  { id: '399006', name: '创业板指' }
];
const usIndices = core.filter(asset => asset.group === '美股指数');
const shortNames: Record<string, string> = { DJI: '道琼斯', IXIC: '纳斯达克综合' };
type Row = { name: string; value?: number; pct?: number; time?: string; status?: string };

function MarketPanel({ region, title, rows, open }: {
  region: string; title: string; rows: Row[]; open: () => void;
}) {
  return <section className={'index-panel index-panel-' + region} aria-label={title}>
    <button className="index-panel-heading" type="button" onClick={open} aria-label={'查看' + title + '行情'}>
      <span className="index-region">{region}</span><strong>{title}</strong><span aria-hidden="true">↗</span>
    </button>
    <dl>{rows.map(row => (
      <div className="index-line" key={row.name}>
        <dt>{row.name}<small title={row.time && row.time + ' 北京时间'}>{row.status || row.time?.slice(5) || '时间未知'}</small></dt>
        <dd><strong>{row.value == null ? '—' : price(row.value)}</strong>
          <span className={changeClass(row.pct)}>{row.pct == null ? '—' : formatPct(row.pct)}</span>
        </dd>
      </div>
    ))}</dl>
  </section>;
}

export function DashboardIndexBoard({ indices, refreshRevision, onChangeView, reloadIndices }: {
  indices: ResourceState<MarketIndexQuote[]>; refreshRevision: number;
  onChangeView: (view: View) => void; reloadIndices: () => Promise<unknown>;
}) {
  const [us, setUs] = useState<Record<string, Row>>({});
  useEffect(() => {
    const controller = new AbortController();
    let timer: number;
    async function update(poll: boolean) {
      const jobs: Promise<unknown>[] = usIndices.map(async asset => {
        const name = shortNames[asset.id] || asset.name;
        try {
          const quote = await api<Snapshot>('/api/global-markets/' + asset.id, { signal: controller.signal });
          const { series } = quote;
          if (!(series.price > 0 && Number.isFinite(series.price))) {
            throw new Error('无有效点位');
          }
          const row = { name, value: series.price, time: stamp(series.asOf).replace(/\//g, '-'),
            pct: series.reference > 0 ? (series.price / series.reference - 1) * 100 : undefined,
            status: quote.fallback ? '缓存行情' : undefined };
          if (!controller.signal.aborted) {
            setUs(previous => ({ ...previous, [asset.id]: row }));
          }
        } catch {
          if (!controller.signal.aborted) {
            setUs(previous => ({ ...previous, [asset.id]: { ...previous[asset.id], name,
              status: previous[asset.id]?.value != null ? '缓存行情' : '暂不可用' } }));
          }
        }
      });
      if (poll) {
        jobs.push(reloadIndices());
      }
      await Promise.all(jobs);
      if (!controller.signal.aborted) {
        timer = window.setTimeout(() => void update(true), 120_000);
      }
    }
    void update(false);
    return () => {
      controller.abort();
      window.clearTimeout(timer);
    };
  }, [refreshRevision, reloadIndices]);

  const cnRows = mainland.map(({ id, name }) => {
    const q = indices.data.find(item => item.code === id);
    const valid = q?.quoteValid && q.qualityStatus !== 'UNAVAILABLE' && Number.isFinite(q.price);
    const pending = indices.phase === 'idle' || indices.phase === 'loading';
    const cached = indices.error || q?.qualityStatus?.startsWith('STALE');
    return { name, value: valid ? q.price : undefined, pct: valid && Number.isFinite(q.changePct) ? q.changePct : undefined,
      time: q?.asOf?.replace('T', ' ').slice(0, 16), status: valid ? (cached ? '缓存行情' : undefined) : (pending ? '读取中' : '暂不可用') };
  });
  return <div className="index-board">
    <MarketPanel region="CN" title="A股指数" rows={cnRows} open={() => onChangeView('marketPulse')} />
    <MarketPanel region="US" title="美股指数" rows={usIndices.map(asset => us[asset.id] || {
      name: shortNames[asset.id] || asset.name, status: '读取中'
    })} open={() => onChangeView('globalMarkets')} />
  </div>;
}
