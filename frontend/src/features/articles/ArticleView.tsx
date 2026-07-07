import { FormEvent, useEffect, useRef, useState } from 'react';

import { api } from '../../shared/api/client';
import { Article, AsyncTask, PageResponse, View } from '../../shared/types';
import { ArticleCard } from './ArticleCard';

type IngestStatus = 'idle' | 'loading' | 'success' | 'error';

const ARTICLE_CATEGORIES = ['金融', '市场', '自我提升', '前沿技术'];

export function ArticleView({
  setView,
  onWorkspaceChanged,
  addToast
}: {
  setView: (view: View) => void;
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

  const fetchArticles = async () => {
    try {
      const response = await api<PageResponse<Article>>(`/api/articles/paged?page=${currentPage}&pageSize=${pageSize}`);
      setPagedArticles(response.items);
      setTotalCount(response.totalCount);
      setTotalPages(response.totalPages);
      setSelectedIds(new Set());
      setSelectAll(false);
      return response;
    } catch (error) {
      addToast(error instanceof Error ? error.message : 'Failed to load articles', 'error');
      return null;
    }
  };

  useEffect(() => {
    fetchArticles();
  }, [currentPage, pageSize]);

  useEffect(() => () => {
    if (highlightClearTimerRef.current !== null) {
      window.clearTimeout(highlightClearTimerRef.current);
    }
  }, []);

  async function submitIngestUrl() {
    if (ingestStatus === 'loading') {
      return;
    }
    const submittedUrl = urlForm.url;
    setIngestStatus('loading');
    setIngestMessage('正在提交生成任务');
    setIngestError('');
    try {
      const submittedTask = await api<AsyncTask>('/api/articles/ingest-url', {
        method: 'POST',
        body: JSON.stringify(urlForm)
      });
      const completedTask = await pollIngestTask(submittedTask);
      if (completedTask.status === 'FAILED') {
        throw new Error(completedTask.errorMessage || completedTask.message || 'URL 解析失败');
      }
      const refreshed = await fetchArticles();
      const generatedArticleId = findGeneratedArticleId(completedTask.article, refreshed, submittedUrl);
      if (generatedArticleId !== null) {
        highlightGeneratedArticle(generatedArticleId);
      }
      setUrlForm((current) => ({ ...current, url: '' }));
      setIngestStatus('success');
      setIngestMessage(completedTask.message || messageForTaskPhase(completedTask.phase));
      addToast('情报卡片已生成，已加入文章列表', 'success');
      await syncWorkspaceAfterSuccessfulIngest();
    } catch (error) {
      const message = error instanceof Error ? error.message : 'URL 解析失败';
      setIngestStatus('error');
      setIngestError(message);
      addToast(message, 'error');
    }
  }

  async function ingestUrl(event: FormEvent) {
    event.preventDefault();
    await submitIngestUrl();
  }

  async function syncWorkspaceAfterSuccessfulIngest() {
    try {
      await onWorkspaceChanged();
    } catch (error) {
      const message = error instanceof Error ? error.message : '刷新失败';
      addToast(`卡片已生成，但工作区数据刷新失败：${message}`, 'error');
    }
  }

  async function pollIngestTask(initialTask: AsyncTask) {
    updateIngestTaskProgress(initialTask);
    for (let attempt = 0; attempt < 60; attempt++) {
      const currentTask = await api<AsyncTask>(`/api/tasks/${initialTask.taskId}`);
      updateIngestTaskProgress(currentTask);
      if (currentTask.status === 'COMPLETED' || currentTask.status === 'FAILED') {
        return currentTask;
      }
      await wait(800);
    }
    throw new Error('生成任务超时，请稍后重试');
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

  function wait(ms: number) {
    return new Promise((resolve) => window.setTimeout(resolve, ms));
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

  async function compoundArticle(articleId: number) {
    await api(`/api/topics/from-article/${articleId}`, { method: 'POST' });
    addToast('文章已沉淀到主题库', 'success');
    await fetchArticles();
    await onWorkspaceChanged();
    setView('topics');
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
    return matched?.id ?? refreshed?.items[0]?.id ?? null;
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

  return (
    <section className="article-container">
      <div className="card">
        <div className="card-header">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div>
              <h3 style={{ margin: 0, fontSize: '20px', fontWeight: 700, color: 'var(--ink)' }}>文章 Article</h3>
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
              disabled={isIngesting}
              required
            />
            <div
              className="article-category-segment"
              aria-label="文章类型"
            >
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
      </div>

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
                <span className="badge selection-pill" style={{ background: 'var(--accent)' }}>
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
        </div>
      )}

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
                isHighlighted={highlightedArticleId === article.id}
                onToggle={() => setExpandedArticleId(expandedArticleId === article.id ? null : article.id)}
                onCompound={() => compoundArticle(article.id)}
                onDelete={() => setShowDeleteConfirm(article.id)}
                categoryColor={getCategoryColor(article.category)}
              />
            </div>
          ))
        )}
      </div>

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
