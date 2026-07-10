import { useEffect, useMemo, useState } from 'react';

import { api } from '../../shared/api/client';
import { WatchlistItem } from '../../shared/types';

const typeLabels: Record<string, string> = {
  STOCK: '股票',
  FUND: '基金',
  SECTOR: '板块'
};

function formatPct(value?: number) {
  if (value === undefined || value === null) {
    return '--';
  }
  const sign = value > 0 ? '+' : '';
  return `${sign}${value.toFixed(2)}%`;
}

function formatPrice(value?: number) {
  if (value === undefined || value === null) {
    return '--';
  }
  return value.toFixed(2);
}

function changeClass(value?: number) {
  if (value === undefined || value === null || value === 0) {
    return 'watchlist-flat';
  }
  return value > 0 ? 'watchlist-up' : 'watchlist-down';
}

// 成交额（元）格式化为"亿/万"
function formatTurnover(value?: number) {
  if (value === undefined || value === null || value <= 0) {
    return null;
  }
  if (value >= 1e8) {
    return `${(value / 1e8).toFixed(2)}亿`;
  }
  if (value >= 1e4) {
    return `${(value / 1e4).toFixed(0)}万`;
  }
  return `${value.toFixed(0)}`;
}

function formatNum(value?: number) {
  if (value === undefined || value === null) {
    return '--';
  }
  return value.toFixed(2);
}

// 异动：涨跌幅绝对值超过阈值
function isAbnormal(value?: number) {
  return value !== undefined && value !== null && Math.abs(value) >= 5;
}

export function WatchlistView({
  addToast,
  setMessage
}: {
  addToast: (message: string, type?: 'success' | 'error' | 'info') => void;
  setMessage: (message: string) => void;
}) {
  const [items, setItems] = useState<WatchlistItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [code, setCode] = useState('');
  const [type, setType] = useState<'STOCK' | 'FUND' | 'SECTOR'>('STOCK');
  const [group, setGroup] = useState('');
  const [sortByChange, setSortByChange] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  async function load() {
    setLoading(true);
    try {
      const data = await api<WatchlistItem[]>('/api/watchlist');
      setItems(data);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '自选列表加载失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
  }, []);

  const sortedItems = useMemo(() => {
    const clone = [...items];
    if (sortByChange) {
      clone.sort((a, b) => (b.changePct ?? -999) - (a.changePct ?? -999));
    }
    return clone;
  }, [items, sortByChange]);

  async function addItem() {
    if (!code.trim()) {
      addToast('请输入标的代码', 'error');
      return;
    }
    setSubmitting(true);
    try {
      await api('/api/watchlist', {
        method: 'POST',
        body: JSON.stringify({ code: code.trim(), type, groupName: group.trim() || undefined })
      });
      addToast('已加入自选', 'success');
      setCode('');
      setGroup('');
      await load();
    } catch (error) {
      addToast(error instanceof Error ? error.message : '添加失败', 'error');
    } finally {
      setSubmitting(false);
    }
  }

  async function removeItem(id: number) {
    try {
      await api(`/api/watchlist/${id}`, { method: 'DELETE' });
      addToast('已移除', 'info');
      await load();
    } catch (error) {
      addToast(error instanceof Error ? error.message : '移除失败', 'error');
    }
  }

  return (
    <section className="panel wide">
      <div className="panel-heading">
        <h3>我的自选</h3>
        <span className="subtle-badge">{items.length} 标的</span>
      </div>

      <div className="watchlist-add-row">
        <input
          className="watchlist-input"
          placeholder="标的代码，如 600519"
          value={code}
          onChange={(event) => setCode(event.target.value)}
        />
        <select
          className="watchlist-input"
          aria-label="标的类型"
          value={type}
          onChange={(event) => setType(event.target.value as 'STOCK' | 'FUND' | 'SECTOR')}
        >
          <option value="STOCK">股票</option>
          <option value="FUND">基金</option>
          <option value="SECTOR">板块</option>
        </select>
        <input
          className="watchlist-input"
          placeholder="分组（可选）"
          value={group}
          onChange={(event) => setGroup(event.target.value)}
        />
        <button className="primary-button" type="button" disabled={submitting} onClick={addItem}>
          {submitting ? '添加中…' : '加入自选'}
        </button>
        <button className="ghost-button" type="button" onClick={load}>刷新行情</button>
        <label className="watchlist-sort">
          <input
            className="watchlist-toggle-input"
            type="checkbox"
            checked={sortByChange}
            onChange={(event) => setSortByChange(event.target.checked)}
          />
          <span className="watchlist-toggle-track" aria-hidden="true">
            <span className="watchlist-toggle-thumb" />
          </span>
          <span className="watchlist-toggle-label">按涨跌幅排序</span>
        </label>
      </div>

      {loading && items.length === 0 ? (
        <p className="muted">加载中…</p>
      ) : sortedItems.length === 0 ? (
        <p className="muted">还没有自选标的，输入代码加入吧。</p>
      ) : (
        <div className="watchlist-grid">
          {sortedItems.map((item) => (
            <article
              className={`panel watchlist-card${isAbnormal(item.changePct) ? ' watchlist-card-abnormal' : ''}`}
              key={item.id}
            >
              <button
                className="watchlist-remove"
                type="button"
                aria-label={`移除-${item.code}`}
                title="移除"
                onClick={() => removeItem(item.id)}
              >
                ×
              </button>
              <div className="watchlist-card-head">
                <strong className="watchlist-name">
                  {item.name || item.code}
                  {isAbnormal(item.changePct) && <span className="watchlist-abnormal-tag">异动</span>}
                </strong>
                <span className="watchlist-meta">
                  {item.code} · {typeLabels[item.type] || item.type}
                  {item.groupName ? ` · ${item.groupName}` : ''}
                </span>
              </div>
              {item.quoteValid ? (
                <>
                  <div className={`watchlist-quote ${changeClass(item.changePct)}`}>
                    <span className="watchlist-price">{formatPrice(item.price)}</span>
                    <span className="watchlist-change">{formatPct(item.changePct)}</span>
                  </div>
                  {item.type === 'STOCK' ? (
                    <div className="watchlist-stats">
                      <span>开： {formatNum(item.open)}</span>
                      <span>高： {formatNum(item.high)}</span>
                      <span>低： {formatNum(item.low)}</span>
                      {item.amplitude !== undefined && item.amplitude !== null && (
                        <span>振幅： {item.amplitude.toFixed(2)}%</span>
                      )}
                      {formatTurnover(item.turnover) && (
                        <span>额： {formatTurnover(item.turnover)}</span>
                      )}
                    </div>
                  ) : (
                    item.quoteNote && <div className="watchlist-stats">{item.quoteNote}</div>
                  )}
                </>
              ) : (
                <p className="muted watchlist-note">{item.quoteNote || '暂无行情'}</p>
              )}
            </article>
          ))}
        </div>
      )}
    </section>
  );
}