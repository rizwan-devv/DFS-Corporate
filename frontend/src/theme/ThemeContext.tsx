import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { THEME_ORDER, isThemeMode, type ThemeMode } from './themeModes';

type ThemeCtx = {
  theme: ThemeMode;
  toggleTheme: () => void;
  setTheme: (t: ThemeMode) => void;
};

const Ctx = createContext<ThemeCtx | null>(null);
const KEY = 'dfs_corporate_theme';

function load(): ThemeMode {
  try {
    const raw = localStorage.getItem(KEY);
    if (isThemeMode(raw)) return raw;
  } catch {
    /* ignore */
  }
  return 'dark';
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setThemeState] = useState<ThemeMode>(() => load());

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme);
    document.documentElement.style.colorScheme = theme === 'light' ? 'light' : 'dark';
    try {
      localStorage.setItem(KEY, theme);
    } catch {
      /* ignore */
    }
  }, [theme]);

  const setTheme = (t: ThemeMode) => setThemeState(t);
  const toggleTheme = () =>
    setThemeState((prev) => THEME_ORDER[(THEME_ORDER.indexOf(prev) + 1) % THEME_ORDER.length]);

  const value = useMemo(() => ({ theme, toggleTheme, setTheme }), [theme]);

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useTheme() {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error('ThemeProvider missing');
  return ctx;
}
