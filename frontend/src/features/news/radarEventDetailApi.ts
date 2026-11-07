import { api } from '../../shared/api/client';
import type { RadarEventDetail } from './researchRadarTypes';

const pendingOverviews = new Map<string, Promise<RadarEventDetail>>();

export function loadRadarEventOverview(eventId: string | number): Promise<RadarEventDetail> {
  const id = String(eventId);
  const pending = pendingOverviews.get(id);
  if (pending) {
    return pending;
  }
  const request = api<RadarEventDetail>(`/api/research-radar/events/${id}?section=OVERVIEW`)
    .finally(() => pendingOverviews.delete(id));
  pendingOverviews.set(id, request);
  return request;
}
