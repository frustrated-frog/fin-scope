export class ApiError extends Error {
  readonly status: number;
  readonly code?: string;
  readonly traceId?: string;

  constructor(status: number, message: string, code?: string, traceId?: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.traceId = traceId;
  }
}

export interface ApiResponse<T> {
  success: boolean;
  code: string;
  message: string;
  data: T;
  traceId: string;
  timestamp: string;
}

export async function api<T>(path: string, options?: RequestInit): Promise<T> {
  let response: Response;
  const headers = options?.body instanceof FormData
    ? { ...(options?.headers ?? {}) }
    : { 'Content-Type': 'application/json', ...(options?.headers ?? {}) };
  try {
    response = await fetch(path, {
      ...options,
      headers
    });
  } catch (error) {
    throw new ApiError(0, '网络请求失败，请检查网络连接后重试', 'NETWORK_ERROR');
  }

  if (response.status === 204) {
    return undefined as T;
  }

  const body = await readBody(response);
  if (!isApiResponse<unknown>(body)) {
    if (!response.ok) {
      throw new ApiError(response.status, `请求失败（HTTP ${response.status}）`, 'HTTP_ERROR');
    }
    throw new ApiError(response.status, '接口响应格式不正确', 'API_PROTOCOL_ERROR');
  }

  if (!response.ok || !body.success) {
    throw new ApiError(response.status, body.message, body.code, body.traceId);
  }

  return body.data as T;
}

async function readBody(response: Response): Promise<unknown> {
  try {
    if (typeof response.text === 'function') {
      const text = await response.text();
      return text.trim() ? JSON.parse(text) : undefined;
    }
    return await response.json();
  } catch {
    return undefined;
  }
}

function isApiResponse<T>(value: unknown): value is ApiResponse<T> {
  if (value == null || typeof value !== 'object') {
    return false;
  }
  const candidate = value as Partial<ApiResponse<T>>;
  return typeof candidate.success === 'boolean'
    && typeof candidate.code === 'string'
    && typeof candidate.message === 'string'
    && Object.prototype.hasOwnProperty.call(candidate, 'data')
    && typeof candidate.traceId === 'string'
    && typeof candidate.timestamp === 'string';
}
