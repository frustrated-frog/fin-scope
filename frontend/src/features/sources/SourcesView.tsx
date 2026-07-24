import { FormEvent, useEffect, useMemo, useRef, useState } from 'react';

import { api } from '../../shared/api/client';
import { AsyncTask, FetchBatch, Source } from '../../shared/types';
import { createIngestTaskChannel, IngestTaskChannel } from '../articles/ingestTaskChannel';

const EMPTY_SOURCE: Source = {
  name: '',
  type: 'RSS',
  url: '',
  enabled: true,
  fetchFrequencyMinutes: 60,
  credibility: 3,
  tags: '',
  maxItemsPerRun: 10,
  scheduleTimes: '08:30',
  scheduledEnabled: false
};

const CREDIBILITY_LEVELS = [1, 2, 3, 4, 5];

function clampNumber(value: number, min: number, max: number, fallback: number) {
  if (!Number.isFinite(value)) {
    return fallback;
  }
  return Math.min(max, Math.max(min, value));
}

function splitTags(tags?: string) {
  return (tags || '')
    .split(/[,，]/)
    .map((tag) => tag.trim())
    .filter(Boolean)
    .slice(0, 6);
}

function scheduleLabel(source: Source) {
  if (!source.scheduledEnabled) {
    return '关闭';
  }
  return source.scheduleTimes || '未配置';
}

export function SourcesView({
  sources,
  fetchBatches,
  onChanged,
  addToast
}: {
  sources: Source[];
  fetchBatches: FetchBatch[];
  onChanged: () => Promise<void>;
  addToast: (message: string, type?: 'success' | 'error' | 'info') => void;
}) {
  const [form, setForm] = useState<Source>(EMPTY_SOURCE);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [saving, setSaving] = useState(false);
  const [busySourceId, setBusySourceId] = useState<number | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<Source | null>(null);
  const [fetchTask, setFetchTask] = useState<AsyncTask | null>(null);
  const [sourceQuery, setSourceQuery] = useState('');
  const [sourceStatus, setSourceStatus] = useState<'ALL' | 'ENABLED' | 'DISABLED'>('ALL');
  const fetchChannelRef = useRef<IngestTaskChannel | null>(null);
  const formPanelRef = useRef<HTMLFormElement | null>(null);

  useEffect(() => () => fetchChannelRef.current?.dispose(), []);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (saving) {
      return;
    }
    const payload = {
      ...form,
      maxItemsPerRun: Number(form.maxItemsPerRun || 10),
      credibility: Number(form.credibility || 3),
      fetchFrequencyMinutes: Number(form.fetchFrequencyMinutes || 60)
    };
    setSaving(true);
    try {
      await api<Source>(editingId ? `/api/sources/${editingId}` : '/api/sources', {
        method: editingId ? 'PUT' : 'POST',
        body: JSON.stringify(payload)
      });
      setForm(EMPTY_SOURCE);
      setEditingId(null);
      addToast(editingId ? '信息源已更新' : '信息源已保存', 'success');
      await onChanged();
    } catch (error) {
      addToast(error instanceof Error ? error.message : '信息源保存失败', 'error');
    } finally {
      setSaving(false);
    }
  }

  async function intakeFetchSource(id?: number) {
    if (!id) {
      return;
    }
    setBusySourceId(id);
    setFetchTask(null);
    try {
      const task = await api<AsyncTask>(`/api/sources/${id}/intake-fetch-async`, { method: 'POST' });
      setFetchTask(task);
      fetchChannelRef.current?.dispose();
      const channel = createIngestTaskChannel(task, {
        fetchTask: () => api<AsyncTask>(`/api/tasks/${task.taskId}`),
        timeoutMs: 5 * 60 * 1000,
        onProgress: setFetchTask
      });
      fetchChannelRef.current = channel;
      const completed = await channel.completion;
      setFetchTask(completed);
      if (completed.status === 'FAILED') throw new Error(completed.errorMessage || completed.message || '摄入候选失败');
      addToast(completed.message || '已抓取到候选池', 'success');
      await onChanged();
    } catch (error) {
      addToast(error instanceof Error ? error.message : '摄入候选失败', 'error');
    } finally {
      fetchChannelRef.current = null;
      setBusySourceId(null);
    }
  }

  async function deleteSource(source: Source) {
    const id = source.id;
    if (!id) {
      return;
    }
    setBusySourceId(id);
    try {
      await api<void>(`/api/sources/${id}`, { method: 'DELETE' });
      if (editingId === id) {
        setEditingId(null);
        setForm(EMPTY_SOURCE);
      }
      setDeleteTarget(null);
      addToast('信息源已删除', 'success');
      await onChanged();
    } catch (error) {
      addToast(error instanceof Error ? error.message : '信息源删除失败', 'error');
    } finally {
      setBusySourceId(null);
    }
  }

  function editSource(source: Source) {
    setEditingId(source.id ?? null);
    setForm({
      ...EMPTY_SOURCE,
      ...source,
      maxItemsPerRun: source.maxItemsPerRun ?? 10,
      scheduleTimes: source.scheduleTimes || '08:30',
      scheduledEnabled: Boolean(source.scheduledEnabled)
    });
    if (window.matchMedia?.('(max-width: 1180px)').matches) {
      window.requestAnimationFrame(() => formPanelRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' }));
    }
  }

  function recentBatch(sourceId?: number) {
    return fetchBatches.find((batch) => batch.sourceId === sourceId);
  }

  function setCredibility(value: number) {
    setForm((current) => ({
      ...current,
      credibility: clampNumber(value, 1, 5, current.credibility || 3)
    }));
  }

  function setMaxItemsPerRun(value: number) {
    setForm((current) => ({
      ...current,
      maxItemsPerRun: clampNumber(value, 1, 50, current.maxItemsPerRun ?? 10)
    }));
  }

  const activeCount = sources.filter((source) => source.enabled).length;
  const scheduledCount = sources.filter((source) => source.enabled && source.scheduledEnabled).length;
  const typeCount = new Set(sources.map((source) => source.type)).size;
  const latestBatch = fetchBatches[0];
  const filteredSources = useMemo(() => {
    const normalizedQuery = sourceQuery.trim().toLowerCase();
    return sources.filter((source) => {
      const matchesStatus = sourceStatus === 'ALL'
        || (sourceStatus === 'ENABLED' ? source.enabled : !source.enabled);
      const searchable = [source.name, source.type, source.url, source.tags].filter(Boolean).join(' ').toLowerCase();
      return matchesStatus && (!normalizedQuery || searchable.includes(normalizedQuery));
    });
  }, [sourceQuery, sourceStatus, sources]);

  return (
    <section className="source-page">
      <header className="source-command-hero">
        <div className="source-command-copy">
          <p className="source-command-kicker">Source intelligence</p>
          <h2>建立稳定、可信的信息入口</h2>
          <p>让每个来源都有清晰的可信度、抓取节奏和运行记录。少追热点，多维护真正值得长期跟踪的渠道。</p>
        </div>
        <div className="source-command-overview" aria-label="信息源健康概览">
          <article>
            <span>启用来源</span>
            <strong>{activeCount}<small>/{sources.length}</small></strong>
            <p>当前参与采集</p>
          </article>
          <article>
            <span>定时运行</span>
            <strong>{scheduledCount}</strong>
            <p>已配置自动节奏</p>
          </article>
          <article>
            <span>来源类型</span>
            <strong>{typeCount}</strong>
            <p>{latestBatch ? `最近批次 ${latestBatch.status}` : '等待首次抓取'}</p>
          </article>
        </div>
      </header>

      <div className="source-workspace">
      <form ref={formPanelRef} className="panel source-form-panel source-material" onSubmit={submit} aria-busy={saving}>
        <div className="source-form-head">
          <div>
            <span>采集入口</span>
            <h3>{editingId ? '编辑信息源' : '新增信息源'}</h3>
          </div>
          <span className={editingId ? 'source-mode-pill is-editing' : 'source-mode-pill'}>
            {editingId ? '编辑模式' : '新建'}
          </span>
        </div>

        <div className="source-form-section">
          <span className="source-section-label">基础信息</span>
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
              <option value="WEB">网页</option>
              <option value="WEB_LIST">网页列表</option>
              <option value="X_POST">X Post</option>
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
        </div>

        <div className="source-form-section">
          <span className="source-section-label">抓取策略</span>
          <div className="source-form-grid source-strategy-grid">
            <div className="source-control-card source-trust-control" role="group" aria-labelledby="source-credibility-label">
              <span id="source-credibility-label" className="source-control-label">可信度</span>
              <div className="source-rating-control">
                {CREDIBILITY_LEVELS.map((level) => (
                  <button
                    key={level}
                    type="button"
                    className={level <= form.credibility ? 'source-rating-button is-active' : 'source-rating-button'}
                    aria-pressed={form.credibility === level}
                    onClick={() => setCredibility(level)}
                  >
                    {level}
                  </button>
                ))}
              </div>
              <span className="source-control-note">当前 {form.credibility}/5</span>
            </div>
            <div className="source-control-card source-count-control">
              <label id="source-max-items-label" className="source-control-label" htmlFor="source-max-items">每次抓取条数</label>
              <div className="source-stepper">
                <button
                  type="button"
                  aria-label="减少每次抓取条数"
                  onClick={() => setMaxItemsPerRun((form.maxItemsPerRun ?? 10) - 1)}
                >
                  -
                </button>
                <input
                  id="source-max-items"
                  inputMode="numeric"
                  pattern="[0-9]*"
                  value={form.maxItemsPerRun ?? 10}
                  onChange={(event) => setMaxItemsPerRun(Number(event.target.value))}
                />
                <button
                  type="button"
                  aria-label="增加每次抓取条数"
                  onClick={() => setMaxItemsPerRun((form.maxItemsPerRun ?? 10) + 1)}
                >
                  +
                </button>
              </div>
              <span className="source-control-note">1-50 条</span>
            </div>
          </div>
          <label>
            每天抓取时间
            <input
              value={form.scheduleTimes || ''}
              placeholder="08:30,18:00"
              onChange={(event) => setForm({ ...form, scheduleTimes: event.target.value })}
            />
          </label>
          <div className="source-toggle-grid">
            <label className="checkbox-row source-toggle">
              <input
                type="checkbox"
                checked={Boolean(form.scheduledEnabled)}
                onChange={(event) => setForm({ ...form, scheduledEnabled: event.target.checked })}
              />
              <span className="checkbox-box" aria-hidden="true" />
              <span className="checkbox-text">开启定时抓取</span>
            </label>
            <label className="checkbox-row source-toggle">
              <input
                type="checkbox"
                checked={Boolean(form.enabled)}
                onChange={(event) => setForm({ ...form, enabled: event.target.checked })}
              />
              <span className="checkbox-box" aria-hidden="true" />
              <span className="checkbox-text">启用信息源</span>
            </label>
          </div>
        </div>
        <div className="source-form-actions">
          {editingId && (
            <button
              className="secondary-button"
              type="button"
              onClick={() => {
                setEditingId(null);
                setForm(EMPTY_SOURCE);
              }}
            >
              取消编辑
            </button>
          )}
          <button className="primary-button source-save-button" type="submit" disabled={saving} aria-busy={saving}>
            <span aria-hidden="true">{saving ? '◌' : editingId ? '✓' : '+'}</span>
            {saving ? '正在保存…' : editingId ? '更新信息源' : '保存信息源'}
          </button>
        </div>
      </form>

      <section className="panel source-directory-panel source-material">
        <div className="source-directory-head">
          <div>
            <span>信源池</span>
            <h3>已配置信息源</h3>
          </div>
          <span className="source-directory-count">显示 {filteredSources.length} / {sources.length}</span>
        </div>
        <div className="source-directory-toolbar">
          <label className="source-search-field">
            <span className="sr-only">搜索信息源</span>
            <span className="source-search-icon" aria-hidden="true" />
            <input
              type="search"
              value={sourceQuery}
              placeholder="搜索名称、标签或 URL"
              onChange={(event) => setSourceQuery(event.target.value)}
            />
          </label>
          <div className="source-status-filter" role="group" aria-label="信息源状态筛选">
            {([
              ['ALL', '全部'],
              ['ENABLED', '启用'],
              ['DISABLED', '停用']
            ] as const).map(([value, label]) => (
              <button
                key={value}
                type="button"
                aria-pressed={sourceStatus === value}
                className={sourceStatus === value ? 'is-active' : ''}
                onClick={() => setSourceStatus(value)}
              >
                {label}
              </button>
            ))}
          </div>
        </div>
        <div className="item-list source-list">
          {filteredSources.length === 0 ? (
            <div className="empty-state">
              <p className="empty-state-text">{sources.length ? '没有匹配的信息源' : '暂无信息源'}</p>
              <p className="muted">{sources.length ? '试试其他关键词或状态。' : '从左侧添加第一个长期跟踪渠道。'}</p>
            </div>
          ) : (
            filteredSources.map((source) => {
              const batch = recentBatch(source.id);
              const tags = splitTags(source.tags);
              const isBusy = busySourceId === source.id;

              return (
                <article
                  key={source.id}
                  className={`source-item${source.enabled ? '' : ' is-disabled'}${isBusy ? ' is-busy' : ''}`}
                  aria-busy={isBusy}
                >
                  <div className="source-status-rail" aria-hidden="true">
                    <span />
                  </div>
                  <div className="source-info">
                    <div className="source-title-row">
                      <span className="source-kind">{source.type}</span>
                      <h4>{source.name}</h4>
                      <span className={source.enabled ? 'source-state-pill is-on' : 'source-state-pill'}>
                        {source.enabled ? '启用' : '停用'}
                      </span>
                    </div>
                    <p className="source-url-line" title={source.url}>{source.url}</p>
                    <div className="source-tag-row">
                      {tags.length ? tags.map((tag) => <span key={tag}>{tag}</span>) : <span>未标记</span>}
                    </div>
                    <dl className="source-readout">
                      <div>
                        <dt>可信度</dt>
                        <dd>{source.credibility}/5</dd>
                      </div>
                      <div>
                        <dt>条数</dt>
                        <dd>{source.maxItemsPerRun ?? 10} 条/次</dd>
                      </div>
                      <div>
                        <dt>定时</dt>
                        <dd>{scheduleLabel(source)}</dd>
                      </div>
                    </dl>
                    {batch && (
                      <p className="source-batch-summary">
                        最近批次：{batch.status} · {batch.candidateCount ?? 0} 条候选
                      </p>
                    )}
                    {fetchTask && isBusy && (
                      <p className="source-batch-summary is-running" role="status">
                        正在处理：{fetchTask.message || fetchTask.phase || '等待开始'}
                      </p>
                    )}
                  </div>
                  <div className="source-actions">
                    <button
                      className="compact-button source-action-button source-action-fetch"
                      type="button"
                      disabled={busySourceId !== null || !source.enabled}
                      onClick={() => intakeFetchSource(source.id)}
                    >
                      {isBusy ? '抓取中…' : '抓取'}
                    </button>
                    <button
                      className="secondary-button source-action-button source-action-edit"
                      type="button"
                      aria-label={`编辑-${source.id}`}
                      disabled={busySourceId !== null}
                      onClick={() => editSource(source)}
                    >
                      编辑
                    </button>
                    <button
                      className="danger-button source-action-button source-action-delete"
                      type="button"
                      aria-label={`删除-${source.id}`}
                      disabled={busySourceId !== null}
                      onClick={() => setDeleteTarget(source)}
                    >
                      删除
                    </button>
                  </div>
                </article>
              );
            })
          )}
        </div>
      </section>
      {deleteTarget && (
        <div className="modal-overlay source-modal-overlay">
          <div className="modal source-delete-modal" role="dialog" aria-modal="true" aria-labelledby="source-delete-title">
            <div className="modal-header source-delete-header">
              <span className="source-delete-mark" aria-hidden="true">!</span>
              <div>
                <p className="modal-kicker">Confirm action</p>
                <h4 id="source-delete-title">删除信息源</h4>
              </div>
            </div>
            <div className="modal-content source-delete-content">
              <p className="source-delete-name">{deleteTarget.name}</p>
              <p>删除后不会再抓取该信息源；已抓取的文章和研究历史仍会保留。</p>
            </div>
            <div className="modal-actions">
              <button
                className="secondary-button"
                type="button"
                disabled={busySourceId === deleteTarget.id}
                onClick={() => setDeleteTarget(null)}
              >
                取消
              </button>
              <button
                className="danger-button source-delete-confirm-button"
                type="button"
                disabled={busySourceId === deleteTarget.id}
                onClick={() => deleteSource(deleteTarget)}
              >
                {busySourceId === deleteTarget.id ? '删除中' : '确认删除'}
              </button>
            </div>
          </div>
        </div>
      )}
      </div>
    </section>
  );
}
