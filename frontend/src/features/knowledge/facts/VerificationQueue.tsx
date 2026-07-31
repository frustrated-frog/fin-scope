import { useEffect, useMemo, useState } from 'react';

import { KnowledgeTopicWorkspace } from '../knowledgeTypes';
import {
  projectVerificationQueue,
  VerificationQueueItem,
  VerificationStatus
} from './verificationQueueProjection';

const statusLabels: Record<VerificationStatus, string> = {
  NEEDS_PRIMARY: '待找一手',
  RECORDED: '已记录'
};

const sourceLabels: Record<string, string> = {
  REGULATOR: '监管',
  OFFICIAL: '官方',
  COMPANY: '公司',
  RESEARCH: '研究',
  MEDIA: '媒体',
  UNKNOWN: '来源待识别'
};

export function VerificationQueue({
  workspaces,
  onNavigate
}: {
  workspaces: KnowledgeTopicWorkspace[];
  onNavigate: (target: string) => void;
}) {
  const items = useMemo(() => projectVerificationQueue(workspaces), [workspaces]);
  const [filter, setFilter] = useState<VerificationStatus>('NEEDS_PRIMARY');
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const counts = useMemo(() => ({
    NEEDS_PRIMARY: items.filter((item) => item.status === 'NEEDS_PRIMARY').length,
    RECORDED: items.filter((item) => item.status === 'RECORDED').length
  }), [items]);
  const visible = useMemo(() => items.filter((item) => item.status === filter), [filter, items]);

  useEffect(() => {
    if (!visible.some((item) => item.id === selectedId)) {
      setSelectedId(visible[0]?.id || null);
    }
  }, [selectedId, visible]);

  const selected = visible.find((item) => item.id === selectedId) || visible[0];

  return (
    <section className="verification-queue" aria-label="投资命题核验队列">
      <header className="verification-queue-intro">
        <div>
          <p className="knowledge-kicker">只核验会改变判断的命题</p>
          <h2>核验队列</h2>
          <p>这里不验证文章。只有已关联投资认识、能被一手材料确认的具体命题，才进入核验。</p>
        </div>
        <div className="verification-queue-counts" aria-label="核验队列摘要">
          <span><strong>{counts.NEEDS_PRIMARY}</strong>待找一手</span>
          <span><strong>{counts.RECORDED}</strong>已记录</span>
        </div>
      </header>

      <div className="verification-queue-filters" role="group" aria-label="命题状态">
        <button type="button" aria-pressed={filter === 'NEEDS_PRIMARY'} onClick={() => setFilter('NEEDS_PRIMARY')}>
          待找一手 {counts.NEEDS_PRIMARY}
        </button>
        <button type="button" aria-pressed={filter === 'RECORDED'} onClick={() => setFilter('RECORDED')}>
          已记录 {counts.RECORDED}
        </button>
      </div>

      {items.length === 0 ? (
        <VerificationEmpty />
      ) : visible.length === 0 ? (
        <div className="verification-queue-empty compact">
          <strong>{filter === 'RECORDED' ? '还没有一手事实记录' : '当前没有等待核验的命题'}</strong>
          <p>{filter === 'RECORDED' ? '找到可靠的一手来源后，命题会出现在这里。' : '无需为了填满列表而核验文章。'}</p>
        </div>
      ) : (
        <div className="verification-queue-layout">
          <aside className="verification-index" aria-label="待核验命题列表">
            <div className="verification-index-heading">
              <span>{filter === 'NEEDS_PRIMARY' ? '等待核验' : '事实记录'}</span>
              <strong>{visible.length}</strong>
            </div>
            <div className="verification-index-list">
              {visible.map((item) => (
                <button
                  key={item.id}
                  type="button"
                  className={item.id === selected?.id ? 'verification-index-item active' : 'verification-index-item'}
                  aria-label={`${item.proposition}，${statusLabels[item.status]}`}
                  aria-current={item.id === selected?.id ? 'true' : undefined}
                  onClick={() => setSelectedId(item.id)}
                >
                  <span>{statusLabels[item.status]}</span>
                  <strong>{item.proposition}</strong>
                  <small>影响：{item.recognitionName}</small>
                </button>
              ))}
            </div>
          </aside>
          {selected && <VerificationDossier item={selected} onNavigate={onNavigate} />}
        </div>
      )}
    </section>
  );
}

function VerificationEmpty() {
  return (
    <div className="verification-queue-empty">
      <span aria-hidden="true">○</span>
      <h2>当前没有需要核验的投资命题</h2>
      <p>文章和事件不会自动进入这里。先形成一条投资认识，再从影响它的新变化中提出具体命题。</p>
      <small>空队列意味着当前没有合格任务，不代表系统缺少文章。</small>
    </div>
  );
}

function VerificationDossier({
  item,
  onNavigate
}: {
  item: VerificationQueueItem;
  onNavigate: (target: string) => void;
}) {
  return (
    <article className="verification-dossier" aria-label="命题核验卷宗">
      <header>
        <div className="verification-dossier-meta">
          <span data-status={item.status}>{statusLabels[item.status]}</span>
          {item.updatedAt && <time dateTime={item.updatedAt}>{item.updatedAt.slice(0, 10)}</time>}
        </div>
        <p>待核验命题</p>
        <h2>{item.proposition}</h2>
      </header>

      <section className="verification-impact" aria-label="判断影响">
        <div>
          <span>影响的投资认识</span>
          <strong>{item.recognitionName}</strong>
          {item.recognitionDescription && <p>{item.recognitionDescription}</p>}
        </div>
        <button type="button" onClick={() => onNavigate(`?section=topics&topic=${item.recognitionId}`)}>查看认识档案</button>
      </section>

      <section className="verification-next-step" data-status={item.status}>
        <span>{item.status === 'NEEDS_PRIMARY' ? '当前缺口' : '记录结论'}</span>
        <strong>{item.status === 'NEEDS_PRIMARY'
          ? '需要找到公告、监管或公司一手材料'
          : '一手事实已记录，不占用待核验队列。'}</strong>
        <p>{item.status === 'NEEDS_PRIMARY'
          ? '现有内容只能说明有人这样报道，不能直接用来更新投资认识。'
          : '后续只有出现冲突材料或事实发生变化时，才需要重新进入核验队列。'}</p>
      </section>

      <section className="verification-materials" aria-label="命题来源材料">
        <div className="verification-materials-heading">
          <div><span>Source record</span><h3>来源材料</h3></div>
          <small>{item.materials.length} 条同命题记录 · 最高置信度 {item.maxConfidence}</small>
        </div>
        <ol>
          {item.materials.map((material, index) => (
            <li key={material.id}>
              <span>{String(index + 1).padStart(2, '0')}</span>
              <div>
                <div className="verification-material-meta">
                  <strong>{sourceLabels[material.sourceTier] || material.sourceTier}</strong>
                  <small>{material.evidenceType}</small>
                </div>
                <p>{material.articleTitle || '来源标题未记录'}</p>
                {material.articleUrl && <a href={material.articleUrl} target="_blank" rel="noopener noreferrer">打开来源</a>}
              </div>
            </li>
          ))}
        </ol>
      </section>

      <footer className="verification-origin-note">事件来源：{item.eventTitle}</footer>
    </article>
  );
}
