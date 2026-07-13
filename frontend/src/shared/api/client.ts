export class ApiError extends Error {
  readonly status: number;
  readonly code?: string;

  constructor(status: number, message: string, code?: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
  }
}

export async function api<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    headers: { 'Content-Type': 'application/json', ...(options?.headers ?? {}) },
    ...options
  });
  if (!response.ok) {
    let errorBody: { message?: string; error?: string; code?: string } = {};
    try {
      if (typeof response.text === 'function') {
        const errorText = await response.text();
        if (errorText.trim()) errorBody = JSON.parse(errorText) as typeof errorBody;
      } else {
        errorBody = await response.json() as typeof errorBody;
      }
    } catch {
      // Invalid or empty error bodies still retain the HTTP status for callers.
    }
    throw new ApiError(
      response.status,
      errorBody.message || errorBody.error || `Request failed: ${response.status}`,
      errorBody.code
    );
  }
  if (response.status === 204) {
    return undefined as T;
  }
  if (typeof response.text !== 'function') {
    return response.json();
  }
  const responseText = await response.text();
  if (!responseText.trim()) {
    return undefined as T;
  }
  return JSON.parse(responseText) as T;
}
