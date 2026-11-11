import { minuteLabel, replayMoney } from './capitalReplaySeries';
import type { MinuteRange } from './capitalReplaySeries';
import { replayPercent } from './capitalReplayEvents';
import type { ReplayEvent } from './capitalReplayEvents';
import type { ReplayStage } from './capitalReplayStages';
import { ReplayFollowup } from './CapitalReplayFollowup';

export type ReplayTimelineMode = 'stages' | 'events';

function ReplayCard({ event, index, selection, onSelect }: {
  event: ReplayEvent;
  index?: number;
  selection: MinuteRange;
  onSelect: (range: MinuteRange) => void;
}) {
  return (
    <button type="button" className={`capital-replay-event ${event.direction}`}
      aria-pressed={selection.from === event.from && selection.to === event.to}
      onClick={() => onSelect(event)}>
      {index !== undefined && <span className="capital-replay-event-number">{String(index + 1).padStart(2, '0')}</span>}
      <span className="capital-replay-event-body">
        <span className="capital-replay-event-heading">
          <time>{minuteLabel(event.from)}–{minuteLabel(event.to)}</time><small>{event.context}</small>
        </span>
        <strong className="capital-replay-event-title">{event.label}</strong>
        <span className="capital-replay-event-metrics">
          <span>区间涨跌 <b>{replayPercent(event.change)}</b></span>
          <span>大额净额 <b>{replayMoney(event.net, true)}</b></span>
          {event.volumeRatio !== null && <span>成交额 <b>{event.volumeRatio.toFixed(2)}×</b></span>}
          {event.net === null && <span>资金覆盖 {event.flowCount}/{event.minuteCount} 分钟</span>}
        </span>
        <ReplayFollowup outcome={event.outcome} />
      </span>
    </button>
  );
}

function ReplayMethod() {
  return <details className="capital-replay-method">
    <summary>识别口径</summary>
    <p>盘面阶段按早盘、上午、午后、尾盘组织。反向变化达到 1.2%、且此前阶段持续至少五分钟时切分；
      达到 2.5% 的快速变化单独保留。午休与价格缺口分别处理，阶段可以共享转折价格点。</p>
    <p>细区间沿用涨跌 0.6%、反向 0.4% 的识别口径。十分钟内涨跌达到 1% 标为快速变化；
      成交额达到此前等长完整区间的 1.5 倍标为放量。价格段之外补充五分钟资金集中区间，
      净额绝对值至少占成交额的 15%，并超过完整五分钟窗口净额绝对值中位数的 1.5 倍。</p>
    <p>后续表现观察十个交易分钟，尾盘不足十分钟时观察至收盘。最高涨幅相对区间末价，
      最大回撤按观察窗口内先高后低的路径计算。路径缺失时不计算极值和回撤，已取得的收盘价仍可单独比较。</p>
  </details>;
}

export function CapitalReplayTimeline({ events, stages, mode, onModeChange, selection, onSelect, hasPriceSequence }: {
  events: ReplayEvent[];
  stages: ReplayStage[];
  mode: ReplayTimelineMode;
  onModeChange: (mode: ReplayTimelineMode) => void;
  selection: MinuteRange;
  onSelect: (range: MinuteRange) => void;
  hasPriceSequence: boolean;
}) {
  const entries = mode === 'stages' ? stages : events;
  return (
    <section className="capital-replay-timeline" aria-label="关键转折时间线">
      <header>
        <div>
          <h4>{mode === 'stages' ? '盘面阶段' : '全部区间'} <span>{entries.length}</span></h4>
          <p>{mode === 'stages' ? `${events.length} 个细区间 · 展开查看完整证据` : '按时间排列 · 点击定位分时区间'}</p>
        </div>
        <ReplayMethod />
      </header>
      {(stages.length > 0 || events.length > 0) && <div className="capital-replay-levels" role="group" aria-label="复盘层级">
        <button type="button" aria-pressed={mode === 'stages'} onClick={() => onModeChange('stages')}>盘面阶段</button>
        <button type="button" aria-pressed={mode === 'events'} onClick={() => onModeChange('events')}>全部区间（{events.length}）</button>
      </div>}
      {entries.length > 0 ? <ol className={mode === 'stages' ? 'capital-replay-stage-list' : 'capital-replay-interval-list'}
        aria-label={mode === 'stages' ? '盘面阶段列表' : '全部区间列表'}>
        {entries.map((event, index) => (
          <li key={`${event.from}-${event.to}`}>
            <ReplayCard event={event} index={index} selection={selection} onSelect={onSelect} />
            {mode === 'stages' && stages[index].events.length > 0 && <details className="capital-replay-stage-details">
              <summary>展开 {stages[index].events.length} 个细区间</summary>
              <ol aria-label={`${minuteLabel(event.from)}–${minuteLabel(event.to)} 的细分区间`}>
                {stages[index].events.map((detail) => <li key={`${detail.from}-${detail.to}`}>
                  <ReplayCard event={detail} selection={selection} onSelect={onSelect} />
                </li>)}
              </ol>
            </details>}
          </li>
        ))}
      </ol> : <p className="capital-replay-chart-note">{hasPriceSequence
        ? '这一时段没有达到识别条件的完整转折区间。'
        : '连续分钟价格缺失，暂不能识别转折；已取得的资金净额仍可查看。'}</p>}
    </section>
  );
}
