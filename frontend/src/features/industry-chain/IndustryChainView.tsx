import { CSSProperties, FormEvent, useEffect, useMemo, useState } from 'react';

import { api } from '../../shared/api/client';
import { IndustryChainCanvas } from './IndustryChainCanvas';
import { IndustryChainInspector } from './IndustryChainInspector';
import { IndustryChainLayerBar } from './IndustryChainLayerBar';
import { IndustryChainDynamics } from './IndustryChainDynamics';
import { IndustryChainResearchPanel } from './IndustryChainResearchPanel';
import type { IndustryChain, IndustryChainEventFeed, IndustryChainEventRefreshSummary, IndustryChainLayer, IndustryChainWorkspace } from './industryChainTypes';
import './industry-chain.css';

const SUGGESTIONS = ['AI 算力', '人形机器人', '低空经济', '创新药', '新能源车', '半导体设备'];
const POLL_INTERVAL_MS = 2400;

export function IndustryChainView({
  addToast = () => undefined,
  setMessage = () => undefined,
  initialStockCode,
  onOpenNewsEvent = () => undefined
}: {
  addToast?: (message: string, type?: 'success' | 'error' | 'info') => void;
  setMessage?: (message: string) => void;
  initialStockCode?: string;
  onOpenNewsEvent?: (eventId: number) => void;
}) {
  const [chains, setChains] = useState<IndustryChain[]>([]);
  const [workspace, setWorkspace] = useState<IndustryChainWorkspace | null>(null);
  const [name, setName] = useState('');
  const [search, setSearch] = useState(initialStockCode ?? '');
  const [loading, setLoading] = useState(true);
  const [libraryCollapsed, setLibraryCollapsed] = useState(false);
  const [selectedNodeKey, setSelectedNodeKey] = useState<string>();
  const [focusMode, setFocusMode] = useState(Boolean(initialStockCode));
  const [expandedNodeKeys, setExpandedNodeKeys] = useState<Set<string>>(new Set());
  const [activeLayer, setActiveLayer] = useState<IndustryChainLayer>('STRUCTURE');
  const [viewMode, setViewMode] = useState<'panorama' | 'research' | 'dynamics'>('panorama');
  const [eventHours, setEventHours] = useState(168);
  const [eventFeed, setEventFeed] = useState<IndustryChainEventFeed>();
  const [selectedEventId, setSelectedEventId] = useState<number>();
  const [eventsLoading, setEventsLoading] = useState(false);

  const loadChains = async () => {
    const value = await api<IndustryChain[]>('/api/industry-chains');
    setChains(value);
    return value;
  };

  useEffect(() => {
    loadChains().then((values) => {
      if (initialStockCode && values[0]) void openChain(values[0]);
    }).catch(handleError).finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    if (!window.matchMedia) return undefined;
    const mobile = window.matchMedia('(max-width: 760px)');
    const expandForMobile = () => {
      if (mobile.matches) setLibraryCollapsed(false);
    };
    expandForMobile();
    mobile.addEventListener('change', expandForMobile);
    return () => mobile.removeEventListener('change', expandForMobile);
  }, []);

  useEffect(() => {
    if (!initialStockCode || !workspace?.graph) return;
    const company = workspace.graph.nodes.find((node) =>
      node.type === 'COMPANY' && node.stockCode?.toLocaleLowerCase() === initialStockCode.toLocaleLowerCase());
    if (company) {
      setSelectedNodeKey(company.nodeKey);
      setFocusMode(true);
      setExpandedNodeKeys(expansionPathToNode(workspace.graph, company.nodeKey));
    }
  }, [initialStockCode, workspace?.graph]);

  useEffect(() => {
    if (workspace?.revision?.status !== 'RUNNING') return undefined;
    let cancelled = false;
    let timer: number | undefined;
    const poll = async () => {
      try {
        const next = await api<IndustryChainWorkspace>(`/api/industry-chains/${workspace.chain.id}`);
        if (cancelled) return;
        setWorkspace(next);
        if (next.revision?.status === 'READY') {
          addToast('产业链图谱已生成', 'success');
          await loadChains();
        } else if (next.revision?.status === 'RUNNING') {
          timer = window.setTimeout(poll, POLL_INTERVAL_MS);
        }
      } catch (error) {
        handleError(error);
        if (!cancelled) timer = window.setTimeout(poll, POLL_INTERVAL_MS);
      }
    };
    timer = window.setTimeout(poll, POLL_INTERVAL_MS);
    return () => {
      cancelled = true;
      if (timer !== undefined) window.clearTimeout(timer);
    };
  }, [workspace?.revision?.status, workspace?.revision?.id]);

  const selectedNode = useMemo(() => workspace?.graph?.nodes.find(
    (node) => node.nodeKey === selectedNodeKey
  ), [selectedNodeKey, workspace?.graph]);
  const selectedEvent = eventFeed?.events.find((event) => event.eventId === selectedEventId) ?? eventFeed?.events[0];

  useEffect(() => {
    if (viewMode !== 'dynamics' || !workspace?.graph) return;
    void loadDynamics(workspace.chain.id, eventHours);
  }, [viewMode, eventHours, workspace?.chain.id, workspace?.graph?.revisionId]);

  async function openChain(chain: IndustryChain) {
    setLoading(true);
    try {
      const value = await api<IndustryChainWorkspace>(`/api/industry-chains/${chain.id}`);
      setWorkspace(value);
      setSelectedNodeKey(undefined);
      setExpandedNodeKeys(new Set());
      setActiveLayer('STRUCTURE');
      setEventFeed(undefined);
      setSelectedEventId(undefined);
      setViewMode('panorama');
      setMessage(`已打开 ${chain.name} 产业链图谱`);
    } catch (error) { handleError(error); } finally { setLoading(false); }
  }

  async function createChain(value: string) {
    const trimmed = value.trim();
    if (!trimmed) return;
    setLoading(true);
    try {
      const next = await api<IndustryChainWorkspace>('/api/industry-chains', {
        method: 'POST', body: JSON.stringify({ name: trimmed })
      });
      setWorkspace(next);
      setName('');
      setExpandedNodeKeys(new Set());
      setActiveLayer('STRUCTURE');
      setMessage(`${next.chain.name} 图谱已进入生成队列`);
      await loadChains();
    } catch (error) { handleError(error); } finally { setLoading(false); }
  }

  async function refreshGraph() {
    if (!workspace) return;
    try {
      const revision = await api<IndustryChainWorkspace['revision']>(
        `/api/industry-chains/${workspace.chain.id}/refresh`, { method: 'POST' }
      );
      setWorkspace({ ...workspace, revision });
      const isCompletion = workspace.structure?.status === 'UPGRADE_AVAILABLE'
        || workspace.structure?.status === 'ENRICHMENT_RECOMMENDED';
      addToast(isCompletion ? '已开始补全结构，当前图谱会保持可读' : '已开始更新图谱，当前版本会保持可读', 'info');
    } catch (error) { handleError(error); }
  }

  async function loadDynamics(chainId: number, hours: number) {
    setEventsLoading(true);
    try {
      const next = await api<IndustryChainEventFeed>(`/api/industry-chains/${chainId}/events?hours=${hours}`);
      setEventFeed(next);
      setSelectedEventId((current) => next.events.some((event) => event.eventId === current) ? current : next.events[0]?.eventId);
    } catch (error) { handleError(error); } finally { setEventsLoading(false); }
  }

  async function refreshDynamics() {
    if (!workspace) return;
    setEventsLoading(true);
    try {
      const summary = await api<IndustryChainEventRefreshSummary>(`/api/industry-chains/${workspace.chain.id}/events/refresh`, { method: 'POST' });
      await loadDynamics(workspace.chain.id, eventHours);
      addToast(summary.added ? `新增 ${summary.added} 条链上动态` : '链上动态已是最新', 'success');
    } catch (error) { handleError(error); } finally { setEventsLoading(false); }
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    void createChain(name);
  }

  function handleError(error: unknown) {
    const next = error instanceof Error ? error.message : '产业链图谱加载失败';
    setMessage(next); addToast(next, 'error');
  }

  function toggleExpanded(key: string) {
    setExpandedNodeKeys((current) => {
      const next = new Set(current);
      if (next.has(key)) next.delete(key); else next.add(key);
      return next;
    });
  }

  function selectNode(key: string) {
    setSelectedNodeKey(key);
    if (!workspace?.graph) return;
    const path = expansionPathToNode(workspace.graph, key);
    setExpandedNodeKeys((current) => new Set([...current, ...path]));
  }

  return (
    <div className={`ic-workbench ${libraryCollapsed ? 'is-library-collapsed' : ''}`}>
      <aside className={`ic-library ${libraryCollapsed ? 'is-collapsed' : ''}`} aria-label="产业链目录">
        <div className="ic-library-rail" hidden={!libraryCollapsed}>
          <span aria-hidden="true">IC</span>
          <button type="button" aria-label="展开产业链目录" aria-expanded="false"
            aria-controls="industry-chain-library-content" onClick={() => setLibraryCollapsed(false)}>
            <span aria-hidden="true">›</span>
          </button>
        </div>
        <div id="industry-chain-library-content" className="ic-library-content" hidden={libraryCollapsed}>
          <div className="ic-library-heading">
            <div className="ic-library-title">
              <strong>产业链目录</strong>
              <span>跟踪产业脉络与实时变化</span>
            </div>
            <div className="ic-library-heading-actions">
              <small>{chains.length} 个图谱</small>
              <button type="button" aria-label="收起产业链目录" aria-expanded="true"
                aria-controls="industry-chain-library-content" onClick={() => setLibraryCollapsed(true)}>
                <span aria-hidden="true">‹</span>
              </button>
            </div>
          </div>
          <form className="ic-create" onSubmit={handleSubmit}>
            <input value={name} onChange={(event) => setName(event.target.value)} placeholder="新建产业链主题" aria-label="产业链名称" />
            <button type="submit" disabled={loading || !name.trim()} aria-label="生成产业链图谱"><span aria-hidden="true">＋</span></button>
          </form>
          {chains.length > 0 && (
            <div className="ic-library-section-title"><span>我的图谱</span><small>{chains.length}</small></div>
          )}
          <div className="ic-chain-list">
            {chains.map((chain) => {
              const ready = Boolean(chain.currentRevisionId);
              const active = workspace?.chain.id === chain.id;
              return (
                <button type="button" key={chain.id} onClick={() => void openChain(chain)}
                  className={active ? 'is-active' : ''} aria-current={active ? 'page' : undefined}
                  aria-label={`${chain.name} 产业链`}>
                  <i className={`ic-chain-status ${ready ? 'is-ready' : ''}`} aria-hidden="true" />
                  <span className="ic-chain-copy">
                    <strong>{chain.name}</strong>
                    <small>{ready ? '可查看链上动态' : '等待首次生成'}</small>
                  </span>
                  <span className="ic-chain-meta">
                    <small>{ready ? `R${chain.currentRevisionId}` : '未生成'}</small>
                    <span aria-hidden="true">›</span>
                  </span>
                </button>
              );
            })}
          </div>
          {!chains.length && !loading && (
            <div className="ic-empty-prompts">
              <p>从一个产业主题开始，系统会核对全景、上下游、应用与代表公司。</p>
              {SUGGESTIONS.map((suggestion) => (
                <button type="button" key={suggestion} onClick={() => void createChain(suggestion)}>{suggestion}</button>
              ))}
            </div>
          )}
        </div>
      </aside>

      <main className="ic-main">
        {!workspace ? (
          <section className="ic-hero">
            <div className="ic-hero-orbit" aria-hidden="true"><i /><i /><i /></div>
            <span>Industry intelligence atlas</span>
            <h2>把产业逻辑，展开成一条可验证的价值链。</h2>
            <p>从原料、部件、制造到应用与公司，沿着证据逐层下钻。</p>
          </section>
        ) : (
          <>
            <header className="ic-toolbar">
              <div>
                <span>Industry chain / {workspace.graph?.schemaVersion || 'building'}</span>
                <h2>{workspace.chain.name}</h2>
                {workspace.graph && <nav className="ic-view-switch" aria-label="产业链视图">
                  <button type="button" className={viewMode === 'panorama' ? 'is-active' : ''}
                    aria-pressed={viewMode === 'panorama'} onClick={() => setViewMode('panorama')}>产业全景</button>
                  <button type="button" className={viewMode === 'research' ? 'is-active' : ''}
                    aria-pressed={viewMode === 'research'} onClick={() => setViewMode('research')}>研究面板</button>
                  <button type="button" className={viewMode === 'dynamics' ? 'is-active' : ''}
                    aria-pressed={viewMode === 'dynamics'} onClick={() => setViewMode('dynamics')}>链上动态</button>
                </nav>}
              </div>
              <div className="ic-toolbar-actions">
                {workspace.structure && workspace.graph && (
                  <StructureMeter structure={workspace.structure} running={workspace.revision?.status === 'RUNNING'} />
                )}
                <div className="ic-toolbar-controls">
                {viewMode === 'panorama' && <label className="ic-search">
                  <span aria-hidden="true">⌕</span>
                  <input type="search" aria-label="搜索图谱节点" value={search}
                    onChange={(event) => setSearch(event.target.value)} placeholder="搜索产品、公司、代码" />
                </label>}
                {viewMode === 'panorama' && <button type="button" className="ic-focus-button" disabled={!selectedNodeKey}
                  onClick={() => setFocusMode((current) => !current)}>
                  {focusMode ? '查看全图' : '聚焦链路'}
                </button>}
                <button type="button" className="ic-refresh-button" onClick={() => void refreshGraph()}
                  disabled={workspace.revision?.status === 'RUNNING'}>{completionActionLabel(workspace)}</button>
                </div>
              </div>
            </header>
            {workspace.revision?.status === 'RUNNING' && (
              <div className="ic-progress" role="status"><i /><strong>{revisionStageLabel(workspace.revision.stage)}</strong>
                <span>{workspace.revision.message || '正在生成产业链图谱'}</span></div>
            )}
            {workspace.revision?.status === 'FAILED' && (
              <div className="ic-progress is-error" role="status"><i /><span>{workspace.revision.message}</span></div>
            )}
            {workspace.graph && viewMode === 'panorama' && (
              <IndustryChainLayerBar activeLayer={activeLayer} onChange={setActiveLayer} />
            )}
            {workspace.graph ? (
              viewMode === 'research' ? <IndustryChainResearchPanel graph={workspace.graph} /> : (
                <div className="ic-graph-grid">
                  <IndustryChainCanvas graph={workspace.graph} selectedNodeKey={selectedNodeKey}
                    search={search} focusMode={focusMode} expandedNodeKeys={expandedNodeKeys} activeLayer={activeLayer}
                    eventCounts={viewMode === 'dynamics' ? eventFeed?.nodeEventCounts : undefined}
                    highlightedPath={viewMode === 'dynamics' ? selectedEvent?.impact.pathNodeKeys : undefined}
                    onSelectNode={selectNode} onToggleExpanded={toggleExpanded} />
                  {viewMode === 'panorama' ? (
                    <IndustryChainInspector graph={workspace.graph} selectedNodeKey={selectedNode?.nodeKey}
                      expanded={Boolean(selectedNodeKey && expandedNodeKeys.has(selectedNodeKey))}
                      onSelectNode={selectNode} onToggleExpanded={toggleExpanded} />
                  ) : (
                    <IndustryChainDynamics graph={workspace.graph} feed={eventFeed} selectedEventId={selectedEventId}
                      hours={eventHours} loading={eventsLoading} onSelectEvent={setSelectedEventId}
                      onHoursChange={setEventHours} onRefresh={() => void refreshDynamics()}
                      onOpenNewsEvent={onOpenNewsEvent} />
                  )}
                </div>
              )
            ) : (
              <section className="ic-building" aria-label="图谱生成中">
                <div className="ic-building-line"><i /><i /><i /><i /></div>
                <strong>正在建立价值链坐标</strong>
                <p>三路搜索正在核对公开资料，完成后会自动呈现图谱。</p>
              </section>
            )}
          </>
        )}
      </main>
    </div>
  );
}

function StructureMeter({ structure, running }: {
  structure: NonNullable<IndustryChainWorkspace['structure']>;
  running: boolean;
}) {
  const label = structureStatusLabel(structure.status, running);
  const tone = structure.status === 'COMPLETE' ? 'is-complete'
    : structure.status === 'BUILDING' ? 'is-building' : 'is-attention';
  const style = { '--ic-structure-score': `${Math.max(0, Math.min(100, structure.score))}%` } as CSSProperties;
  return (
    <section className={`ic-structure-meter ${tone}`} aria-label={`图谱结构完整度 ${structure.score} 分`}>
      <div className="ic-structure-dial" style={style} aria-hidden="true">
        <strong>{structure.score}</strong><span>/100</span>
      </div>
      <div className="ic-structure-copy">
        <header><span>Structure depth</span><strong>{label}</strong></header>
        <p>{structure.coveredStageCount} / {structure.stageCount} 环节 · {structure.semanticNodeCount} 个语义节点</p>
        {structure.gaps[0] && <small title={structure.gaps.join('；')}>{structure.gaps[0]}</small>}
      </div>
    </section>
  );
}

function structureStatusLabel(status: NonNullable<IndustryChainWorkspace['structure']>['status'], running: boolean) {
  if (running) return '补全进行中';
  if (status === 'COMPLETE') return '结构完整';
  if (status === 'UPGRADE_AVAILABLE') return '结构待升级';
  if (status === 'ENRICHMENT_RECOMMENDED') return '建议继续补全';
  return '正在建立结构';
}

function completionActionLabel(workspace: IndustryChainWorkspace) {
  if (workspace.structure?.status === 'UPGRADE_AVAILABLE') return '升级为 V3';
  if (workspace.structure?.status === 'ENRICHMENT_RECOMMENDED') return '补全结构';
  return '更新图谱';
}

function revisionStageLabel(stage: NonNullable<IndustryChainWorkspace['revision']>['stage']) {
  if (stage === 'QUEUED' || stage === 'DISPATCHED') return '等待异步执行';
  if (stage === 'COLLECTING_EVIDENCE') return '正在核对产业资料';
  if (stage === 'COMPLETING_STRUCTURE') return '正在补全结构';
  if (stage === 'VALIDATING_STRUCTURE') return '正在校验新版本';
  if (stage === 'SYNTHESIZING') return '正在生成首版图谱';
  return '正在更新图谱';
}

function expansionPathToNode(graph: NonNullable<IndustryChainWorkspace['graph']>, nodeKey: string) {
  const nodeTypes = new Map(graph.nodes.map((node) => [node.nodeKey, node.type]));
  const queue = [nodeKey];
  const parent = new Map<string, string>();
  const visited = new Set(queue);
  let stageKey: string | undefined;
  while (queue.length > 0 && !stageKey) {
    const current = queue.shift()!;
    if (nodeTypes.get(current) === 'STAGE') {
      stageKey = current;
      break;
    }
    graph.edges.forEach((edge) => {
      const next = edge.sourceKey === current ? edge.targetKey
        : edge.targetKey === current ? edge.sourceKey : undefined;
      if (!next || visited.has(next)) return;
      visited.add(next);
      parent.set(next, current);
      queue.push(next);
    });
  }
  const result = new Set<string>();
  let current = stageKey;
  while (current) {
    if (current !== nodeKey) result.add(current);
    current = parent.get(current);
  }
  return result;
}
