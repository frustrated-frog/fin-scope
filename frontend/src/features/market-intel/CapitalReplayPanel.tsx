import { useEffect, useMemo, useRef, useState } from 'react';

import { api } from '../../shared/api/client';
import { CapitalReplayChart } from './CapitalReplayChart';
import { CapitalReplayTimeline, ReplayFollowup } from './CapitalReplayTimeline';
import { replayEvents, replayPercent } from './capitalReplayEvents';
import { DragonTigerSeatList } from './DragonTigerPanel';
import { marketDataProviderLabel, marketIntelWarningMessages } from './marketIntelPresentation';
import type { DragonTigerView, MarketIntelInstrument } from './marketIntelTypes';
import {
  AM_CLOSE, CLOSE, minuteLabel, OPEN, PM_OPEN, replayMoney, replayPoints, summarizeRange
} from './capitalReplaySeries';
import type { CapitalReplay, MinuteRange, ReplaySession } from './capitalReplaySeries';
import './CapitalReplayPanel.css';

const SPANS = [
  { label: '全天', from: OPEN, to: CLOSE },
  { label: '上午', from: OPEN, to: AM_CLOSE },
  { label: '午后', from: PM_OPEN, to: CLOSE },
  { label: '尾盘', from: 14 * 60 + 30, to: CLOSE }
];

type Props = {
  instrument: MarketIntelInstrument;
  dragonTiger: DragonTigerView | null;
};

function rangeLabel(range: MinuteRange) {
  return range.from === range.to ? minuteLabel(range.from)
    : `${minuteLabel(range.from)}–${minuteLabel(range.to)}`;
}

function SeatDisclosure({ view, date }: { view: DragonTigerView | null; date: string }) {
  const records = view?.records.filter((record) => record.tradeDate === date) ?? [];
  const unavailable = !view || ['NOT_REFRESHED', 'UNAVAILABLE', 'STALE_FALLBACK'].includes(view.health.status);
  return (
    <section className="capital-replay-disclosure" aria-labelledby="capital-replay-seats">
      <header><h4 id="capital-replay-seats">当日龙虎榜披露</h4><span>按榜单统计</span></header>
      {records.length ? records.map((record) => (
        <details key={record.id}>
          <summary>
            <span>{record.reason}</span>
            <small>{/连续|累计/.test(record.reason) ? '多日累计'
              : /^(日|当日)/.test(record.reason) ? '全天' : '见披露区间'}</small>
          </summary>
          <div className="dragon-tiger-seats">
            <DragonTigerSeatList title="买入席位" seats={record.buySeats} />
            <DragonTigerSeatList title="卖出席位" seats={record.sellSeats} />
          </div>
        </details>
      )) : <p>{unavailable ? '该日龙虎榜数据暂未取得。' : '已读取的龙虎榜数据中没有该日披露。'}</p>}
      <p>机构专用和营业部席位按榜单列出。披露金额没有盘中时间。</p>
    </section>
  );
}

function ReplayDesk({ session, dragonTiger }: { session: ReplaySession; dragonTiger: DragonTigerView | null }) {
  const points = useMemo(() => replayPoints(session), [session]);
  const events = useMemo(() => replayEvents(points), [points]);
  const tape = useRef<HTMLDivElement>(null);
  const [spanIndex, setSpanIndex] = useState(0);
  const [selection, setSelection] = useState<MinuteRange>(() => {
    if (events.length) {
      return events[0];
    }
    const end = points[points.length - 1].minute;
    return { from: Math.max(points[0].minute, end > AM_CLOSE ? PM_OPEN : OPEN, end - 4), to: end };
  });
  const span = SPANS[spanIndex];
  const visible = points.filter((point) => point.minute >= span.from && point.minute <= span.to);
  const suggestions = events.filter((event) => event.to >= span.from && event.from <= span.to);
  const detail = summarizeRange(points, selection);
  const selectedEvent = events.find((event) => event.from === selection.from && event.to === selection.to);
  const selectEvent = (range: MinuteRange) => {
    if (range.from < span.from || range.to > span.to) {
      setSpanIndex(0);
    }
    setSelection(range);
    tape.current?.scrollIntoView?.({ block: 'nearest' });
  };
  const flowPartial = detail.net.count > 0 && detail.net.count < detail.expectedFlow;
  const amountPartial = detail.amount.count > 0 && detail.amount.count < detail.expectedPrice;
  const netShare = detail.net.value !== null && detail.amount.value && !flowPartial && !amountPartial
    && detail.net.count === detail.amount.count ? detail.net.value / detail.amount.value * 100 : null;

  return (
    <>
      <div className="capital-replay-workspace">
        <div className="capital-replay-tape" ref={tape}>
          <div className="capital-replay-toolbar">
            <h4>分时资金</h4>
            <div role="group" aria-label="图表时段">
              {SPANS.map((option, index) => (
                <button type="button" key={option.label} aria-pressed={index === spanIndex}
                  disabled={!points.some((point) => point.minute >= option.from && point.minute <= option.to)}
                  onClick={() => {
                    setSpanIndex(index);
                    setSelection(option);
                  }}>{option.label}</button>
              ))}
            </div>
          </div>
          <CapitalReplayChart key={spanIndex} points={visible} span={span} selection={selection}
            events={suggestions} onSelect={setSelection} />
          {!session.flowProvider && <p className="capital-replay-chart-note">该交易日暂未取得分钟资金净额。</p>}
        </div>
        <aside className="capital-replay-readout" aria-label="区间数据">
          <header>
            <span>所选时段</span><strong>{rangeLabel(selection)}</strong>
            {selectedEvent && <small>{selectedEvent.label}</small>}
          </header>
          <dl>
            <div className="lead">
              <dt>{flowPartial ? '已覆盖分钟大额净额' : '大额净额'}</dt>
              <dd className={detail.net.value === null ? '' : detail.net.value > 0 ? 'in' : detail.net.value < 0 ? 'out' : ''}>
                {replayMoney(detail.net.value, true)}
              </dd>
            </div>
            <div><dt>超大单净额</dt><dd>{replayMoney(detail.superLarge.value, true)}</dd></div>
            <div><dt>大单净额</dt><dd>{replayMoney(detail.large.value, true)}</dd></div>
            <div><dt>{amountPartial ? '已覆盖分钟成交额' : '成交额'}</dt><dd>{replayMoney(detail.amount.value)}</dd></div>
            {netShare !== null && <div><dt>净额 / 成交额</dt><dd>{netShare.toFixed(1)}%</dd></div>}
            <div><dt>区间价格</dt><dd>{detail.startPrice?.toFixed(2) ?? '—'} → {detail.endPrice?.toFixed(2) ?? '—'}</dd></div>
            {selectedEvent && <div><dt>区间涨跌</dt><dd>{replayPercent(selectedEvent.change)}</dd></div>}
          </dl>
          {selectedEvent && <ReplayFollowup outcome={selectedEvent.outcome} />}
          <p>资金覆盖 {detail.net.count} / {detail.expectedFlow} 分钟 · 逐笔委托未接入</p>
          <details className="capital-replay-evidence">
            <summary>查看区间分钟明细</summary>
            <div tabIndex={0} role="region" aria-label="区间分钟明细">
              <table>
                <thead><tr><th>时间</th><th>价格</th><th>成交额</th><th>大额净额</th></tr></thead>
                <tbody>{detail.selected.map((point) => (
                  <tr key={point.minute}><th>{minuteLabel(point.minute)}</th><td>{point.price?.toFixed(2) ?? '—'}</td>
                    <td>{replayMoney(point.tradeAmount)}</td><td>{replayMoney(point.net, true)}</td></tr>
                ))}</tbody>
              </table>
            </div>
          </details>
        </aside>
      </div>
      <CapitalReplayTimeline events={suggestions} selection={selection} onSelect={selectEvent} />
      <div className="capital-replay-source">
        <span>价量 {session.priceProvider ? marketDataProviderLabel(
          session.priceProvider === 'EASTMONEY_CAPITAL_FLOW' ? 'EASTMONEY' : session.priceProvider) : '未取得'}</span>
        <span>资金 {session.flowProvider ? marketDataProviderLabel(session.flowProvider) : '未取得'}</span>
        <span>大额净额为大单与超大单净额之和</span>
      </div>
      <SeatDisclosure view={dragonTiger} date={session.date} />
    </>
  );
}

export function CapitalReplayPanel({ instrument, dragonTiger }: Props) {
  const [data, setData] = useState<CapitalReplay | null>(null);
  const [date, setDate] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const supported = ['SH', 'SZ', 'BJ'].includes(instrument.market);

  useEffect(() => {
    if (!supported) {
      return;
    }
    const request = new AbortController();
    setLoading(true);
    setError(null);
    api<CapitalReplay>(`/api/market-intel/instruments/${instrument.id}/capital-replay`, { signal: request.signal })
      .then((value) => {
        if (!request.signal.aborted) {
          setData(value);
        }
      })
      .catch((failure: Error) => {
        if (!request.signal.aborted) {
          setError(failure.message || '分时数据读取失败');
        }
      })
      .finally(() => {
        if (!request.signal.aborted) {
          setLoading(false);
        }
      });
    return () => request.abort();
  }, [instrument.id, supported, attempt]);

  const session = data?.sessions.find((value) => value.date === date) ?? data?.sessions[0];
  const warnings = marketIntelWarningMessages(data?.warnings ?? []);

  return (
    <section className="capital-replay" aria-labelledby="capital-replay-heading">
      <header className="capital-replay-header">
        <div>
          <p className="market-intel-kicker">INTRADAY REPLAY</p>
          <h3 id="capital-replay-heading">资金复盘</h3>
          <p>{instrument.name} <span>{instrument.code}.{instrument.market}</span></p>
        </div>
        {supported && <div className="capital-replay-controls">
          {session && <label>
            <span>交易日</span>
            <select value={session.date} onChange={(event) => setDate(event.target.value)}>
              {data?.sessions.map((value) => <option key={value.date}>{value.date}</option>)}
            </select>
          </label>}
          <button type="button" disabled={loading} onClick={() => setAttempt((value) => value + 1)}>
            {loading ? '读取中…' : '刷新分时'}
          </button>
        </div>}
      </header>
      {!supported && <p className="capital-replay-empty">资金复盘目前覆盖 A 股。请选择沪、深或北交所股票。</p>}
      {supported && loading && !session && <p className="capital-replay-empty" role="status">正在读取分时价格和分钟资金…</p>}
      {error && <p className="capital-replay-notice" role="alert">{error}{session ? '，当前显示上次读取的数据。' : '。请刷新分时重试。'}</p>}
      {supported && !loading && !error && !session && <p className="capital-replay-empty">近期交易日暂无可用的分时记录。</p>}
      {warnings.length > 0 && <p className="capital-replay-notice">{warnings.join(' · ')}</p>}
      {session && <ReplayDesk key={session.date} session={session}
        dragonTiger={dragonTiger?.instrument.id === instrument.id ? dragonTiger : null} />}
    </section>
  );
}
