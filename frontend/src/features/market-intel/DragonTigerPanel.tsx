import { useState } from 'react';

import type { DragonTigerRecord, DragonTigerSeat, DragonTigerView } from './marketIntelTypes';

function formatMoney(value?: number | null) {
  if (value === undefined || value === null) return '--';
  const sign = value > 0 ? '+' : value < 0 ? '-' : '';
  const absolute = Math.abs(value);
  if (absolute >= 1e8) return `${sign}${(absolute / 1e8).toFixed(2)} 亿`;
  if (absolute >= 1e4) return `${sign}${(absolute / 1e4).toFixed(2)} 万`;
  return `${sign}${absolute.toFixed(2)}`;
}

function formatPercent(value?: number | null) {
  if (value === undefined || value === null) return '--';
  return `${value > 0 ? '+' : ''}${value.toFixed(2)}%`;
}

function moneyTone(value?: number | null) {
  if (!value) return 'flat';
  return value > 0 ? 'in' : 'out';
}

function seatKey(seat: DragonTigerSeat) {
  return `${seat.direction}:${seat.rank}:${seat.seatCode ?? seat.seatName}`;
}

function SeatList({ title, seats }: { title: string; seats: DragonTigerSeat[] }) {
  return (
    <section className="dragon-tiger-seat-list">
      <h4>{title}</h4>
      {seats.length ? (
        <ol>
          {seats.map((seat) => (
            <li key={seatKey(seat)}>
              <span className="dragon-tiger-seat-rank">{seat.rank}</span>
              <div>
                <strong>{seat.seatName}</strong>
                <small>
                  {seat.institutional && <em>机构席位</em>}
                  {seat.northbound && <em>北向专用</em>}
                </small>
              </div>
              <dl>
                <div><dt>买入</dt><dd>{formatMoney(seat.buyAmount)}</dd></div>
                <div><dt>卖出</dt><dd>{formatMoney(seat.sellAmount)}</dd></div>
                <div className={moneyTone(seat.netAmount)}><dt>净额</dt><dd>{formatMoney(seat.netAmount)}</dd></div>
              </dl>
            </li>
          ))}
        </ol>
      ) : <p>该方向席位暂未完整披露。</p>}
    </section>
  );
}

function RecordCard({
  record,
  expanded,
  onToggle
}: {
  record: DragonTigerRecord;
  expanded: boolean;
  onToggle: () => void;
}) {
  return (
    <article className="dragon-tiger-record">
      <header>
        <time dateTime={record.tradeDate}>{record.tradeDate}</time>
        <span className={record.qualityStatus === 'COMPLETE' ? 'complete' : 'partial'}>
          {record.qualityStatus === 'COMPLETE' ? '席位完整' : '席位部分可用'}
        </span>
      </header>
      <h4>{record.reason}</h4>
      {record.providerExplanation && <p className="dragon-tiger-explanation">{record.providerExplanation}</p>}
      <dl className="dragon-tiger-summary">
        <div><dt>收盘价</dt><dd>{record.closePrice?.toFixed(2) ?? '--'}</dd></div>
        <div><dt>涨跌幅</dt><dd>{formatPercent(record.changeRate)}</dd></div>
        <div><dt>榜单买入</dt><dd>{formatMoney(record.buyAmount)}</dd></div>
        <div><dt>榜单卖出</dt><dd>{formatMoney(record.sellAmount)}</dd></div>
        <div className={moneyTone(record.netAmount)}><dt>净买额</dt><dd>{formatMoney(record.netAmount)}</dd></div>
        <div><dt>换手率</dt><dd>{formatPercent(record.turnoverRate)}</dd></div>
      </dl>
      <button className="ghost-button dragon-tiger-toggle" type="button" onClick={onToggle}>
        {expanded ? '收起席位明细' : '查看席位明细'}
      </button>
      {expanded && (
        <div className="dragon-tiger-seats">
          <SeatList title="买入席位 TOP5" seats={record.buySeats ?? []} />
          <SeatList title="卖出席位 TOP5" seats={record.sellSeats ?? []} />
        </div>
      )}
    </article>
  );
}

export function DragonTigerPanel({
  view,
  refreshing = false,
  refreshError
}: {
  view: DragonTigerView;
  refreshing?: boolean;
  refreshError?: string | null;
}) {
  const [expanded, setExpanded] = useState<Set<number>>(new Set());
  const unavailable = view.health.status === 'UNAVAILABLE';
  const stale = view.health.status === 'STALE_FALLBACK';
  const notRefreshed = view.health.status === 'NOT_REFRESHED';

  function toggle(id: number) {
    setExpanded((current) => {
      const next = new Set(current);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  return (
    <section className="dragon-tiger-panel" aria-labelledby="dragon-tiger-heading">
      <header>
        <div>
          <p className="market-intel-kicker">Public abnormal trading disclosure</p>
          <h3 id="dragon-tiger-heading">龙虎榜事实</h3>
        </div>
        <strong>近{view.range.days}日上榜 {view.records.length} 次</strong>
      </header>

      {refreshing && (
        <div className="dragon-tiger-health partial" role="status">
          后台正在更新龙虎榜事实…
        </div>
      )}
      {refreshError && (
        <div className="dragon-tiger-health unavailable" role="alert">
          {refreshError}
        </div>
      )}
      {(unavailable || stale) && (
        <div className={`dragon-tiger-health ${unavailable ? 'unavailable' : 'stale'}`} role="alert">
          {view.health.warnings.join('；') || (unavailable
            ? '龙虎榜数据源暂不可用'
            : '龙虎榜在线刷新失败，正在显示最近成功数据')}
        </div>
      )}
      {!unavailable && !stale && view.health.warnings.length > 0
      && !(refreshing && notRefreshed) && (
        <div className="dragon-tiger-health partial" role="status">
          {view.health.warnings.join('；')}
        </div>
      )}

      {view.records.length ? (
        <div className="dragon-tiger-records">
          {view.records.map((record) => (
            <RecordCard
              key={record.id}
              record={record}
              expanded={expanded.has(record.id)}
              onToggle={() => toggle(record.id)}
            />
          ))}
        </div>
      ) : !unavailable && (
        <p className="dragon-tiger-empty">
          {notRefreshed
            ? refreshing
              ? '系统正在后台查询当前窗口内的公开龙虎榜记录，完成后会自动更新。'
              : '尚未刷新龙虎榜事实。点击“刷新市场数据”后，系统会查询当前窗口内的公开披露记录。'
            : `近 ${view.range.days} 日没有公开龙虎榜记录。这表示当前窗口内未发现满足公开披露条件的上榜事件。`}
        </p>
      )}

      <p className="dragon-tiger-disclaimer">
        龙虎榜仅覆盖满足公开披露条件的异常交易，席位不等于具体账户或投资者，不构成投资建议。
      </p>
    </section>
  );
}
