import type { OvernightJointState } from './overnightJointTypes';
import { candidateProtocol } from './overnightCandidateTypes';
import './OvernightCandidateProgress.css';

const reasons: Record<string, string> = {
  NOT_DUE: '次日尚未到期', MISSING_OUTCOME: '缺少次日收盘', MISSING_DECISION_BARS: '缺少判断时行情',
  MISSING_PAST_FEATURES: '历史分钟不完整', CALENDAR_UNAVAILABLE: '交易日历未覆盖',
};
const percent = (value?: number | null) => value == null ? '—' : `${(value * 100).toFixed(1)}%`;
const arms = { ROLLING: '滚动选涨', INCUMBENT: '当时主判断', MOMENTUM: '当日涨幅排序', UNIFORM: '同池平均' };
const featureName = (code: string) => code === 'RELATIVE_AMOUNT_5' ? '相对过去五日成交额' : '开盘至判断时涨幅';
const featureValue = (code: string, value: number) => code === 'RELATIVE_AMOUNT_5' ? `${Math.exp(value).toFixed(2)} 倍` : percent(value);

export function OvernightCandidateProgress({ state }: { state: OvernightJointState }) {
  const models = state.models.filter(model => model.profile.mode === 'TAIL_ENTRY'
    && (model.upSelection?.protocol === candidateProtocol
      || ['FAILED', 'INSUFFICIENT_TRAINING_HISTORY'].includes(model.candidateReadiness?.status ?? '')));
  if (!models.length) {
    return null;
  }
  return <section className="overnight-candidate-progress" aria-label="真实候选滚动择模">
    <header><h5>真实候选验证进度</h5><p>先核对样本，再比较选股能力。仅完整候选日计入成绩。</p></header>
    {models.map(model => {
      const readiness = model.candidateReadiness;
      if (!model.upSelection || readiness?.status === 'FAILED') {
        return <article key={model.id}><header><b>{model.profile.cutoff} 尾盘</b><span>
          {readiness?.status === 'FAILED' ? '滚动择模准备失败' : '历史训练样本不足'}</span></header>
          <p>{readiness?.reason ?? '候选名单已核对，但完整训练日期仍不足；后台继续准备。'}</p>
          <p>本次检查 {readiness?.computedAt.replace('T', ' ').slice(0, 16)}。原有判断继续保留，不能将未生成结果计作模型成绩。</p>
        </article>;
      }
      const selection = model.upSelection.audit.selection;
      const forward = state.candidateForward?.protocol === candidateProtocol
        ? state.candidateForward.groups.find(group => group.cutoff === model.profile.cutoff && group.mode === 'TAIL_ENTRY') : undefined;
      const waiting = selection.reason === 'INSUFFICIENT_COMPLETE_CANDIDATES';
      const advantage = selection.reason === 'CANDIDATE_ADVANTAGE';
      return <article key={model.id}>
        <header><b>{model.profile.cutoff} 尾盘</b><span>{waiting ? '完整样本仍不足' : advantage ? '候选方案进入真实对照' : '尚未找到稳定选涨优势'}</span></header>
        <ol className="overnight-candidate-stages">
          <li><span>样本对齐</span><strong>{readiness?.cohortDays != null ? `${readiness.completeDays} / ${readiness.cohortDays} 日` : '未记录'}</strong><small>原始名单完整可核验</small></li>
          <li><span>近期择模</span><strong>{selection.selectionDays} / {selection.plannedDays} 日</strong><small>覆盖 {percent(selection.candidateCoverage)}</small></li>
          <li><span>真实验证</span><strong>{forward?.dayCount ?? 0} 日</strong><small>新版上线后单独留档</small></li>
        </ol>
        <p>最终拟合至 {model.upSelection.audit.modelFitThrough?.slice(0, 10) ?? '未记录'} · 择模结果截止 {selection.selectionThrough?.slice(0, 10) ?? '等待到期结果'}。</p>
        {waiting && <p>至少需要 10 个完整候选日且覆盖达到 75%。缺失项保留在计划内，后台继续准备。</p>}
        {!waiting && !advantage && <p>样本已进入择模，但现有方案尚未同时满足命中、覆盖和简单基准对照要求。历史比例继续标记为证据不足。</p>}
        <details><summary>查看样本缺口与同名单对照</summary>
          {readiness?.support?.protocol === 'overnight-candidate-support-v1' && <section aria-label="候选与训练样本差异">
            <p><b>{readiness.support.status === 'SHIFT_DETECTED' ? '候选特征与训练池存在明显差异' : '候选与训练池对照'}</b></p>
            {readiness.support.features.length ? <>
              <p>{readiness.support.dayCount} 个完整日期中，{readiness.support.outsidePoolRows} / {readiness.support.candidateRows} 条候选来自原训练池之外。按日期等权比较：</p>
              <div className="overnight-candidate-audit-table overnight-candidate-support-table" tabIndex={0} role="region" aria-label="候选特征差异，可横向滚动"><table><caption>特征中位数 · 同日期间</caption>
                <thead><tr><th>特征</th><th>训练池</th><th>实际候选</th><th>常见区间外占比</th></tr></thead>
                <tbody>{readiness.support.features.map(row => <tr key={row.code}><th>{featureName(row.code)}</th>
                  <td>{featureValue(row.code, row.poolMedian)}</td><td>{featureValue(row.code, row.candidateMedian)}</td>
                  <td>{percent(row.outsideCentralRangeRate)}</td></tr>)}</tbody>
              </table></div>
              <p>常见区间取训练池第 5—95 百分位。这项诊断描述样本差异，不证明漏判原因，也不会据此调高上涨概率。</p>
            </> : <p>同日可训练样本不足，暂无法对照；不把未知差异显示为 0。</p>}
          </section>}
          <div className="overnight-candidate-audit-table"><table><caption>逐日样本可用性</caption>
            <thead><tr><th>日期</th><th>可用 / 计划</th><th>缺口</th></tr></thead>
            <tbody>{readiness?.days?.map(day => <tr key={day.signalDate}><th>{day.signalDate.slice(5)}</th>
              <td>{day.availableCount} / {day.expectedCount}</td><td>{day.complete ? '完整' : Object.entries(day.missing).map(([key, count]) => `${reasons[key] ?? key} ${count}`).join('；')}</td></tr>)}</tbody>
          </table></div>
          <p>以下仅统计新版实际留档、次日到期的完整名单：已配对 {forward?.pairedCount ?? 0} / {forward?.scheduledCount ?? 0} 条。历史回放不混入。</p>
          <div className="overnight-candidate-audit-table"><table><caption>次日上涨比例 · 同一天、同名单、相同名额</caption>
            <thead><tr><th>方案</th><th>固定前三名</th><th>匹配看涨名额</th></tr></thead>
            <tbody>{Object.entries(arms).map(([key, label]) => <tr key={key}><th>{label}</th>
              <td>{percent(forward?.ranking.forced.precision[key])}</td><td>{percent(forward?.ranking.matched.precision[key])}</td></tr>)}</tbody>
          </table></div>
          <p>没有报看涨的日期也保留：{forward?.ranking.noCandidateDays ?? 0} 日。分数相同的股票按平均权重计数，避免代码顺序制造选股优势。</p>
        </details>
      </article>;
    })}
  </section>;
}
