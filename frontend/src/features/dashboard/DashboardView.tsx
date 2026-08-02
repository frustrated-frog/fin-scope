import { Table } from '../../shared/components/Table';
import {
  AgentRun,
  Article,
  ContentIdea,
  Dashboard,
  EventCluster,
  IntakeCandidate,
  LearningTask,
  ResearchRun,
  ResearchThesis,
  View
} from '../../shared/types';
import { KnowledgeOverview } from '../knowledge/knowledgeTypes';

type DashboardViewProps = {
  dashboard: Dashboard | null;
  articles: Article[];
  events: EventCluster[];
  learningTasks: LearningTask[];
  contentIdeas: ContentIdea[];
  researchRuns: ResearchRun[];
  researchTheses: ResearchThesis[];
  agentRuns: AgentRun[];
  intakeCandidates: IntakeCandidate[];
  knowledgeOverview: KnowledgeOverview | null;
  onChangeView: (view: View) => void;
};

const ACTIVE_RUN_STATUSES = new Set(['PENDING', 'QUEUED', 'RUNNING']);
const ACTIVE_AGENT_STATUSES = new Set(['PENDING', 'QUEUED', 'RUNNING']);

export function DashboardView({
  dashboard,
  articles,
  events,
  learningTasks,
  contentIdeas,
  researchRuns,
  researchTheses,
  agentRuns,
  intakeCandidates,
  knowledgeOverview,
  onChangeView
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
  const priorityEvents = [...events]
    .filter((event) => (event.status || 'ACTIVE') !== 'ARCHIVED')
    .sort((left, right) => (right.importanceScore ?? 0) - (left.importanceScore ?? 0))
    .slice(0, 2);
  const openTasks = learningTasks.filter((task) => task.status !== 'DONE').slice(0, 2);
  const openTheses = researchTheses.filter((thesis) => thesis.status === 'OPEN');
  const latestRun = dashboard.latestFetchRuns[0];
  const totalNew = dashboard.latestFetchRuns.reduce((sum, run) => sum + run.successCount, 0);
  const totalDuplicate = dashboard.latestFetchRuns.reduce((sum, run) => sum + run.duplicateCount, 0);
  const completedFetchRuns = dashboard.latestFetchRuns.filter((run) => run.status === 'COMPLETED').length;
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

  return (
    <section className="content-grid dashboard-command">
      <section className="dashboard-pulse" aria-labelledby="dashboard-pulse-heading">
        <div className="dashboard-pulse-intro">
          <span className="dashboard-section-kicker">TODAY / RESEARCH FLOW</span>
          <h3 id="dashboard-pulse-heading">今天的研究脉冲</h3>
          <p>从新信息到可验证结论，先处理会改变判断的队列。</p>
        </div>
        <div className="dashboard-pulse-items">
          {pulseItems.map((item) => (
            <button
              key={item.label}
              className={`dashboard-pulse-item is-${item.tone}`}
              type="button"
              onClick={() => onChangeView(item.view)}
            >
              <span>{item.label}</span>
              <strong>{item.value}</strong>
              <small>{item.detail}</small>
            </button>
          ))}
        </div>
      </section>

      <section className="dashboard-priority" aria-labelledby="dashboard-priority-heading">
        <div className="dashboard-section-heading">
          <div>
            <span className="dashboard-section-kicker">NEXT / DECISIONS</span>
            <h3 id="dashboard-priority-heading">优先处理</h3>
          </div>
          <p>首页只保留下一步，而不是复刻每个工作区的完整列表。</p>
        </div>
        <div className="dashboard-priority-grid">
          <PriorityLane
            label="事件"
            count={priorityEvents.length}
            command="查看研究流"
            onOpen={() => onChangeView('events')}
            empty="暂时没有活跃事件，新的文章会先在文章工作区等待归并。"
            items={priorityEvents.map((event) => ({
              title: event.canonicalTitle,
              meta: `重要度 ${event.importanceScore ?? 0} · ${event.evidenceCount ?? 0} 条证据`,
              description: event.summary
            }))}
          />
          <PriorityLane
            label="学习"
            count={openTasks.length}
            command="查看学习任务"
            onOpen={() => onChangeView('events')}
            empty="暂无待完成学习任务，可以从事件研究台建立新的问题。"
            items={openTasks.map((task) => ({
              title: task.question,
              meta: task.themeCode || '未分类主题',
              description: task.whyNeeded
            }))}
          />
          <PriorityLane
            label="研究运行"
            count={activeRuns.length}
            command="打开研究运行"
            onOpen={() => onChangeView('research')}
            empty="当前没有运行中的研究，可从研究工作区启动新的验证。"
            items={activeRuns.map((run) => ({
              title: run.summary || `${run.runDate} 研究运行`,
              meta: `${run.mode || 'QUICK'} · ${run.status}`,
              description: `${run.articleCount ?? 0} 篇资料 · ${run.evidenceCount ?? 0} 条候选证据`
            }))}
          />
        </div>
      </section>

      <section aria-labelledby="dashboard-workspaces-heading">
        <div className="dashboard-section-heading">
          <div>
            <span className="dashboard-section-kicker">WORKSPACES / OVERVIEW</span>
            <h3 id="dashboard-workspaces-heading">工作区概览</h3>
          </div>
          <p>把数字放在它们所属的研究阶段，而不是孤立地陈列。</p>
        </div>
        <div className="dashboard-workspace-grid">
          <WorkspaceCard
            label="研究流"
            value={`${events.length} 个事件`}
            detail={`${newArticleCount} 条新内容 · ${dashboard.sourceCount} 个信息源`}
            command="进入事件研究台"
            onOpen={() => onChangeView('events')}
          />
          <WorkspaceCard
            label="知识与判断"
            value={`${knowledgeOverview?.activeTopicCount ?? 0} 个活跃主题`}
            detail={`${knowledgeOverview?.acceptedTaskCount ?? 0} 个已接纳任务 · ${dueReviewCount} 个待复习`}
            command="打开知识工作台"
            onOpen={() => onChangeView('knowledge')}
          />
          <WorkspaceCard
            label="投资观察"
            value={`${openTheses.length} 个开放命题`}
            detail={openTheses[0]?.nextValidation || '从研究问题建立可验证的投资观察。'}
            command="查看研究命题"
            onOpen={() => onChangeView('research')}
          />
          <WorkspaceCard
            label="内容输出"
            value={`${contentIdeas.length} 个内容选题`}
            detail={contentIdeas[0]?.title || '研究结论会在这里转化为可继续打磨的表达。'}
            command="进入内容工作室"
            onOpen={() => onChangeView('contentStudio')}
          />
        </div>
      </section>

      <section className="dashboard-ledger" aria-labelledby="dashboard-ledger-heading">
        <div className="dashboard-ledger-summary">
          <div>
            <span className="dashboard-section-kicker">LEDGER / COLLECTION</span>
            <h3 id="dashboard-ledger-heading">运行账本</h3>
            <p>{latestRun ? `${latestRun.sourceName} 最近一次抓取：${latestRun.status}` : '等待首个抓取任务，信息流会在这里留下质量记录。'}</p>
          </div>
          <div className="dashboard-ledger-stats" aria-label="抓取汇总">
            <span><strong>{totalNew}</strong> 新增</span>
            <span><strong>{totalDuplicate}</strong> 重复</span>
            <span><strong>{completedFetchRuns}</strong> 完成</span>
          </div>
        </div>
        <Table
          headers={['来源', '状态', '有效新增', '重复内容']}
          rows={dashboard.latestFetchRuns.map((run) => [
            run.sourceName,
            <span key={`${run.id}-status`} className={`dashboard-status is-${run.status.toLowerCase()}`}>{run.status}</span>,
            String(run.successCount),
            String(run.duplicateCount)
          ])}
          empty="还没有抓取记录。先配置一个稳定信源，系统会在这里记录每次采集的有效产出。"
        />
      </section>
    </section>
  );
}

function PriorityLane({
  label,
  count,
  command,
  onOpen,
  empty,
  items
}: {
  label: string;
  count: number;
  command: string;
  onOpen: () => void;
  empty: string;
  items: Array<{ title: string; meta: string; description?: string }>;
}) {
  return (
    <article className="dashboard-priority-lane">
      <div className="dashboard-priority-lane-head">
        <span>{label}</span>
        <strong>{count}</strong>
      </div>
      <div className="dashboard-priority-items">
        {items.length ? items.map((item) => (
          <div key={`${item.title}-${item.meta}`} className="dashboard-priority-item">
            <strong>{item.title}</strong>
            <span>{item.meta}</span>
            {item.description && <p>{item.description}</p>}
          </div>
        )) : <p className="dashboard-priority-empty">{empty}</p>}
      </div>
      <button className="ghost-button dashboard-lane-command" type="button" onClick={onOpen}>{command}</button>
    </article>
  );
}

function WorkspaceCard({
  label,
  value,
  detail,
  command,
  onOpen
}: {
  label: string;
  value: string;
  detail: string;
  command: string;
  onOpen: () => void;
}) {
  return (
    <article className="dashboard-workspace-card">
      <span>{label}</span>
      <strong>{value}</strong>
      <p>{detail}</p>
      <button className="dashboard-text-command" type="button" onClick={onOpen}>{command}</button>
    </article>
  );
}
