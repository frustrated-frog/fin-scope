import { useEffect, useMemo, useState } from 'react';
import { ApiError, api } from '../../shared/api/client';
import { QuantDataset, QuantStrategyCandidate, QuantStrategyCatalogSource, QuantStrategyCatalogSyncResult, QuantStrategyCompatibilityStatus, QuantStrategyDraft } from './quantTypes';

type Toast = (message: string, type?: 'success' | 'error' | 'info') => void;
type Filter = 'ALL' | QuantStrategyCompatibilityStatus;

const filterOptions: Array<{ id: Filter; label: string }> = [
  { id: 'ALL', label: '全部候选' },
  { id: 'ADAPTABLE', label: '可适配' },
  { id: 'NEEDS_FACTOR', label: '缺少因子' },
  { id: 'UNSUPPORTED', label: '暂不支持' }
];
const statusCopy: Record<QuantStrategyCompatibilityStatus, { label: string; hint: string }> = {
  ADAPTABLE: { label: '可适配', hint: '可形成 FinScope 本地版本' },
  NEEDS_FACTOR: { label: '缺少因子', hint: '先补数据或公式能力' },
  UNSUPPORTED: { label: '暂不支持', hint: '超出当前回测引擎边界' }
};

export function StrategyCatalogPanel({ datasets, addToast, onDraftCreated }: {
  datasets: QuantDataset[];
  addToast: Toast;
  onDraftCreated: (draft: QuantStrategyDraft) => void;
}) {
  const [source, setSource] = useState<QuantStrategyCatalogSource>();
  const [candidates, setCandidates] = useState<QuantStrategyCandidate[]>([]);
  const [selectedId, setSelectedId] = useState<number>();
  const [filter, setFilter] = useState<Filter>('ALL');
  const [query, setQuery] = useState('');
  const [datasetId, setDatasetId] = useState<number | ''>(() => datasets.find(item => item.status === 'READY')?.id ?? '');
  const [busy, setBusy] = useState<'load' | 'sync' | 'draft' | undefined>('load');
  const [loadError, setLoadError] = useState<string>();

  async function load(showError = false) {
    setBusy('load');
    try {
      const [sourceValue, candidateValues] = await Promise.all([
        api<QuantStrategyCatalogSource>('/api/quant/catalog/source'),
        api<QuantStrategyCandidate[]>('/api/quant/catalog/candidates')
      ]);
      setSource(sourceValue); setCandidates(candidateValues); setLoadError(undefined);
      setSelectedId(current => candidateValues.some(item => item.id === current) ? current : candidateValues[0]?.id);
    } catch (error) {
      if (!(error instanceof ApiError && error.status === 404)) {
        const message = error instanceof Error ? error.message : '策略素材库读取失败';
        setLoadError(message); if (showError) addToast(message, 'error');
      }
    } finally { setBusy(current => current === 'load' ? undefined : current); }
  }

  useEffect(() => { load(); }, []);
  useEffect(() => {
    if (datasetId === '') setDatasetId(datasets.find(item => item.status === 'READY')?.id ?? '');
  }, [datasets]);

  const visible = useMemo(() => {
    const normalized = query.trim().toLocaleLowerCase();
    return candidates.filter(item => (filter === 'ALL' || item.compatibilityStatus === filter)
      && (!normalized || item.title.toLocaleLowerCase().includes(normalized)));
  }, [candidates, filter, query]);
  const selected = candidates.find(item => item.id === selectedId);

  function chooseFilter(value: Filter) {
    setFilter(value);
    const first = candidates.find(item => value === 'ALL' || item.compatibilityStatus === value);
    if (first) setSelectedId(first.id);
  }

  async function sync() {
    setBusy('sync');
    try {
      const result = await api<QuantStrategyCatalogSyncResult>('/api/quant/catalog/sync', { method: 'POST' });
      addToast(`已同步 ${result.importedCount} 条策略素材`, 'success');
      await load(true);
    } catch (error) {
      addToast(error instanceof Error ? error.message : '策略目录同步失败，旧快照仍可使用', 'error');
      setBusy(undefined);
    }
  }

  async function createDraft() {
    if (!selected || datasetId === '' || selected.compatibilityStatus === 'UNSUPPORTED') return;
    setBusy('draft');
    try {
      const draft = await api<QuantStrategyDraft>(`/api/quant/catalog/candidates/${selected.id}/drafts`, {
        method: 'POST', body: JSON.stringify({ datasetId })
      });
      onDraftCreated(draft);
      addToast(draft.status === 'VALIDATED' ? '候选已转为可确认草案' : '候选草案未通过协议，已保留问题',
        draft.status === 'VALIDATED' ? 'success' : 'error');
    } catch (error) { addToast(error instanceof Error ? error.message : '策略候选转换失败', 'error'); }
    finally { setBusy(undefined); }
  }

  const readyDatasets = datasets.filter(item => item.status === 'READY');
  return <section className="quant-catalog">
    <header className="quant-catalog-source">
      <div className="quant-source-trace" aria-label="策略素材来源路径">
        <span>GitHub</span><i/><span>{source?.commitSha ? source.commitSha.slice(0, 8) : '未同步'}</span><i/><span>目录快照</span><i/><span>{candidates.length} 个候选</span><i/><span>本地草案</span>
      </div>
      <div className="quant-source-action">
        <p>{source ? `上次同步 ${new Date(source.lastSyncedAt).toLocaleString('zh-CN')}` : '同步公开目录后开始筛选；不会下载或执行上游代码。'}</p>
        <button type="button" onClick={sync} disabled={busy === 'sync'}>{busy === 'sync' ? '正在校对目录…' : source ? '重新同步目录' : '同步策略目录'}</button>
      </div>
    </header>

    {loadError && <div className="quant-catalog-error"><strong>目录读取失败</strong><span>{loadError}</span><button type="button" onClick={() => load(true)}>重试读取</button></div>}
    {!source && busy !== 'load' && candidates.length === 0 ? <div className="quant-catalog-empty"><span>DISCOVERY SOURCE</span><strong>把公开策略目录变成研究候选</strong><p>同步只保存标题、来源指标和论文链接。所有候选仍需经过本地因子映射、数据门禁与人工确认。</p><button type="button" onClick={sync}>同步策略目录</button></div> : <>
      <div className="quant-catalog-toolbar">
        <div className="quant-catalog-filters">{filterOptions.map(option => <button type="button" key={option.id} className={filter === option.id ? 'active' : ''} onClick={() => chooseFilter(option.id)}>{option.label}<small>{option.id === 'ALL' ? candidates.length : candidates.filter(item => item.compatibilityStatus === option.id).length}</small></button>)}</div>
        <label><span>搜索候选</span><input value={query} onChange={event => setQuery(event.target.value)} placeholder="动量、低波、价值…" /></label>
      </div>

      <div className="quant-catalog-grid">
        <aside className="quant-catalog-index">
          <header><span>RESEARCH INDEX</span><strong>{visible.length} 条匹配</strong></header>
          <div className="quant-candidate-list">{visible.map(item => <button type="button" key={item.id} className={selectedId === item.id ? 'active' : ''} onClick={() => setSelectedId(item.id)}>
            <span data-status={item.compatibilityStatus}>{statusCopy[item.compatibilityStatus].label}</span>
            <strong>{item.title}</strong>
            <small>{item.rebalanceCadence ?? '频率未注明'} · {item.mappedFactors[0] ?? item.missingFactors[0] ?? '待拆解'}</small>
          </button>)}{visible.length === 0 && <p>没有匹配候选，试试清除搜索或切换状态。</p>}</div>
        </aside>

        <main className="quant-catalog-evidence">{selected ? <>
          <header><div><span>SOURCE CLAIM / LOCAL BOUNDARY</span><h4>{selected.title}</h4><p>{statusCopy[selected.compatibilityStatus].hint}</p></div><b data-status={selected.compatibilityStatus}>{statusCopy[selected.compatibilityStatus].label}</b></header>
          <section className="quant-catalog-claims"><header><h5>来源记录</h5><span>不代表本地验证结果</span></header><dl><div><dt>Sharpe</dt><dd>{selected.reportedSharpe == null ? 'N/A' : selected.reportedSharpe.toFixed(3)}</dd></div><div><dt>波动率</dt><dd>{selected.reportedVolatility == null ? 'N/A' : `${(selected.reportedVolatility * 100).toFixed(1)}%`}</dd></div><div><dt>调仓</dt><dd>{selected.rebalanceCadence ?? '未注明'}</dd></div></dl><p>来源记录，不代表本地验证结果</p></section>
          <section className="quant-catalog-adaptation"><span>LOCAL ADAPTATION</span><h5>本地适配口径</h5><p>{selected.adaptationNote}</p><div>{selected.mappedFactors.map(item => <code key={item}>{item}</code>)}{selected.missingFactors.map(item => <code className="missing" key={item}>缺 {item}</code>)}{selected.mappedFactors.length + selected.missingFactors.length === 0 && <code className="missing">待人工拆解</code>}</div></section>
          <section className="quant-catalog-links"><a href={selected.paperUrl} aria-disabled={!selected.paperUrl} target="_blank" rel="noreferrer">查看论文来源</a><a href={selected.implementationUrl} aria-disabled={!selected.implementationUrl} target="_blank" rel="noreferrer">查看上游实现</a><small>快照 {selected.sourceCommitSha.slice(0, 8)}</small></section>
          <footer><label><span>绑定研究数据集</span><select value={datasetId} onChange={event => setDatasetId(event.target.value ? Number(event.target.value) : '')}><option value="">选择已通过质量门禁的数据集</option>{readyDatasets.map(item => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label><button type="button" onClick={createDraft} disabled={selected.compatibilityStatus === 'UNSUPPORTED' || datasetId === '' || busy === 'draft'}>{selected.compatibilityStatus === 'UNSUPPORTED' ? '当前引擎不可生成' : busy === 'draft' ? '正在生成受限草案…' : '生成本地策略草案'}</button></footer>
        </> : <div className="quant-catalog-no-selection">从左侧选择一条候选，查看来源口径与本地适配差异。</div>}</main>
      </div>
    </>}
  </section>;
}
