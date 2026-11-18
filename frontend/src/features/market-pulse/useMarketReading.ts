import { useEffect, useState } from 'react';
import { api } from '../../shared/api/client';

export type ReadingFact = { id: string; label: string; value: string; businessDate: string; sectorCode?: string };
export type ReadingPoint = { title: string; detail: string; factIds: string[] };
export type MarketReading = {
  status: string; businessDate: string; asOf?: string; generatedAt?: string; message?: string;
  sectorNames: Record<string, string>; facts: ReadingFact[];
  structure?: StructureScore[]; adjustmentLimit?: number;
  previousScenarios?: { sourceDate: string; title: string; confirmation: ConditionStatus; invalidation: ConditionStatus; confirmationDetail: string; invalidationDetail: string }[];
  report?: { headline: string; summary: string; stateLabel: string; change: ReadingPoint;
    drivers: ReadingPoint[]; contradictions: ReadingPoint[]; conditions: ReadingPoint[];
    scenarios?: MarketScenario[];
    sectors: { sectorCode: string; reason: string; watchCondition: string; factIds: string[] }[] };
};

export type ConditionStatus = 'MET' | 'NOT_MET' | 'UNAVAILABLE';
export type StructureScore = {
  dimension: string; label: string; baselineScore?: number | null; adjustment?: number | null;
  finalScore?: number | null; scope: string; reason?: string; counterEvidence?: string;
  components: Record<string, number>; factIds: string[];
};
export type ScenarioCondition = { metric: string; direction: 'RISE' | 'FALL'; explanation: ReadingPoint };
export type MarketScenario = { thesis: ReadingPoint; confirmation: ScenarioCondition; invalidation: ScenarioCondition };

export function useMarketReading(date: string, refreshKey?: string) {
  const [reading, setReading] = useState<MarketReading>();
  const [error, setError] = useState('');
  const [attempt, setAttempt] = useState(0);
  const [sending, setSending] = useState(false);
  useEffect(() => {
    let active = true;
    let timer: ReturnType<typeof setTimeout>;
    const controller = new AbortController();
    setReading(undefined);
    setError('');
    async function load() {
      try {
        const next = await api<MarketReading>(`/api/market-pulse/ai/${date}`, { signal: controller.signal });
        if (!active) {
          return;
        }
        if (!next || next.businessDate !== date || !Array.isArray(next.facts)) {
          throw new Error('研判数据未完整返回，请重试');
        }
        setReading(next);
        if (['QUEUED', 'RUNNING', 'NOT_GENERATED'].includes(next.status)) {
          timer = setTimeout(() => void load(), next.status === 'NOT_GENERATED' ? 12000 : 3000);
        }
      } catch (cause) {
        if (active) {
          setError(cause instanceof Error ? cause.message : '研判暂时无法读取');
        }
      }
    }
    if (date) {
      void load();
    }
    return () => { active = false; controller.abort(); clearTimeout(timer); };
  }, [date, refreshKey, attempt]);
  async function generate() {
    setSending(true);
    try {
      await api(`/api/market-pulse/ai/${date}?retry=${reading?.status === 'FAILED'}`, { method: 'POST' });
      setAttempt(value => value + 1);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '生成提交失败，请重试');
    } finally {
      setSending(false);
    }
  }
  return { reading: reading?.businessDate === date ? reading : undefined, error, sending, generate,
    reload: () => setAttempt(value => value + 1) };
}
