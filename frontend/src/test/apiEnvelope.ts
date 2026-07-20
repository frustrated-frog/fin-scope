export function apiEnvelope<T>(data: T) {
  return {
    success: true,
    code: 'FS-0000',
    message: '成功',
    data,
    traceId: 'test-trace-id',
    timestamp: '2026-07-16T10:00:00Z'
  };
}

export function apiResponse<T>(data: T, init?: ResponseInit): Response {
  return new Response(JSON.stringify(apiEnvelope(data)), init);
}

export function mockApiResponse<T>(data: T, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => apiEnvelope(data)
  } as Response;
}
