import { act, render } from '@testing-library/react';
import { afterEach, expect, test, vi } from 'vitest';

import { api } from './client';
import { useViewRevision } from './useViewRevision';

vi.mock('./client', () => ({ api: vi.fn() }));

class FakeEventSource {
  static latest: FakeEventSource | undefined;
  onerror: (() => void) | null = null;
  private listeners = new Map<string, (event: Event) => void>();

  constructor() { FakeEventSource.latest = this; }
  addEventListener(name: string, listener: (event: Event) => void) { this.listeners.set(name, listener); }
  close() { /* hook cleanup */ }
  emit(name: string, data: unknown) { this.listeners.get(name)?.({ data: JSON.stringify(data) } as unknown as Event); }
}

function RevisionProbe({ onChanged }: { onChanged: (scope: string) => void }) {
  useViewRevision(['radar'], onChanged);
  return null;
}

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
  vi.mocked(api).mockReset();
});

test('continues refreshing when Redis reports a reset revision over a live SSE connection', async () => {
  vi.useFakeTimers();
  vi.stubGlobal('EventSource', FakeEventSource);
  vi.mocked(api).mockResolvedValue([{ scope: 'radar', revision: 0 }]);
  const onChanged = vi.fn();
  render(<RevisionProbe onChanged={onChanged} />);

  await act(async () => { FakeEventSource.latest?.emit('snapshot-ready', { scope: 'radar', revision: 5 }); });
  await act(async () => { FakeEventSource.latest?.emit('snapshot-ready', { scope: 'radar', revision: 0 }); });
  await act(async () => { await vi.advanceTimersByTimeAsync(60_000); });

  expect(onChanged).toHaveBeenCalledTimes(3);
  expect(onChanged).toHaveBeenLastCalledWith('radar');
});
