import { DashboardMarketOverview } from './DashboardMarketOverview';
import { FlowField } from '../../shared/visuals/fluid/FlowField';
import {
  AgentRun,
  Article,
  Dashboard,
  DashboardHotspotItem,
  DashboardHotspotRanking,
  IntakeCandidate,
  ResearchRun,
  View
} from '../../shared/types';
import { KnowledgeOverview } from '../knowledge/knowledgeTypes';

type DashboardViewProps = {
  dashboard: Dashboard | null;
  hotspotRankings: DashboardHotspotRanking[];
  articles: Article[];
  researchRuns: ResearchRun[];
  agentRuns: AgentRun[];
  intakeCandidates: IntakeCandidate[];
  knowledgeOverview: KnowledgeOverview | null;
  marketRefreshRevision?: number;
  onChangeView: (view: View) => void;
  onOpenRadarEvent: (eventId: string | number) => void;
};

const ACTIVE_RUN_STATUSES = new Set(['PENDING', 'QUEUED', 'RUNNING']);
const ACTIVE_AGENT_STATUSES = new Set(['PENDING', 'QUEUED', 'RUNNING']);

export function DashboardView({
  dashboard,
  hotspotRankings,
  articles,
  researchRuns,
  agentRuns,
  intakeCandidates,
  knowledgeOverview,
  marketRefreshRevision = 0,
  onChangeView,
  onOpenRadarEvent
}: DashboardViewProps) {
  if (!dashboard) {
    return (
      <section className="content-grid" aria-label="首页加载中">
        <div className="dashboard-loading-grid">
          {[1, 2, 3, 4].map((index) => (
            <div key={index} className="dashboard-loading-block">
              <div className="skeleton skeleton-text" style={{ width: '88px' }}></div>
              <div className="skeleton skeleton-heading" style={{ width: '62px' }}></div>
              <div className="skeleton skeleton-text" style={{ width: '146px' }}></div>
            </div>
          ))}
        </div>
      </section>
    );
  }

  const newArticleCount = articles.filter((article) => article.noveltyType === 'NEW').length;
  const pendingCandidates = intakeCandidates.filter((candidate) => (candidate.humanStatus || 'PENDING') === 'PENDING');
  const dueReviewCount = knowledgeOverview?.dueReviewCount ?? 0;
  const activeRuns = researchRuns.filter((run) => ACTIVE_RUN_STATUSES.has(run.status));
  const activeAgentCount = agentRuns.filter((run) => ACTIVE_AGENT_STATUSES.has(run.status)).length;
  const normalizedHotspotRankings = normalizeHotspotRankings(hotspotRankings);
  const pulseItems = [
    {
      label: '新信息',
      value: newArticleCount,
      detail: newArticleCount ? '等待归并进事件与证据链' : '文章池暂未出现新的高新意内容',
      tone: 'fresh',
      view: 'article' as View
    },
    {
      label: '候选待看',
      value: pendingCandidates.length,
      detail: pendingCandidates.length ? '等待确认是否进入研究流' : '当前候选队列已清空',
      tone: 'attention',
      view: 'intake' as View
    },
    {
      label: '到期复习',
      value: dueReviewCount,
      detail: dueReviewCount ? '已有知识结论需要复核' : '没有到期的知识复习',
      tone: 'review',
      view: 'knowledge' as View
    },
    {
      label: '运行中',
      value: activeRuns.length + activeAgentCount,
      detail: activeRuns.length || activeAgentCount ? '研究或 Agent 正在处理资料' : '没有正在运行的自动化任务',
      tone: 'active',
      view: 'research' as View
    }
  ];
  const priorityPulseItem = pulseItems.find((item) => item.value > 0);
  const pulseStatus = priorityPulseItem
    ? `${priorityPulseItem.label} ${priorityPulseItem.value} 项待处理`
    : '研究队列已就绪';
  const pulseFocus = priorityPulseItem
    ? priorityPulseItem.detail
    : '暂时没有待处理研究队列';

  return (
    <section className="content-grid dashboard-command">
      <section className="dashboard-pulse" aria-labelledby="dashboard-pulse-heading">
        <div className="dashboard-pulse-intro">
          <span className="dashboard-section-kicker">TODAY / RESEARCH FLOW</span>
          <div className="dashboard-pulse-heading-row">
            <h3 id="dashboard-pulse-heading">今天的研究脉冲</h3>
          </div>
          <p>从新信息到可验证结论，先处理会改变判断的队列。</p>
          <div className="dashboard-pulse-focus">
            <span>当前焦点</span>
            <strong>{pulseFocus}</strong>
            <span className="dashboard-pulse-status" role="status">
              <i aria-hidden="true"></i>
              {pulseStatus}
            </span>
          </div>
        </div>
        <FlowField mode="cards" className="dashboard-pulse-items">
          {pulseItems.map((item) => (
            <button
              key={item.label}
              className={`dashboard-pulse-item is-${item.tone}`}
              data-flow-surface={item.tone}
              type="button"
              aria-label={`${item.label} ${item.value}，${item.detail}。打开${pulseWorkspaceName(item.view)}`}
              onClick={() => onChangeView(item.view)}
            >
              <span className="dashboard-pulse-item-label"><i aria-hidden="true"></i>{item.label}</span>
              <span className="dashboard-pulse-value">
                <strong>{item.value}</strong>
                <small aria-hidden="true">ITEMS</small>
              </span>
              <span className="dashboard-pulse-meter" aria-hidden="true"><i></i></span>
              <small>{item.detail}</small>
            </button>
          ))}
        </FlowField>
      </section>

      <section className="dashboard-hotspots" aria-labelledby="dashboard-hotspots-heading">
        <div className="dashboard-section-heading">
          <div>
            <span className="dashboard-section-kicker">TOP STORIES / HOTSPOT RANKING</span>
            <h3 id="dashboard-hotspots-heading">今日热点</h3>
          </div>
          <p>按热点分排列金融、科技与政治事件；摘要保留事实内容，点击可进入事件详情。</p>
        </div>
        <FlowField mode="panels" className="dashboard-hotspot-grid">
          {normalizedHotspotRankings.map((ranking) => (
            <HotspotBoard key={ranking.categoryCode} ranking={ranking} onOpen={onOpenRadarEvent} />
          ))}
        </FlowField>
      </section>

      <DashboardMarketOverview refreshRevision={marketRefreshRevision} onChangeView={onChangeView} />

    </section>
  );
}

const HOTSPOT_BOARD_META: Array<Pick<DashboardHotspotRanking, 'categoryCode' | 'label'>> = [
  { categoryCode: 'FINANCE', label: '金融' },
  { categoryCode: 'TECHNOLOGY', label: '科技' },
  { categoryCode: 'POLITICS', label: '政治' }
];

function pulseWorkspaceName(view: View) {
  const labels: Partial<Record<View, string>> = {
    article: '文章工作区',
    intake: '候选队列',
    knowledge: '知识工作台',
    research: '研究工作区'
  };
  return labels[view] || '对应工作区';
}

function normalizeHotspotRankings(rankings?: DashboardHotspotRanking[]) {
  return HOTSPOT_BOARD_META.map((meta) => {
    const ranking = rankings?.find((item) => item.categoryCode === meta.categoryCode);
    return { ...meta, items: (ranking?.items ?? []).slice(0, 5) };
  });
}

function HotspotBoard({ ranking, onOpen }: {
  ranking: DashboardHotspotRanking;
  onOpen: (eventId: string | number) => void;
}) {
  return (
    <article className={`dashboard-hotspot-board is-${ranking.categoryCode.toLowerCase()}`} data-flow-surface={ranking.categoryCode}>
      <header>
        <div>
          <span>{ranking.categoryCode}</span>
          <h4>{ranking.label}</h4>
        </div>
        <strong>{ranking.items.length.toString().padStart(2, '0')}</strong>
      </header>
      <div className="dashboard-hotspot-list">
        {ranking.items.length ? ranking.items.map((item, index) => (
          <HotspotItem key={item.id} item={item} rank={index + 1} onOpen={onOpen} />
        )) : (
          <div className="dashboard-hotspot-empty">
            <span aria-hidden="true">∅</span>
            <p>当前还没有可进入该榜单的活跃事件。</p>
          </div>
        )}
      </div>
    </article>
  );
}

function HotspotItem({ item, rank, onOpen }: {
  item: DashboardHotspotItem;
  rank: number;
  onOpen: (eventId: string | number) => void;
}) {
  return (
    <button
      className={`dashboard-hotspot-item${rank === 1 ? ' is-lead' : ''}`}
      type="button"
      onClick={() => onOpen(item.id)}
    >
      <span className="dashboard-hotspot-rank">{rank.toString().padStart(2, '0')}</span>
      <span className="dashboard-hotspot-copy">
        <strong>{item.title}</strong>
        <p>{item.summary}</p>
        <span className="dashboard-hotspot-meta">
          <b>热点 {item.hotspotScore}</b>
          <span>{hotspotLifecycleLabel(item.lifecycleState)}</span>
          <span>{item.sourceCount} 个独立来源</span>
          <time dateTime={item.lastSeenAt}>{relativeTime(item.lastSeenAt)}</time>
        </span>
      </span>
    </button>
  );
}

function hotspotLifecycleLabel(value?: string) {
  if (value === 'DISCOVERED') return '新发现';
  if (value === 'EMERGING') return '开始增长';
  if (value === 'RISING') return '正在升温';
  if (value === 'PEAK') return '热度峰值';
  if (value === 'STABLE') return '持续发酵';
  if (value === 'COOLING') return '热度下降';
  if (value === 'REACTIVATED') return '再次升温';
  if (value === 'QUIET') return '趋于平静';
  if (value === 'CLOSED') return '已结束';
  return '状态待确认';
}

function relativeTime(value?: string) {
  if (!value) return '更新时间未知';
  const timestamp = new Date(value).getTime();
  if (Number.isNaN(timestamp)) return '更新时间未知';
  const minutes = Math.max(0, Math.floor((Date.now() - timestamp) / 60_000));
  if (minutes < 1) return '刚刚更新';
  if (minutes < 60) return `${minutes} 分钟前`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} 小时前`;
  return `${Math.floor(hours / 24)} 天前`;
}
