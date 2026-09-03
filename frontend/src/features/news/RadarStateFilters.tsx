import type { RadarEvent, RadarStateFilter } from './researchRadarTypes';

const FILTERS: { code: RadarStateFilter; label: string }[] = [
  { code: 'ALL', label: '进行中' }, { code: 'UNREAD', label: '未读' }, { code: 'FOLLOWED', label: '临时关注' },
  { code: 'LATER', label: '稍后看' }, { code: 'IGNORED', label: '已忽略' }
];

export function RadarStateFilters({ value, counts, onChange }: { value: RadarStateFilter; counts: Record<RadarStateFilter, number>; onChange: (value: RadarStateFilter) => void }) {
  return <nav className="radar-state-filters" aria-label="事件处理状态">{FILTERS.map((filter) => (
    <button type="button" key={filter.code} className={value === filter.code ? 'active' : ''} aria-pressed={value === filter.code} onClick={() => onChange(filter.code)}>
      {filter.label}<span>{counts[filter.code]}</span>
    </button>
  ))}</nav>;
}

export function matchesRadarState(event: RadarEvent, filter: RadarStateFilter) {
  if (filter === 'UNREAD') return !event.read && event.disposition !== 'IGNORED';
  if (filter === 'FOLLOWED') return Boolean(event.followed) && event.disposition !== 'IGNORED';
  if (filter === 'LATER') return event.disposition === 'LATER';
  if (filter === 'IGNORED') return event.disposition === 'IGNORED';
  return event.disposition !== 'IGNORED';
}
