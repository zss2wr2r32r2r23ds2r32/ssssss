const TOKEN_KEY = 'nexa.token';

export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token);
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY);
}

export function assetUrl(path: string) {
  const base = window.nexa?.apiBase?.replace(/\/$/, '') || '';
  return `${base}/api${path}`;
}

export async function api<T>(path: string, opts: { method?: string; body?: unknown } = {}): Promise<T> {
  const headers: Record<string, string> = {};
  const token = getToken();
  if (token) headers.Authorization = `Bearer ${token}`;
  let body: string | undefined;
  if (opts.body !== undefined) {
    headers['Content-Type'] = 'application/json';
    body = JSON.stringify(opts.body);
  }
  const base = window.nexa?.apiBase?.replace(/\/$/, '') || '';
  let response: Response;
  try {
    response = await fetch(`${base}/api${path}`, { method: opts.method || 'GET', headers, body });
  } catch {
    throw new Error("Can't reach the Nexa API. Start it with npm run dev.");
  }
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(typeof data.error === 'string' ? data.error : `Request failed (${response.status})`);
  }
  return data as T;
}
