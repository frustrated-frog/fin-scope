import type { ReactNode } from 'react';
import './ResearchWorkspace.css';

type Section = 'catalog' | 'experiments' | 'paper';

const sections = {
  catalog: { title: '策略与因子', description: '从策略背后的逻辑入手，查看它用了哪些因子，再选择一个想验证的想法。', metric: '盘中规则版本' },
  experiments: { title: '量化实验室', description: '配置经典策略，或让 AI 编写策略。用同一份行情比较调仓、成交和扣费收益。', metric: '实验记录' },
  paper: { title: '模拟实盘', description: '每个策略独立记账。按交易日跟踪净值、回看调仓决定，再与同期基准比较。', metric: '运行中账户' },
};

export function ResearchHeader({ section, count, children }: { section: Section; count?: number; children?: ReactNode }) {
  const copy = sections[section];
  return <header className="research-mast" key={section}>
    <div className="research-mast-copy">
      <h3>{copy.title}</h3>
      <p>{copy.description}</p>
      {children}
    </div>
    <div className="research-instrument">
      <div className="research-planes" aria-hidden="true"><i/><i/><i/></div>
      <div className="research-counter"><span>{copy.metric}</span><strong>{count ?? '—'}</strong></div>
    </div>
  </header>;
}
