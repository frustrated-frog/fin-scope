import { FormEvent, useEffect, useRef, useState } from 'react';

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
  const [busySourceId, setBusySourceId] = useState<number | null>(null);
  const [fetchTask, setFetchTask] = useState<AsyncTask | null>(null);
  const fetchChannelRef = useRef<IngestTaskChannel | null>(null);

  useEffect(() => () => fetchChannelRef.current?.dispose(), []);

  async function submit(event: FormEvent) {
    event.preventDefault();
    const payload = {
      ...form,
      maxItemsPerRun: Number(form.maxItemsPerRun || 10),
      credibility: Number(form.credibility || 3),
      fetchFrequencyMinutes: Number(form.fetchFrequencyMinutes || 60)
    };
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

  async function deleteSource(id?: number) {
    if (!id) {
      return;
    }
    setBusySourceId(id);
    try {
      await api<void>(`/api/sources/${id}`, { method: 'DELETE' });
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
  }

  function recentBatch(sourceId?: number) {
    return fetchBatches.find((batch) => batch.sourceId === sourceId);
  }

  return (
    <section className="split">
      <form className="panel form-panel" onSubmit={submit}>
        <div className="panel-heading">
          <h3>{editingId ? '编辑信息源' : '新增信息源'}</h3>
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
        <div className="source-form-grid">
          <label>
            可信度
            <input
              type="number"
              min={1}
              max={5}
              value={form.credibility}
              onChange={(event) => setForm({ ...form, credibility: Number(event.target.value) })}
            />
          </label>
          <label>
            每次抓取条数
            <input
              type="number"
              min={1}
              max={50}
              value={form.maxItemsPerRun ?? 10}
              onChange={(event) => setForm({ ...form, maxItemsPerRun: Number(event.target.value) })}
            />
          </label>
        </div>
        <label>
          每天抓取时间
          <input
            value={form.scheduleTimes || ''}
            placeholder="08:30,18:00"
            onChange={(event) => setForm({ ...form, scheduleTimes: event.target.value })}
          />
        </label>
        <label className="checkbox-row">
          <input
            type="checkbox"
            checked={Boolean(form.scheduledEnabled)}
            onChange={(event) => setForm({ ...form, scheduledEnabled: event.target.checked })}
          />
          <span className="checkbox-box" aria-hidden="true" />
          <span className="checkbox-text">开启定时抓取</span>
        </label>
        <label className="checkbox-row">
          <input
            type="checkbox"
            checked={Boolean(form.enabled)}
            onChange={(event) => setForm({ ...form, enabled: event.target.checked })}
          />
          <span className="checkbox-box" aria-hidden="true" />
          <span className="checkbox-text">启用信息源</span>
        </label>
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
          <button className="primary-button" type="submit">{editingId ? '更新信息源' : '保存信息源'}</button>
        </div>
      </form>

      <section className="panel">
        <div className="panel-heading">
          <h3>已配置信息源</h3>
          <span className="subtle-badge">{sources.filter((source) => source.enabled).length} active</span>
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
                  <div className="source-title-row">
                    <h4>{source.name}</h4>
                    <span className="badge">{source.type}</span>
                    {!source.enabled && <span className="subtle-badge">Disabled</span>}
                  </div>
                  <p>
                    <span>{source.tags || '未标记'}</span>
                    <span> · 可信度 {source.credibility}</span>
                    <span> · </span>
                    <span>{`${source.maxItemsPerRun ?? 10} 条/次`}</span>
                  </p>
                  <p>
                    <span>定时：</span>
                    <span>{source.scheduledEnabled ? source.scheduleTimes || '未配置' : '关闭'}</span>
                  </p>
                  {recentBatch(source.id) && (
                    <p className="source-batch-summary">
                      最近批次：{recentBatch(source.id)?.status} · {recentBatch(source.id)?.candidateCount ?? 0} 条候选
                    </p>
                  )}
                  {fetchTask && busySourceId === source.id && (
                    <p className="source-batch-summary" role="status">
                      正在处理：{fetchTask.message || fetchTask.phase || '等待开始'}
                    </p>
                  )}
                </div>
                <div className="source-actions">
                  <button
                    className="compact-button"
                    type="button"
                    disabled={busySourceId !== null || !source.enabled}
                    onClick={() => intakeFetchSource(source.id)}
                  >
                    抓取
                  </button>
                  <button
                    className="secondary-button"
                    type="button"
                    aria-label={`编辑-${source.id}`}
                    onClick={() => editSource(source)}
                  >
                    编辑
                  </button>
                  <button
                    className="danger-button"
                    type="button"
                    aria-label={`删除-${source.id}`}
                    disabled={busySourceId === source.id}
                    onClick={() => deleteSource(source.id)}
                  >
                    归档
                  </button>
                </div>
              </div>
            ))
          )}
        </div>
      </section>
    </section>
  );
}
