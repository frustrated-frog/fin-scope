import { KnowledgeSection } from './knowledgeTypes';

const sections: Array<{ id: KnowledgeSection; label: string; hint: string }> = [
  { id: 'home', label: '工作台', hint: '今日待办' },
  { id: 'facts', label: '事实核验', hint: '证据卷宗' },
  { id: 'topics', label: '知识库', hint: '长期判断' }
];

export function KnowledgeNavigation({
  section,
  onChange
}: {
  section: KnowledgeSection;
  onChange: (section: KnowledgeSection) => void;
}) {
  const activeSection = section === 'learning' ? 'home' : section === 'review' ? 'topics' : section;
  return (
    <nav className="knowledge-navigation" aria-label="知识工作台">
      {sections.map((item) => (
        <button
          key={item.id}
          type="button"
          aria-label={item.label}
          aria-current={activeSection === item.id ? 'page' : undefined}
          className={activeSection === item.id ? 'knowledge-nav-item active' : 'knowledge-nav-item'}
          onClick={() => onChange(item.id)}
        >
          <span>{item.label}</span>
          <small>{item.hint}</small>
        </button>
      ))}
    </nav>
  );
}
