import { useEffect, useState } from 'react';

import { api } from '../../shared/api/client';
import type {
  InvestmentObservation,
  InvestmentObservationDisposition,
  InvestmentObservationRefreshResult,
  InvestmentObservationWorkspace
} from './investmentObservationTypes';

const changeLabels: Record<InvestmentObservation['changeType'], string> = {
  ORDER: '订单变化',
  PRICE: '价格变化',
  POLICY: '政策变化',
  EARNINGS: '业绩变化',
  COMPETITION: '竞争格局',
  CAPITAL: '资本动作',
  OTHER: '待归类变化'
};

export function InvestmentObservationView({ setMessage, addToast, onOpenSource }: {
  setMessage: (message: string) => void;
  addToast: (message: string, type?: 'success' | 'error' | 'info') => void;
  onOpenSource: (eventId: number) => void;
}) {
  const [workspace, setWorkspace] = useState<InvestmentObservationWorkspace>();
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  async function readWorkspace() {
    const next = await api<InvestmentObservationWorkspace>('/api/investment-observations');
    setWorkspace(next);
    return next;
  }

  async function refreshObservations(manual: boolean) {
    setRefreshing(true);
    try {
      const result = await api<InvestmentObservationRefreshResult>('/api/investment-observations/refresh', {
        method: 'POST'
      });
      await readWorkspace();
      const nextMessage = result.updatedCount > 0
        ? `观察池已更新 ${result.updatedCount} 项，扫描 ${result.scannedCount} 个候选`
        : '当前没有新的可靠变化，已保留原观察池';
      setMessage(nextMessage);
      if (manual) {
        addToast(nextMessage, 'success');
      }
    } catch (error) {
      const nextMessage = error instanceof Error ? error.message : '投资观察刷新失败';
      setMessage(nextMessage);
      if (manual) {
        addToast(nextMessage, 'error');
      }
    } finally {
      setRefreshing(false);
    }
  }

  useEffect(() => {
    let active = true;
    async function initialize() {
      try {
        const current = await api<InvestmentObservationWorkspace>('/api/investment-observations');
        if (!active) {
          return;
        }
        setWorkspace(current);
        if (current.activeCount === 0) {
          await api<InvestmentObservationRefreshResult>('/api/investment-observations/refresh', { method: 'POST' });
          if (active) {
            await readWorkspace();
          }
        }
        if (active) {
          setMessage('投资观察工作台已同步');
        }
      } catch (error) {
        if (active) {
          setMessage(error instanceof Error ? error.message : '投资观察加载失败');
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }
    void initialize();
    return () => {
      active = false;
    };
  }, []);

  async function updateDisposition(item: InvestmentObservation, disposition: InvestmentObservationDisposition) {
    try {
      const updated = await api<InvestmentObservation>(`/api/investment-observations/${item.id}/state`, {
        method: 'PATCH',
        body: JSON.stringify({ disposition, revision: item.revision })
      });
      setWorkspace((current) => current ? replaceObservation(current, updated) : current);
      addToast(disposition === 'IGNORED' ? '已移出当前观察池' : '已标记为稍后看', 'success');
    } catch (error) {
      addToast(error instanceof Error ? error.message : '观察状态更新失败', 'error');
    }
  }

  const focus = workspace?.focus ?? [];
  const queue = [...(workspace?.tracking ?? []), ...(workspace?.learning ?? [])];

  return (
    <section className="investment-observation" aria-label="投资观察工作台">
      <header className="investment-observation-hero">
        <div>
          <p className="investment-observation-kicker">PERSONAL RESEARCH DESK · 个人研究桌面</p>
          <h3>先看变化，再做判断</h3>
          <p>系统从已沉淀的雷达事件中挑选可验证的变化。它不依赖持仓，也不替你给出买卖结论。</p>
        </div>
        <button type="button" onClick={() => void refreshObservations(true)} disabled={refreshing}>
          <span aria-hidden="true">↻</span>
          {refreshing ? '正在重算证据' : '更新观察池'}
        </button>
      </header>

      <div className="investment-observation-stats" aria-label="观察池概览">
        <Stat label="当前观察" value={workspace?.activeCount ?? 0} note="最多展示 20 项" />
        <Stat label="今天变化" value={workspace?.changedTodayCount ?? 0} note="证据或阶段更新" />
        <Stat label="等待验证" value={workspace?.waitingValidationCount ?? 0} note="有明确下一步" />
        <Stat label="已经归档" value={workspace?.archivedCount ?? 0} note="保留历史轨迹" />
      </div>

      {workspace?.warning && <p className="investment-observation-warning">{workspace.warning}</p>}

      {loading ? (
        <div className="investment-observation-loading">正在整理变化、证据与下一验证点…</div>
      ) : focus.length === 0 && queue.length === 0 ? (
        <EmptyWorkspace onRefresh={() => void refreshObservations(true)} refreshing={refreshing} />
      ) : (
        <>
          <section className="investment-observation-focus" aria-labelledby="observation-focus-title">
            <header>
              <div>
                <span>01 / 本期焦点</span>
                <h4 id="observation-focus-title">先读这些，不必追完所有新闻</h4>
              </div>
              <p>{focus.length} / 5 个焦点席位</p>
            </header>
            <div className="investment-observation-focus-grid">
              {focus.map((item, index) => (
                <FocusCard
                  key={item.id}
                  item={item}
                  index={index}
                  onOpenSource={onOpenSource}
                  onDisposition={updateDisposition}
                />
              ))}
            </div>
          </section>

          <section className="investment-observation-queue" aria-labelledby="observation-queue-title">
            <header>
              <div>
                <span>02 / 继续跟踪</span>
                <h4 id="observation-queue-title">有价值，但不急着形成结论</h4>
              </div>
              <small>分数代表证据完整度，不代表上涨概率</small>
            </header>
            {queue.length === 0 ? (
              <p className="investment-observation-queue-empty">暂时没有排队对象。焦点之外的信息不会为了凑数而进入。</p>
            ) : (
              <div className="investment-observation-queue-list">
                {queue.map((item) => (
                  <QueueCard key={item.id} item={item} onOpenSource={onOpenSource} onDisposition={updateDisposition} />
                ))}
              </div>
            )}
          </section>
        </>
      )}

      <footer className="investment-observation-footnote">
        <span>方法说明</span>
        观察评分综合变化强度、可信度、独立来源、持续性、影响机制与可验证性；它是研究排序，不是投资建议。
        {workspace?.refreshedAt && <time>最后整理 {formatTime(workspace.refreshedAt)}</time>}
      </footer>
    </section>
  );
}

function Stat({ label, value, note }: { label: string; value: number; note: string }) {
  return <div><span>{label}</span><strong>{value}</strong><small>{note}</small></div>;
}

function FocusCard({ item, index, onOpenSource, onDisposition }: {
  item: InvestmentObservation;
  index: number;
  onOpenSource: (eventId: number) => void;
  onDisposition: (item: InvestmentObservation, disposition: InvestmentObservationDisposition) => void;
}) {
  return (
    <article className="investment-observation-card" data-insufficient={item.evidenceInsufficient}>
      <header>
        <div className="investment-observation-index">0{index + 1}</div>
        <div className="investment-observation-card-title">
          <div><span>{changeLabels[item.changeType]}</span><span>{item.independentSourceCount} 个独立来源</span></div>
          <h5>{item.title}</h5>
          <p>{item.summary}</p>
        </div>
        <div className="investment-observation-score" aria-label={`证据评分 ${item.score}`}>
          <strong>{item.score}</strong><small>/ 100</small>
        </div>
      </header>
      {item.evidenceInsufficient && <p className="investment-observation-caveat">相对优先展示 · 证据仍需补强</p>}
      <div className="investment-observation-thesis-grid">
        <div><span>为什么值得看</span><p>{item.whyItMatters || '需要继续拆解变化如何影响经营与预期。'}</p></div>
        <div><span>现在不能确定什么</span><p>{item.uncertainty || '现有事实不足以推导未来结果。'}</p></div>
        <div className="investment-observation-next"><span>下一验证点</span><p>{item.nextValidation || '等待更多独立来源或可量化数据。'}</p></div>
      </div>
      <div className="investment-observation-dimensions" aria-label="证据评分拆解">
        {item.scoreDimensions.map((dimension) => (
          <div key={dimension.code} title={dimension.explanation}>
            <span>{dimension.label}</span>
            <i><b style={{ width: `${Math.round(dimension.score / dimension.maxScore * 100)}%` }} /></i>
            <strong>{dimension.score}/{dimension.maxScore}</strong>
          </div>
        ))}
      </div>
      <CardActions item={item} onOpenSource={onOpenSource} onDisposition={onDisposition} />
    </article>
  );
}

function QueueCard({ item, onOpenSource, onDisposition }: {
  item: InvestmentObservation;
  onOpenSource: (eventId: number) => void;
  onDisposition: (item: InvestmentObservation, disposition: InvestmentObservationDisposition) => void;
}) {
  return (
    <article>
      <div className="investment-observation-queue-score"><strong>{item.score}</strong><small>证据分</small></div>
      <div>
        <span>{item.stage === 'TRACKING' ? '继续跟踪' : '学习样本'} · {changeLabels[item.changeType]}</span>
        <h5>{item.title}</h5>
        <p>{item.nextValidation || item.summary}</p>
      </div>
      <CardActions item={item} onOpenSource={onOpenSource} onDisposition={onDisposition} />
    </article>
  );
}

function CardActions({ item, onOpenSource, onDisposition }: {
  item: InvestmentObservation;
  onOpenSource: (eventId: number) => void;
  onDisposition: (item: InvestmentObservation, disposition: InvestmentObservationDisposition) => void;
}) {
  return (
    <div className="investment-observation-actions">
      <button type="button" onClick={() => onOpenSource(item.sourceId)}>查看原始证据</button>
      <button type="button" aria-label={`稍后看：${item.title}`} data-active={item.disposition === 'LATER'}
              onClick={() => void onDisposition(item, 'LATER')}>稍后看</button>
      <button type="button" aria-label={`忽略：${item.title}`} onClick={() => void onDisposition(item, 'IGNORED')}>忽略</button>
    </div>
  );
}

function EmptyWorkspace({ onRefresh, refreshing }: { onRefresh: () => void; refreshing: boolean }) {
  return (
    <div className="investment-observation-empty">
      <span aria-hidden="true">○</span>
      <h4>今天还没有足够清晰的变化</h4>
      <p>这不是错误，也与你有没有持仓无关。系统会继续从雷达事件里寻找可验证、可学习的观察对象。</p>
      <button type="button" onClick={onRefresh} disabled={refreshing}>{refreshing ? '正在检查' : '再检查一次'}</button>
    </div>
  );
}

function replaceObservation(workspace: InvestmentObservationWorkspace, updated: InvestmentObservation) {
  const replace = (items: InvestmentObservation[]) => items
    .map((item) => item.id === updated.id ? updated : item)
    .filter((item) => item.disposition !== 'IGNORED');
  return {
    ...workspace,
    focus: replace(workspace.focus),
    tracking: replace(workspace.tracking),
    learning: replace(workspace.learning),
    activeCount: updated.disposition === 'IGNORED' ? Math.max(0, workspace.activeCount - 1) : workspace.activeCount
  };
}

function formatTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' })
    .format(new Date(value));
}
