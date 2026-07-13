import { KnowledgeSection } from './knowledgeTypes';

const sections: Array<{ id: KnowledgeSection; label: string; hint: string }> = [
  { id: 'home', label: '工作台首页', hint: '下一步' },
  { id: 'topics', label: '主题档案', hint: '知识地图' },
  { id: 'learning', label: '学习队列', hint: '回答问题' },
  { id: 'review', label: '到期复习', hint: '更新判断' }
];

export function KnowledgeNavigation({
  section,
  onChange
}: {
  section: KnowledgeSection;
  onChange: (section: KnowledgeSection) => void;
}) {
  return (
    <nav className="knowledge-navigation" aria-label="知识工作台">
      {sections.map((item) => (
        <button
          key={item.id}
          type="button"
          aria-label={item.label}
          aria-current={section === item.id ? 'page' : undefined}
          className={section === item.id ? 'knowledge-nav-item active' : 'knowledge-nav-item'}
          onClick={() => onChange(item.id)}
        >
          <span>{item.label}</span>
          <small>{item.hint}</small>
        </button>
      ))}
    </nav>
  );
}
