import { useEffect, useState } from 'react';

import { KnowledgeNavigation } from './KnowledgeNavigation';
import { knowledgeApi } from './knowledgeApi';
import { KnowledgeOverview, KnowledgeSection, KnowledgeTopic } from './knowledgeTypes';

const validSections = new Set<KnowledgeSection>(['home', 'topics', 'learning', 'review']);

function locationState() {
  const params = new URLSearchParams(window.location.search);
  const candidate = params.get('section') as KnowledgeSection | null;
  const topic = Number(params.get('topic'));
  return {
    section: candidate && validSections.has(candidate) ? candidate : 'home' as KnowledgeSection,
    topicId: Number.isSafeInteger(topic) && topic > 0 ? topic : undefined
  };
}

export function KnowledgeView({
  addToast,
  setMessage
}: {
  addToast: (message: string, type?: 'success' | 'error' | 'info') => void;
  setMessage: (message: string) => void;
}) {
  const initial = locationState();
  const [section, setSection] = useState<KnowledgeSection>(initial.section);
  const [topicId] = useState<number | undefined>(initial.topicId);
  const [overview, setOverview] = useState<KnowledgeOverview | null>(null);
  const [topics, setTopics] = useState<KnowledgeTopic[]>([]);

  useEffect(() => {
    const load = section === 'home'
      ? knowledgeApi.overview().then(setOverview)
      : section === 'topics'
        ? knowledgeApi.topics().then((page) => setTopics(page.items))
        : Promise.resolve();
    load.catch((error) => {
      const message = error instanceof Error ? error.message : '知识工作台加载失败';
      setMessage(message);
      addToast(message, 'error');
    });
  }, [section]);

  function navigate(next: KnowledgeSection) {
    const params = new URLSearchParams(window.location.search);
    params.set('section', next);
    if (next !== 'topics') params.delete('topic');
    if (next !== 'learning') params.delete('task');
    window.history.pushState({}, '', `${window.location.pathname}?${params.toString()}`);
    setSection(next);
  }

  return (
    <section className="knowledge-workbench" data-testid="knowledge-view" data-topic-id={topicId}>
      <header className="knowledge-header">
        <div>
          <p className="knowledge-kicker">Knowledge workbench</p>
          <h1>把信息变成可复用的判断</h1>
        </div>
        <p>从证据出发，完成问题、记录结论，并在新事实出现时回来复习。</p>
      </header>
      <KnowledgeNavigation section={section} onChange={navigate} />

      {section === 'home' && (
        <section className="knowledge-placeholder">
          <h2>今天从这里继续</h2>
          <p>{overview ? `${overview.acceptedTaskCount} 项学习进行中` : '正在整理你的下一步行动…'}</p>
        </section>
      )}
      {section === 'topics' && (
        <section className="knowledge-placeholder">
          <h2>主题档案</h2>
          <p>{topics.length > 0 ? `${topics.length} 个研究主题` : '从一个长期问题开始建立主题。'}</p>
        </section>
      )}
      {section === 'learning' && <section className="knowledge-placeholder"><h2>学习队列</h2></section>}
      {section === 'review' && <section className="knowledge-placeholder"><h2>到期复习</h2></section>}
    </section>
  );
}
