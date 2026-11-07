import { ApiError, api } from '../../shared/api/client';
import type { RadarEventDetail } from './researchRadarTypes';

export type RadarContextSection = 'timeline' | 'evidence' | 'tracking';
const READ_RETRY_DELAY_MS = 350;
const pendingOverviews = new Map<string, Promise<RadarEventDetail>>();

export async function loadRadarEventSection(eventId: string | number, section: 'overview' | RadarContextSection) {
  const path = `/api/research-radar/events/${eventId}?section=${section.toUpperCase()}`;
  try {
    return await api<RadarEventDetail>(path);
  } catch (error) {
    if (!(error instanceof ApiError) || (error.status !== 0 && error.status < 500)) {
      throw error;
    }
    await new Promise((resolve) => window.setTimeout(resolve, READ_RETRY_DELAY_MS));
    return api<RadarEventDetail>(path);
  }
}

export function loadRadarEventOverview(eventId: string | number): Promise<RadarEventDetail> {
  const id = String(eventId);
  const pending = pendingOverviews.get(id);
  if (pending) {
    return pending;
  }
  const request = loadRadarEventSection(eventId, 'overview')
    .finally(() => pendingOverviews.delete(id));
  pendingOverviews.set(id, request);
  return request;
}
