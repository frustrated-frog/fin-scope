import type {
  IndustryChainGraph, IndustryChainLifecycle, IndustryChainProsperity,
  IndustryChainStageProfile, IndustryChainSupplyDemand
} from './industryChainTypes';

const LIFECYCLE_LABELS: Record<IndustryChainLifecycle, string> = {
  EMERGING: '导入萌芽', GROWTH: '成长扩张', MATURE: '成熟竞争',
  CONSOLIDATING: '出清整合', DECLINING: '衰退替代'
};
const PROSPERITY_LABELS: Record<IndustryChainProsperity, string> = {
  RISING: '景气上行', STABLE: '景气平稳', COOLING: '景气降温', MIXED: '景气分化'
};
const SUPPLY_DEMAND_LABELS: Record<IndustryChainSupplyDemand, string> = {
  TIGHT: '供给偏紧', BALANCED: '供需平衡', LOOSE: '供给宽松', STRUCTURAL: '结构性分化'
};

export function lifecycleLabel(value?: string) {
  return LIFECYCLE_LABELS[value as IndustryChainLifecycle] ?? '待观察';
}

export function prosperityLabel(value?: string) {
  return PROSPERITY_LABELS[value as IndustryChainProsperity] ?? '待观察';
}

export function supplyDemandLabel(value?: string) {
  return SUPPLY_DEMAND_LABELS[value as IndustryChainSupplyDemand] ?? '待观察';
}

export function statusTone(value?: string) {
  if (value === 'RISING' || value === 'TIGHT' || value === 'GROWTH') return 'is-rising';
  if (value === 'COOLING' || value === 'LOOSE' || value === 'DECLINING') return 'is-cooling';
  if (value === 'MIXED' || value === 'STRUCTURAL' || value === 'CONSOLIDATING') return 'is-mixed';
  return 'is-stable';
}

export function IndustryChainResearchPanel({ graph }: { graph: IndustryChainGraph }) {
  const content = graph.researchContent;
  if (!content?.overview) {
    return (
      <section className="ic-research-panel ic-research-empty" role="region"
        aria-label={`${graph.name}产业研究面板`}>
        <span>Research layer unavailable</span>
        <strong>刷新图谱后生成研究内容</strong>
        <p>产业结构仍可在“产业全景”中查看。</p>
      </section>
    );
  }
  const { overview } = content;
  const nodes = new Map(graph.nodes.map((node) => [node.nodeKey, node]));
  const stageProfiles = [...content.stageProfiles].sort((left, right) =>
    (nodes.get(left.nodeKey)?.stageOrder ?? 999) - (nodes.get(right.nodeKey)?.stageOrder ?? 999));

  return (
    <section className="ic-research-panel" role="region" aria-label={`${graph.name}产业研究面板`}>
      <header className="ic-research-lead">
        <div>
          <span>Operating anatomy / {graph.schemaVersion}</span>
          <h3>产业运行剖面</h3>
          <p>{graph.summary}</p>
        </div>
        <div className="ic-trend-tags" aria-label="产业趋势">
          {overview.trendTags.map((tag) => <span key={tag}>{tag}</span>)}
        </div>
      </header>

      <div className="ic-state-ribbon" aria-label="产业状态带">
        <StateCell label="Lifecycle" value={lifecycleLabel(overview.lifecycle)} tone={statusTone(overview.lifecycle)} />
        <StateCell label="Prosperity" value={prosperityLabel(overview.prosperity)} tone={statusTone(overview.prosperity)} />
        <StateCell label="Supply / demand" value={supplyDemandLabel(overview.supplyDemand)} tone={statusTone(overview.supplyDemand)} />
        <StateCell label="Cycle type" value={overview.cycleType} tone="is-cycle" />
      </div>

      <div className="ic-research-grid">
        <article className="ic-driver-board">
          <ResearchHeading eyebrow="Core drivers" title="产业现在由什么驱动" />
          <div className="ic-driver-columns">
            <PhraseList title="需求端" items={overview.demandDrivers} />
            <PhraseList title="供给端" items={overview.supplyDrivers} />
            <PhraseList title="核心变量" items={overview.keyVariables} />
          </div>
        </article>
        <article className="ic-bottleneck-board">
          <ResearchHeading eyebrow="Constraint radar" title="稀缺与过剩同时存在" />
          <div className="ic-constraint-columns">
            <ConstraintList title="当前瓶颈" items={overview.bottlenecks} variant="bottleneck" />
            <ConstraintList title="过剩风险" items={overview.overcapacityRisks} variant="risk" />
          </div>
        </article>
      </div>

      {stageProfiles.length > 0 && (
        <section className="ic-stage-section" aria-labelledby="ic-stage-title">
          <ResearchHeading eyebrow="Value-chain stages" title="环节经营画像" id="ic-stage-title" />
          <div className="ic-stage-profiles">
            {stageProfiles.map((profile, index) => (
              <StageProfileCard key={profile.nodeKey} profile={profile}
                name={nodes.get(profile.nodeKey)?.name ?? profile.nodeKey} index={index} />
            ))}
          </div>
        </section>
      )}

      {content.companyProfiles.length > 0 && (
        <section className="ic-company-section" aria-labelledby="ic-company-title">
          <ResearchHeading eyebrow="Competitive field" title="代表公司矩阵" id="ic-company-title" />
          <div className="ic-company-matrix-scroll">
            <table className="ic-company-matrix">
              <thead><tr><th>公司</th><th>产业位置</th><th>核心产品</th><th>下游领域</th><th>竞争优势</th><th>观察变量</th></tr></thead>
              <tbody>{content.companyProfiles.map((profile) => {
                const node = nodes.get(profile.nodeKey);
                return <tr key={profile.nodeKey}>
                  <th scope="row"><strong>{node?.name ?? profile.nodeKey}</strong><small>{node?.stockCode || '未上市'}</small></th>
                  <td>{profile.industryPosition}</td>
                  <td><InlinePhrases items={profile.coreProducts} /></td>
                  <td><InlinePhrases items={profile.downstreamMarkets} /></td>
                  <td><InlinePhrases items={profile.competitiveAdvantages} /></td>
                  <td>{profile.keyVariables.map((item) => `观察：${item}`).join(' / ')}</td>
                </tr>;
              })}</tbody>
            </table>
          </div>
        </section>
      )}
    </section>
  );
}

function StateCell({ label, value, tone }: { label: string; value: string; tone: string }) {
  return <div className={`ic-state-cell ${tone}`}><span>{label}</span><strong>{value}</strong><i aria-hidden="true" /></div>;
}

function ResearchHeading({ eyebrow, title, id }: { eyebrow: string; title: string; id?: string }) {
  return <header className="ic-research-heading"><span>{eyebrow}</span><h4 id={id}>{title}</h4></header>;
}

function PhraseList({ title, items }: { title: string; items: string[] }) {
  if (!items.length) return null;
  return <div className="ic-phrase-list"><span>{title}</span><ul>{items.map((item) => <li key={item}>{item}</li>)}</ul></div>;
}

function ConstraintList({ title, items, variant }: { title: string; items: string[]; variant: string }) {
  if (!items.length) return null;
  return <div className={`ic-constraint-list is-${variant}`}><span>{title}</span><ol>{items.map((item, index) =>
    <li key={item}><i>{String(index + 1).padStart(2, '0')}</i><strong>{item}</strong></li>)}</ol></div>;
}

function StageProfileCard({ profile, name, index }: {
  profile: IndustryChainStageProfile; name: string; index: number;
}) {
  return <article className="ic-stage-profile">
    <header>
      <span>{String(index + 1).padStart(2, '0')} / Stage</span><h5>{name}</h5><p>{profile.roleSummary}</p>
      <div className="ic-profile-statuses">
        <em className={statusTone(profile.prosperity)}>{prosperityLabel(profile.prosperity)}</em>
        <em>{supplyDemandLabel(profile.supplyDemand)}</em><em>{lifecycleLabel(profile.lifecycle)}</em>
      </div>
    </header>
    <dl className="ic-profile-facts">
      <div><dt>商业模式</dt><dd>{profile.businessModel}</dd></div>
      <div><dt>价值获取</dt><dd>{profile.valueCapture}</dd></div>
      <div><dt>成本结构</dt><dd>{profile.costStructure}</dd></div>
      <div className="is-bottleneck"><dt>核心瓶颈</dt><dd>{profile.bottleneck}</dd></div>
    </dl>
    <div className="ic-profile-lists">
      <PhraseList title="盈利驱动" items={profile.profitDrivers} />
      <PhraseList title="行业壁垒" items={profile.barriers} />
      <PhraseList title="跟踪指标" items={profile.coreMetrics} />
      <PhraseList title="主要风险" items={profile.risks} />
    </div>
  </article>;
}

function InlinePhrases({ items }: { items: string[] }) {
  return <>{items.map((item) => <span className="ic-inline-phrase" key={item}>{item}</span>)}</>;
}
