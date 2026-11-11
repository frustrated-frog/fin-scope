import { replayPercent } from './capitalReplayEvents';
import type { ReplayOutcome } from './capitalReplayOutcome';
import { minuteLabel } from './capitalReplaySeries';

function retainedDescription(outcome: ReplayOutcome) {
  const ratio = outcome.close.retainedMove;
  if (ratio === null) {
    return null;
  }
  if (ratio < -1e-10) {
    return outcome.direction === 'up' ? '收盘跌破起涨点' : '收盘收复下跌起点';
  }
  if (Math.abs(ratio) < 1e-10) {
    return '收盘回到区间起点';
  }
  const move = outcome.direction === 'up' ? '原涨幅' : '原跌幅';
  return ratio > 1 + 1e-10 ? `${move}扩大至 ${(ratio * 100).toFixed(0)}%`
    : `${move}保留 ${(ratio * 100).toFixed(0)}%`;
}

export function ReplayFollowup({ outcome }: { outcome: ReplayOutcome }) {
  const retention = retainedDescription(outcome);
  const endsAtClose = outcome.status === 'PENDING' && outcome.close.status === 'AVAILABLE';
  return (
    <span className="capital-replay-followup" data-status={outcome.status}>
      <span className="capital-replay-followup-heading"><span>{outcome.horizon}</span><strong>{outcome.label}</strong></span>
      {outcome.change !== null && <span className="capital-replay-followup-endpoint">
        截至 {minuteLabel(outcome.through)}，较区间末价 {replayPercent(outcome.change)}
      </span>}
      {outcome.maxRise !== null && outcome.maxDrawdown !== null && <span className="capital-replay-followup-path">
        <span title="后续观察窗口内，相对区间末价的最高涨幅">最高较末价 <b>{replayPercent(outcome.maxRise)}</b></span>
        <span title="后续观察窗口内，从此前最高价到随后低价的最大跌幅，包含区间末价作为起点">
          最大回撤 <b>{replayPercent(outcome.maxDrawdown)}</b>
        </span>
      </span>}
      {outcome.returnedToStart && <span className="capital-replay-followup-return">
        {outcome.direction === 'up' ? '期间曾跌回起涨点' : '期间曾收复下跌起点'}
      </span>}
      {!endsAtClose && <span className="capital-replay-close">
        {outcome.close.change !== null ? <>
          <span>收盘较末价 <b>{replayPercent(outcome.close.change)}</b></span>
          {retention && <small>{retention}</small>}
        </> : <span>收盘价未取得</span>}
      </span>}
    </span>
  );
}
