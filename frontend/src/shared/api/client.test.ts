import { afterEach, describe, expect, it, vi } from 'vitest';
import { api } from './client';

describe('api client', () => {
  afterEach(() => vi.restoreAllMocks());

  it('surfaces the backend business error message', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({
      code: 'CONFLICT',
      message: '记录已被更新，请刷新后再试'
    }), { status: 409, headers: { 'Content-Type': 'application/json' } }));

    await expect(api('/api/strategy/holdings')).rejects.toThrow('记录已被更新，请刷新后再试');
  });
});
