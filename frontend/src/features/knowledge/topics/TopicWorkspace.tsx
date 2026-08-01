import { useState } from 'react';

import { KnowledgeReviewInput, KnowledgeTopicWorkspace } from '../knowledgeTypes';
import { KnowledgeMarkdown } from '../KnowledgeMarkdown';
import { TopicTimeline } from './TopicTimeline';
import { classifyKnowledgeTopic } from './knowledgeClassification';

export function TopicWorkspace({
  workspace,
  reviewMode = false,
  onBack,
  onReview
}: {
  workspace: KnowledgeTopicWorkspace;
  reviewMode?: boolean;
  onBack: () => void;
  onReview: (input: KnowledgeReviewInput) => Promise<void>;
}) {
  const [conclusion, setConclusion] = useState('');
  const [confidence, setConfidence] = useState<KnowledgeReviewInput['confidence']>('MEDIUM');
  const [interval, setInterval] = useState<KnowledgeReviewInput['intervalDays']>(14);
  const [evidenceIds, setEvidenceIds] = useState<number[]>([]);
  const latest = workspace.entries[0];
  const classification = classifyKnowledgeTopic(workspace.topic, workspace.entries.length);
  const isMaterial = classification === 'MATERIAL';

  return (
    <article className="topic-workspace">
      <header>
        <button type="button" onClick={onBack}>← 返回{reviewMode ? '复习队列' : '投资认识'}</button>
        <div><p className="knowledge-kicker">{isMaterial ? '待提炼材料' : '投资认识档案'}</p><h2>{workspace.topic.name}</h2><p>{workspace.topic.description}</p></div>
        <span className="knowledge-state">{isMaterial ? '尚未提炼' : workspace.topic.masteryStatus}</span>
      </header>
      <div className="topic-workspace-grid">
        <main>
          {isMaterial
            ? <section className="topic-current-judgment is-material"><p className="knowledge-kicker">材料不等于认识</p><h3>尚未形成投资认识</h3><p>当前档案只有单一来源，也没有独立结论。请先提炼一个可持续验证的问题，再决定它是否值得长期维护。</p></section>
            : <section className="topic-current-judgment"><p className="knowledge-kicker">当前可检验结论</p><h3>当前判断</h3><KnowledgeMarkdown value={latest?.contentMarkdown || '还没有形成结论。先完成一个学习问题，让证据开始沉淀。'} /></section>}
          <TopicTimeline workspace={workspace} />
        </main>
        <aside>
          <section><h3>资料来源</h3>{workspace.evidence.length ? workspace.evidence.map((item) => <p key={item.id}>{item.articleTitle || item.sourceTier} · {item.confidence}</p>) : <p>暂无资料来源</p>}</section>
          <section><h3>复习节奏</h3><p>已复习 {workspace.reviewState?.reviewCount || 0} 次</p><p>间隔 {workspace.reviewState?.intervalDays || 7} 天</p></section>
        </aside>
      </div>
      {reviewMode && !isMaterial && (
        <section className="topic-review-editor">
          <div><p className="knowledge-kicker">Review</p><h3>用新证据重新检查结论</h3><p>先比较原结论和新增证据，再决定保持、修正或推翻。</p></div>
          <div className="topic-review-compare"><article><span>原结论</span><KnowledgeMarkdown value={latest?.contentMarkdown || '暂无结论'} /></article><article><span>本次证据</span>{workspace.evidence.map((item) => <label key={item.id}><input type="checkbox" checked={evidenceIds.includes(item.id)} onChange={() => setEvidenceIds((ids) => ids.includes(item.id) ? ids.filter((id) => id !== item.id) : [...ids, item.id])} />{item.claim}</label>)}</article></div>
          <label>更新后的结论<textarea rows={6} value={conclusion} onChange={(event) => setConclusion(event.target.value)} /></label>
          <div className="topic-review-controls"><label>置信度<select value={confidence} onChange={(event) => setConfidence(event.target.value as KnowledgeReviewInput['confidence'])}><option value="LOW">低</option><option value="MEDIUM">中</option><option value="HIGH">高</option></select></label><label>下次复习<select aria-label="下次复习" value={interval} onChange={(event) => setInterval(Number(event.target.value) as KnowledgeReviewInput['intervalDays'])}><option value="7">7 天</option><option value="14">14 天</option><option value="30">30 天</option><option value="90">90 天</option></select></label></div>
          <button className="knowledge-primary-button" type="button" disabled={!conclusion.trim()} onClick={() => onReview({ conclusion: conclusion.trim(), confidence, evidenceIds, intervalDays: interval, expectedRevision: workspace.reviewState?.revision || 0 })}>完成复习</button>
        </section>
      )}
    </article>
  );
}
