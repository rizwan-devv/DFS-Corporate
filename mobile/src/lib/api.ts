const API_BASE = (import.meta.env.VITE_API_BASE_URL as string | undefined)?.replace(/\/$/, '') || '';

export type AppKycSession = {
  sessionToken: string;
  appUserId: number;
  phone: string;
  fullName: string;
  email?: string;
  status: string;
  businessName?: string;
  trackingId?: string;
  cnicNumber?: string;
  cnicFullName?: string;
  dateOfBirth?: string;
  videoKycRef?: string;
  biometricRef?: string;
  selfieUploaded?: boolean;
  failureReason?: string;
  requiredDocuments?: { kind: string; label: string; uploaded: boolean }[];
  canSubmit?: boolean;
  completedAt?: string;
};

async function parseError(res: Response) {
  try {
    const data = await res.json();
    return data.error || data.message || res.statusText;
  } catch {
    return res.statusText || 'Request failed';
  }
}

export async function api<T>(
  path: string,
  opts: { method?: string; body?: BodyInit | null; token?: string; json?: unknown } = {},
): Promise<T> {
  const headers: Record<string, string> = {};
  if (opts.token) headers.Authorization = `Bearer ${opts.token}`;
  let body = opts.body;
  if (opts.json !== undefined) {
    headers['Content-Type'] = 'application/json';
    body = JSON.stringify(opts.json);
  }
  const res = await fetch(`${API_BASE}${path}`, {
    method: opts.method || 'GET',
    headers,
    body: body ?? undefined,
  });
  const ct = res.headers.get('content-type') || '';
  if (!ct.includes('application/json')) {
    throw new Error(
      API_BASE
        ? `API unreachable at ${API_BASE} (got HTML). Is backend on 8090? Same Wi‑Fi / firewall?`
        : 'API returned HTML instead of JSON. Set VITE_API_BASE_URL for APK builds.',
    );
  }
  if (!res.ok) throw new Error(await parseError(res));
  return res.json() as Promise<T>;
}

export function openInvite(token: string) {
  return api<AppKycSession>(`/api/public/app-kyc/invite/${encodeURIComponent(token)}`);
}

export function login(phone: string, pin: string) {
  return api<AppKycSession>('/api/public/app-kyc/login', {
    method: 'POST',
    json: { phone, pin },
  });
}

export function me(sessionToken: string) {
  return api<AppKycSession>('/api/public/app-kyc/me', { token: sessionToken });
}

export function saveProfile(sessionToken: string, profile: Record<string, unknown>) {
  return api<AppKycSession>('/api/public/app-kyc/profile', {
    method: 'PUT',
    token: sessionToken,
    json: profile,
  });
}

export function uploadDoc(sessionToken: string, kind: string, file: File) {
  const fd = new FormData();
  fd.append('kind', kind);
  fd.append('file', file);
  return api<AppKycSession>('/api/public/app-kyc/documents', {
    method: 'POST',
    token: sessionToken,
    body: fd,
  });
}

export function stubVideo(sessionToken: string) {
  return api<AppKycSession>('/api/public/app-kyc/video-stub', { method: 'POST', token: sessionToken });
}

export function stubBiometric(sessionToken: string) {
  return api<AppKycSession>('/api/public/app-kyc/biometric-stub', { method: 'POST', token: sessionToken });
}

export function submitKyc(sessionToken: string) {
  return api<AppKycSession>('/api/public/app-kyc/submit', { method: 'POST', token: sessionToken });
}
