import type { OvernightReport, OvernightMode } from './overnightTypes';
import type { OvernightJointState } from './overnightJointTypes';
import './OvernightDirectionPanel.css';

const percent = (value?: number | null) => value == null ? '—' : `${(value * 100).toFixed(1)}%`;
const phases: Record<string, string> = {
  ACCUMULATING: '积累真实预测', QUALIFIED: '通过前瞻对照',
  CHECKPOINT_FAILED: '未通过前瞻对照', MONITORING_DEGRADED: '近期表现退化',
};

export function OvernightDirectionPanel({ report }: { report: OvernightReport }) {
  const direction = report.closeDirection;
  if (!direction) {
    return null;
  }
  const probability = direction.upProbability;
  const history = direction.historical;
  const actual = report.outcome?.closeDirection;
  const challenger = direction.challenger;
  const prior = direction.selectedModel === 'PRIOR';
  const title = probability == null ? '次日涨跌正在准备' : prior ? '历史参考，尚无模型优势'
    : probability >= .5 ? '模型偏向上涨' : '模型偏向未上涨';
  return <section className="overnight-direction" aria-label="次日收盘涨跌预测">
    <header><div><span>次日涨跌 · {report.cutoff} 判断</span><h5>{title}</h5></div>
      <small data-validated={!!direction.validated}>{direction.validated ? '通过前瞻对照' : '尚未验证有效'}</small></header>
    {probability == null ? <p>{direction.reason}</p> : <>
      <dl className="overnight-direction-probabilities">
        <div><dt>{prior ? '历史上涨比例' : '上涨概率'}</dt><dd>{percent(probability)}</dd></div>
        <div><dt>未上涨概率<span>含持平</span></dt><dd>{percent(direction.notUpProbability ?? 1 - probability)}</dd></div>
      </dl>
      <p>比较 {report.targetDate ?? '次一交易日'} 与 {report.signalDate} 的收盘价，不扣交易成本。本次预测仅使用截至 {report.cutoff} 的数据。</p>
      {!direction.validated && <p className="overnight-direction-status">{phases[direction.forwardStatus ?? 'ACCUMULATING']} · {direction.forwardDays ?? 0} / 60 个交易日。当前结果保留观察。</p>}
      {direction.activeSource === 'CONTEXT_DIRECTION' && <p className="overnight-direction-status">已采用通过前瞻对照的环境增强方案，持续监测表现。</p>}
      {actual?.status === 'SETTLED' && <div className="overnight-direction-outcome"><b>次日实际{actual.actualUp ? '上涨' : actual.actualReturn === 0 ? '持平' : '下跌'} {percent(actual.actualReturn)}</b>
        <span>{actual.correct ? '本次方向判断命中' : '本次方向判断未命中'}</span></div>}
      <details><summary>查看涨跌验证依据</summary>
        <p>目标是未复权价格涨跌；普通除权分红尚未完整核验。涨跌与下方扣费盈利分别统计。</p>
        {history && <dl className="overnight-direction-metrics">
          <div><dt>历史方向命中 / 简单基准</dt><dd>{percent(history.accuracy)} / {percent(history.comparisons.HISTORICAL_PRIOR?.accuracy)}</dd></div>
          <div><dt>两类平均识别率</dt><dd>{percent(history.balancedAccuracy)}</dd></div>
          <div><dt>历史预测上涨比例</dt><dd>{percent(history.predictedUpRate)}</dd></div>
          <div><dt>历史检验范围</dt><dd>{history.dayCount} 日 · {history.sampleCount} 例</dd></div>
        </dl>}
        <p>两类平均识别率分别考虑上涨和未上涨，始终猜同一类只能得到 50%。历史开发成绩不能替代真实前瞻验证。</p>
        <p>{direction.probabilitySource === 'INTERCEPT' ? '当前概率采用已到期预测支持的偏差校准。' : '当前为模型原始概率；校准尚未同时证明改善方向命中和概率误差。'}</p>
      </details>
    </>}
    {challenger && <details className="overnight-context-comparison">
      <summary>环境增强对照 <span>{challenger.validated ? '通过前瞻对照' : '独立验证中'}</span></summary>
      <p>{challenger.reason}</p>
      {challenger.upProbability != null && <>
        <dl className="overnight-direction-metrics">
          <div><dt>现有方案 / 环境增强的上涨概率</dt><dd>{percent(challenger.incumbentProbability ?? probability)} / {percent(challenger.upProbability)}</dd></div>
          <div><dt>环境行情时点 / 收到时点</dt><dd>{challenger.contextAt?.slice(11, 16) ?? '—'} / {challenger.contextReceivedAt?.slice(11, 19) ?? '—'}</dd></div>
          <div><dt>研究池覆盖 / 指数行情</dt><dd>{challenger.contextSymbols ?? '—'} 只 / {challenger.indexCount ?? '—'} 个</dd></div>
          <div><dt>同行业样本</dt><dd>{challenger.industryAvailable ? '可用' : '覆盖不足，未使用'}</dd></div>
          {actual?.status === 'SETTLED' && <div><dt>环境增强的次日方向判断</dt><dd>{(challenger.upProbability >= .5) === actual.actualUp ? '命中' : '未命中'}</dd></div>}
        </dl>
        {challenger.selectedModel === 'PRIOR' && <p>候选方案仍采用历史上涨比例，尚未发现学习优势。</p>}
      </>}
      <p>{phases[challenger.forwardStatus ?? 'ACCUMULATING']} · {challenger.forwardDays ?? 0} / 60 个交易日。需同时优于现有方案和简单基准。</p>
      <p>研究池不代表全市场。环境数据在判断前自动采集，缺失时保留缺口；历史缺少的指数、行业信息不会用今天的数据补填。</p>
    </details>}
  </section>;
}

export function OvernightDirectionSummary({ state, mode }: { state: OvernightJointState; mode: OvernightMode }) {
  const models = state.models.filter(model => model.profile.mode === mode && model.closeDirection);
  const groups = state.closeDirectionForward?.groups.filter(group => group.mode === mode) ?? [];
  const contextGroups = state.contextDirectionForward?.groups.filter(group => group.mode === mode) ?? [];
  const snapshot = state.contextSnapshots?.find(row => mode === 'TAIL_ENTRY' ? row.cutoff !== '15:00' : row.cutoff === '15:00');
  return <details className="overnight-direction-summary"><summary>次日涨跌独立验证 <span>{models.length ? `${models.length} 个时点已训练` : '后台自动准备'}</span></summary>
    <p>预测次日收盘是否高于当日收盘；与扣费盈利使用不同标签和验证记录。</p>
    {!groups.length && <p>尚无已到期的真实涨跌对照。尾盘自动发现与盘后持仓将分别积累。</p>}
    {groups.map(group => <div className="overnight-direction-summary-row" key={group.key}>
      <b>{group.cutoff} → 次日收盘</b><span>{phases[group.status] ?? '等待结果'} · {group.dayCount} / {group.requiredDays} 日</span>
      <small>方向命中 / 基准 {percent(group.metrics?.accuracy)} / {percent(group.metrics?.comparisons.HISTORICAL_PRIOR?.accuracy)} · 两类平均识别 {percent(group.metrics?.balancedAccuracy)} · 可核验 {percent(group.coverage)}</small>
    </div>)}
    {models.map(model => <p key={model.id}>{model.profile.cutoff} · {model.closeDirection?.data.symbolCount} 只股票参与涨跌学习。历史开发对照：方向命中 {percent(model.closeDirection?.audit.historical.accuracy)} / 基准 {percent(model.closeDirection?.audit.historical.comparisons.HISTORICAL_PRIOR?.accuracy)}。仅供诊断。</p>)}
    <p>环境增强方案：{snapshot ? `最近采集 ${snapshot.signalDate} ${snapshot.observedAt.slice(11, 16)} · ${snapshot.capturedSymbols} / ${snapshot.expectedSymbols} 只研究池股票。` : '等待交易时段自动采集环境数据。'}</p>
    {contextGroups.map(group => <div className="overnight-direction-summary-row" key={`context-${group.key}`}>
      <b>{group.cutoff} 环境增强对照</b><span>{phases[group.status] ?? '等待结果'} · {group.dayCount} / {group.requiredDays} 日</span>
      <small>候选 / 现有方案命中率 {percent(group.metrics?.accuracy)} / {percent(group.metrics?.comparisons.INCUMBENT?.accuracy)} · 可核验 {percent(group.coverage)}</small>
    </div>)}
  </details>;
}
