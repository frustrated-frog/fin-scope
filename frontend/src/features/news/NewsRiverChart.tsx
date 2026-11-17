import { bucketTotal, riverCategories, timeLabel } from './newsRiverTypes';
import type { RiverBucket, RiverCategory } from './newsRiverTypes';

export function NewsRiverChart({ date, buckets, selected, category, firstRecordedAt, lastRecordedAt, onSelect }: {
  date: string; buckets: RiverBucket[]; selected?: string; category: RiverCategory; firstRecordedAt?: string; lastRecordedAt?: string; onSelect: (start: string) => void;
}) {
  const indexed = new Map(buckets.map(item => [item.start.slice(0, 16), item]));
  const slots = Array.from({ length: 48 }, (_, i) => {
    const start = `${date}T${String(Math.floor(i / 2)).padStart(2, '0')}:${i % 2 ? '30' : '00'}:00`;
    return indexed.get(start.slice(0, 16)) ?? { start, finance: 0, technology: 0, politics: 0, other: 0 };
  });
  const slotIndex = (time: string) => Number(time.slice(11, 13)) * 2 + Math.floor(Number(time.slice(14, 16)) / 30);
  const from = firstRecordedAt ? slotIndex(firstRecordedAt) : 0;
  const to = lastRecordedAt ? slotIndex(lastRecordedAt) : -1;
  const series = riverCategories.filter(item => item.field && (category === 'ALL' || category === item.code));
  const maximum = Math.max(1, ...slots.flatMap(slot => series.map(item => slot[item.field!])));
  const ceiling = Math.max(5, Math.ceil(maximum / 5) * 5);
  return <figure className="river-chart" aria-label="一天内新收录事件的变化">
    <div className="river-chart-unit">新收录事件 / 半小时</div>
    <div className="river-chart-plot">
      <div className="river-chart-grid" aria-hidden="true">{[ceiling, Math.round(ceiling / 2), 0].map((value, i) =>
        <div key={i}><span>{value}</span></div>)}</div>
      <svg viewBox="0 0 960 180" preserveAspectRatio="none" role="img" aria-label="财经、科技、国际时事与其他资讯的收录曲线">
        {series.map(({ code, field }) => {
          const points = slots.slice(from, to + 1).map((slot, i) => `${(i + from) * 20},${165 - slot[field!] / ceiling * 145}`);
          if (!points.length) {
            return null;
          }
          return <g key={code} className={`river-series river-${code.toLowerCase()}`}>
            <polygon points={`${from * 20},165 ${points.join(' ')} ${to * 20},165`} />
            <polyline points={points.join(' ')} vectorEffect="non-scaling-stroke" />
            <circle cx={to * 20} cy={165 - slots[to][field!] / ceiling * 145} r="2.5" fill="currentColor" />
          </g>;
        })}
      </svg>
      {from > 0 && <div className="river-chart-unrecorded" style={{ left: 0, width: `${from / 48 * 100}%` }}><span>记录开始前</span></div>}
      {to < 47 && <div className="river-chart-unrecorded" style={{ left: `${(to + 1) / 48 * 100}%`, right: 0 }}><span>{to < 0 ? '尚未开始记录' : '等待后续记录'}</span></div>}
      <div className="river-chart-targets">{slots.map(slot => {
        const count = category === 'ALL' ? bucketTotal(slot) : slot[riverCategories.find(item => item.code === category)!.field!];
        return <button type="button" key={slot.start} disabled={!count || slotIndex(slot.start) < from || slotIndex(slot.start) > to} aria-pressed={selected?.slice(0, 16) === slot.start.slice(0, 16)}
          aria-label={`${timeLabel(slot.start)} 新收录 ${count} 个事件`} title={`${timeLabel(slot.start)} · ${count} 个新事件`}
          onClick={() => onSelect(slot.start)}><span className="river-chart-marker" /></button>;
      })}</div>
    </div>
    <div className="river-chart-hours" aria-hidden="true">{['00:00', '04:00', '08:00', '12:00', '16:00', '20:00', '24:00'].map(time => <span key={time}>{time}</span>)}</div>
  </figure>;
}
