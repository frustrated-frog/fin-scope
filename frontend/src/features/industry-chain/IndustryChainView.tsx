import { FormEvent, useEffect, useMemo, useState } from 'react';

import { api } from '../../shared/api/client';
import { IndustryChainCanvas } from './IndustryChainCanvas';
import { IndustryChainInspector } from './IndustryChainInspector';
import type { IndustryChain, IndustryChainWorkspace } from './industryChainTypes';
import './industry-chain.css';

const SUGGESTIONS = ['AI 算力', '人形机器人', '低空经济', '创新药', '新能源车', '半导体设备'];
const POLL_INTERVAL_MS = 2400;

export function IndustryChainView({
  addToast = () => undefined,
  setMessage = () => undefined,
  initialStockCode
}: {
  addToast?: (message: string, type?: 'success' | 'error' | 'info') => void;
  setMessage?: (message: string) => void;
  initialStockCode?: string;
}) {
  const [chains, setChains] = useState<IndustryChain[]>([]);
  const [workspace, setWorkspace] = useState<IndustryChainWorkspace | null>(null);
  const [name, setName] = useState('');
  const [search, setSearch] = useState(initialStockCode ?? '');
  const [loading, setLoading] = useState(true);
  const [selectedNodeKey, setSelectedNodeKey] = useState<string>();
  const [focusMode, setFocusMode] = useState(Boolean(initialStockCode));
  const [expandedCompanyKeys, setExpandedCompanyKeys] = useState<Set<string>>(new Set());

  const loadChains = async () => {
    const value = await api<IndustryChain[]>('/api/industry-chains');
    setChains(value);
    return value;
  };

  useEffect(() => {
    loadChains().catch(handleError).finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    if (workspace?.revision?.status !== 'RUNNING') return undefined;
    const timer = window.setTimeout(async () => {
      try {
        const next = await api<IndustryChainWorkspace>(`/api/industry-chains/${workspace.chain.id}`);
        setWorkspace(next);
        if (next.revision?.status === 'READY') {
          addToast('产业链图谱已生成', 'success');
          await loadChains();
        }
      } catch (error) { handleError(error); }
    }, POLL_INTERVAL_MS);
    return () => window.clearTimeout(timer);
  }, [workspace?.revision?.status, workspace?.revision?.id]);

  const selectedNode = useMemo(() => workspace?.graph?.nodes.find(
    (node) => node.nodeKey === selectedNodeKey
  ), [selectedNodeKey, workspace?.graph]);

  async function openChain(chain: IndustryChain) {
    setLoading(true);
    try {
      const value = await api<IndustryChainWorkspace>(`/api/industry-chains/${chain.id}`);
      setWorkspace(value);
      setSelectedNodeKey(undefined);
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
      addToast('已开始刷新图谱，旧版本会保持可读', 'info');
    } catch (error) { handleError(error); }
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    void createChain(name);
  }

  function handleError(error: unknown) {
    const next = error instanceof Error ? error.message : '产业链图谱加载失败';
    setMessage(next); addToast(next, 'error');
  }

  function toggleCompanies(key: string) {
    setExpandedCompanyKeys((current) => {
      const next = new Set(current);
      if (next.has(key)) next.delete(key); else next.add(key);
      return next;
    });
  }

  return (
    <div className="ic-workbench">
      <aside className="ic-library" aria-label="产业链目录">
        <div className="ic-library-title">
          <span>Chain index</span><strong>产业链目录</strong>
        </div>
        <form className="ic-create" onSubmit={handleSubmit}>
          <input value={name} onChange={(event) => setName(event.target.value)} placeholder="输入产业主题" aria-label="产业链名称" />
          <button type="submit" disabled={loading || !name.trim()} aria-label="生成产业链图谱">＋</button>
        </form>
        <div className="ic-chain-list">
          {chains.map((chain) => (
            <button type="button" key={chain.id} onClick={() => void openChain(chain)}
              className={workspace?.chain.id === chain.id ? 'is-active' : ''}
              aria-label={`${chain.name} 产业链`}>
              <span>{chain.name}</span><small>{chain.currentRevisionId ? `R${chain.currentRevisionId}` : '待生成'}</small>
            </button>
          ))}
        </div>
        {!chains.length && !loading && (
          <div className="ic-empty-prompts">
            <p>从一个产业主题开始，系统会核对全景、上下游、应用与代表公司。</p>
            {SUGGESTIONS.map((suggestion) => (
              <button type="button" key={suggestion} onClick={() => void createChain(suggestion)}>{suggestion}</button>
            ))}
          </div>
        )}
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
              </div>
              <div className="ic-toolbar-controls">
                <label className="ic-search">
                  <span aria-hidden="true">⌕</span>
                  <input type="search" aria-label="搜索图谱节点" value={search}
                    onChange={(event) => setSearch(event.target.value)} placeholder="搜索产品、公司、代码" />
                </label>
                <button type="button" className="ic-focus-button" disabled={!selectedNodeKey}
                  onClick={() => setFocusMode((current) => !current)}>
                  {focusMode ? '查看全图' : '聚焦链路'}
                </button>
                <button type="button" className="ic-refresh-button" onClick={() => void refreshGraph()}
                  disabled={workspace.revision?.status === 'RUNNING'}>刷新证据</button>
              </div>
            </header>
            {workspace.revision?.status === 'RUNNING' && (
              <div className="ic-progress" role="status"><i /><span>{workspace.revision.message || '正在生成产业链图谱'}</span></div>
            )}
            {workspace.revision?.status === 'FAILED' && (
              <div className="ic-progress is-error" role="status"><i /><span>{workspace.revision.message}</span></div>
            )}
            {workspace.graph ? (
              <div className="ic-graph-grid">
                <IndustryChainCanvas graph={workspace.graph} selectedNodeKey={selectedNodeKey}
                  search={search} focusMode={focusMode} expandedCompanyKeys={expandedCompanyKeys}
                  onSelectNode={setSelectedNodeKey} onToggleCompanies={toggleCompanies} />
                <IndustryChainInspector graph={workspace.graph} selectedNodeKey={selectedNode?.nodeKey} />
              </div>
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
