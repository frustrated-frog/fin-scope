import { useEffect, useRef, useState } from 'react';

import { api } from '../../shared/api/client';
import { DataQualityNotice } from '../../shared/components/DataQualityNotice';
import { CapitalAgentInterpretationPanel } from './CapitalAgentInterpretationPanel';
import { CapitalBehaviorPanel } from './CapitalBehaviorPanel';
import { CapitalResearchBridge } from './CapitalResearchBridge';
import { CapitalRuleExplanationCard } from './CapitalRuleExplanationCard';
import { CapitalHistoricalEvaluationCard } from './CapitalHistoricalEvaluationCard';
import { DragonTigerPanel } from './DragonTigerPanel';
import { capitalAgentStatusMessage } from './agentWaitPresentation';
import { shouldAutoRefreshDragonTiger } from './dragonTigerRefreshPolicy';
import {
  marketDataProviderLabel,
  marketDataStatusLabel,
  marketIntelWarningMessages
} from './marketIntelPresentation';
import {
  CapitalInterpretation,
  DragonTigerView,
  MarketIntelCapitalOverview,
  MarketIntelInstrument,
  MarketIntelRefreshRun
} from './marketIntelTypes';
import { QuantResearchEntryIntent } from '../strategy/quantTypes';

const POLL_DELAY_MS = 650;
const AGENT_MAX_POLL_ATTEMPTS = 185;
const TERMINAL_AGENT_STATUSES = new Set(['SUCCEEDED', 'FALLBACK', 'INSUFFICIENT_DATA', 'FAILED']);
type RefreshMode = 'manual' | 'auto';
type AutoRefreshState = { instrumentId: number; selectionVersion: number } | null;

function delay(milliseconds: number) {
  return new Promise((resolve) => window.setTimeout(resolve, milliseconds));
}

export function MarketIntelView({
  addToast,
  setMessage,
  onOpenQuantResearch
}: {
  addToast: (message: string, type?: 'success' | 'error' | 'info') => void;
  setMessage: (message: string) => void;
  onOpenQuantResearch?: (intent: QuantResearchEntryIntent) => void;
}) {
  const [instruments, setInstruments] = useState<MarketIntelInstrument[]>([]);
  const [instrumentId, setInstrumentId] = useState<number | null>(null);
  const [overview, setOverview] = useState<MarketIntelCapitalOverview | null>(null);
  const [dragonTiger, setDragonTiger] = useState<DragonTigerView | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [dragonTigerError, setDragonTigerError] = useState<string | null>(null);
  const [autoRefreshState, setAutoRefreshState] = useState<AutoRefreshState>(null);
  const [autoRefreshError, setAutoRefreshError] = useState<string | null>(null);
  const [interpretation, setInterpretation] = useState<CapitalInterpretation | null>(null);
  const [agentBusy, setAgentBusy] = useState(false);
  const [agentLoadError, setAgentLoadError] = useState<string | null>(null);
  const selectionVersion = useRef(0);

  async function fetchOverview(id: number) {
    return api<MarketIntelCapitalOverview>(
      `/api/market-intel/instruments/${id}/capital-behavior?range=20d&granularity=5m`
    );
  }

  async function fetchDragonTiger(id: number) {
    return api<DragonTigerView>(
      `/api/market-intel/instruments/${id}/dragon-tiger?days=120`
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
    setAgentBusy(false);
    setAgentLoadError(null);
    setOverview(null);
    setDragonTiger(null);
    setAutoRefreshError(null);
    Promise.allSettled([
      fetchOverview(instrumentId),
      fetchDragonTiger(instrumentId),
      api<CapitalInterpretation>(`/api/market-intel/instruments/${instrumentId}/capital-interpretations/latest`)
    ])
      .then(([capitalResult, dragonTigerResult, agentResult]) => {
        if (version !== selectionVersion.current) return;
        if (capitalResult.status === 'fulfilled') {
          setOverview(capitalResult.value);
          setLoadError(null);
        } else {
          setLoadError(capitalResult.reason instanceof Error
            ? capitalResult.reason.message : '资金数据加载失败');
        }
        if (agentResult.status === 'fulfilled') {
          setInterpretation(agentResult.value);
          setAgentLoadError(null);
          if (!TERMINAL_AGENT_STATUSES.has(agentResult.value.status)) {
            void resumeAgent(agentResult.value, instrumentId, version);
          }
        } else {
          const status = (agentResult.reason as { status?: number } | undefined)?.status;
          if (status != null && status !== 404) {
            setAgentLoadError(agentResult.reason instanceof Error
              ? agentResult.reason.message : '最近一次 Agent 解读加载失败');
          }
        }
        if (dragonTigerResult.status === 'fulfilled') {
          const value = dragonTigerResult.value;
          setDragonTiger(value);
          setDragonTigerError(null);
          if (shouldAutoRefreshDragonTiger(value, new Date())) {
            void refreshMarketData(instrumentId, 'auto', version);
          }
        } else {
          setDragonTigerError(dragonTigerResult.reason instanceof Error
            ? dragonTigerResult.reason.message : '龙虎榜数据加载失败');
        }
      })
      .finally(() => {
        if (version === selectionVersion.current) {
          setLoading(false);
        }
      });
  }, [instrumentId]);

  async function refreshMarketData(targetInstrumentId: number, mode: RefreshMode, version: number) {
    const automatic = mode === 'auto';
    if (automatic) {
      setAutoRefreshState({ instrumentId: targetInstrumentId, selectionVersion: version });
      setAutoRefreshError(null);
    } else {
      setRefreshing(true);
      setAutoRefreshError(null);
      setMessage('正在刷新市场数据');
    }
    try {
      let run = await api<MarketIntelRefreshRun>(
        `/api/market-intel/instruments/${targetInstrumentId}/refresh`,
        { method: 'POST' }
      );
      for (let attempt = 0; attempt < 20 && !['SUCCEEDED', 'PARTIAL', 'FAILED'].includes(run.status); attempt++) {
        await delay(POLL_DELAY_MS);
        run = await api<MarketIntelRefreshRun>(`/api/market-intel/refresh-runs/${run.id}`);
      }
      if (run.status === 'FAILED') throw new Error(run.errorMessage || '市场数据刷新失败，原有快照已保留');
      if (!['SUCCEEDED', 'PARTIAL'].includes(run.status)) throw new Error('刷新任务仍在运行，可稍后再查看');
      const [latestCapital, latestDragonTiger] = await Promise.allSettled([
        fetchOverview(targetInstrumentId),
        fetchDragonTiger(targetInstrumentId)
      ]);
      if (version === selectionVersion.current) {
        if (latestCapital.status === 'fulfilled') {
          setOverview(latestCapital.value);
          setLoadError(null);
        } else {
          setLoadError(latestCapital.reason instanceof Error
            ? latestCapital.reason.message : '资金数据读取失败');
        }
        if (latestDragonTiger.status === 'fulfilled') {
          setDragonTiger(latestDragonTiger.value);
          setDragonTigerError(null);
        } else {
          setDragonTigerError(latestDragonTiger.reason instanceof Error
            ? latestDragonTiger.reason.message : '龙虎榜数据读取失败');
        }
        // Agent 结论严格绑定生成它的快照；新快照加载后必须由用户重新运行。
        setInterpretation(null);
      }
      if (latestCapital.status === 'rejected' && latestDragonTiger.status === 'rejected') {
        throw new Error('刷新已完成，但两个事实维度都读取失败');
      }
      if (automatic && latestDragonTiger.status === 'rejected') {
        throw new Error(latestDragonTiger.reason instanceof Error
          ? latestDragonTiger.reason.message : '龙虎榜数据读取失败');
      }
      if (automatic) return;
      const readPartial = latestCapital.status === 'rejected' || latestDragonTiger.status === 'rejected';
      const message = readPartial
        ? '市场数据已刷新，但部分页面数据读取失败'
        : run.status === 'PARTIAL'
          ? (run.errorMessage || '市场数据已刷新，部分维度暂不可用')
          : '市场数据已刷新';
      setMessage(message);
      addToast(message, run.status === 'PARTIAL' || readPartial ? 'info' : 'success');
    } catch (error) {
      const message = error instanceof Error ? error.message : '市场数据刷新失败';
      if (automatic) {
        if (version === selectionVersion.current) {
          setAutoRefreshError(`后台更新失败：${message}。已保留当前龙虎榜数据，可手动重试。`);
        }
      } else {
        setMessage(message);
        addToast(message, 'error');
      }
    } finally {
      if (automatic) {
        setAutoRefreshState((current) =>
          current?.instrumentId === targetInstrumentId
          && current.selectionVersion === version ? null : current);
      } else {
        setRefreshing(false);
      }
    }
  }

  async function refreshCapitalData() {
    if (!instrumentId || refreshing || autoRefreshState?.instrumentId === instrumentId) return;
    await refreshMarketData(instrumentId, 'manual', selectionVersion.current);
  }

  async function runAgent() {
    if (!instrumentId || agentBusy) return;
    const targetInstrumentId = instrumentId;
    const version = selectionVersion.current;
    setAgentBusy(true);
    setAgentLoadError(null);
    setMessage('Agent 正在解读资金行为');
    try {
      let value = await api<CapitalInterpretation>(
        `/api/market-intel/instruments/${targetInstrumentId}/capital-interpretations?force=${interpretation ? 'true' : 'false'}`,
        { method: 'POST' }
      );
      if (version !== selectionVersion.current) return;
      setInterpretation(value);
      value = await pollAgent(value, targetInstrumentId, version);
      if (version !== selectionVersion.current) return;
      if (!TERMINAL_AGENT_STATUSES.has(value.status)) throw new Error('Agent 仍在运行，可稍后重新打开查看');
      presentAgentOutcome(value, true);
    } catch (error) {
      if (version !== selectionVersion.current) return;
      const message = error instanceof Error ? error.message : 'Agent 解读失败';
      setMessage(message);
      addToast(message, 'error');
    } finally {
      if (version === selectionVersion.current) setAgentBusy(false);
    }
  }

  async function resumeAgent(initial: CapitalInterpretation, targetInstrumentId: number, version: number) {
    if (version !== selectionVersion.current) return;
    setAgentBusy(true);
    setMessage('正在恢复上次 Agent 解读');
    try {
      const value = await pollAgent(initial, targetInstrumentId, version);
      if (version !== selectionVersion.current) return;
      if (TERMINAL_AGENT_STATUSES.has(value.status)) presentAgentOutcome(value, false);
    } catch (error) {
      if (version !== selectionVersion.current) return;
      setAgentLoadError(error instanceof Error ? error.message : 'Agent 解读恢复失败');
    } finally {
      if (version === selectionVersion.current) setAgentBusy(false);
    }
  }

  async function pollAgent(initial: CapitalInterpretation, targetInstrumentId: number, version: number) {
    let value = initial;
    let consecutiveFailures = 0;
    for (let attempt = 0; attempt < AGENT_MAX_POLL_ATTEMPTS && !TERMINAL_AGENT_STATUSES.has(value.status); attempt++) {
      if (version !== selectionVersion.current) return value;
      const retryDelay = Math.min(POLL_DELAY_MS * Math.pow(2, consecutiveFailures), 10_000);
      await delay(retryDelay);
      if (version !== selectionVersion.current) return value;
      try {
        value = await api<CapitalInterpretation>(`/api/market-intel/capital-interpretations/${value.id}`);
        consecutiveFailures = 0;
        if (version === selectionVersion.current) {
          setInterpretation(value);
          setAgentLoadError(null);
        }
      } catch (error) {
        consecutiveFailures += 1;
        if (version === selectionVersion.current) {
          setAgentLoadError(`Agent 状态连接中断，正在重试（${targetInstrumentId}）`);
        }
      }
    }
    return value;
  }

  function presentAgentOutcome(value: CapitalInterpretation, notify: boolean) {
    const explicitMessage = value.fallbackReason
      ? capitalAgentStatusMessage(value.fallbackReason)
      : null;
    if (value.status === 'FAILED') {
      const message = explicitMessage || 'Agent 解读失败';
      setMessage(message);
      if (notify) addToast(message, 'error');
    } else if (value.status === 'FALLBACK' || value.status === 'INSUFFICIENT_DATA') {
      const message = explicitMessage || (value.status === 'FALLBACK'
        ? '模型不可用，已自动展示规则解读。'
        : '有效数据不足，未调用模型。');
      setMessage(message);
      if (notify) addToast(message, 'info');
    } else {
      setMessage('Agent 解读完成');
    }
  }

  async function retryOverview() {
    if (!instrumentId) return;
    setLoading(true);
    try {
      const [capitalResult, dragonTigerResult] = await Promise.allSettled([
        fetchOverview(instrumentId),
        fetchDragonTiger(instrumentId)
      ]);
      if (capitalResult.status === 'fulfilled') {
        setOverview(capitalResult.value);
        setLoadError(null);
      } else {
        setLoadError(capitalResult.reason instanceof Error
          ? capitalResult.reason.message : '资金数据读取失败');
      }
      if (dragonTigerResult.status === 'fulfilled') {
        setDragonTiger(dragonTigerResult.value);
        setDragonTigerError(null);
      } else {
        setDragonTigerError(dragonTigerResult.reason instanceof Error
          ? dragonTigerResult.reason.message : '龙虎榜数据读取失败');
      }
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
          <button
            className="ghost-button"
            type="button"
            disabled={refreshing || autoRefreshState?.instrumentId === instrumentId || !instrumentId}
            onClick={refreshCapitalData}
          >
            {refreshing || autoRefreshState?.instrumentId === instrumentId
              ? '刷新任务执行中…' : '刷新市场数据'}
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

      {loading && <p className="market-intel-loading" role="status">正在读取市场情报…</p>}
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
      {!loading && dragonTigerError && !dragonTiger && (
        <section className="market-intel-first-run" role="alert">
          <div><strong>龙虎榜数据读取失败</strong><p>{dragonTigerError}</p></div>
          <button className="primary-button" type="button" onClick={retryOverview}>重新读取</button>
        </section>
      )}
      {overview?.snapshot && overview.ruleExplanation && (
        <div className="market-intel-grid">
          <div className="market-intel-primary">
            <CapitalBehaviorPanel overview={overview} />
            <CapitalResearchBridge overview={overview} addToast={addToast} onOpenQuantResearch={onOpenQuantResearch} />
          </div>
          <aside className="market-intel-interpretation">
            {agentLoadError && <p className="market-intel-agent-notice" role="alert">{agentLoadError}</p>}
            <CapitalRuleExplanationCard explanation={overview.ruleExplanation} />
            <CapitalHistoricalEvaluationCard
              evaluation={overview.historicalEvaluation ?? null}
              currentSignalTypes={overview.metrics?.objectiveTags.map((tag) => tag.code) ?? []}
            />
            <CapitalAgentInterpretationPanel
              interpretation={interpretation}
              factorObservations={overview.factorObservations ?? []}
              historicalEvaluations={overview.historicalEvaluation?.signals ?? []}
              evaluationVersion={overview.historicalEvaluation?.evaluationVersion}
              watchConditions={overview.watchConditions ?? []}
              busy={agentBusy}
              onRun={runAgent}
            />
          </aside>
        </div>
      )}
      {dragonTiger && (
        <DragonTigerPanel
          view={dragonTiger}
          refreshing={autoRefreshState?.instrumentId === instrumentId}
          refreshError={autoRefreshError}
        />
      )}
    </div>
  );
}
