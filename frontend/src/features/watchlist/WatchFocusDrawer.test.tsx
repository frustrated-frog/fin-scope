import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { expect, test, vi } from 'vitest';
import { WatchFocusDrawer } from './WatchFocusDrawer';
const item = {
  watchlistId: 3,
  code: '600001',
  type: 'STOCK' as const,
  name: '测试公司',
  reason: '订单',
  nextWatch: '财报',
  direction: '电网设备',
};
test('loads saved focus and saves cleared and edited fields', async () => {
  vi.stubGlobal(
    'fetch',
    vi.fn(async () => ({ ok: true, status: 204 })),
  );
  const save = vi.fn();
  const close = vi.fn();
  render(<WatchFocusDrawer item={item} onSaved={save} onClose={close} />);
  expect(screen.getByLabelText('为什么关注')).toHaveValue('订单');
  fireEvent.change(screen.getByLabelText('为什么关注'), {
    target: { value: '' },
  });
  fireEvent.change(screen.getByLabelText('接下来观察什么'), {
    target: { value: ' 新产品 ' },
  });
  fireEvent.click(screen.getByText('保存关注理由'));
  await waitFor(() =>
    expect(save).toHaveBeenCalledWith(
      expect.objectContaining({
        reason: '',
        nextWatch: '新产品',
        direction: '电网设备',
      }),
    ),
  );
  expect(close).toHaveBeenCalled();
});
test('failed save retains draft and allows retry', async () => {
  vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')));
  const close = vi.fn();
  render(<WatchFocusDrawer item={item} onSaved={vi.fn()} onClose={close} />);
  fireEvent.click(screen.getByText('保存关注理由'));
  await screen.findByRole('alert');
  expect(screen.getByLabelText('为什么关注')).toHaveValue('订单');
  expect(close).not.toHaveBeenCalled();
});
