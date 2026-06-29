export async function api<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    headers: { 'Content-Type': 'application/json', ...(options?.headers ?? {}) },
    ...options
  });
  if (!response.ok) {
    try {
      const errorBody = await response.json();
      throw new Error(errorBody.error || `Request failed: ${response.status}`);
    } catch (error) {
      if (error instanceof Error && !error.message.startsWith('Unexpected')) {
        throw error;
      }
      throw new Error(`Request failed: ${response.status}`);
    }
  }
  return response.json();
}
