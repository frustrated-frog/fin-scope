import { useEffect, useRef } from 'react';

import { api } from './client';

type ViewRevision = { scope: string; revision: number };

const FALLBACK_RECONCILE_MS = 60_000;

/** 只接收页面版本号；页面数据仍按当前筛选条件通过原有 API 读取。 */
export function useViewRevision(scopes: string[], onChanged: (scope: string) => void) {
  const callback = useRef(onChanged);
  callback.current = onChanged;
  const scopesKey = scopes.map((scope) => scope.toLowerCase()).sort().join(',');

  useEffect(() => {
    const activeScopes = new Set(scopesKey.split(',').filter(Boolean));
    const known = new Map<string, number>();
    let stopped = false;
    let fallbackTimer: number | undefined;
    let stream: EventSource | undefined;

    const reconcile = async () => {
      try {
        const values = await api<ViewRevision[]>(`/api/view-revisions?scopes=${encodeURIComponent(scopesKey)}`);
        if (stopped) return;
        values.forEach((value) => {
          const scope = value.scope?.toLowerCase();
          if (!scope || !activeScopes.has(scope)) return;
          const previous = known.get(scope);
          known.set(scope, value.revision);
          // Redis 不可用或重启时服务端会返回 0 或较小版本；此时定时直读页面接口，确保 SQLite 主链路仍可刷新。
          if (value.revision === 0 || (previous !== undefined && value.revision !== previous)) callback.current(scope);
        });
      } catch {
        // 对账失败不干扰当前快照；下一轮重试即可。
      }
    };

    const startFallback = () => {
      if (fallbackTimer !== undefined) return;
      fallbackTimer = window.setInterval(() => { void reconcile(); }, FALLBACK_RECONCILE_MS);
    };

    if (typeof EventSource === 'undefined') {
      startFallback();
    } else {
      stream = new EventSource('/api/view-revisions/stream');
      stream.addEventListener('snapshot-ready', (event) => {
        try {
          const value = JSON.parse((event as MessageEvent<string>).data) as ViewRevision;
          const scope = value.scope?.toLowerCase();
          if (!scope || !activeScopes.has(scope)) return;
          const previous = known.get(scope);
          known.set(scope, value.revision);
          if (previous === undefined || value.revision > previous) {
            callback.current(scope);
          } else if (value.revision === 0 || value.revision < previous) {
            // Redis 重启/不可用时 SSE 仍可能存活但版本回退；切入定时直读的 SQLite 兜底。
            callback.current(scope);
            startFallback();
          }
        } catch {
          // 无效事件不影响后续 SSE 推送。
        }
      });
      stream.onerror = () => startFallback();
    }

    return () => {
      stopped = true;
      stream?.close();
      if (fallbackTimer !== undefined) window.clearInterval(fallbackTimer);
    };
  }, [scopesKey]);
}
