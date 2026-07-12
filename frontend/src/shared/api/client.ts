export async function api<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    headers: { 'Content-Type': 'application/json', ...(options?.headers ?? {}) },
    ...options
  });
  if (!response.ok) {
    const errorText = typeof response.text === 'function' ? await response.text() : null;
    try {
      const errorBody = errorText === null
        ? await response.json() as { message?: string; error?: string }
        : JSON.parse(errorText) as { message?: string; error?: string };
      throw new Error(errorBody.message || errorBody.error || `Request failed: ${response.status}`);
    } catch (error) {
      if (error instanceof Error && !error.message.startsWith('Unexpected')) {
        throw error;
      }
      throw new Error(`Request failed: ${response.status}`);
    }
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
