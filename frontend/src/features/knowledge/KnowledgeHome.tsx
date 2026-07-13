import { KnowledgeOverview } from './knowledgeTypes';

const actionLabels: Record<string, string> = {
  CONTINUE_TASK: '继续学习',
  REVIEW_TOPIC: '开始复习',
  START_TASK: '开始回答',
  CHECK_NEW_EVIDENCE: '检查新证据'
};

const masteryLabels: Record<string, string> = {
  EXPLORING: '探索中', BUILDING: '构建中', REVIEWING: '复习中', MATURE: '已成熟'
};

export function KnowledgeHome({
  overview,
  onNavigate
}: {
  overview: KnowledgeOverview;
  onNavigate: (target: string) => void;
}) {
  const actions = overview.actions.slice(0, 3);
  return (
    <div className="knowledge-home">
      <div className="knowledge-section-heading">
        <div>
          <p className="knowledge-kicker">Today</p>
          <h2>今天从这里继续</h2>
          <p>系统只保留最值得推进的三件事，让每次进入都有明确起点。</p>
        </div>
      </div>

      <ol className="knowledge-action-list" aria-label="下一步行动">
        {actions.length > 0 ? actions.map((action, index) => (
          <li className="knowledge-action-card" key={`${action.type}-${action.taskId || action.topicId || index}`}>
            <span className="knowledge-action-index">{String(index + 1).padStart(2, '0')}</span>
            <div>
              <p className="knowledge-action-source">{action.sourceLabel || '知识工作台'}</p>
              <h3>{action.title}</h3>
              <p>{action.reason}</p>
            </div>
            <button
              className="knowledge-primary-action"
              type="button"
              aria-label={`继续处理：${action.title}`}
              onClick={() => onNavigate(action.routeTarget)}
            >
              {actionLabels[action.type] || '继续处理'} <span aria-hidden="true">→</span>
            </button>
          </li>
        )) : (
          <li className="knowledge-empty-action">
            <strong>当前没有必须处理的任务</strong>
            <p>可以从主题档案挑一个长期问题，或先去研究流收集新的证据。</p>
          </li>
        )}
      </ol>

      <div className="knowledge-home-grid">
        <section className="knowledge-paper knowledge-active-topics">
          <div className="knowledge-panel-heading">
            <div><p className="knowledge-kicker">Active files</p><h3>正在研究的主题</h3></div>
            <button type="button" onClick={() => onNavigate('?section=topics')}>查看全部</button>
          </div>
          <div className="knowledge-topic-rows">
            {overview.activeTopics.length > 0 ? overview.activeTopics.map((topic) => (
              <button key={topic.id} type="button" onClick={() => onNavigate(`?section=topics&topic=${topic.id}`)}>
                <span><strong>{topic.name}</strong><small>{topic.description || '尚未补充研究范围'}</small></span>
                <span className={`knowledge-state state-${topic.masteryStatus?.toLowerCase()}`}>
                  {masteryLabels[topic.masteryStatus] || topic.masteryStatus}
                </span>
              </button>
            )) : <p className="knowledge-empty-copy">还没有活跃主题。建立第一个档案，把零散信息放进长期问题里。</p>}
          </div>
        </section>

        <aside className="knowledge-now-panel">
          <div><span>进行中的学习</span><strong>{overview.acceptedTaskCount}</strong></div>
          <div><span>今天到期复习</span><strong>{overview.dueReviewCount}</strong></div>
          <button
            type="button"
            className="knowledge-suggestion-inbox"
            onClick={() => onNavigate('?section=learning&status=SUGGESTED')}
          >
            <span>建议收件箱</span>
            <strong>{overview.suggestedTaskCount} 条待确认建议</strong>
            <small>建议不会自动进入你的学习队列</small>
          </button>
        </aside>
      </div>

      <section className="knowledge-paper knowledge-recent">
        <div className="knowledge-panel-heading">
          <div><p className="knowledge-kicker">Recent notes</p><h3>最近形成的知识记录</h3></div>
        </div>
        {overview.recentEntries.length > 0 ? (
          <div className="knowledge-entry-grid">
            {overview.recentEntries.map((entry) => (
              <article key={entry.id}>
                <span>{entry.confidence === 'HIGH' ? '高置信' : entry.confidence === 'MEDIUM' ? '中置信' : '待验证'}</span>
                <h4>{entry.questionSnapshot || '主题记录'}</h4>
                <p>{entry.contentMarkdown}</p>
              </article>
            ))}
          </div>
        ) : <p className="knowledge-empty-copy">完成一个学习问题后，形成的答案会沉淀在这里。</p>}
      </section>
    </div>
  );
}
