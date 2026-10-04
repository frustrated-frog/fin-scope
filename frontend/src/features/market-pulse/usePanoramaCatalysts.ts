import { useEffect, useState } from 'react';
import { api } from '../../shared/api/client';
import type { MarketEventConfirmation, MarketPulseWorkspace } from './marketPulseTypes';

type Snapshot = {
  date: string; revision?: string; status: 'loading' | 'ready' | 'error' | 'unavailable';
  events: MarketEventConfirmation[];
};

/** Read the selected day's saved confirmations; never substitute the latest day's events. */
export function usePanoramaCatalysts(workspace: MarketPulseWorkspace, businessDate: string, hasSnapshot: boolean) {
  const [snapshot, setSnapshot] = useState<Snapshot>();
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    if (businessDate === workspace.businessDate || !hasSnapshot) {
      return;
    }
    const abort = new AbortController();
    const base = { date: businessDate, revision: workspace.generatedAt, events: [] };
    setSnapshot({ ...base, status: 'loading' });
    const timer = window.setTimeout(() => {
      void api<MarketPulseWorkspace>(`/api/market-pulse/${businessDate}`, { signal: abort.signal }).then(result => {
        if (!abort.signal.aborted) {
          if (result.businessDate !== businessDate || (result.eventConfirmations != null && !Array.isArray(result.eventConfirmations))) {
            throw new Error('行业催化截面不一致');
          }
          setSnapshot({ ...base, status: 'ready', events: result.eventConfirmations ?? [] });
        }
      }).catch(() => {
        if (!abort.signal.aborted) {
          setSnapshot({ ...base, status: 'error' });
        }
      });
    }, 150);
    return () => { window.clearTimeout(timer); abort.abort(); };
  }, [businessDate, workspace.businessDate, workspace.generatedAt, hasSnapshot, attempt]);
  const current: Snapshot = businessDate === workspace.businessDate
    ? { date: businessDate, status: 'ready', events: workspace.eventConfirmations ?? [] }
    : !hasSnapshot ? { date: businessDate, status: 'unavailable', events: [] }
      : snapshot?.date === businessDate && snapshot.revision === workspace.generatedAt ? snapshot
        : { date: businessDate, status: 'loading', events: [] };
  return { ...current, retry: () => setAttempt(value => value + 1) };
}
