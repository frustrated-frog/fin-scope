import { useMemo, useState } from 'react';

import { api } from '../../shared/api/client';
import { FollowedSector, WatchlistItem } from '../../shared/types';
import { AttributionReaderView } from './AttributionReaderView';
import { DataQualityNotice } from './DataQualityNotice';
import { SectorMarketPanel } from './SectorMarketPanel';
import { useWatchlistDashboardData } from './useWatchlistDashboardData';
import { changeClass, formatPct, formatPrice, formatTurnover } from './watchlistFormatters';

type AttributionInstrument = {
  code: string;
  type: 'STOCK' | 'FUND' | 'SECTOR';
  name?: string;
  changePct?: number;
  confirmedNavChangePct?: number;
  quoteDate?: string;
  attributionReportId?: number;
  attributionReportDate?: string;
  attributionChangePct?: number;
};

type AttributionTarget = {
  taskId?: string;
  reportId: number;
  code: string;
  type: AttributionInstrument['type'];
  name?: string;
  changePct?: number;
};

const typeLabels: Record<string, string> = {
  STOCK: '股票',
  FUND: '基金'
};

function formatNum(value?: number) {
  if (value === undefined || value === null) {
    return '--';
  }
  return value.toFixed(2);
}

function formatChangeAmount(value?: number) {
  if (value === undefined || value === null) {
    return '--';
  }
  const sign = value > 0 ? '+' : '';
  return `${sign}${value.toFixed(2)}`;
}

// 异动：涨跌幅绝对值超过阈值
function isAbnormal(value?: number) {
  return value !== undefined && value !== null && Math.abs(value) >= 5;
}

function latestChangePct(item: AttributionInstrument) {
  return item.changePct ?? (item.type === 'FUND' ? item.confirmedNavChangePct : undefined);
}

const DEFAULT_GROUP_LABEL = '未分组';
const COLLAPSE_STORAGE_KEY = 'watchlist.collapsedGroups';

function loadCollapsed(): Record<string, boolean> {
  try {
    const raw = localStorage.getItem(COLLAPSE_STORAGE_KEY);
    return raw ? (JSON.parse(raw) as Record<string, boolean>) : {};
  } catch {
    return {};
  }
}

export function WatchlistView({
  addToast,
  setMessage
}: {
  addToast: (message: string, type?: 'success' | 'error' | 'info') => void;
  setMessage: (message: string) => void;
}) {
  const dashboard = useWatchlistDashboardData();
  const items = dashboard.investments.data;
  const marketIndices = dashboard.indices.data;
  const loading = dashboard.investments.phase === 'loading';
  const loadError = dashboard.investments.error || null;
  const refreshing = dashboard.refreshing;
  const refreshStatus = dashboard.refreshStatus || null;
  const [code, setCode] = useState('');
  const [type, setType] = useState<'STOCK' | 'FUND'>('STOCK');
  const [group, setGroup] = useState('');
  const [sortByChange, setSortByChange] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [attribution, setAttribution] = useState<AttributionTarget | null>(null);
  const [attributing, setAttributing] = useState<string | null>(null);
  const [collapsed, setCollapsed] = useState<Record<string, boolean>>(loadCollapsed);
  const [movingId, setMovingId] = useState<number | null>(null);
  const [groupFocused, setGroupFocused] = useState(false);

  async function load() {
    const result = await dashboard.loadInvestments();
    if (result.failed) setMessage('自选列表加载失败');
  }

  async function refreshQuotes() {
    await dashboard.refreshAll();
  }

  async function startAttribution(item: AttributionInstrument) {
    const changePct = latestChangePct(item);
    setAttributing(item.code);
    try {
      const res = await api<{ taskId: string; reportId: string | number }>('/api/attribution/start', {
        method: 'POST',
        body: JSON.stringify({
          code: item.code,
          type: item.type,
          name: item.name,
          changePct,
          quoteDate: item.quoteDate
        })
      });
      setAttribution({
        taskId: res.taskId,
        reportId: Number(res.reportId),
        code: item.code,
        type: item.type,
        name: item.name,
        changePct
      });
    } catch (error) {
      addToast(error instanceof Error ? error.message : '归因启动失败', 'error');
    } finally {
      setAttributing(null);
    }
  }

  function closeAttribution() {
    setAttribution(null);
    refreshQuotes();
  }

  function openAttribution(item: AttributionInstrument) {
    if (!item.attributionReportId) return;
    setAttribution({
      reportId: item.attributionReportId,
      code: item.code,
      type: item.type,
      name: item.name,
      changePct: item.attributionChangePct
    });
  }

  function hasCurrentAttribution(item: AttributionInstrument) {
    return Boolean(item.quoteDate && item.attributionReportDate && item.quoteDate === item.attributionReportDate);
  }

  const sortedItems = useMemo(() => {
    const clone = [...items];
    if (sortByChange) {
      clone.sort((a, b) => (latestChangePct(b) ?? -999) - (latestChangePct(a) ?? -999));
    }
    return clone;
  }, [items, sortByChange]);

  // 按分组聚合：未填分组归入"未分组"，并计算每组汇总
  const groups = useMemo(() => {
    const map = new Map<string, WatchlistItem[]>();
    for (const item of sortedItems) {
      const key = item.groupName?.trim() || DEFAULT_GROUP_LABEL;
      const bucket = map.get(key);
      if (bucket) {
        bucket.push(item);
      } else {
        map.set(key, [item]);
      }
    }
    return Array.from(map.entries()).map(([name, list]) => {
      const valid = list.filter((it) => latestChangePct(it) !== undefined);
      const avgChange = valid.length
        ? valid.reduce((sum, it) => sum + (latestChangePct(it) ?? 0), 0) / valid.length
        : undefined;
      const abnormalCount = list.filter((it) => isAbnormal(latestChangePct(it))).length;
      return { name, list, avgChange, abnormalCount };
    });
  }, [sortedItems]);

  // 已有分组名（供移动分组下拉与新增联想）
  const existingGroups = useMemo(() => {
    const set = new Set<string>();
    items.forEach((it) => {
      const g = it.groupName?.trim();
      if (g) {
        set.add(g);
      }
    });
    return Array.from(set).sort((a, b) => a.localeCompare(b, 'zh'));
  }, [items]);

  // 分组输入的联想候选：按已输入内容过滤
  const groupSuggestions = useMemo(() => {
    const keyword = group.trim().toLowerCase();
    if (!keyword) {
      return existingGroups;
    }
    return existingGroups.filter((g) => g.toLowerCase().includes(keyword) && g.toLowerCase() !== keyword);
  }, [existingGroups, group]);

  function persistCollapsed(next: Record<string, boolean>) {
    setCollapsed(next);
    try {
      localStorage.setItem(COLLAPSE_STORAGE_KEY, JSON.stringify(next));
    } catch {
      /* ignore */
    }
  }

  function toggleGroup(name: string) {
    persistCollapsed({ ...collapsed, [name]: !collapsed[name] });
  }

  function setAllCollapsed(value: boolean) {
    const next: Record<string, boolean> = {};
    groups.forEach((g) => {
      next[g.name] = value;
    });
    persistCollapsed(next);
  }

  // 将标的移动到目标分组（空串表示移出到"未分组"）
  async function moveGroup(item: WatchlistItem, target: string) {
    const normalized = target === DEFAULT_GROUP_LABEL ? '' : target.trim();
    if ((item.groupName?.trim() || '') === normalized) {
      return;
    }
    setMovingId(item.id);
    try {
      await api(`/api/watchlist/${item.id}/group`, {
        method: 'PATCH',
        body: JSON.stringify({ groupName: normalized || null })
      });
      addToast('已更新分组', 'success');
      await load();
    } catch (error) {
      addToast(error instanceof Error ? error.message : '分组更新失败', 'error');
    } finally {
      setMovingId(null);
    }
  }

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

  if (attribution) {
    return (
      <AttributionReaderView
        taskId={attribution.taskId}
        reportId={attribution.reportId}
        code={attribution.code}
        type={attribution.type}
        name={attribution.name}
        changePct={attribution.changePct}
        onBack={closeAttribution}
      />
    );
  }

  return (
    <div className="watchlist-page">
      <section className="market-index-overview" aria-labelledby="market-index-title">
        <h4 id="market-index-title">市场指数</h4>
        <div className="market-index-grid">
          {marketIndices.map((index) => (
            <article className="market-index-card" data-testid="market-index-card" key={index.code}>
              <span className="market-index-name">{index.name}</span>
              {['STALE_FALLBACK', 'UNAVAILABLE'].includes(index.qualityStatus || '')
                && <span className="market-data-old-badge">旧数据</span>}
              {index.quoteValid ? (
                <div className="market-index-values">
                  <strong className={changeClass(index.changePct)}>{formatNum(index.price)}</strong>
                  <div className="market-index-change-row">
                    <span className={changeClass(index.changePct)}>{formatChangeAmount(index.changeAmount)}</span>
                    <span className={changeClass(index.changePct)}>{formatPct(index.changePct)}</span>
                  </div>
                </div>
              ) : (
                <span className="muted market-index-note">{index.quoteNote || '暂无行情'}</span>
              )}
            </article>
          ))}
        </div>
      </section>

      <SectorMarketPanel
        overview={dashboard.sectorOverview}
        follows={dashboard.followedSectors}
        category={dashboard.sectorCategory}
        setCategory={dashboard.setSectorCategory}
        reloadFollows={() => dashboard.loadFollowedSectors()}
        retryOverview={() => dashboard.loadSectorOverview(dashboard.sectorCategory)}
        addToast={addToast}
        onStartAttribution={(sector: FollowedSector) => startAttribution({ ...sector, type: 'SECTOR' })}
        onOpenAttribution={(sector: FollowedSector) => openAttribution({ ...sector, type: 'SECTOR' })}
      />

      <section className="panel wide">
        <div className="panel-heading">
          <h3>我的自选</h3>
          {loadError ? (
            <span className="subtle-badge watchlist-load-failed">加载失败</span>
          ) : (
            <span className="subtle-badge">{items.length} 标的</span>
          )}
          {groups.length > 1 && (
            <div className="watchlist-group-toolbar">
              <button className="ghost-button compact-button" type="button" onClick={() => setAllCollapsed(false)}>
                全部展开
              </button>
              <button className="ghost-button compact-button" type="button" onClick={() => setAllCollapsed(true)}>
                全部收起
              </button>
            </div>
          )}
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
          onChange={(event) => setType(event.target.value as 'STOCK' | 'FUND')}
        >
          <option value="STOCK">股票</option>
          <option value="FUND">基金</option>
        </select>
        <div className="watchlist-group-field">
          <input
            className="watchlist-input"
            placeholder="分组（可选）"
            value={group}
            onChange={(event) => setGroup(event.target.value)}
            onFocus={() => setGroupFocused(true)}
            onBlur={() => window.setTimeout(() => setGroupFocused(false), 120)}
          />
          {groupFocused && groupSuggestions.length > 0 && (
            <ul className="watchlist-group-suggest" role="listbox">
              {groupSuggestions.map((g) => (
                <li key={g}>
                  <button
                    type="button"
                    className="watchlist-group-suggest-item"
                    onMouseDown={(event) => {
                      event.preventDefault();
                      setGroup(g);
                      setGroupFocused(false);
                    }}
                  >
                    {g}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
        <button className="primary-button" type="button" disabled={submitting} onClick={addItem}>
          {submitting ? '添加中…' : '加入自选'}
        </button>
        <button
          className={`ghost-button watchlist-refresh-button${refreshing ? ' is-refreshing' : ''}`}
          type="button"
          disabled={refreshing}
          onClick={() => refreshQuotes()}
        >
          <span className="watchlist-refresh-icon" aria-hidden="true">↻</span>
          {refreshing ? '刷新中…' : '刷新行情'}
        </button>
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

      {refreshStatus && (
        <div className={`watchlist-refresh-status${refreshing ? ' is-refreshing' : ''}`} role="status" aria-live="polite">
          <span aria-hidden="true">{refreshing ? '◌' : refreshStatus.startsWith('部分') ? '!' : '✓'}</span>
          {refreshStatus}
        </div>
      )}

      <DataQualityNotice quality={dashboard.marketDataQuality} />

      {loadError && (
        <p className="watchlist-load-error" role="alert">
          自选列表加载失败：{loadError}。请点击“刷新行情”重试。
        </p>
      )}

      {loading && items.length === 0 ? (
        <p className="muted">加载中…</p>
      ) : loadError ? null : sortedItems.length === 0 ? (
        <p className="muted">还没有自选标的，输入代码加入吧。</p>
      ) : (
        <>
          <div className="watchlist-groups">
            {groups.map((groupBlock) => {
              const isCollapsed = Boolean(collapsed[groupBlock.name]);
              return (
                <section className="watchlist-group" key={groupBlock.name}>
                  <button
                    className="watchlist-group-head"
                    type="button"
                    aria-expanded={!isCollapsed}
                    onClick={() => toggleGroup(groupBlock.name)}
                  >
                    <span className={`watchlist-group-caret${isCollapsed ? ' is-collapsed' : ''}`} aria-hidden="true">
                      ▾
                    </span>
                    <span className="watchlist-group-name">{groupBlock.name}</span>
                    <span className="watchlist-group-count">{groupBlock.list.length}</span>
                    {groupBlock.avgChange !== undefined && (
                      <span className={`watchlist-group-avg ${changeClass(groupBlock.avgChange)}`}>
                        均 {formatPct(groupBlock.avgChange)}
                      </span>
                    )}
                    {groupBlock.abnormalCount > 0 && (
                      <span className="watchlist-group-abnormal">{groupBlock.abnormalCount} 异动</span>
                    )}
                  </button>
                  {!isCollapsed && (
                    <div className="watchlist-grid">
                      {groupBlock.list.map((item) => (
                        <article
                          className={`panel watchlist-card${isAbnormal(latestChangePct(item)) ? ' watchlist-card-abnormal' : ''}`}
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
                              {isAbnormal(latestChangePct(item)) && <span className="watchlist-abnormal-tag">异动</span>}
                              {['STALE_FALLBACK', 'UNAVAILABLE'].includes(item.qualityStatus || '')
                                && <span className="market-data-old-badge">旧数据</span>}
                            </strong>
                            <span className="watchlist-meta">
                              {item.code} · {typeLabels[item.type] || item.type}
                            </span>
                          </div>
                          {item.quoteValid ? (
                            <>
                              <div className={`watchlist-quote ${changeClass(latestChangePct(item))}`}>
                                {item.type === 'FUND' ? (
                                  <div className="fund-quote-values">
                                    <span>
                                      <small>确认净值 {item.confirmedNavDate && <time>{item.confirmedNavDate}</time>}</small>
                                      <strong>{formatPrice(item.confirmedNav, 4)} <em>{formatPct(item.confirmedNavChangePct)}</em></strong>
                                    </span>
                                    <span>
                                      <small>{item.price == null ? '盘中估值暂不可用' : '盘中估值'}</small>
                                      <strong>{formatPrice(item.price, 4)} <em>{formatPct(item.changePct)}</em></strong>
                                    </span>
                                  </div>
                                ) : <><span className="watchlist-price">{formatPrice(item.price)}</span><span className="watchlist-change">{formatPct(item.changePct)}</span></>}
                              </div>
                              {item.type === 'STOCK' ? (
                                <div className="watchlist-stats">
                                  <span>开： {formatNum(item.open)}</span>
                                  <span>高： {formatNum(item.high)}</span>
                                  <span>低： {formatNum(item.low)}</span>
                                  {item.amplitude !== undefined && item.amplitude !== null && (
                                    <span>振幅： {item.amplitude.toFixed(2)}%</span>
                                  )}
                                  {formatTurnover(item.turnover) && <span>额： {formatTurnover(item.turnover)}</span>}
                                </div>
                              ) : item.type !== 'FUND' ? (
                                item.quoteNote && <div className="watchlist-stats">{item.quoteNote}</div>
                              ) : null}
                            </>
                          ) : (
                            <p className="muted watchlist-note">{item.quoteNote || '暂无行情'}</p>
                          )}
                          {item.attributionSummary && item.attributionReportId && (
                            <button
                              className="watchlist-attr-summary"
                              type="button"
                              aria-label={`查看${item.name || item.code}的完整归因报告`}
                              onClick={() => openAttribution(item)}
                            >
                              <span className="watchlist-attr-summary-head">
                                <strong>{hasCurrentAttribution(item) ? '今日归因' : '最近归因'}</strong>
                                <time>{item.attributionReportDate}</time>
                                <i aria-hidden="true">›</i>
                              </span>
                              <span className="watchlist-attr-summary-copy" title={item.attributionSummary}>{item.attributionSummary}</span>
                              <span className="watchlist-attr-summary-link">查看完整报告</span>
                            </button>
                          )}
                          <div className="watchlist-card-actions">
                            <label className="watchlist-move" title="移动到分组">
                              <span className="watchlist-move-label">分组</span>
                              <select
                                className="watchlist-move-select"
                                aria-label={`移动分组-${item.code}`}
                                disabled={movingId === item.id}
                                value={item.groupName?.trim() || DEFAULT_GROUP_LABEL}
                                onChange={(event) => moveGroup(item, event.target.value)}
                              >
                                <option value={DEFAULT_GROUP_LABEL}>{DEFAULT_GROUP_LABEL}</option>
                                {existingGroups.map((g) => (
                                  <option key={g} value={g}>
                                    {g}
                                  </option>
                                ))}
                              </select>
                            </label>
                            <button
                              className="watchlist-attr-button"
                              type="button"
                              disabled={attributing === item.code}
                              onClick={() => startAttribution(item)}
                            >
                              {attributing === item.code ? '启动中…' : `🔬 ${hasCurrentAttribution(item) ? '重新归因' : '深度归因'}`}
                            </button>
                          </div>
                        </article>
                      ))}
                    </div>
                  )}
                </section>
              );
            })}
          </div>
        </>
      )}
      </section>
    </div>
  );
}
