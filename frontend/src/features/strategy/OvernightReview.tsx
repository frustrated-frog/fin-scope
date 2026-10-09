import { useState } from 'react';
import type { OvernightMode, OvernightReport } from './overnightTypes';

const percent = (value?: number) => value == null ? '—' : `${(value * 100).toFixed(1)}%`;
export function OvernightReview({ records, mode, busy, onSettle, onSelect }: {
  records: OvernightReport[]; mode: OvernightMode; busy: boolean; onSettle: () => void; onSelect: (report: OvernightReport) => void;
}) {
  const [metric, setMetric] = useState('');
  const [date, setDate] = useState('');
  const [evidence, setEvidence] = useState('FORWARD');
  const scoped = records.filter(item => item.mode === mode && item.evidenceKind === evidence);
  const activeMetric = metric || (scoped.some(item => item.closeDirection?.upProbability != null) ? 'DIRECTION' : '10:00');
  const dates = [...new Set(scoped.map(item => item.signalDate))].sort().reverse();
  const selectedDate = dates.includes(date) ? date : '';
  const filtered = scoped.filter(item => !selectedDate || item.signalDate === selectedDate);
  return <section className="overnight-review" aria-label="两类预测独立档案">
    <header className="overnight-view-heading"><span>预测 → 实际 / 留下每一次判断</span><h4>历史复盘</h4><p>最近 50 份档案中，当前场景与范围共 {scoped.length} 份。全历史统计见「预测表现」。</p></header>
    <div className="overnight-review-filters"><label>记录范围<select value={evidence} onChange={event => { setEvidence(event.target.value); setDate(''); }}><option value="FORWARD">真实前瞻</option><option value="RETROSPECTIVE">历史回顾</option></select></label>
      <label>信号日期<select value={selectedDate} onChange={event => setDate(event.target.value)}><option value="">全部日期</option>{dates.map(day => <option key={day} value={day}>{day}</option>)}</select></label>
      <label>对照目标<select value={activeMetric} onChange={event => setMetric(event.target.value)}><option value="DIRECTION">次日收盘涨跌</option><option value="OPEN">次日开盘净收益</option><option value="10:00">次日 10:00 净收益</option><option value="14:30">次日 14:30 净收益</option><option value="CLOSE">次日收盘净收益</option></select></label>
      <button type="button" onClick={onSettle} disabled={busy || !scoped.length}>{busy ? '读取到期行情…' : '更新到期结果'}</button></div>
    {!filtered.length && <div className="overnight-quiet-state"><h4>这个范围还没有档案</h4><p>自动研判形成有效结果后会在这里留档；历史回顾与真实前瞻分别展示。</p></div>}
    <div className="overnight-review-list">{filtered.map(item => {
      const direction = item.closeDirection;
      const actual = item.outcome?.closeDirection;
      const directionView = activeMetric === 'DIRECTION';
      const target = item.targets.find(row => row.target === activeMetric);
      const realized = item.outcome?.targets.find(row => row.target === activeMetric);
      const probability = directionView ? direction?.upProbability : target?.upProbability;
      const actualReturn = directionView ? actual?.status === 'SETTLED' ? actual.actualReturn : undefined : realized?.actualNetReturn;
      const correct = directionView ? actual?.status === 'SETTLED' ? actual.correct : undefined : probability != null && actualReturn != null ? (probability >= .5) === (actualReturn > 0) : undefined;
      const baseline = directionView ? direction?.selectedModel === 'PRIOR' : target?.probabilitySource === 'HISTORICAL_BASELINE';
      return <button type="button" className="overnight-review-row" key={item.id ?? `${item.instrumentCode}-${item.signalDate}-${item.cutoff}`} onClick={() => onSelect(item)}>
        <span><b>{item.instrumentCode}</b><small>{item.signalDate} · {item.cutoff}</small></span>
        <span><small>{baseline ? directionView ? '历史上涨比例' : '历史盈利比例' : directionView ? '冻结上涨概率' : '冻结盈利概率'}</small><strong>{percent(probability)}</strong></span>
        <span className="overnight-review-arrow" aria-hidden="true">→</span>
        <span><small>{directionView ? '次日收盘实际涨跌' : `${activeMetric === 'OPEN' ? '开盘' : activeMetric === 'CLOSE' ? '收盘' : activeMetric} 实际净收益`}</small><strong>{actualReturn != null ? percent(actualReturn) : directionView && !direction ? '未记录该目标' : '待核验'}</strong></span>
        <span className="overnight-review-verdict" data-correct={correct}>{correct != null ? correct ? '方向命中' : '方向未命中' : probability == null ? '预测缺失' : '等待结果'}<small>查看完整依据 ↗</small></span>
      </button>;
    })}</div>
    <p className="overnight-review-note">收盘涨跌与各时点净收益分别对照；净收益已扣冻结成本。未记录涨跌预测的旧档案默认展示 10:00 收益，到期结果自动更新。</p>
  </section>;
}
