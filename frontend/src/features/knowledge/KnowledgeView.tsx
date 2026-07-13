import { useEffect, useState } from 'react';

import { KnowledgeNavigation } from './KnowledgeNavigation';
import { KnowledgeHome } from './KnowledgeHome';
import { knowledgeApi } from './knowledgeApi';
import { KnowledgeOverview, KnowledgeSection, KnowledgeTopic } from './knowledgeTypes';
import { TopicLibrary } from './topics/TopicLibrary';

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
  const [topicCount, setTopicCount] = useState(0);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    setLoading(true);
    const load = section === 'home'
      ? knowledgeApi.overview().then(setOverview)
      : section === 'topics'
        ? knowledgeApi.topics().then((page) => {
          setTopics(page.items);
          setTopicCount(page.totalCount);
        })
        : Promise.resolve();
    load.catch((error) => {
      const message = error instanceof Error ? error.message : '知识工作台加载失败';
      setMessage(message);
      addToast(message, 'error');
    }).finally(() => setLoading(false));
  }, [section]);

  function navigate(next: KnowledgeSection) {
    const params = new URLSearchParams(window.location.search);
    params.set('section', next);
    if (next !== 'topics') params.delete('topic');
    if (next !== 'learning') params.delete('task');
    window.history.pushState({}, '', `${window.location.pathname}?${params.toString()}`);
    setSection(next);
  }

  function navigateTarget(target: string) {
    const targetParams = new URLSearchParams(target.startsWith('?') ? target.slice(1) : target);
    const next = targetParams.get('section') as KnowledgeSection | null;
    if (!next || !validSections.has(next)) return;
    window.history.pushState({}, '', `${window.location.pathname}?${targetParams.toString()}`);
    setSection(next);
  }

  async function searchTopics(query: string) {
    setLoading(true);
    try {
      const page = await knowledgeApi.topics({ query });
      setTopics(page.items);
      setTopicCount(page.totalCount);
    } finally {
      setLoading(false);
    }
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

      {section === 'home' && (overview
        ? <KnowledgeHome overview={overview} onNavigate={navigateTarget} />
        : <section className="knowledge-loading" aria-label="正在加载知识首页"><span /></section>)}
      {section === 'topics' && <TopicLibrary
        topics={topics}
        totalCount={topicCount}
        loading={loading}
        onSearch={searchTopics}
        onOpenTopic={(id) => navigateTarget(`?section=topics&topic=${id}`)}
      />}
      {section === 'learning' && <section className="knowledge-placeholder"><h2>学习队列</h2></section>}
      {section === 'review' && <section className="knowledge-placeholder"><h2>到期复习</h2></section>}
    </section>
  );
}
