import { useMemo, useState } from 'react';

import { Table } from '../../shared/components/Table';
import { EventCluster, EvidenceItem } from '../../shared/types';

function uniqueValues(values: Array<string | undefined>) {
  return Array.from(new Set(values.filter(Boolean))) as string[];
}

export function EvidenceView({
  evidenceItems,
  events
}: {
  evidenceItems: EvidenceItem[];
  events: EventCluster[];
}) {
  const [sourceTierFilter, setSourceTierFilter] = useState('ALL');
  const [evidenceTypeFilter, setEvidenceTypeFilter] = useState('ALL');

  const eventTitleMap = useMemo(
    () => new Map(events.map((event) => [event.id, event.canonicalTitle])),
    [events]
  );
  const sourceTierOptions = useMemo(
    () => uniqueValues(evidenceItems.map((item) => item.sourceTier)),
    [evidenceItems]
  );
  const evidenceTypeOptions = useMemo(
    () => uniqueValues(evidenceItems.map((item) => item.evidenceType)),
    [evidenceItems]
  );
  const visibleEvidence = useMemo(
    () => evidenceItems.filter((item) => (
      (sourceTierFilter === 'ALL' || item.sourceTier === sourceTierFilter)
        && (evidenceTypeFilter === 'ALL' || item.evidenceType === evidenceTypeFilter)
    )),
    [evidenceItems, evidenceTypeFilter, sourceTierFilter]
  );
  const highConfidenceCount = visibleEvidence.filter((item) => item.confidence >= 85).length;

  return (
    <section className="panel evidence-ledger">
      <div className="panel-heading">
        <div>
          <h3>证据账本</h3>
          <p className="muted">把分散在事件里的原始证据集中成一个研究台账。</p>
        </div>
        <div className="event-summary-strip">
          <span className="subtle-badge">证据总数 {visibleEvidence.length}</span>
          <span className="subtle-badge">高可信 {highConfidenceCount}</span>
        </div>
      </div>

      <div className="event-filter-bar evidence-filter-bar">
        <label className="inline-select">
          <span>来源层级</span>
          <select
            aria-label="证据账本来源层级"
            value={sourceTierFilter}
            onChange={(event) => setSourceTierFilter(event.target.value)}
          >
            <option value="ALL">ALL</option>
            {sourceTierOptions.map((tier) => (
              <option key={tier} value={tier}>Tier: {tier}</option>
            ))}
          </select>
        </label>
        <label className="inline-select">
          <span>证据类型</span>
          <select
            aria-label="证据账本类型"
            value={evidenceTypeFilter}
            onChange={(event) => setEvidenceTypeFilter(event.target.value)}
          >
            <option value="ALL">ALL</option>
            {evidenceTypeOptions.map((type) => (
              <option key={type} value={type}>Type: {type}</option>
            ))}
          </select>
        </label>
      </div>

      <Table
        headers={['Event', 'Tier', 'Type', 'Claim', 'Confidence']}
        empty="暂无证据记录"
        rows={visibleEvidence.map((item) => [
          eventTitleMap.get(item.eventId) || `Event #${item.eventId}`,
          item.sourceTier,
          item.evidenceType,
          item.claim,
          `${item.confidence}`
        ])}
      />
    </section>
  );
}
