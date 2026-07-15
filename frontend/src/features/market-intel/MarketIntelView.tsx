import { useEffect, useRef, useState } from 'react';

import { api } from '../../shared/api/client';
import { DataQualityNotice } from '../../shared/components/DataQualityNotice';
import { CapitalAgentInterpretationPanel } from './CapitalAgentInterpretationPanel';
import { CapitalBehaviorPanel } from './CapitalBehaviorPanel';
import { CapitalRuleExplanationCard } from './CapitalRuleExplanationCard';
import { capitalAgentStatusMessage } from './agentWaitPresentation';
import {
  marketDataProviderLabel,
  marketDataStatusLabel,
  marketIntelWarningMessages
} from './marketIntelPresentation';
import {
  CapitalInterpretation,
  MarketIntelCapitalOverview,
  MarketIntelInstrument,
  MarketIntelRefreshRun
} from './marketIntelTypes';

const POLL_DELAY_MS = 650;
const AGENT_MAX_POLL_ATTEMPTS = 185;
const TERMINAL_AGENT_STATUSES = new Set(['SUCCEEDED', 'FALLBACK', 'INSUFFICIENT_DATA', 'FAILED']);
function delay(milliseconds: number) {
  return new Promise((resolve) => window.setTimeout(resolve, milliseconds));
}

export function MarketIntelView({
  addToast,
  setMessage
}: {
  addToast: (message: string, type?: 'success' | 'error' | 'info') => void;
  setMessage: (message: string) => void;
}) {
  const [instruments, setInstruments] = useState<MarketIntelInstrument[]>([]);
  const [instrumentId, setInstrumentId] = useState<number | null>(null);
  const [overview, setOverview] = useState<MarketIntelCapitalOverview | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [interpretation, setInterpretation] = useState<CapitalInterpretation | null>(null);
  const [agentBusy, setAgentBusy] = useState(false);
  const selectionVersion = useRef(0);

  async function fetchOverview(id: number) {
    return api<MarketIntelCapitalOverview>(
      `/api/market-intel/instruments/${id}/capital-behavior?range=20d&granularity=5m`
    );
  }

  useEffect(() => {
    let cancelled = false;
    api<MarketIntelInstrument[]>('/api/market-intel/instruments')
      .then((values) => {
        if (cancelled) return;
        setInstruments(values);
        setInstrumentId((current) => current ?? values[0]?.id ?? null);
        if (!values.length) setLoading(false);
      })
      .catch((error) => {
        if (cancelled) return;
        const message = error instanceof Error ? error.message : '标的列表加载失败';
        setLoadError(message);
        setLoading(false);
        setMessage(message);
      });
    return () => { cancelled = true; };
  }, []);

  useEffect(() => {
    if (!instrumentId) return;
    const version = ++selectionVersion.current;
    setLoading(true);
    setInterpretation(null);
    fetchOverview(instrumentId)
      .then((value) => {
        if (version !== selectionVersion.current) return;
        setOverview(value);
        setLoadError(null);
      })
      .catch((error) => {
        if (version !== selectionVersion.current) return;
        setOverview(null);
        setLoadError(error instanceof Error ? error.message : '资金数据加载失败');
      })
      .finally(() => {
        if (version === selectionVersion.current) setLoading(false);
      });
  }, [instrumentId]);

  async function refreshCapitalData() {
    if (!instrumentId || refreshing) return;
    setRefreshing(true);
    setMessage('正在刷新资金数据');
    try {
      let run = await api<MarketIntelRefreshRun>(
        `/api/market-intel/instruments/${instrumentId}/refresh`,
        { method: 'POST' }
      );
      for (let attempt = 0; attempt < 20 && !['SUCCEEDED', 'PARTIAL', 'FAILED'].includes(run.status); attempt++) {
        await delay(POLL_DELAY_MS);
        run = await api<MarketIntelRefreshRun>(`/api/market-intel/refresh-runs/${run.id}`);
      }
      if (run.status === 'FAILED') throw new Error(run.errorMessage || '资金源刷新失败，原有快照已保留');
      if (!['SUCCEEDED', 'PARTIAL'].includes(run.status)) throw new Error('刷新任务仍在运行，可稍后再查看');
      const latest = await fetchOverview(instrumentId);
      setOverview(latest);
      setLoadError(null);
      const message = run.status === 'PARTIAL' ? (run.errorMessage || '资金流已刷新，部分辅助行情暂不可用') : '资金数据已刷新';
      setMessage(message);
      addToast(message, run.status === 'PARTIAL' ? 'info' : 'success');
    } catch (error) {
      const message = error instanceof Error ? error.message : '资金数据刷新失败';
      setMessage(message);
      addToast(message, 'error');
    } finally {
      setRefreshing(false);
    }
  }

  async function runAgent() {
    if (!instrumentId || agentBusy) return;
    setAgentBusy(true);
    setMessage('Agent 正在解读资金行为');
    try {
      let value = await api<CapitalInterpretation>(
        `/api/market-intel/instruments/${instrumentId}/capital-interpretations?force=true`,
        { method: 'POST' }
      );
      setInterpretation(value);
      for (let attempt = 0; attempt < AGENT_MAX_POLL_ATTEMPTS && !TERMINAL_AGENT_STATUSES.has(value.status); attempt++) {
        await delay(POLL_DELAY_MS);
        value = await api<CapitalInterpretation>(`/api/market-intel/capital-interpretations/${value.id}`);
        setInterpretation(value);
      }
      if (!TERMINAL_AGENT_STATUSES.has(value.status)) throw new Error('Agent 仍在运行，可稍后重新打开查看');
      const explicitMessage = value.fallbackReason
        ? capitalAgentStatusMessage(value.fallbackReason)
        : null;
      if (value.status === 'FAILED') {
        const message = explicitMessage || 'Agent 解读失败';
        setMessage(message);
        addToast(message, 'error');
      } else if (value.status === 'FALLBACK' || value.status === 'INSUFFICIENT_DATA') {
        const message = explicitMessage || (value.status === 'FALLBACK'
          ? '模型不可用，已自动展示规则解读。'
          : '有效数据不足，未调用模型。');
        setMessage(message);
        addToast(message, 'info');
      } else {
        setMessage('Agent 解读完成');
      }
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Agent 解读失败';
      setMessage(message);
      addToast(message, 'error');
    } finally {
      setAgentBusy(false);
    }
  }

  async function retryOverview() {
    if (!instrumentId) return;
    setLoading(true);
    try {
      const value = await fetchOverview(instrumentId);
      setOverview(value);
      setLoadError(null);
    } catch (error) {
      setLoadError(error instanceof Error ? error.message : '资金数据读取失败');
    } finally {
      setLoading(false);
    }
  }

  if (!instruments.length && !loading) {
    return <section className="panel wide market-intel-empty-page"><h3>还没有可分析的 A 股标的</h3><p>先在自选页添加股票，再回到这里刷新资金数据。</p></section>;
  }

  const healthWarnings = marketIntelWarningMessages(overview?.health.warnings ?? []);

  return (
    <div className="market-intel-page">
      <section className="market-intel-hero">
        <div className="market-intel-hero-copy">
          <p className="market-intel-kicker">Capital behavior research desk</p>
          <h3>{overview?.instrument.name ?? instruments.find((item) => item.id === instrumentId)?.name ?? '资金行为'}</h3>
          <p>先读事实与规则，再决定是否让 Agent 展开假设。拆单、吸筹、出货都不会被包装成确定结论。</p>
        </div>
        <div className="market-intel-controls">
          <label>
            <span>研究标的</span>
            <select disabled={refreshing || agentBusy} value={instrumentId ?? ''} onChange={(event) => setInstrumentId(Number(event.target.value))}>
              {instruments.map((instrument) => (
                <option value={instrument.id} key={instrument.id}>{instrument.code} · {instrument.name}</option>
              ))}
            </select>
          </label>
          <button className="ghost-button" type="button" disabled={refreshing || !instrumentId} onClick={refreshCapitalData}>
            {refreshing ? '刷新任务执行中…' : '刷新资金数据'}
          </button>
        </div>
        {overview && (
          <div className="market-intel-health">
            <span className={overview.health.status === 'FRESH_PRIMARY' ? 'fresh'
              : overview.health.status === 'UNAVAILABLE' ? 'empty' : 'stale'}>
              {marketDataStatusLabel(overview.health.status)}
            </span>
            <dl>
              <div><dt>数据源</dt><dd>{marketDataProviderLabel(overview.health.providerCode)}</dd></div>
              <div><dt>快照时点</dt><dd>{overview.health.asOf ? new Date(overview.health.asOf).toLocaleString('zh-CN', { hour12: false }) : '--'}</dd></div>
            </dl>
          </div>
        )}
        {overview?.snapshot && (
          <DataQualityNotice quality={{
            status: overview.health.status,
            sourceCode: marketDataProviderLabel(overview.health.providerCode),
            warning: healthWarnings.join('；') || undefined
          }} />
        )}
      </section>

      {loading && <p className="market-intel-loading" role="status">正在读取资金快照…</p>}
      {!loading && loadError && !overview && (
        <section className="market-intel-first-run" role="alert">
          <div><strong>资金数据读取失败</strong><p>{loadError}</p></div>
          <button className="primary-button" type="button" onClick={retryOverview}>重新读取</button>
        </section>
      )}
      {!loading && overview && !overview.snapshot && (
        <section className="market-intel-first-run">
          <div><strong>这个标的还没有资金快照</strong><p>首次刷新后会保存成交额、换手率与资金时间线，后续解读都基于这份可追溯事实。</p></div>
          <button className="primary-button" type="button" onClick={refreshCapitalData}>生成第一份资金快照</button>
        </section>
      )}
      {overview?.snapshot && overview.ruleExplanation && (
        <div className="market-intel-grid">
          <div className="market-intel-primary">
            <CapitalBehaviorPanel overview={overview} />
          </div>
          <aside className="market-intel-interpretation">
            <CapitalRuleExplanationCard explanation={overview.ruleExplanation} />
            <CapitalAgentInterpretationPanel
              interpretation={interpretation}
              factorObservations={overview.factorObservations ?? []}
              watchConditions={overview.watchConditions ?? []}
              busy={agentBusy}
              onRun={runAgent}
            />
          </aside>
        </div>
      )}
    </div>
  );
}
