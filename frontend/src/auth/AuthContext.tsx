import { createContext, useContext, useMemo, useState, type ReactNode } from 'react';

export type Session = {
  token: string;
  role: string;
  portalRoles?: string[];
  partyStatus?: string;
  partyType?: string;
  partyPublicId?: string;
  fullName?: string;
};

type AuthCtx = {
  session: Session | null;
  setSession: (s: Session | null) => void;
  logout: () => void;
};

const Ctx = createContext<AuthCtx | null>(null);
const KEY = 'dfs_corporate_session';

function load(): Session | null {
  try {
    const raw = localStorage.getItem(KEY);
    return raw ? (JSON.parse(raw) as Session) : null;
  } catch {
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSessionState] = useState<Session | null>(() => load());

  const setSession = (s: Session | null) => {
    setSessionState(s);
    if (s) localStorage.setItem(KEY, JSON.stringify(s));
    else localStorage.removeItem(KEY);
  };

  const value = useMemo(
    () => ({
      session,
      setSession,
      logout: () => setSession(null),
    }),
    [session],
  );

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useAuth() {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error('AuthProvider missing');
  return ctx;
}
