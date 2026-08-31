import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';
import type { AppKycSession } from '../lib/api';

const KEY = 'dfs_app_kyc_session';

type Ctx = {
  session: AppKycSession | null;
  setSession: (s: AppKycSession | null) => void;
  clear: () => void;
};

const SessionContext = createContext<Ctx | null>(null);

function load(): AppKycSession | null {
  try {
    const raw = localStorage.getItem(KEY);
    return raw ? (JSON.parse(raw) as AppKycSession) : null;
  } catch {
    return null;
  }
}

export function SessionProvider({ children }: { children: ReactNode }) {
  const [session, setSessionState] = useState<AppKycSession | null>(() => load());

  const setSession = useCallback((s: AppKycSession | null) => {
    setSessionState(s);
    if (s) localStorage.setItem(KEY, JSON.stringify(s));
    else localStorage.removeItem(KEY);
  }, []);

  const clear = useCallback(() => setSession(null), [setSession]);

  const value = useMemo(() => ({ session, setSession, clear }), [session, setSession, clear]);
  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSession() {
  const ctx = useContext(SessionContext);
  if (!ctx) throw new Error('useSession outside provider');
  return ctx;
}
