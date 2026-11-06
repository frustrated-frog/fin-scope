import { useEffect, useState } from 'react';
import { api } from '../../shared/api/client';
import { isResearchScanView, ResearchScanView } from './researchScanTypes';

export const RESEARCH_SCAN_PATH = '/api/watchlist/research-scan';

export function useWatchlistResearchScan(membershipKey: string, ready: boolean) {
  const [result, setResult] = useState<{ key: string; view: ResearchScanView }>();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [request, setRequest] = useState({ key: '', version: 0, method: 'GET' });

  useEffect(() => {
    if (!ready || !membershipKey) {
      return;
    }
    let cancelled = false;
    let timer: ReturnType<typeof setTimeout> | undefined;
    const controller = new AbortController();
    setLoading(true);
    setError('');

    async function load(method: string) {
      try {
        const result = await api<ResearchScanView>(RESEARCH_SCAN_PATH, { method, signal: controller.signal });
        if (cancelled) {
          return;
        }
        if (!isResearchScanView(result)) {
          throw new Error('研究扫描响应格式不正确');
        }
        setResult({ key: membershipKey, view: result });
        setLoading(false);
        setError('');
        if (result.active?.status === 'RUNNING') {
          timer = setTimeout(() => void load('GET'), 2000);
        }
      } catch (cause) {
        if (cancelled) {
          return;
        }
        setLoading(false);
        setError(cause instanceof Error ? cause.message : '研究扫描加载失败');
      }
    }

    void load(request.key === membershipKey ? request.method : 'GET');
    return () => {
      cancelled = true;
      controller.abort();
      clearTimeout(timer);
    };
  }, [membershipKey, ready, request]);

  return {
    view: result?.key === membershipKey ? result.view : undefined,
    loading,
    error,
    retry: () => setRequest((current) => ({ key: membershipKey, version: current.version + 1, method: 'GET' })),
    rescan: () => setRequest((current) => ({ key: membershipKey, version: current.version + 1, method: 'POST' }))
  };
}
