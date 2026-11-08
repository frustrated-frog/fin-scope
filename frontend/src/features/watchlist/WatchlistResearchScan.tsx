import { useEffect, useMemo, useState } from 'react';
import { api } from '../../shared/api/client';
import type { WatchlistItem } from '../../shared/types';
import { RESEARCH_SCAN_PATH, useWatchlistResearchScan } from './useWatchlistResearchScan';
import type { ResearchScanRun, ResearchSnapshot } from './researchScanTypes';
import { formatResearchNumber as number, isCompletedResearchRun } from './researchScanTypes';
import { WatchlistResearchObservations } from './WatchlistResearchObservations';
import './watchlistResearchScan.css';

const tabs = ['研究观察', '强弱分解', '隐藏联动', '增长质量'] as const;
type Tab = typeof tabs[number];

function validSnapshot(snapshot?: ResearchSnapshot | null): snapshot is ResearchSnapshot {
  return Boolean(snapshot && Array.isArray(snapshot.findings) && Array.isArray(snapshot.strengths)
    && Array.isArray(snapshot.relationships) && Array.isArray(snapshot.growthQuality) && Array.isArray(snapshot.gaps));
}

export function WatchlistResearchScan({ items, ready, onOpen }: {
  items: WatchlistItem[];
  ready: boolean;
  onOpen: (item: WatchlistItem) => void;
}) {
  const membership = useMemo(() => items.filter((item) => item.type === 'STOCK')
    .map((item) => item.code).sort().join(','), [items]);
  const { view, loading, error, retry, rescan } = useWatchlistResearchScan(membership, ready);
  const [tab, setTab] = useState<Tab>('研究观察');
  const [selected, setSelected] = useState('');
  const [historical, setHistorical] = useState<ResearchScanRun>();
  const [historyError, setHistoryError] = useState('');

  useEffect(() => {
    setSelected('');
    setHistorical(undefined);
  }, [membership]);

  useEffect(() => {
    if (!selected) {
      setHistoryError('');
      return;
    }
    let cancelled = false;
    const controller = new AbortController();
    setHistoryError('');
    void api<ResearchScanRun>(`${RESEARCH_SCAN_PATH}/${encodeURIComponent(selected)}`, { signal: controller.signal })
      .then((result) => {
        if (cancelled) {
          return;
        }
        if (!isCompletedResearchRun(result) || result.id !== selected || !validSnapshot(result.snapshot)) {
          throw new Error('历史扫描数据不完整');
        }
        setHistorical(result);
      }).catch((cause) => {
        if (!cancelled) {
          setHistoryError(cause instanceof Error ? cause.message : '历史扫描读取失败');
        }
      });
    return () => {
      cancelled = true;
      controller.abort();
    };
  }, [selected, membership]);

  if (!ready || !membership) {
    return null;
  }
  const run = selected ? (historical?.id === selected ? historical : undefined) : view?.latest;
  const snapshot = validSnapshot(run?.snapshot) ? run.snapshot : undefined;
  const active = view?.active;
  const findStock = (code: string) => items.find((item) => item.type === 'STOCK' && item.code === code);
  const stockName = (code: string, fallback?: string) => findStock(code)?.name || fallback || code;
  const stockLink = (code: string, name?: string) => {
    const current = findStock(code);
    const label = current?.name || name || code;
    return current ? <button className="research-stock-link" type="button" onClick={() => onOpen(current)}>
      {label}<small>{code}</small>
    </button> : <span>{label}<small> {code} · 已移出自选</small></span>;
  };
  const tabCounts = snapshot ? [snapshot.findings.length, snapshot.strengths.length,
    snapshot.relationships.length, snapshot.growthQuality.length] : [];

  return <section className="watchlist-research-scan" aria-label="自选研究扫描">
    <header className="research-scan-head">
      <div><h3>自选研究扫描</h3><p>{snapshot ? <>
        行情截至 {snapshot.marketDate || '暂无行情'} · 强弱 {snapshot.marketCovered}/{snapshot.totalStocks} · 财务 {snapshot.financialCovered}/{snapshot.totalStocks}
      </> : '自动核对市场强弱、股票联动和财务增长质量'}</p></div>
      <div className="research-scan-actions">
        <select aria-label="选择扫描记录" value={selected} onChange={(event) => setSelected(event.target.value)}>
          <option value="">最新结果</option>
          {(view?.history || []).map((entry) => <option key={entry.id} value={entry.id}>
            扫描 {entry.scanDate} {entry.startedAt ? new Date(entry.startedAt).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }) : ''}
          </option>)}
        </select>
        <button type="button" disabled={Boolean(active) || loading} onClick={() => {
          setSelected('');
          rescan();
        }}>{active ? '扫描中…' : '重新扫描'}</button>
      </div>
    </header>
    {active && <p className="research-scan-progress" role="status">
      <span className="research-scan-dot" aria-hidden="true" />
      {active.stage === 'FINANCIAL' ? '阶段 2/2' : '阶段 1/2'} · {active.message} · 已处理 {active.processedStocks}/{active.totalStocks}
    </p>}
    {error && <p className="research-scan-error" role="status">扫描暂不可用：{error} <button type="button" onClick={retry}>重试读取</button></p>}
    {!selected && view?.lastAttempt?.status === 'FAILED' && <p className="research-scan-error" role="status">{view.lastAttempt.message}</p>}
    {historyError && <p className="research-scan-error" role="status">{historyError}</p>}
    {selected && !run && !historyError && <p role="status">正在读取历史扫描…</p>}
    {!snapshot && !selected && !error && <p className="research-scan-empty" role="status">
      {active ? '首次扫描正在整理数据，完成后会自动展示。' : loading ? '正在读取扫描…' : '暂无已完成的扫描。'}
    </p>}
    {snapshot && <>
      <div className="research-scan-tabs" role="tablist" aria-label="扫描分析类别">
        {tabs.map((label, index) => <button key={label} type="button" role="tab" aria-selected={tab === label}
          id={`research-tab-${label}`} aria-controls="research-scan-content" onClick={() => setTab(label)}>
          {label}<span aria-hidden="true">{tabCounts[index]}</span>
        </button>)}
      </div>
      <div className="research-scan-content" id="research-scan-content" role="tabpanel" aria-labelledby={`research-tab-${tab}`} tabIndex={0}>
        {tab === '研究观察' && <WatchlistResearchObservations key={run?.id} snapshot={snapshot}
          stockLink={stockLink} stockName={stockName} />}
        {tab === '强弱分解' && (snapshot.strengths.length ? <table>
          <caption>最近20个完整交易区间 · 以此前60—120日拟合沪深300统计基线</caption>
          <thead><tr><th>股票</th><th>实际涨跌</th><th>统计基线</th><th>偏离</th><th>Beta / 拟合度</th><th>高于基线</th><th>评价区间</th></tr></thead>
          <tbody>{snapshot.strengths.map((stock) => <tr key={stock.code}>
            <th>{stockLink(stock.code, stock.name)}{stock.gaps.length > 0 && <small className="research-row-gap">待补充</small>}</th>
            <td>{number(stock.actualReturnPct, '%', true)}</td><td>{number(stock.baselineReturnPct, '%', true)}</td>
            <td className={stock.excessReturnPp != null && stock.excessReturnPp > 0 ? 'research-positive' : ''}>{number(stock.excessReturnPp, ' pp', true)}</td>
            <td>{number(stock.beta)} / {number(stock.fitQuality == null ? null : stock.fitQuality * 100, '%')}</td>
            <td>{stock.evaluationSamples ? `${stock.positiveResidualDays}/${stock.evaluationSamples} 日` : '—'}</td>
            <td><small>{stock.evaluatedFrom || '—'}<br />{stock.evaluatedThrough || ''}</small></td>
          </tr>)}</tbody>
        </table> : <p className="research-scan-empty">尚无足够的共同日收益样本。</p>)}
        {tab === '隐藏联动' && (snapshot.relationships.length ? <table>
          <caption>最近60个交易区间 · 至少50个共同日收益样本 · 紧密联动同时要求原始相关≥0.75、残差相关≥0.50</caption>
          <thead><tr><th>股票组合</th><th>原始相关</th><th>扣除大盘后</th><th>大盘下跌日</th><th>样本 / 区间</th></tr></thead>
          <tbody>{snapshot.relationships.map((pair) => <tr key={`${pair.leftCode}-${pair.rightCode}`}>
            <th><div className="research-pair">{stockLink(pair.leftCode, pair.leftName)}<span>＋</span>{stockLink(pair.rightCode, pair.rightName)}</div>{pair.strong && <small className="research-row-gap">紧密联动</small>}</th>
            <td>{number(pair.correlation)}</td><td>{number(pair.residualCorrelation)}</td>
            <td>{number(pair.downsideCorrelation)}<small> {pair.downsideSamples} 日</small></td>
            <td><small>{pair.samples} 日 · {pair.from}<br />{pair.through}</small></td>
          </tr>)}</tbody>
        </table> : <p className="research-scan-empty">暂无可比较的股票组合，需要至少两只股票和足够的同期日线。</p>)}
        {tab === '增长质量' && (snapshot.growthQuality.length ? <table>
          <caption>滚动四季度与此前四季度对比 · 现金转化为经营现金流 / 归母净利润</caption>
          <thead><tr><th>股票</th><th>收入增长</th><th>利润增长</th><th>现金转化</th><th>毛利率 / 同比变化</th><th>财务区间</th></tr></thead>
          <tbody>{snapshot.growthQuality.map((stock) => <tr key={stock.code}>
            <th>{stockLink(stock.code, stock.name)}{stock.gaps.length > 0 && <small className="research-row-gap">有数据缺口</small>}</th>
            <td>{number(stock.revenueGrowthPct, '%', true)}</td><td>{number(stock.profitGrowthPct, '%', true)}</td>
            <td>{number(stock.cashConversion, ' 倍')}</td><td>{number(stock.grossMarginPct, '%')} / {number(stock.grossMarginChangePp, ' pp', true)}</td>
            <td><small>{stock.periodFrom || '—'}<br />{stock.periodThrough || ''}</small></td>
          </tr>)}</tbody>
        </table> : <p className="research-scan-empty">财报正在补齐，取得连续季度数据后会自动计算。</p>)}
      </div>
    </>}
  </section>;
}
