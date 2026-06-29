import { FormEvent, useState } from 'react';

import { api } from '../../shared/api/client';
import { Source } from '../../shared/types';

export function SourcesView({
  sources,
  onChanged,
  addToast
}: {
  sources: Source[];
  onChanged: () => Promise<void>;
  addToast: (message: string, type?: 'success' | 'error' | 'info') => void;
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
    setForm((current) => ({ ...current, name: '', url: '', tags: '' }));
    addToast('信息源已保存', 'success');
    await onChanged();
  }

  async function fetchSource(id?: number) {
    if (!id) {
      return;
    }
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
