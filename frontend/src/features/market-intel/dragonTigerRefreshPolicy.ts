import type { DragonTigerView } from './marketIntelTypes';

function startOfLocalDay(value: Date) {
  return new Date(value.getFullYear(), value.getMonth(), value.getDate());
}

export function businessDaysElapsed(from: Date, to: Date) {
  if (Number.isNaN(from.getTime()) || Number.isNaN(to.getTime()) || from >= to) return 0;
  const cursor = startOfLocalDay(from);
  const end = startOfLocalDay(to);
  let elapsed = 0;
  cursor.setDate(cursor.getDate() + 1);
  while (cursor <= end) {
    const day = cursor.getDay();
    if (day !== 0 && day !== 6) elapsed += 1;
    cursor.setDate(cursor.getDate() + 1);
  }
  return elapsed;
}

export function shouldAutoRefreshDragonTiger(view: DragonTigerView, now: Date) {
  if (view.health.status === 'NOT_REFRESHED' || view.health.status === 'STALE_FALLBACK') {
    return true;
  }
  if (!view.health.asOf) return false;
  const asOf = new Date(view.health.asOf);
  if (Number.isNaN(asOf.getTime())) return false;
  return businessDaysElapsed(asOf, now) > 1;
}
