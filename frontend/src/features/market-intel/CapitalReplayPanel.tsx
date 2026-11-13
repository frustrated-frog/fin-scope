import { useEffect, useMemo, useRef, useState } from 'react';

import { api } from '../../shared/api/client';
import { CapitalReplayChart } from './CapitalReplayChart';
import { CapitalReplayTimeline } from './CapitalReplayTimeline';
import type { ReplayTimelineMode } from './CapitalReplayTimeline';
import { ReplayFollowup } from './CapitalReplayFollowup';
import { replayEvents, replayPercent } from './capitalReplayEvents';
import { replayStages } from './capitalReplayStages';
import { marketIntelWarningMessages } from './marketIntelPresentation';
import type { MarketIntelInstrument } from './marketIntelTypes';
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
};

function rangeLabel(range: MinuteRange) {
  return range.from === range.to ? minuteLabel(range.from)
    : `${minuteLabel(range.from)}–${minuteLabel(range.to)}`;
}

function ReplayDesk({ session }: { session: ReplaySession }) {
  const points = useMemo(() => replayPoints(session), [session]);
  const events = useMemo(() => replayEvents(points), [points]);
  const stages = useMemo(() => replayStages(points, events), [points, events]);
  const [timelineMode, setTimelineMode] = useState<ReplayTimelineMode>('stages');
  const tape = useRef<HTMLDivElement>(null);
  const [spanIndex, setSpanIndex] = useState(0);
  const [selection, setSelection] = useState<MinuteRange>(() => {
    const query = new URLSearchParams(window.location.search);
    const from = query.get('replayFrom');
    const to = query.get('replayTo');
    if (query.get('replayDate') === session.date && from && to && /^\d{2}:\d{2}$/.test(from) && /^\d{2}:\d{2}$/.test(to)) {
      const lower = Number(from.slice(0, 2)) * 60 + Number(from.slice(3));
      const upper = Number(to.slice(0, 2)) * 60 + Number(to.slice(3));
      if (lower >= OPEN && upper <= CLOSE && lower <= upper) {
        return { from: lower, to: upper };
      }
    }
    if (stages.length) {
      return stages[0];
    }
    const end = points[points.length - 1].minute;
    return { from: Math.max(points[0].minute, end > AM_CLOSE ? PM_OPEN : OPEN, end - 4), to: end };
  });
  const span = SPANS[spanIndex];
  const visible = points.filter((point) => point.minute >= span.from && point.minute <= span.to);
  const suggestions = events.filter((event) => event.to > span.from && event.from < span.to);
  const visibleStages = stages.filter((stage) => stage.to > span.from && stage.from < span.to);
  const markers = timelineMode === 'stages' ? visibleStages : suggestions;
  const priceCount = visible.filter((point) => point.price !== null).length;
  const amountCount = visible.filter((point) => point.tradeAmount !== null).length;
  const hasPriceSequence = visible.some((point, index) => index > 0 && point.price !== null
    && visible[index - 1].price !== null && point.minute === visible[index - 1].minute + 1);
  const detail = summarizeRange(points, selection);
  const candidates = timelineMode === 'stages' ? [...stages, ...events] : [...events, ...stages];
  const selectedEvent = candidates.find((event) => event.from === selection.from && event.to === selection.to);
  const changeTimelineMode = (mode: ReplayTimelineMode) => {
    setTimelineMode(mode);
    const first = mode === 'stages' ? visibleStages[0] : suggestions[0];
    if (first) {
      setSelection(first);
    }
  };
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
            events={markers} onSelect={setSelection} />
          {(priceCount < visible.length || amountCount < visible.length) && <p className="capital-replay-chart-note">
            已取得的 {visible.length} 个分钟点中，价格覆盖 {priceCount} 个，成交额覆盖 {amountCount} 个。缺失段不连线、不参与转折判断。
          </p>}
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
      <CapitalReplayTimeline events={suggestions} stages={visibleStages} mode={timelineMode} onModeChange={changeTimelineMode}
        selection={selection} onSelect={selectEvent}
        hasPriceSequence={hasPriceSequence} />
    </>
  );
}

export function CapitalReplayPanel({ instrument }: Props) {
  const [data, setData] = useState<CapitalReplay | null>(null);
  const [date, setDate] = useState<string | null>(() => new URLSearchParams(window.location.search).get('replayDate'));
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
    api<CapitalReplay>(`/api/market-intel/instruments/${instrument.id}/capital-replay${attempt === 0 && new URLSearchParams(window.location.search).has('agentView') ? '?refresh=false' : ''}`, { signal: request.signal })
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
      {session && <ReplayDesk key={session.date} session={session} />}
    </section>
  );
}
