import { useEffect, useMemo, useRef, useState } from 'react';
import { ApiError, api } from '../../shared/api/client';
import {
  QuantDataset,
  QuantStrategyAcademyBuildResult,
  QuantStrategyAcademyCard,
  QuantStrategyAcademyShelf,
  QuantStrategyCatalogSource,
  QuantStrategyCatalogSyncResult
} from './quantTypes';

type Toast = (message: string, type?: 'success' | 'error' | 'info') => void;

const evidenceCopy: Record<string, string> = {
  RESEARCH_REPLICATION: '公开复现',
  HISTORICAL_EVIDENCE: '历史证据',
  FORWARD_OBSERVING: '前向观察',
  LEARNING_CASE: '学习案例'
};

const shelves: Array<{ id: 'application' | 'validating' | 'observation' | 'learning'; title: string; eyebrow: string;
  description: string; accepts: QuantStrategyAcademyShelf[] }> = [
  { id: 'application', title: '应用候选', eyebrow: 'EVIDENCE ≥ 70', description: '历史证据相对完整，下一步仍是影子观察。', accepts: ['APPLICATION_CANDIDATE'] },
  { id: 'validating', title: '验证队列', eyebrow: 'EVIDENCE PENDING', description: '实验正在运行，当前没有历史证据分。', accepts: ['VALIDATING'] },
  { id: 'observation', title: '当前观察', eyebrow: 'EVIDENCE 55+ / GATES PENDING', description: '分数已有参考价值，但至少一项应用硬门槛尚未满足。', accepts: ['OBSERVATION'] },
  { id: 'learning', title: '学习案例', eyebrow: 'LEARN FROM THE EDGE', description: '保留弱结果与失败原因，比隐藏它们更有价值。', accepts: ['LEARNING_CASE'] }
];

function percent(value?: number, signed = false) {
  if (value == null || !Number.isFinite(value)) {
    return '—';
  }
  const sign = signed && value > 0 ? '+' : '';
  return `${sign}${(value * 100).toFixed(1)}%`;
}

function cardTone(card: QuantStrategyAcademyCard) {
  if (card.shelf === 'APPLICATION_CANDIDATE') {
    return 'ready';
  }
  if (card.shelf === 'OBSERVATION' || card.shelf === 'VALIDATING') {
    return 'watch';
  }
  return 'learn';
}

export function StrategyCatalogPanel({ datasets, addToast }: { datasets: QuantDataset[]; addToast: Toast }) {
  const [source, setSource] = useState<QuantStrategyCatalogSource>();
  const [cards, setCards] = useState<QuantStrategyAcademyCard[]>([]);
  const [selectedId, setSelectedId] = useState<number>();
  const [datasetId, setDatasetId] = useState<number | ''>(() => datasets.find(item => item.status === 'READY' && item.dataKind === 'REAL')?.id ?? '');
  const [busy, setBusy] = useState<'load' | 'sync' | 'build' | undefined>('load');
  const [sourceMissing, setSourceMissing] = useState(false);
  const [loadError, setLoadError] = useState<string>();
  const loadSequence = useRef(0);
  const datasetIdRef = useRef<number | ''>(datasetId);
  datasetIdRef.current = datasetId;

  async function load(showError = false) {
    const requestSequence = ++loadSequence.current;
    const requestedDatasetId = datasetIdRef.current;
    setBusy(current => current === 'build' || current === 'sync' ? current : 'load');
    const [cardResult, sourceResult] = await Promise.allSettled([
      api<QuantStrategyAcademyCard[]>(requestedDatasetId === '' ? '/api/quant/academy/cards'
        : `/api/quant/academy/cards?datasetId=${requestedDatasetId}`),
      api<QuantStrategyCatalogSource>('/api/quant/catalog/source')
    ]);
    if (requestSequence !== loadSequence.current) {
      return;
    }
    if (cardResult.status === 'fulfilled') {
      setCards(cardResult.value);
      setLoadError(undefined);
      setSelectedId(current => cardResult.value.some(item => item.candidateId === current)
        ? current : cardResult.value[0]?.candidateId);
    } else {
      const message = cardResult.reason instanceof Error ? cardResult.reason.message : '策略学院读取失败';
      setLoadError(message);
      if (showError) {
        addToast(message, 'error');
      }
    }
    if (sourceResult.status === 'fulfilled') {
      setSource(sourceResult.value);
      setSourceMissing(false);
    } else if (sourceResult.reason instanceof ApiError && sourceResult.reason.status === 404) {
      setSource(undefined);
      setSourceMissing(true);
    } else if (showError) {
      addToast(sourceResult.reason instanceof Error ? sourceResult.reason.message : '公开策略来源读取失败', 'error');
    }
    setBusy(current => current === 'load' ? undefined : current);
  }

  useEffect(() => {
    void load();
  }, [datasetId]);

  useEffect(() => {
    if (datasetId === '') {
      setDatasetId(datasets.find(item => item.status === 'READY' && item.dataKind === 'REAL')?.id ?? '');
    }
  }, [datasets, datasetId]);

  useEffect(() => {
    const hasActive = cards.some(card => card.experimentStatus === 'QUEUED' || card.experimentStatus === 'RUNNING');
    if (!hasActive) {
      return undefined;
    }
    const timer = window.setInterval(() => {
      void load();
    }, 3500);
    return () => window.clearInterval(timer);
  }, [cards, datasetId]);

  const realDatasets = datasets.filter(item => item.status === 'READY' && item.dataKind === 'REAL');
  const selected = cards.find(item => item.candidateId === selectedId) ?? cards[0];
  const shelfCards = useMemo(() => shelves.map(shelf => ({
    ...shelf,
    cards: cards.filter(card => shelf.accepts.includes(card.shelf))
  })), [cards]);
  const historicalCount = cards.filter(card => card.evidenceLevel === 'HISTORICAL_EVIDENCE').length;

  async function sync() {
    setBusy('sync');
    try {
      const result = await api<QuantStrategyCatalogSyncResult>('/api/quant/catalog/sync', { method: 'POST' });
      addToast(`已同步 ${result.importedCount} 条公开策略线索`, 'success');
      await load(true);
    } catch (error) {
      addToast(error instanceof Error ? error.message : '公开策略目录同步失败', 'error');
    } finally {
      setBusy(undefined);
    }
  }

  async function build() {
    if (datasetId === '') {
      addToast('请先准备一份通过质量门禁的真实研究数据集', 'error');
      return;
    }
    setBusy('build');
    try {
      const result = await api<QuantStrategyAcademyBuildResult>('/api/quant/academy/build', {
        method: 'POST', body: JSON.stringify({ datasetId })
      });
      addToast(`已启动 ${result.experimentStartedCount} 个历史验证，复用 ${result.reusedCount} 个已有结果`,
        result.failedCount === result.scannedCount && result.scannedCount > 0 ? 'error' : 'success');
      await load(true);
    } catch (error) {
      addToast(error instanceof Error ? error.message : '策略学院构建失败', 'error');
    } finally {
      setBusy(undefined);
    }
  }

  return <section className="strategy-academy">
    <header className="strategy-academy-hero">
      <div className="strategy-academy-title">
        <span>FINSCOPE / STRATEGY ACADEMY</span>
        <h3>自动策略学院</h3>
        <p>从公开研究出发，用真实历史数据重新验证。先看懂策略为什么可能有效，再决定是否值得长期观察。</p>
      </div>
      <div className="strategy-academy-build">
        <label>
          <span>验证数据</span>
          <select aria-label="验证数据" value={datasetId}
            disabled={busy === 'build' || busy === 'sync'}
            onChange={event => setDatasetId(event.target.value ? Number(event.target.value) : '')}>
            <option value="">选择真实研究数据集</option>
            {realDatasets.map(item => <option key={item.id} value={item.id}>{item.name}</option>)}
          </select>
        </label>
        <button type="button" aria-label="自动构建本期学院" onClick={build}
          disabled={datasetId === '' || busy === 'build' || busy === 'sync' || sourceMissing}>
          <span>{busy === 'build' ? '正在建立验证队列…' : '自动构建本期学院'}</span>
          <small>最多 6 个候选 · 自动隔离失败</small>
        </button>
      </div>
    </header>

    <div className="strategy-academy-staircase" aria-label="策略证据阶梯">
      <article data-state={source ? 'reached' : 'waiting'}><i>01</i><div><strong>公开研究复现</strong><small>{source ? `来源快照 ${source.commitSha.slice(0, 8)}` : '等待同步公开目录'}</small></div></article>
      <span aria-hidden="true" />
      <article data-state={historicalCount > 0 ? 'reached' : cards.length > 0 ? 'active' : 'waiting'}><i>02</i><div><strong>本地历史验证</strong><small>{historicalCount ? `${historicalCount} 张卡已有真实历史证据` : '规则、成本与结果全部留痕'}</small></div></article>
      <span aria-hidden="true" />
      <article data-state="locked"><i>03</i><div><strong>前向观察</strong><small>固定规则后，用未来真实行情继续验证</small></div></article>
    </div>

    {sourceMissing && <section className="strategy-academy-source-empty">
      <div><span>SOURCE REQUIRED</span><h4>先同步公开策略目录</h4><p>系统只读取论文、来源实现与策略标题，不下载或执行外部代码。</p></div>
      <button type="button" onClick={sync} disabled={busy === 'sync' || busy === 'build'}>{busy === 'sync' ? '正在同步…' : '同步公开目录'}</button>
    </section>}

    {source && <div className="strategy-academy-source-line">
      <span>公开来源已冻结至 <code>{source.commitSha.slice(0, 8)}</code></span>
      <button type="button" onClick={sync} disabled={busy === 'sync' || busy === 'build'}>{busy === 'sync' ? '正在校对…' : '更新来源目录'}</button>
    </div>}

    {loadError && <section className="strategy-academy-error" role="alert"><strong>学院卡片读取失败</strong><span>{loadError}</span><button type="button" onClick={() => load(true)}>重新读取</button></section>}

    {!loadError && !sourceMissing && busy === 'load' && <div className="strategy-academy-loading" aria-label="策略学院加载中">正在整理来源、实验与证据等级…</div>}

    {!loadError && !sourceMissing && busy !== 'load' && cards.length === 0 && <section className="strategy-academy-empty">
      <span>THE FIRST COHORT</span><h4>还没有学院卡片</h4><p>选择真实研究数据后，系统会自动生成少量受限策略并开始历史验证。验证失败的策略也会保留为学习案例。</p>
    </section>}

    {cards.length > 0 && <div className="strategy-academy-workbench">
      <div className="strategy-academy-shelves">
        {shelfCards.map(shelf => <section className={`strategy-academy-shelf is-${shelf.id}`} key={shelf.id}>
          <header><div><span>{shelf.eyebrow}</span><h4>{shelf.title}</h4><p>{shelf.description}</p></div><b>{shelf.cards.length}</b></header>
          <div>{shelf.cards.length > 0 ? shelf.cards.map(card => <button type="button" key={card.candidateId}
            className={selected?.candidateId === card.candidateId ? 'active' : ''}
            aria-label={`${card.title}，证据分 ${card.evidenceScore}`}
            onClick={() => setSelectedId(card.candidateId)}>
            <span data-tone={cardTone(card)}>{evidenceCopy[card.evidenceLevel]}</span>
            <strong>{card.title}</strong>
            <small>{card.mappedFactors.join(' · ') || '等待因子映射'}</small>
            <i><b style={{ width: `${Math.max(3, card.evidenceScore)}%` }} /></i>
            <em>{card.evidenceScore || '—'}</em>
          </button>) : <p>本书架暂时没有卡片。</p>}</div>
        </section>)}
      </div>

      {selected && <article className="strategy-academy-dossier">
        <header>
          <div><span>{evidenceCopy[selected.evidenceLevel]} · {selected.datasetName ?? '等待本地数据'}</span><h4>{selected.title}</h4><p>{selected.evidenceSummary}</p></div>
          <div className="strategy-academy-score" data-tone={cardTone(selected)}><strong>{selected.evidenceScore || '—'}</strong><small>证据分 / 100</small></div>
        </header>

        <section className="strategy-academy-questions">
          <article><span>它赚的是什么钱？</span><p>{selected.earningLogic}</p></article>
          <article><span>为什么可能有效？</span><p>{selected.rationale}</p></article>
          <article><span>什么环境更适合？</span><p>{selected.suitableRegime}</p></article>
          <article><span>它可能怎么失效？</span><p>{selected.invalidationRisk}</p></article>
        </section>

        {selected.metrics && <section className="strategy-academy-metrics" aria-label="历史验证指标">
          <div><span>年化收益</span><strong>{percent(selected.metrics.annualizedReturn, true)}</strong></div>
          <div><span>相对基准</span><strong>{percent(selected.metrics.excessReturn, true)}</strong></div>
          <div><span>最大回撤</span><strong>{percent(selected.metrics.maxDrawdown)}</strong></div>
          <div><span>Sharpe</span><strong>{selected.metrics.sharpeRatio.toFixed(2)}</strong></div>
        </section>}

        {selected.dimensions.length > 0 && <section className="strategy-academy-dimensions">
          <header><span>EVIDENCE LEDGER</span><h5>这张分数是怎么来的</h5></header>
          {selected.dimensions.map(item => <div key={item.code}><span>{item.label}</span><i><b style={{ width: `${item.maxScore ? item.score / item.maxScore * 100 : 0}%` }} /></i><strong>{item.score}/{item.maxScore}</strong><small>{item.explanation}</small></div>)}
        </section>}

        {selected.annualEvidence.length > 0 && <section className="strategy-academy-years">
          <header><span>YEAR-BY-YEAR CHECK</span><h5>收益是否只靠某一年</h5></header>
          <div role="table" aria-label="逐年历史证据">
            <div role="row"><b role="columnheader">年度</b><b role="columnheader">策略</b>
              <b role="columnheader">基准</b><b role="columnheader">超额</b></div>
            {selected.annualEvidence.map(year => <div role="row" key={year.year}>
              <strong role="cell">{year.year}</strong><span role="cell">{percent(year.portfolioReturn, true)}</span>
              <span role="cell">{percent(year.benchmarkReturn, true)}</span>
              <em role="cell" data-positive={year.excessReturn > 0}>{percent(year.excessReturn, true)}</em>
            </div>)}
          </div>
        </section>}

        <section className="strategy-academy-provenance">
          <div><span>本地适配边界</span><p>{selected.adaptationNote || '等待形成可验证的 A 股多头本地版本。'}</p></div>
          <div><span>必须记住</span><ul>{selected.limitations.length > 0 ? selected.limitations.map(item => <li key={item}>{item}</li>) : <li>公开来源不代表本地验证，历史结果也不代表未来收益。</li>}</ul></div>
          <footer>
            {selected.paperUrl && <a href={selected.paperUrl} target="_blank" rel="noreferrer">查看公开研究</a>}
            {selected.implementationUrl && <a href={selected.implementationUrl} target="_blank" rel="noreferrer">查看来源实现</a>}
            {selected.experimentId && <small>实验 #{selected.experimentId} · 版本 #{selected.strategyVersionId}</small>}
          </footer>
        </section>
      </article>}
    </div>}
  </section>;
}
