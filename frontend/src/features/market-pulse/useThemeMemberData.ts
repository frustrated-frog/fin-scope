import { useEffect, useRef, useState } from 'react';
import { api } from '../../shared/api/client';
import type { ResearchMemberResult } from './marketResearchTypes';

const CONCURRENCY = 2;
const MAX_MEMBERS = 250;
const A_SHARE = /^(?:(?:600|601|603|605|688)\d{3}\.SH|(?:000|001|002|003|300|301)\d{3}\.SZ|(?:43|83|87|88|92)\d{4}\.BJ)$/;

/** Only persisted theme members enter this bounded queue; abort/date changes never enqueue its remainder. */
export function useThemeMemberData(codes: string[], businessDate: string, onComplete: () => void) {
  const key = [...new Set(codes.filter(code => A_SHARE.test(code)))].sort().slice(0, MAX_MEMBERS).join(',');
  const [results, setResults] = useState<Record<string, ResearchMemberResult>>({});
  const [activeCodes, setActiveCodes] = useState<string[]>([]);
  const [running, setRunning] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const complete = useRef(onComplete);
  complete.current = onComplete;
  useEffect(() => {
    const queue = key ? key.split(',') : [];
    const controller = new AbortController();
    let cursor = 0;
    setResults({});
    setActiveCodes([]);
    setRunning(queue.length > 0);
    if (!queue.length) {
      return () => controller.abort();
    }
    async function worker() {
      while (cursor < queue.length && !controller.signal.aborted) {
        const code = queue[cursor++];
        setActiveCodes(current => [...current, code]);
        let result: ResearchMemberResult;
        try {
          result = await api<ResearchMemberResult>(`/api/market-pulse/research/${businessDate}/members/${code}`, { method: 'POST', signal: controller.signal });
          if (result.instrumentCode !== code || result.businessDate !== businessDate
            || !['READY', 'PARTIAL', 'FAILED', 'SKIPPED'].includes(result.status)
            || !Number.isInteger(result.validBars) || result.validBars < 0 || result.validBars > 22 || result.requiredBars !== 22
            || (result.status === 'READY' && (result.reason !== 'COMPLETE' || result.validBars !== 22))) {
            throw new Error('补齐结果日期、代码或覆盖信息不匹配');
          }
        } catch (cause) {
          result = { instrumentCode: code, businessDate, status: 'FAILED', reason: 'UPSTREAM_FAILED', validBars: 0, requiredBars: 22,
            message: cause instanceof Error ? cause.message : '成员行情补齐失败，请稍后重试' };
        }
        if (controller.signal.aborted) {
          return;
        }
        setResults(current => ({ ...current, [code]: result }));
        setActiveCodes(current => current.filter(item => item !== code));
      }
    }
    void Promise.all(Array.from({ length: Math.min(CONCURRENCY, queue.length) }, worker)).then(() => {
      if (!controller.signal.aborted) {
        setRunning(false);
        complete.current();
      }
    });
    return () => controller.abort();
  }, [key, businessDate, attempt]);
  const currentResults = Object.fromEntries(Object.entries(results).filter(([code, result]) => result.businessDate === businessDate && key.split(',').includes(code)));
  return { results: currentResults, activeCodes, running, total: key ? key.split(',').length : 0, retry: () => setAttempt(value => value + 1) };
}
