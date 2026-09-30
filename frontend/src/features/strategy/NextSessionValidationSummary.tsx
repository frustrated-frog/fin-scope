import type { NextSessionPredictionRecord } from './quantTypes';
import { useEffect, useState } from 'react';
import { api } from '../../shared/api/client';
import type { NextSessionValidation } from './nextSessionValidation';

const percent = (value?: number | null) => value == null ? '—' : `${(value * 100).toFixed(1)}%`;

export function NextSessionValidationSummary({ records, code }: { records: NextSessionPredictionRecord[]; code?: string }) {
  const [summary, setSummary] = useState<NextSessionValidation>();
  const [error, setError] = useState('');
  const [version, setVersion] = useState('');
  useEffect(() => {
    let active = true;
    setSummary(undefined); setError('');
    api<NextSessionValidation>(`/api/quant/next-session-predictions/validation${code ? `?code=${encodeURIComponent(code.slice(0, 6))}` : ''}`)
      .then(value => {
        if (!Array.isArray(value.groups)) {
          throw new Error('全历史验收接口尚未就绪');
        }
        if (active) {
          setSummary(value);
        }
      }).catch(() => {
        if (active) {
          setError('全历史验收读取失败，请确认后端已更新并重启。');
        }
      });
    return () => { active = false; };
  }, [code, records]);
  const group = summary?.groups.find(item => item.version === version) ?? summary?.groups.find(item => item.version === records[0]?.prediction.modelVersion)
    ?? summary?.groups[0];
  return <div className="next-validation" aria-label="真实预测效果验收">
    <div className="next-validation-heading">
      <div><h4>全历史表现</h4><p>全历史冻结账本共 {summary?.recordCount ?? '—'} 条 · 按交易日等权统计</p></div>
      {!!summary?.groups.length && <label className="next-version-picker">模型版本
        <select aria-label="模型版本" title={group?.version} value={group?.version ?? ''} onChange={event => setVersion(event.target.value)}>
          {summary.groups.map(item => <option key={item.version} value={item.version}>{item.version}</option>)}
        </select>
      </label>}
    </div>
    {error && <p className="next-history-empty" role="alert">{error}</p>}
    {!summary && !error && <p role="status">正在读取全历史验证结果…</p>}
    {summary && !summary.groups.length && <p className="next-history-empty">暂无可分组的验证记录。</p>}
    {group && <article className="next-validation-version">
      <dl className="next-validation-metrics">
        <div><dt>方向命中率</dt><dd>{percent(group.accuracy)}</dd><small>按交易日等权</small></div>
        <div><dt>概率误差 · Brier</dt><dd>{group.brier?.toFixed(4) ?? '—'}</dd><small>越低越好</small></div>
        <div><dt>有效记录 / 交易日</dt><dd>{group.count} / {group.days}</dd><small>已到期且可验证</small></div>
      </dl>
      <div className="next-validation-caution"><strong>{group.days < 60 ? '早期观察' : '持续验证'}</strong><span>{group.days < 60 ? '独立预测日不足 60 天，仅供早期观察。' : '样本数量不代表已证明优势。'}方向命中不代表扣费后盈利。</span></div>
      <div className="next-validation-counts"><span>待到期 <b>{group.pending}</b></span><span>无法验证 <b>{group.unavailable}</b></span><span>重复排除 <b>{group.duplicates}</b></span></div>
      <details className="next-validation-calibration"><summary>概率是否说得准<span>查看分段对照</span></summary>
        <div className="next-validation-table-wrap" tabIndex={0} role="region" aria-label="概率分段对照">
          <table><thead><tr><th>预测概率段</th><th>记录 / 日期</th><th>平均预测概率</th><th>实际上涨比例</th></tr></thead>
            <tbody>{group.bins.map(bin => <tr key={bin.lower}><td>{percent(bin.lower)}–{percent(bin.upper)}<small>{bin.upper === 1 ? '含上界' : '不含上界'}</small></td><td>{bin.count} / {bin.days}</td><td>{percent(bin.forecast)}</td><td>{percent(bin.actual)}</td></tr>)}</tbody>
          </table>
          {!group.bins.length && <p>暂无概率分段数据。</p>}
        </div>
      </details>
    </article>}
    <details className="next-validation-method"><summary>统计口径与验证边界</summary>
      <p>按版本分组，同股同目标日保留最早生成的一条；先按日内股票平均，再按交易日等权。下方明细只展示最近 100 条，筛选不改变全历史统计。</p>
      <p>收盘方向：目标日收盘相对信号日收盘的涨跌，不扣交易费用。本区只评估这一口径。</p>
      <p>强势观察：该股历史类似事件的结果频率，不直接等于当前获利概率。</p>
      <p>尾盘与盘后：各自按入场价格代理或持仓收盘参考价，计算四个退出时点的净收益，单独复盘。</p>
      <p>当前冻结账本未保存逐条基线预测，不能用训练期 Brier 代替真实前瞻基线，因此暂不判定“优于基线”。</p>
    </details>
  </div>;
}
