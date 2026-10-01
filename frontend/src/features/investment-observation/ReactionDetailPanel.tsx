import { useEffect, useRef, useState } from 'react';
import { api } from '../../shared/api/client';
import type { ReactionSample, ReactionSource } from './reactionTypes';
import { dateTime, reactionHref, resolutionLabels, sourceHref } from './reactionTypes';
import { ReactionDetail } from './ReactionDetail';
import { ReactionEventContext } from './ReactionEventContext';
import { ReactionRegistrationForm } from './ReactionRegistrationForm';

export function ReactionDetailPanel({
  sample,
  loading,
  error,
  onClose,
  onOpen,
  onUpdate,
  onResearch,
  addToast,
}: {
  sample?: ReactionSample;
  loading: boolean;
  error: string;
  onClose: () => void;
  onOpen: (id: number) => void;
  onUpdate: (sample: ReactionSample) => void;
  onResearch?: (question: string) => void;
  addToast: (message: string, type?: 'success' | 'error' | 'info') => void;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const [tab, setTab] = useState<'reaction' | 'context' | 'source'>('reaction');
  const [busy, setBusy] = useState(false);
  const [versions, setVersions] = useState<ReactionSource[]>([]);
  const [versionError, setVersionError] = useState('');
  const currentId = useRef(sample?.id);
  currentId.current = sample?.id;
  useEffect(() => {
    const opener = document.activeElement as HTMLElement | null;
    const element = dialog.current;
    element?.showModal?.();
    if (element && !element.open) {
      element.setAttribute('open', '');
    }
    const overflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      element?.close?.();
      document.body.style.overflow = overflow;
      opener?.focus({ preventScroll: true });
    };
  }, []);
  useEffect(() => {
    setTab('reaction');
  }, [sample?.id]);
  useEffect(() => {
    if (!sample || tab !== 'source') {
      return;
    }
    let active = true;
    setVersions([]);
    setVersionError('');
    void api<ReactionSource[]>(`/api/investment-reactions/${sample.id}/versions`)
      .then((result) => {
        if (active) {
          setVersions(result);
        }
      })
      .catch((reason) => {
        if (active) {
          setVersionError(reason instanceof Error ? reason.message : '来源版本读取失败');
        }
      });
    return () => {
      active = false;
    };
  }, [sample?.id, sample?.revision, tab]);
  async function act(action: () => Promise<ReactionSample | undefined>) {
    const id = sample?.id;
    setBusy(true);
    try {
      const result = await action();
      if (result && currentId.current === id) {
        onUpdate(result);
      }
    } catch (reason) {
      addToast(reason instanceof Error ? reason.message : '操作失败', 'error');
    } finally {
      setBusy(false);
    }
  }
  return (
    <dialog
      ref={dialog}
      className="reaction-sidepanel reaction-workspace"
      aria-label="事件详情"
      onCancel={(event) => {
        event.preventDefault();
        onClose();
      }}
    >
      <header className="reaction-sidepanel-bar">
        <strong>事件详情</strong>
        <div>
          {sample && <a href={reactionHref(sample)}>独立链接 ↗</a>}
          <button onClick={onClose} aria-label="关闭事件详情">
            关闭 ×
          </button>
        </div>
      </header>
      {loading ? (
        <p className="reaction-empty" role="status">
          正在读取事件详情…
        </p>
      ) : error ? (
        <p className="reaction-warning" role="alert">
          {error}
        </p>
      ) : null}
      {sample && (
        <>
          <nav className="reaction-sidepanel-tabs" aria-label="事件详情内容">
            {(
              [
                ['reaction', '事件与反应'],
                ['context', '同事件与历史对照'],
                ['source', '来源与内容变化'],
              ] as const
            ).map(([key, label]) => (
              <button key={key} aria-pressed={tab === key} onClick={() => setTab(key)}>
                {label}
              </button>
            ))}
          </nav>
          <div className="reaction-sidepanel-content">
            {sample.excluded && <p className="reaction-warning">此样本已排除统计与自动行情更新，原始记录仍然保留。</p>}
            {tab === 'reaction' &&
              (sample.state === 'DRAFT' ? (
                <section>
                  <h3>{sample.title}</h3>
                  <p>
                    <strong>{sample.instrumentName || '暂无明确关联公司'}</strong> {sample.instrumentCode}
                  </p>
                  <p className="reaction-note">
                    {resolutionLabels[sample.resolutionStatus || 'PENDING']} · {sample.discoveryIssue}
                  </p>
                  <p>{sample.summary}</p>
                  {Boolean(sample.resolutionCandidates?.length) && (
                    <p className="reaction-note">
                      待区分的证券：
                      {sample.resolutionCandidates?.map((value) => `${value.name}（${value.code}）`).join('、')}
                      。候选尚未用于行情计算。
                    </p>
                  )}
                  <details>
                    <summary>手动补充（可选）</summary>
                    <ReactionRegistrationForm
                      sample={sample}
                      busy={busy}
                      onConfirm={(body) =>
                        act(async () =>
                          api<ReactionSample>(`/api/investment-reactions/${sample.id}/confirm`, {
                            method: 'POST',
                            body: JSON.stringify(body),
                          }),
                        )
                      }
                    />
                  </details>
                </section>
              ) : (
                <ReactionDetail
                  sample={sample}
                  samples={[]}
                  busy={busy}
                  onOpen={onOpen}
                  onResearch={onResearch}
                  hideContext
                  onCompare={() => setTab('context')}
                  onRefresh={() =>
                    void act(() =>
                      api<ReactionSample>(`/api/investment-reactions/${sample.id}/refresh`, { method: 'POST' }),
                    )
                  }
                  onFollow={() =>
                    void act(async () => {
                      const peers = await api<ReactionSample[]>(`/api/investment-reactions/${sample.id}/follow`, {
                        method: 'PATCH',
                        body: JSON.stringify({ followed: !sample.followed }),
                      });
                      return peers.find((value) => value.id === sample.id);
                    })
                  }
                  onArchive={() =>
                    void act(() =>
                      api<ReactionSample>(`/api/investment-reactions/${sample.id}/archive`, {
                        method: 'PATCH',
                        body: JSON.stringify({
                          archived: sample.state !== 'ARCHIVED',
                          revision: sample.revision,
                        }),
                      }),
                    )
                  }
                />
              ))}
            {tab === 'context' && <ReactionEventContext sample={sample} onOpen={onOpen} />}
            {tab === 'source' && (
              <section>
                <h3>{sample.state === 'DRAFT' ? '当前识别依据' : '观察时保留的依据'}</h3>
                <p>{sample.fact || sample.title}</p>
                <p>{sample.ruleEvidence}</p>
                {sourceHref(sample.sourceUrl) && (
                  <a href={sourceHref(sample.sourceUrl)} target="_blank" rel="noreferrer">
                    查看原始来源 ↗
                  </a>
                )}
                <h3>已保存的内容版本</h3>
                <p className="reaction-note">最新版本在前，最多展示 100 条。观察中的原始判断依据不会被后续内容覆盖。</p>
                {versionError && <p role="alert">{versionError}</p>}
                {versions.map((version) => (
                  <details key={version.versionId}>
                    <summary>
                      {dateTime(version.capturedAt)} · {version.title}
                    </summary>
                    <p>{version.body || '来源未提供正文'}</p>
                  </details>
                ))}
              </section>
            )}
            <footer className="reaction-sidepanel-footer">
              <button
                disabled={busy}
                onClick={() =>
                  void act(() =>
                    api<ReactionSample>(`/api/investment-reactions/${sample.id}/exclude`, {
                      method: 'PATCH',
                      body: JSON.stringify({
                        excluded: !sample.excluded,
                        revision: sample.revision,
                      }),
                    }),
                  )
                }
              >
                {sample.excluded ? '恢复统计与跟踪' : '识别错误，排除统计'}
              </button>
              <small>排除用于错误样本，归档用于收起正常观察。</small>
            </footer>
          </div>
        </>
      )}
    </dialog>
  );
}
