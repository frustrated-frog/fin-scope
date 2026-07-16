import { afterEach, beforeEach, describe, expect, it, test, vi } from 'vitest';

import { api } from './client';

beforeEach(() => {
  vi.unstubAllGlobals();
});

afterEach(() => {
  vi.restoreAllMocks();
});

test('treats HTTP 204 as no content', async () => {
  const response = {
    ok: true,
    status: 204,
    text: vi.fn()
  } as unknown as Response;
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response));

  await expect(api('/api/watchlist/1', { method: 'DELETE' })).resolves.toBeUndefined();
  expect(response.text).not.toHaveBeenCalled();
});

describe('api business errors', () => {
  it('surfaces the backend business error message', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({
      success: false,
      code: 'FS-2004',
      message: '记录已被更新，请刷新后再试',
      data: null,
      traceId: 'trace-409',
      timestamp: '2026-07-16T10:00:00Z'
    }), { status: 409, headers: { 'Content-Type': 'application/json' } }));

    await expect(api('/api/strategy/holdings')).rejects.toThrow('记录已被更新，请刷新后再试');
  });

  it('preserves status and code for recoverable optimistic-lock conflicts', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({
      success: false,
      code: 'FS-2004',
      message: '记录已被更新，请刷新后再试',
      data: null,
      traceId: 'trace-409',
      timestamp: '2026-07-16T10:00:00Z'
    }), { status: 409, headers: { 'Content-Type': 'application/json' } }));

    await expect(api('/api/knowledge/tasks/7/complete')).rejects.toMatchObject({
      name: 'ApiError',
      status: 409,
      code: 'FS-2004',
      traceId: 'trace-409'
    });
  });
});

test('unwraps data from a successful response envelope', async () => {
  vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({
    success: true,
    code: 'FS-0000',
    message: '成功',
    data: { id: 7, name: '测试主题' },
    traceId: 'trace-ok',
    timestamp: '2026-07-16T10:00:00Z'
  }), { status: 200, headers: { 'Content-Type': 'application/json' } }));

  await expect(api<{ id: number; name: string }>('/api/topics/7')).resolves.toEqual({
    id: 7,
    name: '测试主题'
  });
});

test('rejects a successful HTTP response that violates the response protocol', async () => {
  vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({ id: 7 }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' }
  }));

  await expect(api('/api/topics/7')).rejects.toMatchObject({
    name: 'ApiError',
    status: 200,
    code: 'API_PROTOCOL_ERROR'
  });
});

test('rejects a failed envelope even when HTTP status is successful', async () => {
  vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({
    success: false,
    code: 'FS-2002',
    message: '当前状态不允许该操作',
    data: null,
    traceId: 'trace-business',
    timestamp: '2026-07-16T10:00:00Z'
  }), { status: 200, headers: { 'Content-Type': 'application/json' } }));

  await expect(api('/api/topics/7')).rejects.toMatchObject({
    status: 200,
    code: 'FS-2002',
    traceId: 'trace-business'
  });
});
