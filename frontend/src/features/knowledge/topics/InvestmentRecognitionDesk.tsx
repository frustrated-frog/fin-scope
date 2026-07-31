import { useState } from 'react';

import {
  InvestmentRecognitionCandidate,
  InvestmentRecognitionStatus,
  KnowledgeTopic
} from '../knowledgeTypes';
import { TopicLibrary } from './TopicLibrary';

type DeskTab = 'CANDIDATE' | 'FORMAL' | 'NEEDS_EVIDENCE' | 'INVALIDATED';

export function InvestmentRecognitionDesk({
  candidates,
  topics,
  loading,
  running,
  onRun,
  onAccept,
  onStatus,
  onSearch,
  onOpenTopic,
  onCreate
}: {
  candidates: InvestmentRecognitionCandidate[];
  topics: KnowledgeTopic[];
  loading: boolean;
  running: boolean;
  onRun: () => Promise<void>;
  onAccept: (id: number, revision: number) => Promise<void>;
  onStatus: (id: number, status: InvestmentRecognitionStatus, revision: number) => Promise<void>;
  onSearch: (query: string) => Promise<void>;
  onOpenTopic: (topicId: number) => void;
  onCreate: (input: { name: string; description: string }) => Promise<void>;
}) {
  const [tab, setTab] = useState<DeskTab>('CANDIDATE');
  const [busyId, setBusyId] = useState<number>();
  const candidateItems = candidates.filter((item) => item.status === 'CANDIDATE');
  const evidenceGaps = candidates.filter((item) => item.status === 'NEEDS_EVIDENCE');
  const invalidated = candidates.filter((item) => item.status === 'INVALIDATED' || item.status === 'DISMISSED');

  async function act(id: number, action: () => Promise<void>) {
    setBusyId(id);
    try { await action(); } finally { setBusyId(undefined); }
  }

  return (
    <section className="recognition-desk">
      <header className="recognition-agent-hero">
        <div>
          <p className="knowledge-kicker">Investment recognition agent</p>
          <h2>从市场变化，形成可验证的投资认识</h2>
          <p>Agent 只读取自选与结构化行情；快讯只能触发检查，文章库不会进入生成或证明链路。</p>
        </div>
        <button className="knowledge-primary-button" type="button" disabled={running} onClick={onRun}>
          {running ? '正在检查投资数据…' : '运行投资 Agent'}
        </button>
        <dl className="recognition-agent-metrics" aria-label="认识 Agent 概览">
          <div><dt>待判断</dt><dd>{candidateItems.length}</dd></div>
          <div><dt>正式认识</dt><dd>{topics.length}</dd></div>
          <div><dt>证据缺口</dt><dd>{evidenceGaps.length}</dd></div>
          <div><dt>数据边界</dt><dd>0 篇文章</dd></div>
        </dl>
      </header>

      <nav className="recognition-status-tabs" aria-label="投资认识状态">
        <button type="button" aria-pressed={tab === 'CANDIDATE'} onClick={() => setTab('CANDIDATE')}>Agent 候选 {candidateItems.length}</button>
        <button type="button" aria-pressed={tab === 'FORMAL'} onClick={() => setTab('FORMAL')}>正式认识 {topics.length}</button>
        <button type="button" aria-pressed={tab === 'NEEDS_EVIDENCE'} onClick={() => setTab('NEEDS_EVIDENCE')}>待补证据 {evidenceGaps.length}</button>
        <button type="button" aria-pressed={tab === 'INVALIDATED'} onClick={() => setTab('INVALIDATED')}>已失效 {invalidated.length}</button>
      </nav>

      {tab === 'FORMAL' ? (
        <TopicLibrary topics={topics} loading={loading} onSearch={onSearch} onOpenTopic={onOpenTopic} onCreate={onCreate} />
      ) : (
        <CandidateList
          items={tab === 'CANDIDATE' ? candidateItems : tab === 'NEEDS_EVIDENCE' ? evidenceGaps : invalidated}
          tab={tab}
          loading={loading}
          busyId={busyId}
          onAccept={(item) => act(item.id, () => onAccept(item.id, item.revision))}
          onStatus={(item, status) => act(item.id, () => onStatus(item.id, status, item.revision))}
        />
      )}
    </section>
  );
}

function CandidateList({ items, tab, loading, busyId, onAccept, onStatus }: {
  items: InvestmentRecognitionCandidate[];
  tab: DeskTab;
  loading: boolean;
  busyId?: number;
  onAccept: (item: InvestmentRecognitionCandidate) => Promise<void>;
  onStatus: (item: InvestmentRecognitionCandidate, status: InvestmentRecognitionStatus) => Promise<void>;
}) {
  if (loading) return <section className="knowledge-loading" aria-label="正在加载投资认识"><span /></section>;
  if (items.length === 0) return (
    <div className="recognition-empty">
      <strong>{tab === 'CANDIDATE' ? '当前没有待判断的 Agent 候选' : tab === 'NEEDS_EVIDENCE' ? '当前没有证据缺口' : '还没有失效记录'}</strong>
      <p>{tab === 'CANDIDATE' ? '运行 Agent 后，只有达到行情变化门槛且能写出失效条件的命题才会进入这里。' : '工作台保留状态历史，但不会用文章内容填充空白。'}</p>
    </div>
  );
  return <div className="recognition-candidate-list">{items.map((item) => (
    <article className="recognition-candidate" key={item.id}>
      <header>
        <div className="recognition-subject"><span>{item.subjectType}</span><strong>{item.subjectName}</strong><code>{item.subjectCode}</code></div>
        <div className="recognition-confidence">{item.confidence} · {item.dataAsOf ? formatTime(item.dataAsOf) : '数据时间待补'}</div>
        <h3>{item.thesis}</h3>
      </header>
      <div className="recognition-reasoning-grid">
        <ReasonBlock title="观察事实" text={item.observedChange} />
        <ReasonBlock title="作用机制" text={item.mechanism} />
        <ReasonList title="支持数据" values={item.supportingData} />
        <ReasonList title="反向证据" values={item.counterData} tone="counter" />
        <ReasonList title="后续验证" values={item.validationMetrics} />
        <ReasonBlock title="失效条件" text={item.invalidationConditions} tone="counter" />
      </div>
      <footer>
        <span>观察窗口：{item.horizon}</span>
        {tab === 'CANDIDATE' && <div>
          <button type="button" disabled={busyId === item.id} onClick={() => onStatus(item, 'NEEDS_EVIDENCE')}>转为待补证据</button>
          <button className="knowledge-primary-button" type="button" disabled={busyId === item.id} onClick={() => onAccept(item)}>收为正式认识</button>
        </div>}
        {tab === 'NEEDS_EVIDENCE' && <button type="button" disabled={busyId === item.id} onClick={() => onStatus(item, 'DISMISSED')}>暂不跟踪</button>}
      </footer>
    </article>
  ))}</div>;
}

function ReasonBlock({ title, text, tone = '' }: { title: string; text: string; tone?: string }) {
  return <section className={tone ? `recognition-reason-block ${tone}` : 'recognition-reason-block'}><h4>{title}</h4><p>{text}</p></section>;
}

function ReasonList({ title, values, tone = '' }: { title: string; values: string[]; tone?: string }) {
  return <section className={tone ? `recognition-reason-block ${tone}` : 'recognition-reason-block'}><h4>{title}</h4><ul>{values.length ? values.map((value) => <li key={value}>{value}</li>) : <li>暂无可用数据</li>}</ul></section>;
}

function formatTime(value: string) {
  return value.replace('T', ' ').slice(0, 16);
}
