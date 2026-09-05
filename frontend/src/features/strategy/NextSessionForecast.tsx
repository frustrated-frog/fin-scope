import { useEffect, useState } from 'react';
import { api } from '../../shared/api/client';
import type { NextSessionPrediction, NextSessionPredictionRecord } from './quantTypes';
import './NextSessionForecast.css';

const statusCopy: Record<NextSessionPrediction['status'], string> = {
  READY: '初步验证通过', WATCH: '观察预测 · 方向暂不判断', INSUFFICIENT_DATA: '历史样本不足',
  STALE_DATA: '行情已过期', CALENDAR_UNAVAILABLE: '交易日历待核验', BEFORE_CLOSE: '等待完整收盘数据',
};
const percent = (value?: number) => value == null ? '—' : `${(value * 100).toFixed(1)}%`;
const signed = (value?: number) => value == null ? '—' : `${value > 0 ? '+' : ''}${percent(value)}`;

export function NextSessionForecast({ prediction, compact = false }: { prediction?: NextSessionPrediction; compact?: boolean }) {
  if (!prediction) {
    return <section className="next-session-forecast"><strong>这份历史报告尚未包含次日收盘预测</strong><p>新生成的研究会同时保留次日预测与原有交易周期验证。</p></section>;
  }
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
    {!compact && valid && <div className="next-session-audit"><span>滚动验证 {prediction.validationSampleCount} 日 · 准确率 {percent(prediction.accuracy)}</span><span>Brier {prediction.brierScore?.toFixed(3) ?? '—'} / 基线 {prediction.baselineBrierScore?.toFixed(3) ?? '—'}（越低越好）</span><span>历史区间覆盖 {percent(prediction.intervalCoverage)} · 校准数据截至 {prediction.calibrationThrough}</span></div>}
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
