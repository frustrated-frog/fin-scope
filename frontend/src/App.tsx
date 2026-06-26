import { FormEvent, useEffect, useMemo, useState } from 'react';

type View = 'dashboard' | 'sources' | 'inbox' | 'briefs' | 'topics' | 'learning' | 'agents' | 'settings';

type Source = {
  id?: number;
  name: string;
  type: string;
  url: string;
  enabled: boolean;
  fetchFrequencyMinutes: number;
  credibility: number;
  tags?: string;
};

type Article = {
  id: number;
  title: string;
  url?: string;
  sourceName: string;
  category?: string;
  noveltyType?: string;
  noveltyReason?: string;
  summary?: string;
  body?: string;
  publishedAt?: string;
  fetchedAt?: string;
  insightCard?: InsightCard;
};

type InsightCard = {
  id?: number;
  oneSentenceSummary?: string;
  coreEvent?: string;
  importance?: string;
  impactTargets?: string;
  followUpQuestions?: string;
  cardMarkdown?: string;
  background?: string;
  keyData?: string;
  timeline?: string;
  relatedParties?: string;
  riskFactors?: string;
  futureOutlook?: string;
  impactOnInvestment?: string;
  impactOnStartup?: string;
  professionalInsight?: string;
  facts?: string;
  reasoning?: string;
  opinions?: string;
};

type Brief = {
  id: number;
  briefDate: string;
  title: string;
  markdownPath: string;
  content?: string;
};

type Topic = {
  id: number;
  name: string;
  slug?: string;
  status: string;
  description?: string;
  markdownPath?: string;
  terms?: string;
  learningQuestions?: string;
  articleCount?: number;
  briefCount?: number;
};

type TopicDetail = {
  topic: Topic;
  linkedArticles: Article[];
  linkedBriefs: Brief[];
  markdown: string;
};

type AgentRun = {
  id: number;
  nodeName: string;
  status: string;
  durationMs: number;
  errorMessage?: string;
};

type Dashboard = {
  sourceCount: number;
  articleCount: number;
  briefCount: number;
  latestFetchRuns: Array<{ id: number; sourceName: string; status: string; successCount: number; duplicateCount: number }>;
};

type PageResponse<T> = {
  items: T[];
  totalCount: number;
  page: number;
  pageSize: number;
  totalPages: number;
};

type DeleteArticlesRequest = {
  ids: number[];
};

const navItems: Array<{ id: View; label: string; hint: string; code: string }> = [
  { id: 'dashboard', label: 'Dashboard', hint: '总览', code: '01' },
  { id: 'sources', label: 'Sources', hint: '信源', code: '02' },
  { id: 'inbox', label: 'Inbox', hint: '信息流', code: '03' },
  { id: 'briefs', label: 'Briefs', hint: '日报', code: '04' },
  { id: 'topics', label: 'Topics', hint: '主题', code: '05' },
  { id: 'learning', label: 'Learning', hint: '学习', code: '06' },
  { id: 'agents', label: 'Agent Runs', hint: 'Trace', code: '07' },
  { id: 'settings', label: 'Settings', hint: '设置', code: '08' }
];

async function api<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    headers: { 'Content-Type': 'application/json', ...(options?.headers ?? {}) },
    ...options
  });
  if (!response.ok) {
    try {
      const errorBody = await response.json();
      throw new Error(errorBody.error || `Request failed: ${response.status}`);
    } catch (error) {
      if (error instanceof Error && !error.message.startsWith('Unexpected')) {
        throw error;
      }
      throw new Error(`Request failed: ${response.status}`);
    }
  }
  return response.json();
}

function useCountUp(end: number, duration: number = 800, delay: number = 0) {
  const [count, setCount] = useState(0);

  useEffect(() => {
    if (end === 0) {
      setCount(0);
      return;
    }
    const timer = setTimeout(() => {
      const startTime = Date.now();
      const animate = () => {
        const elapsed = Date.now() - startTime;
        const progress = Math.min(elapsed / duration, 1);
        const eased = 1 - Math.pow(1 - progress, 3);
        setCount(Math.floor(eased * end));
        if (progress < 1) requestAnimationFrame(animate);
      };
      requestAnimationFrame(animate);
    }, delay);
    return () => clearTimeout(timer);
  }, [end, duration, delay]);

  return count;
}

export default function App() {
  const [view, setView] = useState<View>('dashboard');
  const [theme, setTheme] = useState<'light' | 'dark'>('dark');
  const [dashboard, setDashboard] = useState<Dashboard | null>(null);
  const [sources, setSources] = useState<Source[]>([]);
  const [articles, setArticles] = useState<Article[]>([]);
  const [briefs, setBriefs] = useState<Brief[]>([]);
  const [topics, setTopics] = useState<Topic[]>([]);
  const [topicDetail, setTopicDetail] = useState<TopicDetail | null>(null);
  const [agentRuns, setAgentRuns] = useState<AgentRun[]>([]);
  const [message, setMessage] = useState('准备就绪');
  const [toasts, setToasts] = useState<Array<{id: number; message: string; type: 'success' | 'error' | 'info'}>>([]);

  const addToast = (message: string, type: 'success' | 'error' | 'info' = 'info') => {
    const id = Date.now();
    setToasts(prev => [...prev, { id, message, type }]);
    setTimeout(() => {
      setToasts(prev => prev.filter(t => t.id !== id));
    }, 3000);
  };

  const refresh = async () => {
    const [dashboardData, sourceData, articleData, briefData, topicData, agentData] = await Promise.all([
      api<Dashboard>('/api/dashboard'),
      api<Source[]>('/api/sources'),
      api<Article[]>('/api/articles'),
      api<Brief[]>('/api/briefs'),
      api<Topic[]>('/api/topics'),
      api<AgentRun[]>('/api/agent-runs')
    ]);
    setDashboard(dashboardData);
    setSources(sourceData);
    setArticles(articleData);
    setBriefs(briefData);
    setTopics(topicData);
    setAgentRuns(agentData);
  };

  useEffect(() => {
    refresh().catch((error) => setMessage(error.message));
  }, []);

  const currentTitle = useMemo(() => navItems.find((item) => item.id === view)?.label ?? 'Dashboard', [view]);

  async function openTopic(topicId: number) {
    const detail = await api<TopicDetail>(`/api/topics/${topicId}`);
    setTopicDetail(detail);
    setView('learning');
  }

  async function refreshWorkspace() {
    setMessage('正在刷新');
    try {
      await refresh();
      setMessage('数据已同步');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '刷新失败');
    }
  }

  return (
    <div className="app-shell" data-theme={theme}>
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-mark">FS</span>
          <div>
            <h1>FinScope</h1>
            <p>Research Intelligence</p>
          </div>
        </div>
        <div className="sidebar-signal">
          <span>Pipeline</span>
          <strong>Sources / Inbox / Brief / Topics</strong>
        </div>
        <nav aria-label="Workspace">
          {navItems.map((item) => (
            <button
              key={item.id}
              className={view === item.id ? 'nav-item active' : 'nav-item'}
              aria-label={item.label}
              onClick={() => setView(item.id)}
            >
              <span className="nav-code">{item.code}</span>
              <span className="nav-copy">
                <span>{item.label}</span>
                <small>{item.hint}</small>
              </span>
            </button>
          ))}
        </nav>
      </aside>

      <main className="workspace">
        <header className="topbar">
          <div>
            <p className="eyebrow">Local-first investment intelligence</p>
            <h2>{currentTitle}</h2>
          </div>
          <div className="topbar-actions">
            <div className="market-chip">
              <span>Articles</span>
              <strong>{articles.length}</strong>
            </div>
            <div className="market-chip">
              <span>Topics</span>
              <strong>{topics.length}</strong>
            </div>
            <button className="ghost-button" type="button" onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}>
              {theme === 'dark' ? '浅色' : '深色'}
            </button>
            <button className="ghost-button" type="button" onClick={refreshWorkspace}>刷新</button>
            <div className="status-pill">{message}</div>
          </div>
        </header>

        {view === 'dashboard' && <DashboardView dashboard={dashboard} articles={articles} />}
        {view === 'sources' && <SourcesView sources={sources} onChanged={refresh} setMessage={setMessage} addToast={addToast} />}
        {view === 'inbox' && (
          <InboxView articles={articles} onChanged={refresh} setMessage={setMessage} setView={setView} addToast={addToast} />
        )}
        {view === 'briefs' && (
          <BriefsView briefs={briefs} onChanged={refresh} setMessage={setMessage} setView={setView} />
        )}
        {view === 'topics' && (
          <TopicsView topics={topics} onChanged={refresh} setMessage={setMessage} onOpenTopic={openTopic} />
        )}
        {view === 'learning' && (
          <LearningView
            topics={topics}
            topicDetail={topicDetail}
            onOpenTopic={openTopic}
            onChanged={refresh}
            setMessage={setMessage}
          />
        )}
        {view === 'agents' && <AgentRunsView agentRuns={agentRuns} />}
        {view === 'settings' && <SettingsView setMessage={setMessage} />}
      </main>

      <div className="toast-container">
        {toasts.map(toast => (
          <div key={toast.id} className={`toast toast-${toast.type}`}>
            {toast.message}
          </div>
        ))}
      </div>
    </div>
  );
}

function DashboardView({ dashboard, articles }: { dashboard: Dashboard | null; articles: Article[] }) {
  const sourceCount = useCountUp(dashboard?.sourceCount ?? 0, 800, 0);
  const articleCount = useCountUp(dashboard?.articleCount ?? 0, 800, 100);
  const briefCount = useCountUp(dashboard?.briefCount ?? 0, 800, 200);
  const novelCount = useCountUp(articles.filter((article) => article.noveltyType === 'NEW').length, 800, 300);

  if (!dashboard) {
    return (
      <section className="content-grid">
        <div className="dashboard-grid">
          {[1, 2, 3, 4].map(i => (
            <div key={i} className="card">
              <div className="card-content">
                <div className="skeleton skeleton-text" style={{ width: '80px' }}></div>
                <div className="skeleton skeleton-heading" style={{ width: '60px' }}></div>
                <div className="skeleton skeleton-text" style={{ width: '100px' }}></div>
              </div>
            </div>
          ))}
        </div>
      </section>
    );
  }

  const metrics = [
    { label: '信息源', value: sourceCount, caption: 'Sources' },
    { label: '文章池', value: articleCount, caption: 'Inbox' },
    { label: '简报', value: briefCount, caption: 'Briefs' },
    { label: '新内容', value: novelCount, caption: 'Novelty' }
  ];

  return (
    <section className="content-grid">
      <div className="dashboard-grid">
        {metrics.map((metric) => (
          <div key={metric.label} className="dashboard-card">
            <span className="dashboard-card-label">{metric.caption}</span>
            <strong className="dashboard-card-value">{metric.value}</strong>
            <small className="dashboard-card-caption">{metric.label}</small>
          </div>
        ))}
      </div>

      <div className="panel">
        <div className="panel-heading">
          <h3>最近抓取</h3>
          <span className="badge">Fetch Runs</span>
        </div>
        <Table
          headers={['来源', '状态', '新增', '重复']}
          rows={dashboard.latestFetchRuns.map((run) => [
            run.sourceName,
            run.status,
            String(run.successCount),
            String(run.duplicateCount)
          ])}
          empty="还没有抓取记录"
        />
      </div>
    </section>
  );
}

function SourcesView({
  sources,
  onChanged,
  setMessage,
  addToast
}: {
  sources: Source[];
  onChanged: () => Promise<void>;
  setMessage: (message: string) => void;
  addToast: (message: string, type: 'success' | 'error' | 'info') => void;
}) {
  const [form, setForm] = useState<Source>({
    name: '',
    type: 'RSS',
    url: '',
    enabled: true,
    fetchFrequencyMinutes: 60,
    credibility: 3,
    tags: ''
  });

  async function submit(event: FormEvent) {
    event.preventDefault();
    await api<Source>('/api/sources', { method: 'POST', body: JSON.stringify(form) });
    setForm({ ...form, name: '', url: '', tags: '' });
    addToast('信息源已保存', 'success');
    await onChanged();
  }

  async function fetchSource(id?: number) {
    if (!id) return;
    await api(`/api/sources/${id}/fetch`, { method: 'POST' });
    addToast('抓取完成', 'success');
    await onChanged();
  }

  return (
    <section className="split">
      <form className="panel form-panel" onSubmit={submit}>
        <div className="panel-heading">
          <h3>新增信息源</h3>
          <span className="badge">Source</span>
        </div>
        <label>
          名称
          <input
            value={form.name}
            onChange={(event) => setForm({ ...form, name: event.target.value })}
            required
          />
        </label>
        <label>
          类型
          <select
            value={form.type}
            onChange={(event) => setForm({ ...form, type: event.target.value })}
          >
            <option value="RSS">RSS</option>
            <option value="WEB">Web</option>
          </select>
        </label>
        <label>
          URL
          <input
            value={form.url}
            onChange={(event) => setForm({ ...form, url: event.target.value })}
            required
          />
        </label>
        <label>
          标签
          <input
            value={form.tags}
            onChange={(event) => setForm({ ...form, tags: event.target.value })}
          />
        </label>
        <button className="primary-button" type="submit">保存信息源</button>
      </form>

      <section className="panel">
        <div className="panel-heading">
          <h3>已配置信息源</h3>
          <span className="subtle-badge">{sources.length} active</span>
        </div>
        <div className="item-list">
          {sources.length === 0 ? (
            <div className="empty-state">
              <p className="empty-state-text">暂无信息源</p>
            </div>
          ) : (
            sources.map((source) => (
              <div key={source.id} className="source-item">
                <div className="source-info">
                  <h4>{source.name}</h4>
                  <p>{source.tags || '未标记'} · 可信度 {source.credibility}</p>
                </div>
                <button
                  className="compact-button"
                  onClick={() => fetchSource(source.id)}
                >
                  抓取
                </button>
              </div>
            ))
          )}
        </div>
      </section>
    </section>
  );
}

function InboxView({
  articles: initialArticles,
  onChanged,
  setMessage,
  setView,
  addToast
}: {
  articles: Article[];
  onChanged: () => Promise<void>;
  setMessage: (message: string) => void;
  setView: (view: View) => void;
  addToast: (message: string, type: 'success' | 'error' | 'info') => void;
}) {
  // Pagination state
  const [currentPage, setCurrentPage] = useState(0);
  const [pageSize] = useState(20);
  const [totalCount, setTotalCount] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [pagedArticles, setPagedArticles] = useState<Article[]>([]);

  // Selection state for batch operations
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const [selectAll, setSelectAll] = useState(false);

  // Existing state
  const [urlForm, setUrlForm] = useState({ url: '', sourceName: '手动研究', tags: '市场' });
  const [expandedArticleId, setExpandedArticleId] = useState<number | null>(null);
  const [showDeleteConfirm, setShowDeleteConfirm] = useState<number | null>(null);
  const [showBatchDeleteConfirm, setShowBatchDeleteConfirm] = useState(false);

  // Fetch paginated articles
  const fetchArticles = async () => {
    try {
      const response = await api<PageResponse<Article>>(`/api/articles/paged?page=${currentPage}&pageSize=${pageSize}`);
      setPagedArticles(response.items);
      setTotalCount(response.totalCount);
      setTotalPages(response.totalPages);
      setSelectedIds(new Set());
      setSelectAll(false);
    } catch (error) {
      addToast(error instanceof Error ? error.message : 'Failed to load articles', 'error');
    }
  };

  useEffect(() => {
    fetchArticles();
  }, [currentPage, pageSize]);

  async function ingestUrl(event: FormEvent) {
    event.preventDefault();
    try {
      await api('/api/articles/ingest-url', {
        method: 'POST',
        body: JSON.stringify(urlForm)
      });
      setUrlForm({ ...urlForm, url: '' });
      addToast('URL 已生成情报卡片', 'success');
      await fetchArticles();
    } catch (error) {
      addToast(error instanceof Error ? error.message : 'URL 解析失败', 'error');
    }
  }

  async function compoundArticle(articleId: number) {
    await api(`/api/topics/from-article/${articleId}`, { method: 'POST' });
    addToast('文章已沉淀到主题库', 'success');
    await fetchArticles();
    setView('topics');
  }

  // NEW: Delete handlers
  async function deleteArticle(id: number) {
    try {
      await api(`/api/articles/${id}`, { method: 'DELETE' });
      addToast('文章已删除', 'success');
      await fetchArticles();
      setShowDeleteConfirm(null);
    } catch (error) {
      addToast(error instanceof Error ? error.message : '删除失败', 'error');
    }
  }

  async function deleteSelected() {
    if (selectedIds.size === 0) return;

    try {
      await api('/api/articles/batch', {
        method: 'DELETE',
        body: JSON.stringify({ ids: Array.from(selectedIds) })
      });
      addToast(`已删除 ${selectedIds.size} 篇文章`, 'success');
      await fetchArticles();
      setShowBatchDeleteConfirm(false);
    } catch (error) {
      addToast(error instanceof Error ? error.message : '批量删除失败', 'error');
    }
  }

  // NEW: Selection handlers
  function toggleSelect(id: number) {
    const newSelected = new Set(selectedIds);
    if (newSelected.has(id)) {
      newSelected.delete(id);
    } else {
      newSelected.add(id);
    }
    setSelectedIds(newSelected);
    setSelectAll(newSelected.size === pagedArticles.length);
  }

  function toggleSelectAll() {
    if (selectAll) {
      setSelectedIds(new Set());
      setSelectAll(false);
    } else {
      setSelectedIds(new Set(pagedArticles.map(a => a.id)));
      setSelectAll(true);
    }
  }

  function goToPage(page: number) {
    if (page >= 0 && page < totalPages) {
      setCurrentPage(page);
    }
  }

  const getCategoryColor = (category?: string) => {
    switch (category) {
      case '宏观': return '#10b981';
      case '政策': return '#3b82f6';
      case '公司': return '#8b5cf6';
      case '行业': return '#f59e0b';
      case '科技': return '#ec4899';
      default: return '#6b7280';
    }
  };

  return (
    <section className="inbox-container">
      <div className="card">
        <div className="card-header">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div>
              <h3 style={{ margin: 0, fontSize: '20px', fontWeight: 700, color: 'var(--ink)' }}>信息流 Inbox</h3>
              <p style={{ margin: '4px 0 0', fontSize: '13px', color: 'var(--muted)' }}>
                URL / RSS / Web 抓取内容统一进入卡片化信息池
              </p>
            </div>
            <span className="badge">{totalCount} items</span>
          </div>
        </div>
        <div className="card-content">
          <form className="url-ingest-form" onSubmit={ingestUrl}>
            <input
              type="url"
              value={urlForm.url}
              onChange={(event) => setUrlForm({ ...urlForm, url: event.target.value })}
              placeholder="输入文章URL..."
              required
            />
            <button className="primary-button" type="submit">生成情报卡片</button>
          </form>
        </div>
      </div>

      {/* Batch Actions Toolbar */}
      {pagedArticles.length > 0 && (
        <div className="batch-toolbar card">
          <div className="card-content" style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <label style={{ display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer' }}>
              <input
                type="checkbox"
                checked={selectAll}
                onChange={toggleSelectAll}
                style={{ width: '18px', height: '18px', cursor: 'pointer' }}
              />
              <span>全选当前页</span>
            </label>
            {selectedIds.size > 0 && (
              <>
                <span className="badge" style={{ background: 'var(--accent)' }}>
                  已选 {selectedIds.size} 项
                </span>
                <button
                  className="danger-button"
                  onClick={() => setShowBatchDeleteConfirm(true)}
                >
                  删除所选
                </button>
              </>
            )}
          </div>
        </div>
      )}

      {/* Articles List */}
      <div className="articles-list">
        {pagedArticles.length === 0 ? (
          <div className="card">
            <div className="card-content">
              <div className="empty-state">
                <p className="empty-state-text">暂无文章,添加URL开始抓取</p>
              </div>
            </div>
          </div>
        ) : (
          pagedArticles.map((article) => (
            <div key={article.id} style={{ position: 'relative' }}>
              <input
                type="checkbox"
                checked={selectedIds.has(article.id)}
                onChange={() => toggleSelect(article.id)}
                style={{
                  position: 'absolute',
                  top: '16px',
                  right: '16px',
                  width: '18px',
                  height: '18px',
                  cursor: 'pointer',
                  zIndex: 10
                }}
              />
              <ArticleCard
                article={article}
                isExpanded={expandedArticleId === article.id}
                onToggle={() => setExpandedArticleId(expandedArticleId === article.id ? null : article.id)}
                onCompound={() => compoundArticle(article.id)}
                onDelete={() => setShowDeleteConfirm(article.id)}
                categoryColor={getCategoryColor(article.category)}
              />
            </div>
          ))
        )}
      </div>

      {/* Pagination Controls */}
      {totalPages > 1 && (
        <div className="pagination-controls card">
          <div className="card-content" style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '12px' }}>
            <button
              className="secondary-button"
              onClick={() => goToPage(currentPage - 1)}
              disabled={currentPage === 0}
            >
              上一页
            </button>
            <span style={{ color: 'var(--muted)' }}>
              第 {currentPage + 1} / {totalPages} 页
            </span>
            <button
              className="secondary-button"
              onClick={() => goToPage(currentPage + 1)}
              disabled={currentPage >= totalPages - 1}
            >
              下一页
            </button>
          </div>
        </div>
      )}

      {/* Delete Confirmation Modals */}
      {showDeleteConfirm !== null && (
        <div className="modal-overlay">
          <div className="modal">
            <div className="modal-header">
              <h4>确认删除</h4>
            </div>
            <div className="modal-content">
              <p>确定要删除这篇文章吗?此操作无法撤销。</p>
            </div>
            <div className="modal-actions">
              <button className="secondary-button" onClick={() => setShowDeleteConfirm(null)}>
                取消
              </button>
              <button className="danger-button" onClick={() => deleteArticle(showDeleteConfirm)}>
                确认删除
              </button>
            </div>
          </div>
        </div>
      )}

      {showBatchDeleteConfirm && (
        <div className="modal-overlay">
          <div className="modal">
            <div className="modal-header">
              <h4>批量删除确认</h4>
            </div>
            <div className="modal-content">
              <p>确定要删除选中的 {selectedIds.size} 篇文章吗?此操作无法撤销。</p>
            </div>
            <div className="modal-actions">
              <button className="secondary-button" onClick={() => setShowBatchDeleteConfirm(false)}>
                取消
              </button>
              <button className="danger-button" onClick={deleteSelected}>
                确认删除
              </button>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}

function InsightCardPreview({ card }: { card: InsightCard }) {
  const basicFields = [
    ['一句话摘要', card.oneSentenceSummary],
    ['核心事件', card.coreEvent],
    ['为什么重要', card.importance],
    ['影响对象', card.impactTargets]
  ].filter(([, value]) => Boolean(value));

  const deepFields = [
    ['背景是什么', card.background],
    ['关键数据', card.keyData],
    ['时间线', card.timeline],
    ['相关方', card.relatedParties],
    ['风险因素', card.riskFactors],
    ['未来展望', card.futureOutlook],
    ['对投资的影响', card.impactOnInvestment],
    ['对创业的影响', card.impactOnStartup],
    ['专业解读', card.professionalInsight]
  ].filter(([, value]) => Boolean(value));

  const frpFields = [
    ['事实', card.facts],
    ['推理', card.reasoning],
    ['观点', card.opinions]
  ].filter(([, value]) => Boolean(value));

  return (
    <div className="insight-card-preview">
      {/* 基础字段 */}
      {basicFields.map(([label, value]) => (
        <div className="insight-field-item" key={label}>
          <div className="insight-field-label">{label}</div>
          <div className="insight-field-value">{value}</div>
        </div>
      ))}

      {/* 深度解读 */}
      {deepFields.length > 0 && (
        <div className="insight-deep-section">
          <div className="insight-section-title">深度解读</div>
          {deepFields.map(([label, value]) => (
            <div className="insight-field-item" key={label}>
              <div className="insight-field-label">{label}</div>
              <div className="insight-field-value">{value}</div>
            </div>
          ))}
        </div>
      )}

      {/* 事实推理观点 */}
      {frpFields.length > 0 && (
        <div className="insight-frp-section">
          <div className="insight-section-title">事实、推理与观点</div>
          {frpFields.map(([label, value]) => (
            <div className="insight-field-item" key={label}>
              <div className="insight-field-label">{label}</div>
              <div className="insight-field-value">{value}</div>
            </div>
          ))}
        </div>
      )}

      {/* 后续观察 */}
      {card.followUpQuestions && (
        <div className="insight-field-item">
          <div className="insight-field-label">后续观察</div>
          <div className="insight-field-value">{card.followUpQuestions}</div>
        </div>
      )}
    </div>
  );
}

function BriefsView({
  briefs,
  onChanged,
  setMessage,
  setView
}: {
  briefs: Brief[];
  onChanged: () => Promise<void>;
  setMessage: (message: string) => void;
  setView: (view: View) => void;
}) {
  async function generateBrief() {
    await api('/api/briefs/generate', { method: 'POST' });
    setMessage('今日简报已生成');
    await onChanged();
  }

  async function compoundBrief(date: string) {
    await api(`/api/topics/from-brief/${date}`, { method: 'POST' });
    setMessage('简报已沉淀到主题库');
    await onChanged();
    setView('topics');
  }

  return (
    <section className="panel wide">
      <div className="panel-heading">
        <h3>每日简报</h3>
        <button className="primary-button" onClick={generateBrief}>生成今日简报</button>
      </div>
      <div className="item-list">
        {briefs.map((brief) => (
          <article className="list-item" key={brief.id}>
            <div>
              <strong>{brief.title}</strong>
              <p>{brief.markdownPath}</p>
            </div>
            <div className="item-actions">
              <button className="compact-button" onClick={() => compoundBrief(brief.briefDate)}>沉淀主题</button>
              <span className="badge">{brief.briefDate}</span>
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}

function TopicsView({
  topics,
  onChanged,
  setMessage,
  onOpenTopic
}: {
  topics: Topic[];
  onChanged: () => Promise<void>;
  setMessage: (message: string) => void;
  onOpenTopic: (topicId: number) => Promise<void>;
}) {
  const [name, setName] = useState('');

  async function createTopic(event: FormEvent) {
    event.preventDefault();
    await api('/api/topics', {
      method: 'POST',
      body: JSON.stringify({ name, description: '手动创建的学习主题', status: 'LEARNING' })
    });
    setName('');
    setMessage('主题已创建');
    await onChanged();
  }

  return (
    <section className="split">
      <form className="panel form-panel" onSubmit={createTopic}>
        <div className="panel-heading">
          <h3>新增主题</h3>
          <span className="subtle-badge">Topic</span>
        </div>
        <label>
          主题名
          <input value={name} onChange={(event) => setName(event.target.value)} required />
        </label>
        <button className="primary-button" type="submit">保存主题</button>
      </form>
      <section className="panel">
        <div className="panel-heading">
          <h3>主题库</h3>
          <span className="subtle-badge">{topics.length} topics</span>
        </div>
        <div className="item-list">
          {topics.map((topic) => (
            <article className="list-item" key={topic.id}>
              <div>
                <strong>{topic.name}</strong>
                <p>{topic.description || '暂无描述'}</p>
                <p className="topic-meta">
                  <span>关联文章 {topic.articleCount ?? 0}</span>
                  <span>关联简报 {topic.briefCount ?? 0}</span>
                </p>
                {topic.terms && <p className="topic-terms">{topic.terms}</p>}
                {topic.markdownPath && <p className="vault-path">{topic.markdownPath}</p>}
              </div>
              <div className="item-actions">
                <button className="compact-button" onClick={() => onOpenTopic(topic.id)}>查看详情</button>
                <span className="badge">{topic.status}</span>
              </div>
            </article>
          ))}
        </div>
      </section>
    </section>
  );
}

function LearningView({
  topics,
  topicDetail,
  onOpenTopic,
  onChanged,
  setMessage
}: {
  topics: Topic[];
  topicDetail: TopicDetail | null;
  onOpenTopic: (topicId: number) => Promise<void>;
  onChanged: () => Promise<void>;
  setMessage: (message: string) => void;
}) {
  const [status, setStatus] = useState('LEARNING');
  const [note, setNote] = useState('');
  const activeTopic = topicDetail?.topic;

  async function appendNote(event: FormEvent) {
    event.preventDefault();
    if (!activeTopic) return;
    await api(`/api/topics/${activeTopic.id}/notes`, {
      method: 'POST',
      body: JSON.stringify({ status, note })
    });
    setNote('');
    setMessage('个人理解已写入主题 Markdown');
    await onChanged();
    await onOpenTopic(activeTopic.id);
  }

  return (
    <section className="learning-grid">
      <section className="panel learning-queue">
        <h3>学习队列</h3>
        <div className="item-list">
          {topics.map((topic) => (
            <article className="list-item" key={topic.id}>
              <div>
                <strong>{topic.name}</strong>
                <p>{topic.description || '暂无描述'}</p>
                <ul className="question-list">
                  {splitLines(topic.learningQuestions).slice(0, 2).map((question) => (
                    <li key={question}>{question}</li>
                  ))}
                </ul>
              </div>
              <button className="compact-button" onClick={() => onOpenTopic(topic.id)}>记录理解</button>
            </article>
          ))}
        </div>
      </section>

      <section className="panel detail-panel">
        {!activeTopic ? (
          <p className="muted">选择一个主题后记录自己的理解。</p>
        ) : (
          <>
            <div className="panel-heading">
              <div>
                <h3>{activeTopic.name}</h3>
                <p className="muted">{activeTopic.markdownPath}</p>
              </div>
              <span className="badge">{activeTopic.status}</span>
            </div>
            <p>{activeTopic.description}</p>
            <div className="topic-links">
              <div>
                <strong>关联文章</strong>
                <ul>
                  {topicDetail.linkedArticles.map((article) => (
                    <li key={article.id}>{article.title}</li>
                  ))}
                </ul>
              </div>
              <div>
                <strong>关联简报</strong>
                <ul>
                  {topicDetail.linkedBriefs.map((brief) => (
                    <li key={brief.id}>{brief.title}</li>
                  ))}
                </ul>
              </div>
            </div>
            <form className="note-form" onSubmit={appendNote}>
              <label>
                学习状态
                <select value={status} onChange={(event) => setStatus(event.target.value)}>
                  <option value="LEARNING">学习中</option>
                  <option value="REVIEWING">复盘中</option>
                  <option value="MATURE">可输出</option>
                </select>
              </label>
              <label>
                个人理解
                <textarea value={note} onChange={(event) => setNote(event.target.value)} required rows={5} />
              </label>
              <button className="primary-button" type="submit">保存理解</button>
            </form>
            <pre className="markdown-preview">{topicDetail.markdown}</pre>
          </>
        )}
      </section>
    </section>
  );
}

function AgentRunsView({ agentRuns }: { agentRuns: AgentRun[] }) {
  return (
    <section className="panel wide">
      <div className="panel-heading">
        <h3>Agent Trace</h3>
        <span className="subtle-badge">{agentRuns.length} runs</span>
      </div>
      <Table
        headers={['节点', '状态', '耗时', '错误']}
        rows={agentRuns.map((run) => [run.nodeName, run.status, `${run.durationMs}ms`, run.errorMessage || '-'])}
        empty="暂无 Agent 运行记录"
      />
    </section>
  );
}

function SettingsView({ setMessage }: { setMessage: (message: string) => void }) {
  async function exportData() {
    const result = await api<{ path: string }>('/api/exports', { method: 'POST' });
    setMessage(`导出完成：${result.path}`);
  }

  return (
    <section className="panel wide">
      <h3>导出与恢复</h3>
      <p className="muted">本地优先存储，导出包包含 SQLite、Markdown Vault 与 manifest。</p>
      <button className="primary-button" onClick={exportData}>生成备份包</button>
    </section>
  );
}

function splitLines(value?: string) {
  if (!value) {
    return [];
  }
  return value.split('\n').map((item) => item.trim()).filter(Boolean);
}

function ArticleCard({
  article,
  isExpanded,
  onToggle,
  onCompound,
  onDelete,
  categoryColor
}: {
  article: Article;
  isExpanded: boolean;
  onToggle: () => void;
  onCompound: () => void;
  onDelete: () => void;
  categoryColor: string;
}) {
  return (
    <div className="article-card">
      <div className="article-card-header" onClick={onToggle}>
        <span className="article-category-tag" style={{ backgroundColor: categoryColor }}>
          {article.category || '市场'}
        </span>
        <div className="article-card-main">
          <h4 className="article-title">{article.title}</h4>
          <div className="article-meta">
            <span>{article.sourceName}</span>
            <span>·</span>
            {article.noveltyType && (
              <span className={`badge ${article.noveltyType.toLowerCase()}`}>{article.noveltyType}</span>
            )}
          </div>
          {article.summary && (
            <p className="article-summary">{article.summary}</p>
          )}
        </div>
        <span className="article-expand-icon">
          {isExpanded ? '▼' : '▶'}
        </span>
      </div>

      {isExpanded && (
        <div className="article-card-expanded">
          {article.body && (
            <div className="article-body-content">
              <h5>原文内容</h5>
              <div className="markdown-content">
                {article.body}
              </div>
            </div>
          )}

          {article.insightCard && (
            <div className="insight-section">
              <h5>AI 解读</h5>
              <InsightCardPreview card={article.insightCard} />
            </div>
          )}

          <div className="article-card-actions">
            <button className="primary-button" onClick={(e) => { e.stopPropagation(); onCompound(); }}>
              沉淀到主题库
            </button>
            {article.url && (
              <a href={article.url} target="_blank" rel="noopener noreferrer" className="link-button">
                查看原文
              </a>
            )}
            <button
              className="danger-button"
              onClick={(e) => { e.stopPropagation(); onDelete(); }}
              style={{ marginLeft: 'auto' }}
            >
              删除
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

function Table({ headers, rows, empty }: { headers: string[]; rows: string[][]; empty: string }) {
  if (rows.length === 0) {
    return <p className="muted">{empty}</p>;
  }
  return (
    <table>
      <thead>
        <tr>{headers.map((header) => <th key={header}>{header}</th>)}</tr>
      </thead>
      <tbody>
        {rows.map((row, index) => (
          <tr key={index}>
            {row.map((cell, cellIndex) => <td key={cellIndex}>{cell}</td>)}
          </tr>
        ))}
      </tbody>
    </table>
  );
}
