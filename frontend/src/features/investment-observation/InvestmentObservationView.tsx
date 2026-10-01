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
  { value: 'PENDING', label: '待补全' },
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
        <div>
          <h3>投资观察</h3>
          <p>跟踪一件事，读懂它之后的市场反应。</p>
        </div>
        <div className="reaction-browser-tools">
          <button
            disabled={busy || discovery?.running}
            onClick={() =>
              void act(async () => {
                setDiscovery(
                  await api<DiscoveryStatus>('/api/investment-reactions/sync', {
                    method: 'POST',
                  }),
                );
                addToast('已提交自动发现与补全，结果会在更新提示中出现', 'info');
              })
            }
          >
            {discovery?.running ? '同步中…' : '立即同步'}
          </button>
          <details>
            <summary>更多</summary>
            <div>
              <button onClick={() => void loadManual()}>手动补充</button>
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
      <div className="reaction-browser-status">
        <span>{discovery?.running ? '正在自动识别与更新' : '自动观察'}</span>
        <small>
          {discovery?.lastCompletedAt ? `最近同步 ${dateTime(discovery.lastCompletedAt)}` : '正在读取同步状态'} ·
          日频行情在收盘后更新
        </small>
      </div>
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
            {item.label}
            <span>{data?.counts[item.value] ?? '—'}</span>
          </button>
        ))}
        {view === 'EXCLUDED' && <button aria-pressed>已排除样本</button>}
      </nav>
      {view === 'PENDING' && !extra && (
        <p className="reaction-note">
          自动采集 {data?.automaticEvents ?? 0} 件事件，其中 {data?.linkedEvents ?? 0} 件至少关联一只股票。
          {data?.oldestPendingAt ? `最早待补全登记于 ${dateTime(data.oldestPendingAt)}。` : ''}
          统计不含已排除的错误样本。
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
              <input
                aria-label="搜索事件或股票"
                placeholder="搜索事件、公司或代码"
                value={query}
                onChange={(event) => setQuery(event.target.value)}
              />
              <button type="submit">搜索</button>
            </label>
            <label>
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
            </label>
            <button
              type="button"
              aria-pressed={followed}
              onClick={() => {
                setFollowed((value) => !value);
                resetList();
              }}
            >
              仅关注
            </button>
            <button
              type="button"
              aria-pressed={changed}
              onClick={() => {
                setChanged((value) => !value);
                resetList();
              }}
            >
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
              <span>最新反应</span>
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
                    ? '已关联股票的事件会自动进入这里；尚缺材料的线索可在“待补全”查看。'
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
                return (
                  <button
                    className="reaction-browser-row"
                    key={sample.sourceIdentity || sample.id}
                    onClick={() => void openPath(`/api/investment-reactions/${sample.id}`)}
                  >
                    <div className="reaction-browser-subject">
                      <span className="reaction-browser-meta">
                        {subtypeLabels[sample.eventSubtype || ''] || '事件观察'} · {dateTime(sample.publishedAt)}
                        {sample.followed ? ' · 已关注' : ''}
                      </span>
                      <strong>{sample.title}</strong>
                      <span>
                        {names || '暂无明确关联公司'}
                        {(data.stockCounts[sample.sourceIdentity || ''] || 0) > 1
                          ? ` · ${data.stockCounts[sample.sourceIdentity!]} 只股票`
                          : sample.instrumentCode
                            ? ` · ${sample.instrumentCode}`
                            : ''}
                      </span>
                    </div>
                    <div className="reaction-browser-value">
                      <strong>{signed(profile?.currentRelativePp, ' pp')}</strong>
                      <small>
                        {profile
                          ? `相对基准 · 已观察 ${profile.observedSessions} 日`
                          : sample.discoveryIssue || '等待可用行情'}
                      </small>
                    </div>
                    <div className="reaction-browser-progress">
                      <span>{state}</span>
                      <div aria-label={`已观察 ${profile?.observedSessions || 0} 个交易日`}>
                        {[1, 2, 3, 4, 5].map((day) => (
                          <i key={day} data-ready={(profile?.observedSessions || 0) >= day} />
                        ))}
                      </div>
                      <small>{sample.lastAttemptAt ? `更新 ${dateTime(sample.lastAttemptAt)}` : '系统自动跟踪'}</small>
                    </div>
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
