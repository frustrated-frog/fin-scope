import { afterEach, beforeEach, describe, expect, it, test, vi } from 'vitest';

import { api } from './client';

beforeEach(() => {
  vi.unstubAllGlobals();
});

afterEach(() => {
  vi.restoreAllMocks();
});

test('treats a successful empty response body as no content', async () => {
  const response = {
    ok: true,
    status: 200,
    text: vi.fn().mockResolvedValue('')
  } as unknown as Response;
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response));

  await expect(api('/api/watchlist/1', { method: 'DELETE' })).resolves.toBeUndefined();
  expect(response.text).toHaveBeenCalledTimes(1);
});

describe('api business errors', () => {
  it('surfaces the backend business error message', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({
      code: 'CONFLICT',
      message: '记录已被更新，请刷新后再试'
    }), { status: 409, headers: { 'Content-Type': 'application/json' } }));

    await expect(api('/api/strategy/holdings')).rejects.toThrow('记录已被更新，请刷新后再试');
  });
});
