import { useEffect, useMemo, useState } from 'react';

import { EventCluster, EvidenceItem } from '../../../shared/types';
import {
  FactCandidate,
  FactVerificationState,
  projectFactCandidates
} from './factProjection';

const stateLabels: Record<FactVerificationState, string> = {
  SUBSTANTIAL: '证据较充分',
  NEEDS_CORROBORATION: '待交叉验证',
  UNVERIFIED: '待核验'
};

const sourceLabels: Record<string, string> = {
  REGULATOR: '监管来源',
  OFFICIAL: '官方来源',
  COMPANY: '公司披露',
  RESEARCH: '研究材料',
  MEDIA: '媒体材料'
};

const typeLabels: Record<string, string> = {
  FACT: '直接事实',
  TIMELINE: '时间线',
  DATA: '数据线索',
  IMPACT: '影响判断'
};

const observationLabels: Record<string, string> = {
  COMPANY: '公司经营',
  INDUSTRY: '行业供需',
  POLICY: '政策影响',
  MACRO: '宏观环境',
  MARKET: '市场交易',
  TECHNOLOGY: '技术进展',
  OTHER: '其他变化'
};

type StateFilter = 'ALL' | FactVerificationState;

export function FactWorkbench({
  events,
  evidenceItems
}: {
  events: EventCluster[];
  evidenceItems: EvidenceItem[];
}) {
  const candidates = useMemo(() => projectFactCandidates(events, evidenceItems), [events, evidenceItems]);
  const [query, setQuery] = useState('');
  const [stateFilter, setStateFilter] = useState<StateFilter>('ALL');
  const [showMaterialFree, setShowMaterialFree] = useState(false);
  const [selectedId, setSelectedId] = useState<number | null>(candidates[0]?.event.id ?? null);

  const materialFreeCount = candidates.filter((item) => item.evidence.length === 0).length;
  const scopedCandidates = useMemo(
    () => showMaterialFree ? candidates : candidates.filter((item) => item.evidence.length > 0),
    [candidates, showMaterialFree]
  );

  const counts = useMemo(() => ({
    ALL: scopedCandidates.length,
    SUBSTANTIAL: scopedCandidates.filter((item) => item.verificationState === 'SUBSTANTIAL').length,
    NEEDS_CORROBORATION: scopedCandidates.filter((item) => item.verificationState === 'NEEDS_CORROBORATION').length,
    UNVERIFIED: scopedCandidates.filter((item) => item.verificationState === 'UNVERIFIED').length
  }), [scopedCandidates]);

  const visible = useMemo(() => {
    const normalized = query.trim().toLowerCase();
    return scopedCandidates.filter((candidate) => {
      const matchesState = stateFilter === 'ALL' || candidate.verificationState === stateFilter;
      const searchable = [
        candidate.event.canonicalTitle,
        candidate.event.summary,
        ...candidate.evidence.map((item) => item.claim)
      ].filter(Boolean).join(' ').toLowerCase();
      return matchesState && (!normalized || searchable.includes(normalized));
    });
  }, [query, scopedCandidates, stateFilter]);

  useEffect(() => {
    if (!visible.some((item) => item.event.id === selectedId)) {
      setSelectedId(visible[0]?.event.id ?? null);
    }
  }, [selectedId, visible]);

  const selected = visible.find((item) => item.event.id === selectedId) ?? visible[0];

  if (candidates.length === 0) {
    return (
      <section className="fact-workbench fact-workbench-empty" aria-label="事实核验">
        <span className="fact-empty-mark" aria-hidden="true">∴</span>
        <h2>还没有可核验的事实候选</h2>
        <p>事件形成后，相关材料会在这里按来源与证据类型组合成只读核验视图。</p>
        <small>这里不会创建或修改数据库记录。</small>
      </section>
    );
  }

  return (
    <section className="fact-workbench" aria-label="事实核验">
      <header className="fact-workbench-intro">
        <div>
          <p className="knowledge-kicker">从信号到可信变化</p>
          <h2>把新闻核成事实，再决定是否更新判断</h2>
          <p>新闻只是线索。这里检查直接材料与一手来源，避免把重复报道或媒体判断当成已经确认的事实。</p>
        </div>
        <div className="fact-verification-summary" aria-label="事实核验摘要">
          <span><strong>{counts.UNVERIFIED}</strong>待核验</span>
          <span><strong>{counts.NEEDS_CORROBORATION}</strong>待交叉</span>
          <span><strong>{counts.SUBSTANTIAL}</strong>较充分</span>
        </div>
      </header>

      <div className="fact-controls">
        <label className="fact-search">
          <span>搜索事实</span>
          <input
            type="search"
            aria-label="搜索事实"
            placeholder="标题、摘要或证据内容"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
          />
        </label>
        <div className="fact-state-filters" role="group" aria-label="核验状态">
          {([
            ['ALL', '全部事实'],
            ['SUBSTANTIAL', '证据较充分'],
            ['NEEDS_CORROBORATION', '待交叉验证'],
            ['UNVERIFIED', '待核验']
          ] as Array<[StateFilter, string]>).map(([state, label]) => (
            <button
              key={state}
              type="button"
              aria-label={`${label} ${counts[state]}`}
              aria-pressed={stateFilter === state}
              onClick={() => setStateFilter(state)}
            >
              {label} {counts[state]}
            </button>
          ))}
          {materialFreeCount > 0 && (
            <button
              type="button"
              className="fact-material-free-toggle"
              aria-label={`${showMaterialFree ? '隐藏' : '显示'}无材料候选 ${materialFreeCount}`}
              aria-pressed={showMaterialFree}
              onClick={() => setShowMaterialFree((current) => !current)}
            >
              {showMaterialFree ? '隐藏无材料' : '显示无材料'} {materialFreeCount}
            </button>
          )}
        </div>
      </div>

      {visible.length === 0 ? (
        <div className="fact-filter-empty">
          <strong>没有符合条件的事实候选</strong>
          <p>清除关键词或切换核验状态后继续查看。</p>
        </div>
      ) : (
        <div className="fact-desk-layout">
          <aside className="fact-index" aria-label="事实候选列表">
            <div className="fact-index-heading">
              <span>事实索引</span>
              <strong>{visible.length}</strong>
            </div>
            <div className="fact-index-list">
              {visible.map((candidate) => (
                <FactIndexItem
                  key={candidate.event.id}
                  candidate={candidate}
                  selected={candidate.event.id === selected?.event.id}
                  onSelect={() => setSelectedId(candidate.event.id)}
                />
              ))}
            </div>
          </aside>
          {selected && <FactDossier candidate={selected} />}
        </div>
      )}
    </section>
  );
}

function FactIndexItem({
  candidate,
  selected,
  onSelect
}: {
  candidate: FactCandidate;
  selected: boolean;
  onSelect: () => void;
}) {
  return (
    <button
      type="button"
      className={selected ? 'fact-index-item active' : 'fact-index-item'}
      aria-label={`${candidate.event.canonicalTitle}，${stateLabels[candidate.verificationState]}`}
      aria-current={selected ? 'true' : undefined}
      onClick={onSelect}
    >
      <span className={`fact-state fact-state-${candidate.verificationState.toLowerCase()}`}>
        {stateLabels[candidate.verificationState]}
      </span>
      <strong>{candidate.event.canonicalTitle}</strong>
      <small>
        {candidate.evidence.length} 条材料 · {candidate.primaryEvidenceCount} 条一手来源
      </small>
    </button>
  );
}

function FactDossier({ candidate }: { candidate: FactCandidate }) {
  const updatedAt = candidate.event.lastMeaningfulUpdateAt || candidate.event.updatedAt || candidate.event.lastSeenAt;
  return (
    <article className="fact-dossier" aria-label="事实证据卷宗">
      <header className="fact-dossier-header">
        <div className="fact-dossier-meta">
          <span className={`fact-state fact-state-${candidate.verificationState.toLowerCase()}`}>
            {stateLabels[candidate.verificationState]}
          </span>
          <span>{observationLabels[candidate.event.themeCode] || '其他变化'}</span>
          {updatedAt && <time dateTime={updatedAt}>{updatedAt.slice(0, 10)}</time>}
        </div>
        <h2>{candidate.event.canonicalTitle}</h2>
        <p>{candidate.event.summary || '当前事件没有摘要，请直接核查下方材料。'}</p>
      </header>

      <section className="fact-assurance" aria-label="证据充足度">
        <div className="fact-assurance-heading">
          <div><span>核验轨道</span><strong>两道门槛，缺一不可</strong></div>
          <b>{candidate.maxConfidence || '—'}<small>最高材料置信度</small></b>
        </div>
        <div className="fact-assurance-track">
          <span data-met={candidate.directEvidenceCount > 0}>
            <i aria-hidden="true" />
            <b>直接材料</b>
            <small>{candidate.directEvidenceCount} 条事实 / 时间线</small>
          </span>
          <em aria-hidden="true" />
          <span data-met={candidate.primaryEvidenceCount > 0}>
            <i aria-hidden="true" />
            <b>一手来源</b>
            <small>{candidate.primaryEvidenceCount} 条监管 / 官方 / 公司</small>
          </span>
        </div>
        {candidate.gaps.length > 0 && (
          <div className="fact-gaps">
            <strong>还缺什么</strong>
            <ul>{candidate.gaps.map((gap) => <li key={gap}>{gap}</li>)}</ul>
          </div>
        )}
      </section>

      <section className="fact-materials" aria-label="支持材料">
        <div className="fact-materials-heading">
          <div><span>Supporting record</span><h3>支持材料</h3></div>
          <small>按来源质量与置信度排序</small>
        </div>
        {candidate.evidence.length > 0 ? (
          <ol>
            {candidate.evidence.map((item, index) => (
              <li key={item.id}>
                <span className="fact-material-number">{String(index + 1).padStart(2, '0')}</span>
                <div>
                  <div className="fact-material-meta">
                    <span>{sourceLabels[item.sourceTier] || item.sourceTier}</span>
                    <span>{typeLabels[item.evidenceType] || item.evidenceType}</span>
                    <strong>{item.confidence}</strong>
                  </div>
                  <p>{item.claim}</p>
                  <footer>
                    <span>{item.articleTitle || '来源标题未记录'}</span>
                    {item.articlePublishedAt && <time dateTime={item.articlePublishedAt}>{item.articlePublishedAt.slice(0, 10)}</time>}
                    {item.articleUrl && <a href={item.articleUrl} target="_blank" rel="noopener noreferrer">打开原文</a>}
                  </footer>
                </div>
              </li>
            ))}
          </ol>
        ) : (
          <div className="fact-no-materials">
            <strong>该候选事实尚无证据材料</strong>
            <p>它只能作为索引存在，不能据此形成知识结论。</p>
          </div>
        )}
      </section>
    </article>
  );
}
