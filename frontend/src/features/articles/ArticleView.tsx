import { FormEvent, useEffect, useState } from 'react';

import { api } from '../../shared/api/client';
import { Article, PageResponse, View } from '../../shared/types';
import { ArticleCard } from './ArticleCard';

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
  const [urlForm, setUrlForm] = useState({ url: '', sourceName: '手动研究', tags: '市场' });
  const [expandedArticleId, setExpandedArticleId] = useState<number | null>(null);
  const [showDeleteConfirm, setShowDeleteConfirm] = useState<number | null>(null);
  const [showBatchDeleteConfirm, setShowBatchDeleteConfirm] = useState(false);

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
      setUrlForm((current) => ({ ...current, url: '' }));
      addToast('URL 已生成情报卡片', 'success');
      await fetchArticles();
      await onWorkspaceChanged();
    } catch (error) {
      addToast(error instanceof Error ? error.message : 'URL 解析失败', 'error');
    }
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
      case '宏观':
        return '#10b981';
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
              required
            />
            <button className="primary-button" type="submit">生成情报卡片</button>
          </form>
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
