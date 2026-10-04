import { useEffect, useState } from 'react';
import { api } from '../../shared/api/client';
import type { MarketBreadth, MarketPulseWorkspace } from './marketPulseTypes';

type Result = { date: string; revision?: string; status: 'loading' | 'ready' | 'error'; breadth?: MarketBreadth };
export function useBreadthComparison(workspace: MarketPulseWorkspace, dates: string[]) {
  const [choice, setChoice] = useState<string>();
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState<Result>();
  const options = [...new Set([...dates, ...(workspace.breadth?.history ?? []).map(point => point.businessDate ?? '')])]
    .filter(date => /^\d{4}-\d{2}-\d{2}$/.test(date) && date < (workspace.businessDate ?? '')).sort().reverse().slice(0, 60);
  const date = choice === 'none' ? '' : choice && options.includes(choice) ? choice : options[0] ?? '';
  useEffect(() => {
    if (!date) {
      return;
    }
    const abort = new AbortController();
    const base = { date, revision: workspace.generatedAt };
    setResult({ ...base, status: 'loading' });
    const timer = window.setTimeout(() => {
      void api<MarketPulseWorkspace>(`/api/market-pulse/${date}`, { signal: abort.signal }).then(value => {
        if (!abort.signal.aborted) {
          if (value.businessDate !== date || !value.breadth || value.breadth.businessDate !== date) {
            throw new Error('对照日期没有有效宽度截面');
          }
          setResult({ ...base, status: 'ready', breadth: value.breadth });
        }
      }).catch(() => {
        if (!abort.signal.aborted) {
          setResult({ ...base, status: 'error' });
        }
      });
    }, 150);
    return () => { window.clearTimeout(timer); abort.abort(); };
  }, [date, workspace.generatedAt, attempt]);
  const matching = !!date && result?.date === date && result.revision === workspace.generatedAt;
  return { date, options, choose: setChoice, retry: () => setAttempt(value => value + 1),
    status: !date ? 'off' : matching ? result.status : 'loading',
    previous: matching && result.status === 'ready' ? result.breadth : undefined };
}
