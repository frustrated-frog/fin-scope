import { useEffect, useRef, useState } from 'react';
import { api } from '../../shared/api/client';
import type {
  DiscoveryStatus,
  ReactionCandidate,
  ReactionEventPage,
  ReactionSample,
  WorkspaceView,
} from './reactionTypes';

/** Owns list snapshots and independent detail refresh; polling never reorders a reader's list. */
export function useReactionWorkspace(addToast: (message: string, type?: 'success' | 'error' | 'info') => void) {
  const [view, setView] = useState<WorkspaceView>('TRACKING');
  const [query, setQuery] = useState('');
  const [search, setSearch] = useState('');
  const [type, setType] = useState('');
  const [reason, setReason] = useState('');
  const [followed, setFollowed] = useState(false);
  const [changed, setChanged] = useState(false);
  const [page, setPage] = useState(1);
  const [anchor, setAnchor] = useState<number>();
  const [data, setData] = useState<ReactionEventPage>();
  const [discovery, setDiscovery] = useState<DiscoveryStatus>();
  const [selected, setSelected] = useState<ReactionSample>();
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState('');
  const [detailOpen, setDetailOpen] = useState(false);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [pending, setPending] = useState(false);
  const [reload, setReload] = useState(0);
  const [extra, setExtra] = useState<'manual' | 'legacy'>();
  const [candidates, setCandidates] = useState<ReactionCandidate[]>([]);
  const detailGeneration = useRef(0);
  const selectedRef = useRef(selected);
  selectedRef.current = selected;
  const revisionRef = useRef(data?.revision);
  revisionRef.current = data?.revision;
  const listRef = useRef<HTMLDivElement>(null);
  const params = new URLSearchParams({
    view,
    query: search,
    page: String(page),
    size: '20',
  });
  if (anchor != null) {
    params.set('anchor', String(anchor));
  }
  if (type) {
    params.set('eventType', type);
  }
  if (reason && view === 'PENDING') {
    params.set('resolutionStatus', reason);
  }
  if (followed) {
    params.set('followed', 'true');
  }
  if (changed) {
    params.set('changedToday', 'true');
  }
  const path = `/api/investment-reactions/events?${params}`;

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    void api<ReactionEventPage>(path)
      .then((result) => {
        if (active) {
          setData(result);
          setPending(false);
          listRef.current?.scrollTo?.({ top: 0 });
        }
      })
      .catch((reason) => {
        if (active) {
          setError(message(reason));
        }
      })
      .finally(() => {
        if (active) {
          setLoading(false);
        }
      });
    return () => {
      active = false;
    };
  }, [path, reload]);

  useEffect(() => {
    let active = true;
    let polling = false;
    async function poll() {
      if (polling) {
        return;
      }
      polling = true;
      const check = new URLSearchParams(params);
      check.delete('anchor');
      check.set('page', '1');
      try {
        const [status, latest] = await Promise.all([
          api<DiscoveryStatus>('/api/investment-reactions/discovery'),
          api<ReactionEventPage>(`/api/investment-reactions/events?${check}`),
        ]);
        if (active) {
          setDiscovery(status);
          if (revisionRef.current != null && latest.revision !== revisionRef.current) {
            setPending(true);
          }
        }
      } catch (reason) {
        if (active) {
          setError(`自动更新读取失败：${message(reason)}`);
        }
      } finally {
        polling = false;
      }
    }
    void api<DiscoveryStatus>('/api/investment-reactions/discovery')
      .then((status) => {
        if (active) {
          setDiscovery(status);
        }
      })
      .catch((reason) => {
        if (active) {
          setError(message(reason));
        }
      });
    const timer = window.setInterval(() => {
      void poll();
    }, 15000);
    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, [path]);

  useEffect(() => {
    if (!detailOpen) {
      return;
    }
    let active = true;
    let polling = false;
    const timer = window.setInterval(() => {
      const sample = selectedRef.current;
      const generation = detailGeneration.current;
      if (!sample || polling) {
        return;
      }
      polling = true;
      void api<ReactionSample>(`/api/investment-reactions/${sample.id}`)
        .then((next) => {
          if (
            active &&
            generation === detailGeneration.current &&
            next.revision >= (selectedRef.current?.revision ?? 0)
          ) {
            setSelected(next);
            setDetailError('');
          }
        })
        .catch((reason) => {
          if (active) {
            setDetailError(message(reason));
          }
        })
        .finally(() => {
          polling = false;
        });
    }, 15000);
    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, [detailOpen]);

  useEffect(() => {
    const url = new URLSearchParams(window.location.search);
    const key = url.get('reactionEvent');
    const id = url.get('reactionSample');
    if (key) {
      const detail = new URLSearchParams({ key });
      if (url.get('reactionStock')) {
        detail.set('stock', url.get('reactionStock')!);
      }
      void openPath(`/api/investment-reactions/event?${detail}`);
    } else if (id && /^\d+$/.test(id)) {
      void openPath(`/api/investment-reactions/${id}`);
    }
    return () => {
      detailGeneration.current++;
    };
  }, []);

  function resetList() {
    setPage(1);
    setAnchor(undefined);
  }
  function refreshList() {
    resetList();
    setReload((value) => value + 1);
  }
  async function openPath(detailPath: string) {
    const generation = ++detailGeneration.current;
    setSelected(undefined);
    setDetailError('');
    setDetailOpen(true);
    setDetailLoading(true);
    try {
      const next = await api<ReactionSample>(detailPath);
      if (generation === detailGeneration.current) {
        setSelected(next);
      }
    } catch (reason) {
      if (generation === detailGeneration.current) {
        setDetailError(message(reason));
      }
    } finally {
      if (generation === detailGeneration.current) {
        setDetailLoading(false);
      }
    }
  }
  function closeDetail() {
    detailGeneration.current++;
    setDetailOpen(false);
    setSelected(undefined);
  }
  async function act(action: () => Promise<void>) {
    setBusy(true);
    try {
      await action();
    } catch (reason) {
      addToast(message(reason), 'error');
    } finally {
      setBusy(false);
    }
  }
  function updateSample(sample: ReactionSample) {
    setSelected(sample);
    setPending(true);
  }
  async function loadManual() {
    setExtra('manual');
    await act(async () => {
      setCandidates(await api<ReactionCandidate[]>('/api/investment-reactions/candidates'));
    });
  }

  return {
    view,
    setView,
    query,
    setQuery,
    search,
    setSearch,
    type,
    setType,
    reason,
    setReason,
    followed,
    setFollowed,
    changed,
    setChanged,
    page,
    setPage,
    setAnchor,
    data,
    discovery,
    setDiscovery,
    selected,
    detailLoading,
    detailError,
    detailOpen,
    loading,
    busy,
    error,
    pending,
    extra,
    setExtra,
    candidates,
    listRef,
    resetList,
    refreshList,
    openPath,
    closeDetail,
    act,
    updateSample,
    loadManual,
  };
}

function message(reason: unknown) {
  return reason instanceof Error ? reason.message : '操作失败，请稍后重试';
}
