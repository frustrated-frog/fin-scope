import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, expect, test, vi } from 'vitest';
import { api } from '../../shared/api/client';
import { RadarNotificationPanel } from './RadarNotificationPanel';

vi.mock('../../shared/api/client', () => ({ api: vi.fn() }));
beforeEach(() => {
  vi.mocked(api).mockReset();
  vi.mocked(api).mockResolvedValue({ unreadCount: 1, todayCount: 1, items: [
    { id: 1, eventId: 900, title: '窗口外事件', read: false }
  ] });
});

test('does not consume a notification when its event fails to open', async () => {
  const open = vi.fn().mockRejectedValue(new Error('事件已过期'));
  render(<RadarNotificationPanel onOpenEvent={open} />);
  fireEvent.click(screen.getByRole('button', { name: /关注提醒/ }));
  fireEvent.click(await screen.findByRole('button', { name: '窗口外事件' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('事件已过期');
  expect(api).not.toHaveBeenCalledWith('/api/research-radar/notifications/1/read', expect.anything());
});

test('opens the event before marking its notification read', async () => {
  const open = vi.fn().mockImplementation(async () => {
    expect(api).not.toHaveBeenCalledWith('/api/research-radar/notifications/1/read', expect.anything());
    return true;
  });
  render(<RadarNotificationPanel onOpenEvent={open} />);
  fireEvent.click(screen.getByRole('button', { name: /关注提醒/ }));
  fireEvent.click(await screen.findByRole('button', { name: '窗口外事件' }));
  await waitFor(() => expect(api).toHaveBeenCalledWith('/api/research-radar/notifications/1/read', { method: 'POST' }));
  expect(open).toHaveBeenCalledWith(900);
});
