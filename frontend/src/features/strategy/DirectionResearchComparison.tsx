import research from './conditionalDirectionResearch.json';
import './NextSessionForecast.css';

const percent = (value: number) => `${(value * 100).toFixed(2)}%`;
const points = (value: number) => `${value > 0 ? '+' : ''}${(value * 100).toFixed(2)} 个百分点`;

export function DirectionResearchComparison() {
  const fixed = research.comparisons.FIXED_LEGACY;
  const improved = research.accuracy > fixed.accuracy;
  return <details className="next-session-forecast" aria-label="次日方向增强实验">
    <summary>次日方向增强实验 · 历史研究快照</summary>
    <p>{research.universeCount} 只股票 · {research.dayCount} 个交易日 · {research.sampleCount} 个股票日；信号从 {research.testStart} 开始，最后目标日 {research.testEnd}。</p>
    <p>{research.snapshotNote}</p>
    <table>
      <thead><tr><th>对照方法</th><th>方向准确率</th></tr></thead>
      <tbody>
        <tr><td>固定基础树＋旧校准</td><td>{percent(fixed.accuracy)}</td></tr>
        <tr><td>滚动基础树原始概率</td><td>{percent(research.comparisons.ROLLING_RAW.accuracy)}</td></tr>
        <tr><td>测试前选中的条件修正</td><td>{percent(research.accuracy)}</td></tr>
      </tbody>
    </table>
    <p>{improved ? '历史平均命中提高，仍需新的前瞻验证。' : '本轮平均命中未超过较强对照，尚未达到准确率升级目标。'}</p>
    <p>相对固定基础树的差值 {points(fixed.accuracyDifference)}；日期分块区间 {points(fixed.accuracyDifferenceLower)} ～ {points(fixed.accuracyDifferenceUpper)}。这是历史均值的不确定范围，不是明天准确率的保证。</p>
    <p>候选只在测试前选择，按日期等权评价；已看过的历史不能充当新的前瞻证据。测试前选中 {research.selected}，平衡准确率 {percent(research.balancedAccuracy)}，Brier {research.brierScore.toFixed(4)}。</p>
  </details>;
}
