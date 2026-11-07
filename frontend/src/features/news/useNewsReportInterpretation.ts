import { useEffect, useRef, useState } from 'react';
import { api } from '../../shared/api/client';

export type NewsInterpretationResult = {
  facts: string[];
  whyItMatters: string;
  unknowns: string[];
  nextChecks: string[];
};

type Interpretation = {
  contentVersion: number;
  status: 'QUEUED' | 'RUNNING' | 'SUCCESS' | 'FAILED';
  result?: NewsInterpretationResult | null;
  failureMessage?: string | null;
};

type InterpretationView = {
  contentVersion: number;
  available: boolean;
  current?: Interpretation | null;
  previous?: Interpretation | null;
};

export function useNewsReportInterpretation(id?: string, version?: number) {
  const [state, setState] = useState<{ key: string; view?: InterpretationView; loading: boolean; error?: string }>();
  const action = useRef<(method: 'GET' | 'POST') => void>();
  const key = id && version ? `${id}:${version}` : '';

  useEffect(() => {
    if (!id || !version) {
      return;
    }
    const path = `/api/news/window/interpretation?${new URLSearchParams({ id, version: String(version) })}`;
    const controller = new AbortController();
    let timer: ReturnType<typeof setTimeout> | undefined;
    let loading = false;

    async function load(method: 'GET' | 'POST') {
      if (loading || controller.signal.aborted) {
        return;
      }
      loading = true;
      clearTimeout(timer);
      if (method === 'POST') {
        setState((current) => ({ key, view: current?.view, loading: true }));
      }
      try {
        const view = await api<InterpretationView>(path, { method, signal: controller.signal });
        if (controller.signal.aborted) {
          return;
        }
        if (view?.contentVersion !== version || typeof view.available !== 'boolean') {
          throw new Error('解读状态读取失败，请重试');
        }
        setState({ key, view, loading: false });
        if (view.current?.status === 'QUEUED' || view.current?.status === 'RUNNING') {
          timer = setTimeout(() => void load('GET'), 1500);
        }
      } catch (error) {
        if (!controller.signal.aborted) {
          setState((current) => ({ key, view: current?.key === key ? current.view : undefined,
            loading: false, error: error instanceof Error ? error.message : '解读请求失败，请重试' }));
        }
      } finally {
        loading = false;
      }
    }

    action.current = (method) => void load(method);
    void load('GET');
    return () => {
      action.current = undefined;
      controller.abort();
      clearTimeout(timer);
    };
  }, [id, version, key]);

  const active = state?.key === key ? state : undefined;
  const pending = active?.view?.current?.status === 'QUEUED' || active?.view?.current?.status === 'RUNNING';
  return { ...active, loading: active?.loading ?? Boolean(key), pending,
    start: () => action.current?.('POST'), refresh: () => action.current?.('GET') };
}
