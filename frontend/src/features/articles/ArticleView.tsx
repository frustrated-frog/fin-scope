import { FormEvent, useEffect, useRef, useState } from 'react';

import { api } from '../../shared/api/client';
import { Article, AsyncTask, PageResponse } from '../../shared/types';
import { ArticleCard } from './ArticleCard';
import { createIngestTaskChannel, IngestTaskChannel } from './ingestTaskChannel';

type IngestStatus = 'idle' | 'loading' | 'success' | 'error';

const ARTICLE_CATEGORIES = ['金融', '市场', '自我提升', '前沿技术'];

export function ArticleView({
  onWorkspaceChanged,
  addToast
}: {
  onWorkspaceChanged: () => Promise<void>;
  addToast: (message: string, type?: 'success' | 'error' | 'info') => void;
}) {
  const [currentPage, setCurrentPage] = useState(0);
  const [pageSize] = useState(20);
  const [totalCount, setTotalCount] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [pagedArticles, setPagedArticles] = useState<Article[]>([]);
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const [selectAll, setSelectAll] = useState(false);
  const [urlForm, setUrlForm] = useState({ url: '', sourceName: '手动研究', tags: '市场', category: '市场' });
  const [expandedArticleId, setExpandedArticleId] = useState<number | null>(null);
  const [highlightedArticleId, setHighlightedArticleId] = useState<number | null>(null);
  const [ingestStatus, setIngestStatus] = useState<IngestStatus>('idle');
  const [ingestMessage, setIngestMessage] = useState('');
  const [ingestError, setIngestError] = useState('');
  const [showDeleteConfirm, setShowDeleteConfirm] = useState<number | null>(null);
  const [showBatchDeleteConfirm, setShowBatchDeleteConfirm] = useState(false);
  const highlightClearTimerRef = useRef<number | null>(null);
  const ingestChannelRef = useRef<IngestTaskChannel | null>(null);
  const ingestGenerationRef = useRef(0);
  const articleFetchGenerationRef = useRef(0);

  const fetchArticles = async (generation?: number, page = currentPage) => {
    const fetchGeneration = ++articleFetchGenerationRef.current;
    try {
      const response = await api<PageResponse<Article>>(`/api/articles/paged?page=${page}&pageSize=${pageSize}`);
      if (fetchGeneration !== articleFetchGenerationRef.current
        || (generation !== undefined && ingestGenerationRef.current !== generation)) return null;
      if (response.totalCount > 0 && response.items.length === 0 && response.totalPages > 0 && page >= response.totalPages) {
        setCurrentPage(response.totalPages - 1);
        return response;
      }
      setPagedArticles(response.items);
      setTotalCount(response.totalCount);
      setTotalPages(response.totalPages);
      setSelectedIds(new Set());
      setSelectAll(false);
      return response;
    } catch (error) {
      if (generation === undefined || ingestGenerationRef.current === generation) {
        addToast(error instanceof Error ? error.message : 'Failed to load articles', 'error');
      }
      return null;
    }
  };

  useEffect(() => {
    fetchArticles();
  }, [currentPage, pageSize]);

  useEffect(() => () => {
    ingestGenerationRef.current += 1;
    ingestChannelRef.current?.dispose();
    if (highlightClearTimerRef.current !== null) {
      window.clearTimeout(highlightClearTimerRef.current);
    }
  }, []);

  async function submitIngestUrl() {
    if (ingestStatus === 'loading') {
      return;
    }
    const submittedUrl = urlForm.url;
    let submittedTask: AsyncTask | null = null;
    setIngestStatus('loading');
    setIngestMessage('正在提交生成任务');
    setIngestError('');
    const generation = ++ingestGenerationRef.current;
    ingestChannelRef.current?.dispose();
    try {
      const createdTask = await api<AsyncTask>('/api/articles/ingest-url', {
        method: 'POST',
        body: JSON.stringify(urlForm)
      });
      submittedTask = createdTask;
      if (ingestGenerationRef.current !== generation) return;
      const channel = createIngestTaskChannel(createdTask, {
        fetchTask: () => api<AsyncTask>(`/api/tasks/${createdTask.taskId}`),
        onProgress: (task) => {
          if (ingestGenerationRef.current === generation) updateIngestTaskProgress(task);
        }
      });
      ingestChannelRef.current = channel;
      const completedTask = await channel.completion;
      if (ingestGenerationRef.current !== generation) return;
      if (completedTask.status === 'FAILED') {
        throw new Error(completedTask.errorMessage || completedTask.message || 'URL 解析失败');
      }
      const refreshed = await fetchArticles(generation);
      if (ingestGenerationRef.current !== generation) return;
      await finishSuccessfulIngest(completedTask, refreshed, submittedUrl, generation);
    } catch (error) {
      if (ingestGenerationRef.current !== generation) return;
      if (isIngestTaskTimeout(error)) {
        const recovered = await recoverTimedOutIngest(submittedTask, generation);
        if (ingestGenerationRef.current !== generation) return;
        if (recovered) {
          return;
        }
      }
      const message = error instanceof Error ? error.message : 'URL 解析失败';
      setIngestStatus('error');
      setIngestError(message);
      addToast(message, 'error');
    } finally {
      if (ingestGenerationRef.current === generation) ingestChannelRef.current = null;
    }
  }

  async function ingestUrl(event: FormEvent) {
    event.preventDefault();
    await submitIngestUrl();
  }

  async function finishSuccessfulIngest(task: AsyncTask,
                                        refreshed: PageResponse<Article> | null,
                                        submittedUrl: string,
                                        generation?: number) {
    if (generation !== undefined && ingestGenerationRef.current !== generation) return false;
    const generatedArticleId = findGeneratedArticleId(task.article, refreshed, submittedUrl);
    if (generatedArticleId !== null) {
      highlightGeneratedArticle(generatedArticleId);
    }
    setUrlForm((current) => ({ ...current, url: '' }));
    setIngestStatus('success');
    setIngestMessage(task.message || messageForTaskPhase(task.phase));
    addToast('情报卡片已生成，已加入文章列表', 'success');
    await syncWorkspaceAfterSuccessfulIngest(generation);
    return generation === undefined || ingestGenerationRef.current === generation;
  }

  async function recoverTimedOutIngest(task: AsyncTask | null, generation: number) {
    if (!task?.taskId) {
      return false;
    }
    const taskSnapshot = await api<AsyncTask>(`/api/tasks/${task.taskId}`);
    if (taskSnapshot.status !== 'COMPLETED' || (taskSnapshot.articleId == null && taskSnapshot.article?.id == null)) {
      return false;
    }
    const refreshed = await fetchArticles(generation);
    if (ingestGenerationRef.current !== generation) return false;
    return finishSuccessfulIngest(taskSnapshot, refreshed, '', generation);
  }

  async function syncWorkspaceAfterSuccessfulIngest(generation?: number) {
    try {
      await onWorkspaceChanged();
    } catch (error) {
      if (generation !== undefined && ingestGenerationRef.current !== generation) return;
      const message = error instanceof Error ? error.message : '刷新失败';
      addToast(`卡片已生成，但工作区数据刷新失败：${message}`, 'error');
    }
  }

  function isIngestTaskTimeout(error: unknown) {
    return error instanceof Error && error.message === '生成任务超时，请稍后重试';
  }

  function updateIngestTaskProgress(task: AsyncTask) {
    setIngestMessage(task.message || messageForTaskPhase(task.phase));
    if (task.status === 'FAILED') {
      setIngestStatus('error');
      setIngestError(task.errorMessage || task.message || 'URL 解析失败');
      return;
    }
    if (task.status === 'COMPLETED') {
      setIngestStatus('success');
      return;
    }
    setIngestStatus('loading');
  }

  function highlightGeneratedArticle(articleId: number) {
    setExpandedArticleId(articleId);
    setHighlightedArticleId(articleId);
    if (highlightClearTimerRef.current !== null) {
      window.clearTimeout(highlightClearTimerRef.current);
    }
    highlightClearTimerRef.current = window.setTimeout(() => {
      setHighlightedArticleId((currentArticleId) => currentArticleId === articleId ? null : currentArticleId);
      highlightClearTimerRef.current = null;
    }, 5000);
  }

  async function deleteArticle(id: number) {
    try {
      await api(`/api/articles/${id}`, { method: 'DELETE' });
      addToast('文章已删除', 'success');
      await fetchArticles();
      await onWorkspaceChanged();
      setShowDeleteConfirm(null);
    } catch (error) {
      addToast(error instanceof Error ? error.message : '删除失败', 'error');
    }
  }

  async function deleteSelected() {
    if (selectedIds.size === 0) {
      return;
    }

    try {
      await api('/api/articles/batch', {
        method: 'DELETE',
        body: JSON.stringify({ ids: Array.from(selectedIds) })
      });
      addToast(`已删除 ${selectedIds.size} 篇文章`, 'success');
      await fetchArticles();
      await onWorkspaceChanged();
      setShowBatchDeleteConfirm(false);
    } catch (error) {
      addToast(error instanceof Error ? error.message : '批量删除失败', 'error');
    }
  }

  function toggleSelect(id: number) {
    const nextSelected = new Set(selectedIds);
    if (nextSelected.has(id)) {
      nextSelected.delete(id);
    } else {
      nextSelected.add(id);
    }
    setSelectedIds(nextSelected);
    setSelectAll(nextSelected.size === pagedArticles.length);
  }

  function toggleSelectAll() {
    if (selectAll) {
      setSelectedIds(new Set());
      setSelectAll(false);
      return;
    }
    setSelectedIds(new Set(pagedArticles.map((article) => article.id)));
    setSelectAll(true);
  }

  function goToPage(page: number) {
    if (page >= 0 && page < totalPages) {
      setCurrentPage(page);
    }
  }

  function getCategoryColor(category?: string) {
    switch (category) {
      case '金融':
      case '宏观':
        return '#10b981';
      case '市场':
        return '#2563eb';
      case '自我提升':
        return '#8b5cf6';
      case '前沿技术':
        return '#ec4899';
      case '政策':
        return '#3b82f6';
      case '公司':
        return '#8b5cf6';
      case '行业':
        return '#f59e0b';
      case '科技':
        return '#ec4899';
      default:
        return '#6b7280';
    }
  }

  function findGeneratedArticleId(article: Article | undefined,
                                  refreshed: PageResponse<Article> | null,
                                  submittedUrl: string) {
    if (article?.id != null) {
      return article.id;
    }
    const matched = refreshed?.items.find((item) => item.url === submittedUrl);
    return matched?.id ?? null;
  }

  function messageForTaskPhase(phase?: AsyncTask['phase']) {
    switch (phase) {
      case 'FETCHING':
        return '正在抓取网页';
      case 'PARSING':
        return '正在解析正文';
      case 'LLM':
        return '正在生成情报卡片';
      case 'PERSISTING':
        return '正在写入文章库';
      case 'COMPLETED':
        return '情报卡片已生成，已加入文章列表';
      case 'FAILED':
        return '生成失败';
      default:
        return '正在排队等待生成';
    }
  }

  const isIngesting = ingestStatus === 'loading';
  const visibleStart = totalCount === 0 ? 0 : currentPage * pageSize + 1;
  const visibleEnd = Math.min(totalCount, currentPage * pageSize + pagedArticles.length);
  const selectedCount = selectedIds.size;
  const primaryCategory = pagedArticles[0]?.category || '市场';

  return (
    <section className="article-container article-command-center">
      <header className="article-control-hero article-command-hero">
        <p className="eyebrow">Signal intake</p>
        <h3>文章情报台</h3>
        <p>把外部链接转化为可追踪、可检索的阅读资产，独立保存在文章知识库。</p>
        <div className="article-hero-readouts" aria-label="文章总览">
          <span><small>当前队列</small><strong>{totalCount}</strong></span>
          <span><small>本页信号</small><strong>{pagedArticles.length}</strong></span>
          <span><small>已选择</small><strong>{selectedCount}</strong></span>
        </div>
      </header>

      <aside className="article-signal-panel" aria-label="文章情报控制台">
        <div className="article-ingest-panel">
          <div className="article-panel-heading">
            <span>入口</span>
            <strong>URL 采集</strong>
          </div>
          <form className="url-ingest-form" onSubmit={ingestUrl}>
            <label className="article-url-field">
              <span>文章链接</span>
              <input
                type="url"
                value={urlForm.url}
                onChange={(event) => setUrlForm({ ...urlForm, url: event.target.value })}
                placeholder="输入文章URL..."
                disabled={isIngesting}
                required
              />
            </label>
            <div className="article-category-wrap">
              <span>内容类型</span>
              <div className="article-category-segment" aria-label="文章类型">
                {ARTICLE_CATEGORIES.map((category) => (
                  <button
                    key={category}
                    type="button"
                    className={`article-category-option${urlForm.category === category ? ' is-active' : ''}`}
                    aria-pressed={urlForm.category === category}
                    disabled={isIngesting}
                    onClick={() => setUrlForm({ ...urlForm, category })}
                  >
                    {category}
                  </button>
                ))}
              </div>
            </div>
            <button className="primary-button" type="submit" disabled={isIngesting}>
              {isIngesting ? '生成中...' : '生成情报卡片'}
            </button>
          </form>
          {ingestStatus !== 'idle' && (
            <div className={`url-ingest-status url-ingest-status-${ingestStatus}`} role="status">
              <div className="url-ingest-status-main">
                <strong>
                  {ingestStatus === 'loading' && '正在抓取网页、解析正文并生成情报卡片'}
                  {ingestStatus === 'success' && '生成完成'}
                  {ingestStatus === 'error' && '生成失败'}
                </strong>
                {ingestStatus === 'loading' && (
                  <span>{ingestMessage || '复杂页面可能需要几十秒，可继续浏览下方文章。'}</span>
                )}
                {ingestStatus === 'success' && (
                  <span>{ingestMessage || '新卡片已展开并加入文章列表。'}</span>
                )}
                {ingestStatus === 'error' && (
                  <span>{ingestError}</span>
                )}
              </div>
              {ingestStatus === 'loading' && (
                <div className="url-ingest-steps" aria-label="生成进度">
                  <span>抓取网页</span>
                  <span>提取正文</span>
                  <span>生成卡片</span>
                </div>
              )}
              {ingestStatus === 'error' && (
                <button className="secondary-button" type="button" onClick={submitIngestUrl}>
                  重试
                </button>
              )}
            </div>
          )}
        </div>

        <div className="article-queue-panel">
          <div className="article-panel-heading">
            <span>队列</span>
            <strong>{totalCount ? `${visibleStart}-${visibleEnd}` : '0'} / {totalCount}</strong>
          </div>
          <div className="article-queue-matrix">
            <span><small>主分类</small><strong>{primaryCategory}</strong></span>
            <span><small>页码</small><strong>{Math.max(1, currentPage + 1)} / {Math.max(1, totalPages)}</strong></span>
          </div>
          {pagedArticles.length > 0 && (
            <div className="batch-toolbar">
              <label className="article-select-all">
                <input
                  type="checkbox"
                  aria-label="全选文章"
                  checked={selectAll}
                  onChange={toggleSelectAll}
                />
                <span>全选当前页</span>
              </label>
              {selectedIds.size > 0 && (
                <>
                  <span className="badge selection-pill">
                    已选 {selectedIds.size} 项
                  </span>
                  <button
                    className="danger-button selection-pill"
                    onClick={() => setShowBatchDeleteConfirm(true)}
                  >
                    删除所选
                  </button>
                </>
              )}
            </div>
          )}
        </div>
      </aside>

      <main className="article-stream-panel">
        <div className="article-stream-head">
          <div>
            <p className="eyebrow">Article signal stream</p>
            <h3>情报卡片流</h3>
          </div>
          <span className="subtle-badge">{totalCount} active signals</span>
        </div>

        <div className="articles-list">
          {pagedArticles.length === 0 ? (
            <div className="article-empty-panel">
              <strong>暂无文章</strong>
              <p>添加 URL 后，系统会抓取正文并生成结构化情报卡片。</p>
            </div>
          ) : (
            pagedArticles.map((article) => (
              <div className="article-row-shell" key={article.id}>
                <input
                  type="checkbox"
                  aria-label={`选择文章-${article.id}`}
                  checked={selectedIds.has(article.id)}
                  onChange={() => toggleSelect(article.id)}
                />
                <ArticleCard
                  article={article}
                  isExpanded={expandedArticleId === article.id}
                  isHighlighted={highlightedArticleId === article.id}
                  onToggle={() => setExpandedArticleId(expandedArticleId === article.id ? null : article.id)}
                  onDelete={() => setShowDeleteConfirm(article.id)}
                  categoryColor={getCategoryColor(article.category)}
                />
              </div>
            ))
          )}
        </div>

        {totalPages > 1 && (
          <div className="pagination-controls article-pagination">
            <div>
              <strong>第 {currentPage + 1} / {totalPages} 页</strong>
              <span>显示 {visibleStart}-{visibleEnd}，共 {totalCount} 篇</span>
            </div>
            <div className="article-pagination-actions">
              <button
                className="secondary-button"
                onClick={() => goToPage(currentPage - 1)}
                disabled={currentPage === 0}
              >
                上一页
              </button>
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
      </main>

      {showDeleteConfirm !== null && (
        <div className="modal-overlay">
          <div className="modal" role="dialog" aria-modal="true" aria-labelledby="delete-article-title">
            <div className="modal-header">
              <h4 id="delete-article-title">确认删除</h4>
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
          <div className="modal" role="dialog" aria-modal="true" aria-labelledby="delete-articles-title">
            <div className="modal-header">
              <h4 id="delete-articles-title">批量删除确认</h4>
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
