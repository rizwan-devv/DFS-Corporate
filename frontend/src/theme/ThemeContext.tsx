import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { brandForTheme } from '../lib/brand';
import {
  THEME_ORDER,
  colorThemeAttr,
  isLightScheme,
  migrateTheme,
  type ThemeMode,
} from './themeModes';

type ThemeCtx = {
  theme: ThemeMode;
  toggleTheme: () => void;
  setTheme: (t: ThemeMode) => void;
};

const Ctx = createContext<ThemeCtx | null>(null);
const KEY = 'dfs_corporate_theme';

function load(): ThemeMode {
  try {
    const migrated = migrateTheme(localStorage.getItem(KEY));
    if (migrated) return migrated;
  } catch {
    /* ignore */
  }
  return 'light';
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setThemeState] = useState<ThemeMode>(() => load());

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', colorThemeAttr(theme));
    document.documentElement.style.colorScheme = isLightScheme(theme) ? 'light' : 'dark';
    document.title = brandForTheme(theme).name;
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
