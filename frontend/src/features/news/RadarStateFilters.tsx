import type { RadarEvent, RadarStateFilter } from './researchRadarTypes';

const FILTERS: { code: RadarStateFilter; label: string }[] = [
  { code: 'ALL', label: '进行中' }, { code: 'UNREAD', label: '未读' }, { code: 'FOLLOWED', label: '已关注' },
  { code: 'LATER', label: '稍后看' }, { code: 'IGNORED', label: '已忽略' }
];

export function RadarStateFilters({ value, events, onChange }: { value: RadarStateFilter; events: RadarEvent[]; onChange: (value: RadarStateFilter) => void }) {
  const count = (code: RadarStateFilter) => events.filter((event) => matchesRadarState(event, code)).length;
  return <nav className="radar-state-filters" aria-label="事件处理状态">{FILTERS.map((filter) => (
    <button type="button" key={filter.code} className={value === filter.code ? 'active' : ''} aria-pressed={value === filter.code} onClick={() => onChange(filter.code)}>
      {filter.label}<span>{count(filter.code)}</span>
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
