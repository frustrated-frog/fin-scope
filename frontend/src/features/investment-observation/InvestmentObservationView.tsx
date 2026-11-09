import { useReactionWorkspace } from './useReactionWorkspace';
import { api } from '../../shared/api/client';
import type { DiscoveryStatus, EventType, ReactionSample, WorkspaceView } from './reactionTypes';
import { dateTime, eventLabels, resolutionLabels, signed, subtypeLabels } from './reactionTypes';
import { ReactionDetailPanel } from './ReactionDetailPanel';
import { LegacyObservations } from './LegacyObservations';
import './investmentReaction.css';
import './reactionWorkspace.css';

const views: Array<{ value: WorkspaceView; label: string }> = [
  { value: 'TRACKING', label: '跟踪中' },
  { value: 'HISTORY', label: '历史案例' },
];

export function InvestmentObservationView({
  setMessage,
  addToast,
  onOpenMajorEvents,
  onResearch,
}: {
  setMessage: (message: string) => void;
  addToast: (message: string, type?: 'success' | 'error' | 'info') => void;
  onOpenMajorEvents?: () => void;
  onResearch?: (question: string) => void;
}) {
  const {
    view,
    setView,
    query,
    setQuery,
    setSearch,
    type,
    setType,
    subtype,
    setSubtype,
    companyOnly,
    setCompanyOnly,
    reason,
    setReason,
    followed,
    setFollowed,
    changed,
    setChanged,
    page,
    setPage,
    setAnchor,
    data,
    discovery,
    setDiscovery,
    selected,
    detailLoading,
    detailError,
    detailOpen,
    loading,
    busy,
    error,
    pending,
    extra,
    setExtra,
    candidates,
    listRef,
    resetList,
    refreshList,
    openPath,
    closeDetail,
    act,
    updateSample,
    loadManual,
  } = useReactionWorkspace(addToast);
  return (
    <section className="reaction-workspace reaction-browser" aria-label="市场反应观察室">
      <header className="reaction-browser-header">
        <div className="reaction-browser-heading">
          <span className="reaction-browser-mark" aria-hidden="true">
            <svg viewBox="0 0 28 28" fill="none">
              <path d="M7 5.5h10l4 4v13H7z" />
              <path d="M17 5.5v4h4M10 17l3-3 3 2 5-5M10 10h3" />
            </svg>
          </span>
          <div>
            <h3>投资观察</h3>
            <p>跟踪一件事，读懂它之后的市场反应。</p>
          </div>
        </div>
        <div className="reaction-browser-tools">
          <div className="reaction-browser-status">
            <span><i aria-hidden="true" />{discovery?.running ? '正在自动识别与更新' : '自动观察'}</span>
            <small>{discovery?.lastCompletedAt ? `最近同步 ${dateTime(discovery.lastCompletedAt)}` : '正在读取同步状态'}</small>
          </div>
          <button
            className="reaction-browser-sync"
            disabled={busy || discovery?.running}
            onClick={() =>
              void act(async () => {
                setDiscovery(
                  await api<DiscoveryStatus>('/api/investment-reactions/sync', {
                    method: 'POST',
                  }),
                );
                addToast('正在核对近期业绩与合同事件', 'info');
              })
            }
          >
            <svg aria-hidden="true" viewBox="0 0 20 20" fill="none"><path d="M16 7a6.5 6.5 0 0 0-11-2L3 7m0 0V3m0 4h4M4 13a6.5 6.5 0 0 0 11 2l2-2m0 0v4m0-4h-4" /></svg>
            {discovery?.running ? '同步中…' : '立即同步'}
          </button>
          <details>
            <summary>更多<svg aria-hidden="true" viewBox="0 0 16 16" fill="none"><path d="m4 6 4 4 4-4" /></svg></summary>
            <div>
              <button onClick={() => void loadManual()}>手动补充</button>
              <button
                onClick={() => {
                  setView('PENDING');
                  setExtra(undefined);
                  setReason('');
                  resetList();
                }}
              >
                待补全记录
              </button>
              <button onClick={() => setExtra('legacy')}>历史资料</button>
              <button
                onClick={() => {
                  setView('EXCLUDED');
                  setExtra(undefined);
                  resetList();
                }}
              >
                已排除样本
              </button>
            </div>
          </details>
        </div>
      </header>
      <div className="reaction-browser-navigation">
        <nav className="reaction-browser-tabs" aria-label="投资观察视图">
          {views.map((item) => (
            <button
              key={item.value}
              aria-pressed={!extra && view === item.value}
              onClick={() => {
                setView(item.value);
                setExtra(undefined);
                setReason('');
                resetList();
              }}
            >
              <span className="reaction-browser-tab-label">{item.label}</span>
              <span className="reaction-browser-count">{data?.counts[item.value]?.toLocaleString('zh-CN') ?? '—'}</span>
            </button>
          ))}
          {view === 'PENDING' && <button aria-pressed>待补全记录</button>}
          {view === 'EXCLUDED' && <button aria-pressed>已排除样本</button>}
        </nav>
        <span className="reaction-browser-market-note"><i aria-hidden="true" />日频行情在收盘后更新</span>
      </div>
      {view === 'PENDING' && !extra && (
        <p className="reaction-note">
          这里保留此前的待补全记录和手动草稿。新的自动观察只收录已确认公司、事件和公开时间的记录。
        </p>
      )}
      {extra ? (
        <section className="reaction-browser-extra">
          <button onClick={() => setExtra(undefined)}>返回事件列表</button>
          {extra === 'legacy' ? (
            <LegacyObservations />
          ) : (
            <>
              <h4>从大事记补充</h4>
              {onOpenMajorEvents && <button onClick={onOpenMajorEvents}>前往大事记</button>}
              <p>自动识别之外的可选入口。</p>
              {candidates.map((candidate) => (
                <article key={candidate.majorEventId}>
                  <span>{candidate.title}</span>
                  <button
                    disabled={busy}
                    onClick={() =>
                      void act(async () => {
                        const sample = await api<ReactionSample>('/api/investment-reactions', {
                          method: 'POST',
                          body: JSON.stringify({
                            majorEventId: candidate.majorEventId,
                          }),
                        });
                        setMessage('已保存事件，可在详情中补充');
                        await openPath(`/api/investment-reactions/${sample.id}`);
                      })
                    }
                  >
                    补充观察
                  </button>
                </article>
              ))}
            </>
          )}
        </section>
      ) : (
        <>
          <form
            className="reaction-browser-filters"
            onSubmit={(event) => {
              event.preventDefault();
              setSearch(query);
              resetList();
            }}
          >
            <label className="reaction-browser-search">
              <span className="sr-only">搜索事件或股票</span>
              <svg aria-hidden="true" viewBox="0 0 20 20" fill="none"><circle cx="8.5" cy="8.5" r="5.5" /><path d="m13 13 4 4" /></svg>
              <input
                aria-label="搜索事件或股票"
                placeholder={view === 'HISTORY' && companyOnly ? '搜索关联公司或股票代码' : '搜索事件、公司或代码'}
                value={query}
                onChange={(event) => setQuery(event.target.value)}
              />
              <button type="submit">搜索</button>
            </label>
            {view === 'HISTORY' ? (
              <>
                <select
                  aria-label="事件子类"
                  value={subtype}
                  onChange={(event) => {
                    setSubtype(event.target.value);
                    resetList();
                  }}
                >
                  <option value="">全部事件子类</option>
                  {Object.entries(eventLabels).map(([typeKey, label]) => (
                    <optgroup key={typeKey} label={label}>
                      {Object.entries(subtypeLabels).filter(([key]) => key.startsWith(typeKey)).map(([key, name]) => (
                        <option key={key} value={key}>{name}</option>
                      ))}
                    </optgroup>
                  ))}
                  <optgroup label="其他旧案例">
                    <option value="OPERATING_UPDATE">经营进展</option>
                    <option value="UNCLASSIFIED">未分类</option>
                  </optgroup>
                </select>
                <button
                  type="button"
                  className="reaction-browser-filter-toggle"
                  aria-pressed={companyOnly}
                  onClick={() => {
                    setCompanyOnly((value) => !value);
                    resetList();
                  }}
                >
                  仅搜公司
                </button>
              </>
            ) : <label>
              <span className="sr-only">事件类型</span>
              <select
                aria-label="事件类型"
                value={type}
                onChange={(event) => {
                  setType(event.target.value as EventType);
                  resetList();
                }}
              >
                <option value="">全部事件类型</option>
                {Object.entries(eventLabels).map(([key, label]) => (
                  <option key={key} value={key}>
                    {label}
                  </option>
                ))}
              </select>
            </label>}
            <button
              type="button"
              className="reaction-browser-filter-toggle"
              aria-pressed={followed}
              onClick={() => {
                setFollowed((value) => !value);
                resetList();
              }}
            >
              <svg aria-hidden="true" viewBox="0 0 20 20" fill="none"><path d="m10 2 2.4 4.9 5.4.8-3.9 3.8.9 5.4-4.8-2.5-4.8 2.5.9-5.4L2.2 7.7l5.4-.8z" /></svg>
              仅关注
            </button>
            <button
              type="button"
              className="reaction-browser-filter-toggle"
              aria-pressed={changed}
              onClick={() => {
                setChanged((value) => !value);
                resetList();
              }}
            >
              <svg aria-hidden="true" viewBox="0 0 20 20" fill="none"><path d="M3 11h3l2-6 4 11 2-5h3" /></svg>
              有新变化
            </button>
            {view === 'PENDING' && (
              <select
                aria-label="待补全原因"
                value={reason}
                onChange={(event) => {
                  setReason(event.target.value);
                  resetList();
                }}
              >
                <option value="">全部待补全原因</option>
                {Object.entries(resolutionLabels)
                  .filter(([key]) => key !== 'RESOLVED')
                  .map(([key, label]) => (
                    <option value={key} key={key}>
                      {label} · {data?.pendingReasons[key] ?? 0}
                    </option>
                  ))}
              </select>
            )}
          </form>
          {error && (
            <p className="reaction-warning" role="alert">
              {error} <button onClick={refreshList}>重新读取</button>
            </p>
          )}
          {pending && (
            <button className="reaction-browser-update" onClick={refreshList}>
              有新事件或反应变化 · 点击更新列表
            </button>
          )}
          <div className="reaction-browser-list" ref={listRef} aria-busy={loading}>
            <div className="reaction-browser-columns">
              <span>事件 / 关联股票</span>
              <span>{view === 'HISTORY' ? '相对基准 · pp' : '最新反应'}</span>
              <span>观察进度</span>
            </div>
            {loading ? (
              <p className="reaction-empty" role="status">
                正在读取事件…
              </p>
            ) : !data?.items.length ? (
              <div className="reaction-empty">
                <h4>{view === 'TRACKING' ? '当前没有符合条件的跟踪事件' : '没有符合条件的事件'}</h4>
                <p>
                  {view === 'TRACKING'
                    ? '核对通过的业绩与合同事件会自动进入这里。'
                    : '调整搜索和筛选条件，可检索完整事件库。'}
                </p>
              </div>
            ) : (
              data.items.map((sample) => {
                const profile = sample.calculation?.profile;
                const state = sample.excluded
                  ? '已排除统计'
                  : sample.state === 'DRAFT'
                    ? resolutionLabels[sample.resolutionStatus || 'PENDING']
                    : sample.refreshError
                      ? '行情暂不可用'
                      : profile?.windowEnded
                        ? '观察已结束'
                        : '持续跟踪';
                const names = data.stockNames[sample.sourceIdentity || ''] || sample.instrumentName;
                const caseType = (view === 'HISTORY' ? sample.caseSubtype : undefined) || sample.eventSubtype || '';
                const reportCount = data.caseReportCounts?.[sample.sourceIdentity || ''] || 1;
                const relative = profile?.currentRelativePp;
                const direction = relative == null || !Number.isFinite(relative) || relative === 0 ? 'neutral' : relative > 0 ? 'up' : 'down';
                const observedSessions = profile?.observedSessions || 0;
                const status = sample.excluded ? 'excluded' : sample.state === 'DRAFT' || sample.refreshError ? 'pending' : profile?.windowEnded ? 'ended' : 'tracking';
                return (
                  <button
                    className="reaction-browser-row"
                    key={sample.sourceIdentity || sample.id}
                    onClick={() => void openPath(`/api/investment-reactions/${sample.id}`)}
                  >
                    <div className="reaction-browser-subject">
                      <span className="reaction-browser-meta">
                        <span className="reaction-browser-event-type" data-tone={caseType.startsWith('EARNINGS') ? 'earnings' : caseType.startsWith('CONTRACT') ? 'contract' : 'operating'}>
                          {subtypeLabels[caseType] || '事件观察'}
                        </span>
                        <time dateTime={sample.publishedAt}>{dateTime(sample.publishedAt)}</time>
                        {reportCount > 1 && <span className="reaction-browser-reports">合并 {reportCount} 条记录</span>}
                        {sample.followed && <span className="reaction-browser-followed">已关注</span>}
                      </span>
                      <strong title={sample.title}>{sample.title}</strong>
                      <span className="reaction-browser-company">
                        <svg aria-hidden="true" viewBox="0 0 16 16" fill="none"><path d="M2.5 13.5h11M4 13V3.5h8V13M6.5 6h3M6.5 8.5h3M7 13v-2h2v2" /></svg>
                        <span>{names || '暂无明确关联公司'}</span>
                        {(data.stockCounts[sample.sourceIdentity || ''] || 0) > 1
                          ? <span className="reaction-browser-stock-code">{data.stockCounts[sample.sourceIdentity!]} 只股票</span>
                          : sample.instrumentCode
                            ? <span className="reaction-browser-stock-code">{sample.instrumentCode}</span>
                            : null}
                      </span>
                    </div>
                    {view === 'HISTORY' ? (
                      <HistoricalReturns sample={sample} />
                    ) : <div className="reaction-browser-value" data-direction={direction}>
                      <span className="reaction-browser-value-line">
                        {direction !== 'neutral' && <svg aria-hidden="true" viewBox="0 0 16 16" fill="none"><path d={direction === 'up' ? 'M3 11 12 3m-6 0h6v6' : 'M3 5l9 8m-6 0h6V7'} /></svg>}
                        <strong>{signed(relative)}{relative != null && Number.isFinite(relative) && <span className="reaction-browser-unit"> pp</span>}</strong>
                      </span>
                      <small>
                        {profile
                          ? `相对基准 · 已观察 ${profile.observedSessions} 日`
                          : sample.discoveryIssue || '等待可用行情'}
                      </small>
                    </div>}
                    <div className="reaction-browser-progress" data-status={status}>
                      <span className="reaction-browser-progress-heading"><span className="reaction-browser-state"><i aria-hidden="true" />{state}</span><span className="reaction-browser-session">T+{Math.min(observedSessions, 5)}<small> / 5</small></span></span>
                      <div className="reaction-browser-track" aria-label={`已观察 ${observedSessions} 个交易日`}>
                        {[1, 2, 3, 4, 5].map((day) => (
                          <i key={day} title={`T+${day}`} data-ready={observedSessions >= day} data-current={status === 'tracking' && observedSessions === day} />
                        ))}
                      </div>
                      <small>{sample.lastAttemptAt ? `更新 ${dateTime(sample.lastAttemptAt)}` : '系统自动跟踪'}</small>
                    </div>
                    <svg className="reaction-browser-row-arrow" aria-hidden="true" viewBox="0 0 16 16" fill="none"><path d="m6 3 5 5-5 5" /></svg>
                  </button>
                );
              })
            )}
          </div>
          <footer className="reaction-browser-pagination">
            <span>共 {data?.total ?? 0} 件事件 · 每页 20 件</span>
            <div>
              <button
                disabled={loading || page <= 1}
                onClick={() => {
                  setAnchor(data?.anchor);
                  setPage((value) => value - 1);
                }}
              >
                上一页
              </button>
              <span>
                第 {page} / {Math.max(1, Math.ceil((data?.total || 0) / 20))} 页
              </span>
              <button
                disabled={loading || page * 20 >= (data?.total || 0)}
                onClick={() => {
                  setAnchor(data?.anchor);
                  setPage((value) => value + 1);
                }}
              >
                下一页
              </button>
            </div>
          </footer>
        </>
      )}
      {detailOpen && (
        <ReactionDetailPanel
          sample={selected}
          loading={detailLoading}
          error={detailError}
          onClose={closeDetail}
          onOpen={(id) => void openPath(`/api/investment-reactions/${id}`)}
          onUpdate={updateSample}
          onResearch={onResearch}
          addToast={addToast}
        />
      )}
    </section>
  );
}

function HistoricalReturns({ sample }: { sample: ReactionSample }) {
  return (
    <div className="reaction-browser-history-returns" aria-label="事件后相对基准表现，单位为百分点">
      {[1, 3, 5].map((day) => {
        const window = sample.calculation?.windows.find((item) => item.sessions === day);
        const value = window?.status === 'READY' ? window.relativeReturnPp : undefined;
        const tone = value == null || value === 0 ? 'neutral' : value > 0 ? 'up' : 'down';
        return (
          <span key={day}>
            <small>T+{day}</small>
            <strong data-direction={tone}>{signed(value)}</strong>
          </span>
        );
      })}
    </div>
  );
}
