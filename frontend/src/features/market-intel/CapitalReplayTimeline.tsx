import { minuteLabel, replayMoney } from './capitalReplaySeries';
import type { MinuteRange } from './capitalReplaySeries';
import { replayPercent } from './capitalReplayEvents';
import type { ReplayEvent, ReplayOutcome } from './capitalReplayEvents';

export function ReplayFollowup({ outcome }: { outcome: ReplayOutcome }) {
  return (
    <div className="capital-replay-followup" data-status={outcome.status}>
      <span>{outcome.horizon}</span>
      <strong>{outcome.label}</strong>
      {outcome.change !== null && <span>
        截至 {minuteLabel(outcome.through)}，较区间末价 {replayPercent(outcome.change)}
      </span>}
    </div>
  );
}

export function CapitalReplayTimeline({ events, selection, onSelect }: {
  events: ReplayEvent[];
  selection: MinuteRange;
  onSelect: (range: MinuteRange) => void;
}) {
  return (
    <section className="capital-replay-timeline" aria-label="关键转折时间线">
      <header>
        <div><h4>关键转折 <span>{events.length}</span></h4><p>按时间排列 · 点击定位分时区间</p></div>
        <details className="capital-replay-method">
          <summary>识别口径</summary>
          <p>连续分钟涨跌达到 0.6% 形成一段，反向变化达到 0.4% 切分转折。
            十分钟内涨跌达到 1% 标为快速变化；成交额达到此前等长完整区间的 1.5 倍标为放量。
            价格段之外补充五分钟大额净额集中区间，净额绝对值须至少占成交额的 15%，
            并超过完整五分钟窗口净额绝对值中位数的 1.5 倍。午休和价格缺口分别处理。
            后续表现使用区间结束后的行情，尾盘不足十分钟时观察至收盘。</p>
        </details>
      </header>
      {events.length > 0 ? <ol>
        {events.map((event, index) => (
          <li key={`${event.from}-${event.to}`}>
            <button type="button" className={`capital-replay-event ${event.direction}`}
              aria-pressed={selection.from === event.from && selection.to === event.to}
              onClick={() => onSelect(event)}>
              <span className="capital-replay-event-number">{String(index + 1).padStart(2, '0')}</span>
              <div className="capital-replay-event-body">
                <div className="capital-replay-event-heading">
                  <time>{minuteLabel(event.from)}–{minuteLabel(event.to)}</time><small>{event.context}</small>
                </div>
                <strong className="capital-replay-event-title">{event.label}</strong>
                <div className="capital-replay-event-metrics">
                  <span>区间涨跌 <b>{replayPercent(event.change)}</b></span>
                  <span>大额净额 <b>{replayMoney(event.net, true)}</b></span>
                  {event.volumeRatio !== null && <span>成交额 <b>{event.volumeRatio.toFixed(2)}×</b></span>}
                  {event.net === null && <span>资金覆盖 {event.flowCount}/{event.minuteCount} 分钟</span>}
                </div>
                <ReplayFollowup outcome={event.outcome} />
              </div>
            </button>
          </li>
        ))}
      </ol> : <p className="capital-replay-chart-note">这一时段没有达到识别条件的完整转折区间。</p>}
    </section>
  );
}
