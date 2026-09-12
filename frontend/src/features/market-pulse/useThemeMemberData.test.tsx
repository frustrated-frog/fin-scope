import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeEach, expect, test, vi } from 'vitest';
import { useThemeMemberData } from './useThemeMemberData';

function response(code: string, date = '2026-09-11') {
  return { ok: true, status: 200, text: async () => JSON.stringify({ success: true, code: 'SUCCESS', message: '', traceId: 't', timestamp: '', data: { businessDate: date, instrumentCode: code, status: 'READY', reason: 'COMPLETE', message: '行情完整', validBars: 22, requiredBars: 22 } }) } as Response;
}
beforeEach(() => { vi.stubGlobal('fetch', vi.fn()); });
test('deduplicates members and keeps at most two requests active', async () => {
  const resolve: Array<() => void> = [];
  const complete = vi.fn();
  vi.mocked(fetch).mockImplementation(path => new Promise(done => { resolve.push(() => done(response(String(path).split('/').slice(-1)[0]))); }));
  const { result } = renderHook(() => useThemeMemberData(['600001.SH', '600002.SH', '600001.SH', '600003.SH'], '2026-09-11', complete));
  await waitFor(() => expect(fetch).toHaveBeenCalledTimes(2));
  await act(async () => { resolve[0](); });
  await waitFor(() => expect(fetch).toHaveBeenCalledTimes(3));
  await act(async () => { resolve[1](); resolve[2](); });
  await waitFor(() => expect(result.current.running).toBe(false));
  expect(complete).toHaveBeenCalledTimes(1);
  expect(Object.keys(result.current.results)).toHaveLength(3);
});
test('stops old queue on date change and ignores its late response', async () => {
  let resolveOld!: (response: Response) => void;
  vi.mocked(fetch).mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve; }))
    .mockResolvedValue(response('600001.SH', '2026-09-10'));
  const { result, rerender } = renderHook(({ date }) => useThemeMemberData(['600001.SH'], date, vi.fn()), { initialProps: { date: '2026-09-11' } });
  rerender({ date: '2026-09-10' });
  await waitFor(() => expect(result.current.results['600001.SH']?.businessDate).toBe('2026-09-10'));
  await act(async () => resolveOld(response('600001.SH')));
  expect(result.current.results['600001.SH'].businessDate).toBe('2026-09-10');
});
test('does not spin on failure and supports explicit retry', async () => {
  vi.mocked(fetch).mockRejectedValueOnce(new Error('network')).mockResolvedValue(response('600001.SH'));
  const { result } = renderHook(() => useThemeMemberData(['600001.SH'], '2026-09-11', vi.fn()));
  await waitFor(() => expect(result.current.results['600001.SH']?.status).toBe('FAILED'));
  expect(fetch).toHaveBeenCalledTimes(1);
  act(() => result.current.retry());
  await waitFor(() => expect(result.current.results['600001.SH']?.status).toBe('READY'));
  expect(fetch).toHaveBeenCalledTimes(2);
});
