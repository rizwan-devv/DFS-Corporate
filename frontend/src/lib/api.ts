export const API_BASE = import.meta.env.VITE_API_BASE ?? '';

/** Full URL for non-JSON fetches (e.g. admin document file download). */
export function apiUrl(path: string): string {
  return `${API_BASE}${path}`;
}

export type PartyType = 'MERCHANT' | 'SUB_MERCHANT';

export async function api<T = unknown>(
  path: string,
  options: RequestInit & { token?: string | null } = {},
): Promise<T> {
  const headers = new Headers(options.headers || {});
  if (options.token) headers.set('Authorization', `Bearer ${options.token}`);
  if (options.body && !(options.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }
  const res = await fetch(`${API_BASE}${path}`, { ...options, headers });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error((data as { error?: string }).error || res.statusText || 'Request failed');
  }
  return data as T;
}
