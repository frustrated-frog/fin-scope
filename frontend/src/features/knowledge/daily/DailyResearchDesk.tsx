import { KnowledgeOverview } from '../knowledgeTypes';
import { ResearchRadarSnapshot } from '../../news/researchRadarTypes';
import { describeVerificationGap, projectDailyResearch } from './dailyResearchProjection';

const actionLabels: Record<string, string> = {
  CONTINUE_TASK: '继续回答',
  REVIEW_TOPIC: '复查判断',
  START_TASK: '开始回答',
  CHECK_NEW_EVIDENCE: '检查新证据'
};

export function DailyResearchDesk({
  overview,
  radar,
  radarError,
  onNavigate
}: {
  overview: KnowledgeOverview;
  radar: ResearchRadarSnapshot | null;
  radarError?: string;
  onNavigate: (target: string) => void;
}) {
  const daily = projectDailyResearch(radar);
  const actions = overview.actions
    .filter((action) => action.type === 'REVIEW_TOPIC' || action.type === 'CHECK_NEW_EVIDENCE')
    .slice(0, 3);

  return (
    <div className="daily-research-desk">
      <header className="daily-research-header">
        <div>
          <p className="knowledge-kicker">每日研究账页</p>
          <h1>今天哪些变化，值得修正我的判断？</h1>
          <p>快讯只提供线索。先把重复报道合成变化，再检查它是否足以改变原有认识。</p>
        </div>
        <div className="daily-research-meta">
          <span>{daily.changes.length} 项变化</span>
          <span>{daily.flashes.length} 条快讯</span>
          <time dateTime={daily.refreshedAt}>{formatRefreshTime(daily.refreshedAt)}</time>
        </div>
      </header>

      {(radarError || daily.warnings.length > 0) && (
        <div className="daily-research-warning" role="status">
          {radarError || `部分资讯来源暂不可用：${daily.warnings.join('；')}`}
        </div>
      )}

      <div className="daily-research-grid">
        <section className="daily-change-ledger" aria-label="今日市场变化">
          <div className="daily-section-heading">
            <div><span>聚合后再阅读</span><h2>值得研究的变化</h2></div>
            <button type="button" onClick={() => onNavigate('?section=facts')}>打开核验队列</button>
          </div>
          {daily.changes.length > 0 ? (
            <div className="daily-change-list">
              {daily.changes.map((change) => (
                <article className="daily-change" key={change.id}>
                  <div className="daily-change-lead">
                    <div className="daily-change-meta">
                      <span>{change.recommendation}</span>
                      <time dateTime={change.lastSeenAt}>{formatTime(change.lastSeenAt)}</time>
                      <small>{change.sourceCount} 个来源 · {change.signalCount} 条信号</small>
                    </div>
                    <h3>{change.title}</h3>
                    <p>{change.summary || '当前只有聚合标题，需继续查看来源材料。'}</p>
                  </div>
                  <dl className="daily-judgment-chain">
                    <div>
                      <dt>为何重要</dt>
                      <dd>{change.watchlistRelated ? change.watchlistExplanation : change.reasons[0] || '多条市场信号在短时间内指向同一变化'}</dd>
                    </div>
                    <div>
                      <dt>尚待确认</dt>
                      <dd>{describeVerificationGap(change)}</dd>
                    </div>
                    <div>
                      <dt>下一观察</dt>
                      <dd>{change.nextObservation || '等待一手披露或后续经营数据'}</dd>
                    </div>
                  </dl>
                </article>
              ))}
            </div>
          ) : (
            <div className="daily-research-empty"><strong>今天还没有形成可研究的聚合变化</strong><p>右侧快讯仍可浏览，但它们暂时只是线索。</p></div>
          )}
        </section>

        <aside className="daily-flash-ledger" aria-label="今日快讯流水">
          <div className="daily-section-heading">
            <div><span>未经沉淀的线索</span><h2>快讯流水</h2></div>
          </div>
          {daily.flashes.length > 0 ? (
            <ol>
              {daily.flashes.map((flash) => (
                <li key={flash.id}>
                  <time dateTime={flash.publishedAt}>{formatTime(flash.publishedAt)}</time>
                  <div>
                    <span>{flash.sourceName}</span>
                    {flash.url ? <a href={flash.url} target="_blank" rel="noreferrer">{flash.title}</a> : <strong>{flash.title}</strong>}
                    <p>{flash.content}</p>
                  </div>
                </li>
              ))}
            </ol>
          ) : <p className="daily-research-empty">暂时没有可用快讯。</p>}
        </aside>
      </div>

      <section className="recognition-update" aria-labelledby="recognition-update-heading">
        <div className="daily-section-heading">
          <div><span>让新事实回到旧判断</span><h2 id="recognition-update-heading">需要更新的认识</h2></div>
          <button type="button" onClick={() => onNavigate('?section=topics')}>打开投资认识</button>
        </div>
        {actions.length > 0 ? (
          <div className="recognition-action-list">
            {actions.map((action, index) => (
              <article key={`${action.type}-${action.taskId || action.topicId || index}`}>
                <span>{actionLabels[action.type] || '继续研究'}</span>
                <h3>{action.title}</h3>
                <p>{action.reason}</p>
                <button type="button" aria-label={`复查：${action.title}`} onClick={() => onNavigate(action.routeTarget)}>进入判断 <span aria-hidden="true">→</span></button>
              </article>
            ))}
          </div>
        ) : (
          <div className="daily-research-empty"><strong>当前没有必须更新的判断</strong><p>这不是“没有新闻”，而是今天的变化尚未触发已有认识。</p></div>
        )}
        {overview.acceptedTaskCount > 0 && (
          <div className="recognition-draft-note">
            <span>{overview.acceptedTaskCount} 个学习草稿尚未计入投资认识</span>
            <button type="button" onClick={() => onNavigate('?section=learning')}>查看学习草稿</button>
          </div>
        )}
        {overview.recentEntries.length > 0 && (
          <div className="recognition-recent">
            <span>最近形成</span>
            {overview.recentEntries.slice(0, 3).map((entry) => <p key={entry.id}><strong>{entry.questionSnapshot || '投资认识'}</strong>{entry.contentMarkdown}</p>)}
          </div>
        )}
      </section>
    </div>
  );
}

function parseDate(value?: string) { return value ? new Date(value) : undefined; }
function formatTime(value?: string) {
  const date = parseDate(value);
  return date && !Number.isNaN(date.getTime())
    ? new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false }).format(date)
    : '--:--';
}
function formatRefreshTime(value?: string) {
  return value ? `更新于 ${formatTime(value)}` : '等待今日资讯';
}
