import { useState } from 'react';
import { api } from '../../shared/api/client';
import type { RadarNotification, RadarNotificationCenter } from './researchRadarTypes';

export function RadarNotificationPanel({ hint = 0, onOpenEvent }: {
  hint?: number;
  onOpenEvent: (eventId: number) => Promise<boolean>;
}) {
  const [open, setOpen] = useState(false);
  const [center, setCenter] = useState<RadarNotificationCenter>();
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function toggle() {
    const next = !open;
    setOpen(next);
    if (next) {
      setLoading(true);
      setError('');
      try {
        setCenter(await api<RadarNotificationCenter>('/api/research-radar/notifications?limit=30'));
      } catch (cause) {
        setError(cause instanceof Error ? cause.message : '提醒加载失败');
      } finally {
        setLoading(false);
      }
    }
  }

  async function readAll() {
    setBusy(true);
    setError('');
    try {
      await api('/api/research-radar/notifications/read-all', { method: 'POST' });
      setCenter((value) => value ? { ...value, unreadCount: 0, items: value.items.map((item) => ({ ...item, read: true })) } : value);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '已读状态更新失败');
    } finally {
      setBusy(false);
    }
  }

  async function openItem(item: RadarNotification) {
    setBusy(true);
    setError('');
    try {
      if (item.eventId !== undefined && !await onOpenEvent(item.eventId)) {
        return;
      }
      if (!item.read) {
        await api(`/api/research-radar/notifications/${item.id}/read`, { method: 'POST' });
        setCenter((value) => value ? {
          ...value,
          unreadCount: Math.max(0, value.unreadCount - 1),
          items: value.items.map((value) => value.id === item.id ? { ...value, read: true } : value)
        } : value);
      }
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '事件无法打开，可能已过期，请刷新后重试');
    } finally {
      setBusy(false);
    }
  }

  return <div className="radar-notification-shell">
    <button type="button" className="ghost-button radar-notification-trigger" aria-expanded={open} onClick={() => void toggle()}>关注提醒 <span>{center?.unreadCount ?? hint}</span></button>
    {open ? <div className="radar-notification-panel">
      <header><div><strong>变化提醒</strong><small>今日 {center?.todayCount ?? 0} 条</small></div>{center?.unreadCount ? <button type="button" disabled={busy} onClick={() => void readAll()}>全部已读</button> : null}</header>
      {error ? <p role="alert">{error}</p> : null}
      {loading ? <p>正在读取提醒…</p> : center?.items.length ? <ul>{center.items.map((item) => <li key={item.id ?? item.notificationType} className={item.read || item.id === undefined ? '' : 'is-unread'}>
        {item.id === undefined ? <div><strong>{item.title}</strong><span>{item.message}</span></div> : <button type="button" disabled={busy} onClick={() => void openItem(item)}><strong>{item.title}</strong><span>{item.message}</span></button>}
      </li>)}</ul> : <p>关注事件出现新变化后会显示在这里。</p>}
    </div> : null}
  </div>;
}
