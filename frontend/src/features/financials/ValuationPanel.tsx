import { useEffect, useState } from 'react';

import { api } from '../../shared/api/client';
import { StockCorporateAction, StockValuationView, ValuationMetricSummary } from './financialTypes';

const metricLabels: Record<string, string> = {
  PE_TTM: '市盈率 TTM', PE_MRQ: '市盈率 MRQ', PB_MRQ: '市净率 MRQ',
  PS_TTM: '市销率 TTM', PCF_TTM: '市现率 TTM'
};

const eventLabels: Record<string, string> = {
  CASH_DIVIDEND: '现金分红', STOCK_DIVIDEND: '送转股', RIGHTS_ISSUE: '配股', UNKNOWN: '其他'
};

export function ValuationPanel({ instrumentId }: { instrumentId: number }) {
  const [view, setView] = useState<StockValuationView>();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;
    setError('');
    api<StockValuationView>(`/api/financials/instruments/${instrumentId}/valuation`)
      .then((value) => { if (active) setView(value); })
      .catch((reason) => { if (active) setError(messageOf(reason)); });
    return () => { active = false; };
  }, [instrumentId]);

  async function refresh() {
    setBusy(true);
    setError('');
    try {
      setView(await api<StockValuationView>(
        `/api/financials/instruments/${instrumentId}/valuation/refresh`, { method: 'POST' }
      ));
    } catch (reason) {
      setError(messageOf(reason));
    } finally {
      setBusy(false);
    }
  }

  return <section className="valuation-panel">
    <header>
      <div>
        <span>VALUATION LEDGER</span>
        <h4>估值与公司行为</h4>
        <p>同花顺最新快照按交易日留存，历史分位随本地数据逐日积累。</p>
      </div>
      <button type="button" className="primary-button" onClick={refresh} disabled={busy}>
        {busy ? '刷新中…' : '刷新估值数据'}
      </button>
    </header>

    {error && <p className="valuation-error" role="alert">{error}</p>}
    {!view ? <p className="valuation-empty">正在读取本地估值档案…</p> : !view.latest
      ? <div className="valuation-empty"><strong>尚未积累估值快照</strong><span>点击刷新后将保存首个交易日样本。</span></div>
      : <>
        <div className="valuation-metric-grid">
          {view.metrics.map((metric) => <MetricCard key={metric.metricCode} metric={metric} />)}
        </div>
        {view.metrics.some((metric) => metric.historyStatus === 'ACCUMULATING')
          && <p className="valuation-accumulating">历史分位积累中 · 至少需要 20 个有效交易日快照</p>}
        <div className="valuation-ledgers">
          <HistoryTable view={view} />
          <CorporateActions actions={view.corporateActions} />
        </div>
        <footer>
          <span>来源 {view.latest.sourceCode}</span>
          <span>观测日 {view.latest.observedDate}</span>
          <span>质量 {view.latest.qualityStatus}</span>
        </footer>
      </>}
  </section>;
}

function MetricCard({ metric }: { metric: ValuationMetricSummary }) {
  const ready = metric.historyStatus === 'READY';
  return <article>
    <span>{metricLabels[metric.metricCode] ?? metric.metricCode}</span>
    <strong>{decimal(metric.value)}</strong>
    <dl>
      <div><dt>3 年分位</dt><dd>{ready ? percent(metric.percentile3y) : '积累中'}</dd></div>
      <div><dt>5 年分位</dt><dd>{ready ? percent(metric.percentile5y) : '积累中'}</dd></div>
    </dl>
    <small>{metric.sampleCount5y} 个有效样本</small>
  </article>;
}

function HistoryTable({ view }: { view: StockValuationView }) {
  return <article className="valuation-ledger-card">
    <header><h5>本地估值历史</h5><span>{view.history.length} 个交易日</span></header>
    <div><table><thead><tr><th>日期</th><th>PE TTM</th><th>PB MRQ</th><th>PS TTM</th></tr></thead>
      <tbody>{view.history.slice(0, 20).map((item) => <tr key={`${item.observedDate}-${item.sourceCode}`}>
        <td>{item.observedDate}</td><td>{decimal(item.peTtm)}</td>
        <td>{decimal(item.pbMrq)}</td><td>{decimal(item.psTtm)}</td>
      </tr>)}</tbody></table></div>
  </article>;
}

function CorporateActions({ actions }: { actions: StockCorporateAction[] }) {
  return <article className="valuation-ledger-card">
    <header><h5>公司行为</h5><span>近 5 年</span></header>
    {actions.length === 0 ? <p>暂无除权除息记录</p> : <div className="valuation-action-list">
      {actions.map((action) => <div key={`${action.exDate}-${action.sourceCode}`}>
        <time>{action.exDate}</time>
        <strong>{action.eventTypes.map((type) => eventLabels[type] ?? type).join(' · ')}</strong>
        <small>{actionDetail(action)}</small>
      </div>)}
    </div>}
  </article>;
}

function actionDetail(action: StockCorporateAction) {
  const values = [];
  if (Number(action.dividendPerShare) > 0) values.push(`每股派息 ${action.dividendPerShare} ${action.currency}`);
  if (Number(action.perShareBonus) > 0) values.push(`每股送转 ${action.perShareBonus}`);
  if (Number(action.allotmentRatio) > 0) values.push(`配股比例 ${action.allotmentRatio}`);
  return values.join('；') || '供应商未提供更多数值';
}

function decimal(value?: number | string | null) {
  return value == null || value === '' ? '—' : Number(value).toFixed(2);
}

function percent(value?: number | string | null) {
  return value == null ? '—' : `${Number(value).toFixed(1)}%`;
}

function messageOf(reason: unknown) {
  return reason instanceof Error ? reason.message : '估值数据加载失败';
}
