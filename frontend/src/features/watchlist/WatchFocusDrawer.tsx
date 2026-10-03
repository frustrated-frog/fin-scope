import { useEffect, useRef, useState } from 'react';
import { api } from '../../shared/api/client';
import type { WatchFocus } from './watchFocusTypes';
import { acquireWatchlistOverlayScrollLock } from './watchlistOverlayScrollLock';
import './watchFocus.css';

export function WatchFocusDrawer({
  item,
  onClose,
  onSaved,
}: {
  item: WatchFocus;
  onClose: () => void;
  onSaved: (value: WatchFocus) => void;
}) {
  const [reason, setReason] = useState(item.reason ?? '');
  const [nextWatch, setNextWatch] = useState(item.nextWatch ?? '');
  const [direction, setDirection] = useState(item.direction ?? '');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const panel = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null;
    const release = acquireWatchlistOverlayScrollLock();
    panel.current?.querySelector<HTMLButtonElement>('button')?.focus();
    return () => {
      release();
      previous?.focus();
    };
  }, []);
  async function save(event: React.FormEvent) {
    event.preventDefault();
    setSaving(true);
    setError('');
    try {
      const fields = {
        reason: reason.trim(),
        nextWatch: nextWatch.trim(),
        direction: direction.trim(),
      };
      await api(`/api/watchlist/${item.watchlistId}/focus`, {
        method: 'PATCH',
        body: JSON.stringify(fields),
      });
      onSaved({ ...item, ...fields });
      onClose();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '保存失败，请重试');
    } finally {
      setSaving(false);
    }
  }
  return (
    <div
      className="watch-focus-backdrop"
      onClick={(event) => {
        if (event.target === event.currentTarget && !saving) {
          onClose();
        }
      }}
    >
      <div
        ref={panel}
        className="watch-focus-drawer"
        role="dialog"
        aria-modal="true"
        aria-labelledby="watch-focus-title"
        onKeyDown={(event) => {
          if (event.key === 'Escape' && !saving) {
            onClose();
          }
          if (event.key === 'Tab') {
            const nodes = Array.from(
              panel.current?.querySelectorAll<HTMLElement>(
                'button:not(:disabled), input, textarea',
              ) ?? [],
            );
            const first = nodes[0];
            const last = nodes[nodes.length - 1];
            if (event.shiftKey && document.activeElement === first) {
              event.preventDefault();
              last?.focus();
            }
            if (!event.shiftKey && document.activeElement === last) {
              event.preventDefault();
              first?.focus();
            }
          }
        }}
      >
        <header>
          <div>
            <h3 id="watch-focus-title">关注理由</h3>
            <p>
              {item.name || item.code} · {item.code}
            </p>
          </div>
          <button
            type="button"
            disabled={saving}
            onClick={onClose}
            aria-label="关闭关注理由"
          >
            关闭
          </button>
        </header>
        <form onSubmit={save}>
          <label>
            为什么关注
            <textarea
              value={reason}
              onChange={(event) => setReason(event.target.value)}
              maxLength={500}
              rows={4}
              placeholder="例如：关注海外订单带来的业务增长"
            />
          </label>
          <label>
            接下来观察什么
            <textarea
              value={nextWatch}
              onChange={(event) => setNextWatch(event.target.value)}
              maxLength={500}
              rows={4}
              placeholder="例如：下次财报的海外收入、订单交付进展"
            />
          </label>
          <label>
            关注方向
            <input
              value={direction}
              onChange={(event) => setDirection(event.target.value)}
              maxLength={80}
              placeholder="例如：电网设备"
            />
          </label>
          <p className="watch-focus-hint">
            填写行业名称，可在今日雷达看到关联方向。留空后保存可清除原有内容。
          </p>
          {error && <p role="alert">{error}</p>}
          <footer>
            <button type="button" disabled={saving} onClick={onClose}>
              取消
            </button>
            <button type="submit" disabled={saving}>
              {saving ? '保存中…' : '保存关注理由'}
            </button>
          </footer>
        </form>
      </div>
    </div>
  );
}
