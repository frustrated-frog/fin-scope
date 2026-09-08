import { useEffect, useState } from 'react';
import { api } from '../../shared/api/client';
import type { DirectionEvaluation, NextSessionPrediction, NextSessionPredictionRecord } from './quantTypes';
import './NextSessionForecast.css';

const statusCopy: Record<NextSessionPrediction['status'], string> = {
  READY: '初步验证通过', WATCH: '观察预测 · 方向暂不判断', INSUFFICIENT_DATA: '历史样本不足',
  STALE_DATA: '行情已过期', CALENDAR_UNAVAILABLE: '交易日历待核验', BEFORE_CLOSE: '等待完整收盘数据',
};
const percent = (value?: number, digits = 1) => value == null ? '—' : `${(value * 100).toFixed(digits)}%`;
const signed = (value?: number, digits = 1) => value == null ? '—' : `${value > 0 ? '+' : ''}${percent(value, digits)}`;
const excessPoints = (value: number) => signed(value, 3).replace('%', ' 个百分点');

function DirectionEvidence({ audit }: { audit: DirectionEvaluation }) {
  return <div className="next-session-audit" aria-label="全体方向与置信度评价">
    <p>{audit.dayCount} 个交易日 · {audit.sampleCount} 个股票样本 · 按交易日等权</p>
    {audit.flatSampleCount != null && <p>零涨跌 {audit.flatSampleCount} 个样本，归非上涨类别。</p>}
    <p>全体方向准确率 {percent(audit.accuracy)} · 平衡准确率 {percent(audit.balancedAccuracy ?? undefined)}</p>
    <p>高置信度预测：覆盖 {percent(audit.highConfidence.coverage)} · 命中 {percent(audit.highConfidence.accuracy ?? undefined)}（单列统计，不替代全体准确率）</p>
    {Object.entries(audit.comparisons).map(([code, comparison]) => <p key={code}>相对 {code} 的准确率差区间 {excessPoints(comparison.accuracyDifferenceLower)} ～ {excessPoints(comparison.accuracyDifferenceUpper)}；Brier 差区间 {comparison.brierDifferenceLower.toFixed(4)} ～ {comparison.brierDifferenceUpper.toFixed(4)}</p>)}
    <p>以上区间按交易日分块重采样，并对多基线比较校正。{audit.reason}</p>
  </div>;
}

export function NextSessionForecast({ prediction, compact = false }: { prediction?: NextSessionPrediction; compact?: boolean }) {
  if (!prediction) {
    return <section className="next-session-forecast"><strong>这份历史报告尚未包含次日收盘预测</strong><p>新生成的研究会同时保留次日预测与原有交易周期验证。</p></section>;
  }
  const joint = prediction.jointModel;
  const valid = ['READY', 'WATCH'].includes(prediction.status);
  const today = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai' }).format(new Date());
  const historical = Boolean(prediction.targetDate && prediction.targetDate <= today);
  return <section className="next-session-forecast" data-status={prediction.status} aria-label="次日收盘预测">
    <header><div><span>{historical ? '已冻结的目标日预测' : '下一交易日收盘预测'}</span><strong>{prediction.targetDate ?? '目标日期待确认'}</strong></div><b>{statusCopy[prediction.status]}</b></header>
    <p>目标日收盘相对 {prediction.asOfDate} 收盘的涨跌 · 不等于可成交收益</p>
    {valid && <dl>
      <div><dt>上涨概率</dt><dd>{percent(prediction.upProbability)}</dd></div>
      <div><dt>预期涨跌</dt><dd>{signed(prediction.expectedReturn)}</dd></div>
      <div><dt>80% 校准区间</dt><dd>{signed(prediction.lowerReturn)} ～ {signed(prediction.upperReturn)}</dd></div>
    </dl>}
    {!compact && valid && <div className="next-session-audit"><span>{joint?.applied ? '独立验证' : '滚动验证'} {prediction.validationSampleCount} 个样本 · 准确率 {percent(prediction.accuracy)}</span><span>Brier {prediction.brierScore?.toFixed(3) ?? '—'} / 基线 {prediction.baselineBrierScore?.toFixed(3) ?? '—'}（越低越好）</span><span>历史区间覆盖 {percent(prediction.intervalCoverage)} · 校准数据截至 {prediction.calibrationThrough}</span></div>}
    {!compact && valid && prediction.directionEvaluation && <DirectionEvidence audit={prediction.directionEvaluation} />}
    {valid && joint && <div className="next-session-joint" aria-label="联合模型与排序证据">
      <strong>{joint.applied ? '联合方向模型已用于本次预测' : '联合模型对照 · 当前保留原预测'}</strong>
      {joint.applied && joint.returnApplied != null && <p>收益幅度与区间：{joint.returnApplied ? '联合收益模型' : '原单股模型'}</p>}
      <p>{joint.universeCount} 只股票 · {joint.featureCount} 个因子 · {joint.selectedClassifier} + LambdaRank</p>
      {joint.trainingUniverseCount != null && <p>训练池 {joint.trainingUniverseCount} 只 · 当前可预测 {joint.universeCount} 只 · 展示候选池 {joint.displayUniverseCount ?? joint.universeCount} 只 · 训练期行业因子覆盖 {percent(joint.industryCoverage)}</p>}
      {joint.rankingTarget && <p>选股目标：{joint.rankingTarget === 'MARKET_RESIDUAL' ? '扣除市场影响、按历史波动率调整的个股强弱' : '绝对收盘涨跌'}；价格预测：{joint.returnTarget === 'MARKET_RESIDUAL' ? '市场分量 + 个股分量' : '直接预测收盘涨跌'}。</p>}
      {joint.evidenceKind === 'RETROSPECTIVE' && <p>历史回归对照：该时间段已经参与方法研发，不作为新方法的全新样本外证据。</p>}
      <p>股票排序：{joint.rankingEligible ? '已启用' : '对照观察'} · 截面位置 {percent(joint.rankingPercentile)}（越高越靠前，非上涨概率）</p>
      <details><summary>查看独立测试与新旧比较</summary>
        <p>测试 {joint.testStart} ～ {joint.testEnd} · {joint.validationDayCount} 个交易日 / {joint.validationSampleCount} 个股票样本，指标按日等权。</p>
        {joint.adaptationEvidence && <p>{joint.adaptationEvidence.periodCount} 个测试前比较时期 · 选中 {joint.adaptationEvidence.selected} · {joint.adaptationEvidence.rule}</p>}
        {joint.directionEvaluation && <DirectionEvidence audit={joint.directionEvaluation} />}
        <p>联合 Brier {joint.pooledBrierScore.toFixed(4)} / 历史频率 {joint.baselineBrierScore.toFixed(4)} / 联合逻辑回归 {joint.logisticBrierScore.toFixed(4)}（越低越好）</p>
        <p>该股联合 Brier {joint.stockBrierScore?.toFixed(4) ?? '—'} · 对照上涨概率 {percent(joint.upProbability)} · 对照预期涨跌 {signed(joint.expectedReturn)}</p>
        <p>排序 Rank IC {joint.rankIc.toFixed(3)} · Top 5 次日平均涨跌 {signed(joint.top5Return, 3)} · 超过同日股票池 {excessPoints(joint.top5PoolExcess)} / 动量排序 {excessPoints(joint.top5MomentumExcess)}</p>
        <p>{joint.reason}。这些是当前可用股票池内的历史比较，尚未消除幸存者偏差。</p>
      </details>
    </div>}
    <details><summary>生成时点与预测边界</summary><p>{prediction.generatedAt.replace('T', ' ')} · {prediction.modelCode ?? '未训练'} · {prediction.modelVersion}</p><p>训练标签截至 {prediction.trainingThrough ?? '—'}；校准标签截至 {prediction.calibrationThrough ?? '—'}；训练 / 校准样本 {prediction.trainingSampleCount} / {prediction.calibrationSampleCount}</p>{prediction.warnings.map(warning => <p key={warning}>{warning}</p>)}</details>
  </section>;
}

export function NextSessionOutcomeHistory({ code }: { code?: string }) {
  const [records, setRecords] = useState<NextSessionPredictionRecord[]>([]);
  const [failed, setFailed] = useState(false);
  useEffect(() => {
    let active = true;
    setRecords([]);
    const load = async () => {
      try {
        const result = await api<NextSessionPredictionRecord[]>(`/api/quant/next-session-predictions?limit=20${code ? `&code=${encodeURIComponent(code.slice(0, 6))}` : ''}`);
        if (!Array.isArray(result) || result.some(item => !item?.prediction?.targetDate)) {
          throw new Error('次日验证账本响应不完整');
        }
        if (active) {
          setRecords(Array.isArray(result) ? result : []);
          setFailed(false);
        }
      } catch {
        if (active) {
          setFailed(true);
        }
      }
    };
    void load();
    const timer = window.setInterval(load, 60000);
    return () => { active = false; window.clearInterval(timer); };
  }, [code]);
  return <section className="next-session-forecast next-session-history" aria-label="次日预测真实验证">
    <header><strong>次日预测真实验证</strong><span>目标交易日收盘后自动结算</span></header>
    {failed ? <p role="status">验证账本暂时无法读取，稍后自动重试。</p> : records.length === 0 ? <p>尚无新协议的前瞻记录。新的预测会自动留存，未到期不计入成绩。</p> : <div className="quant-table-wrap"><table><thead><tr><th>股票</th><th>目标日</th><th>当时概率</th><th>真实涨跌</th><th>验证状态</th></tr></thead><tbody>{records.map(record => <tr key={record.id}><td>{record.instrumentCode}</td><td>{record.prediction.targetDate}</td><td>{percent(record.prediction.upProbability)}</td><td>{signed(record.actualReturn)}</td><td title={record.outcomeNote}>{record.status === 'MATURED' ? `${record.correct ? '方向命中' : '方向未中'} · ${record.intervalCovered ? '区间内' : '区间外'}` : record.status === 'PENDING' ? '等待目标日收盘' : '无法验证'}</td></tr>)}</tbody></table></div>}
  </section>;
}
