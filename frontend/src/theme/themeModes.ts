export type ThemeMode = 'dark' | 'light' | 'd3' | 'payfast';

/** Cycle order used by the theme switcher. */
export const THEME_ORDER: ThemeMode[] = ['dark', 'light', 'd3', 'payfast'];

export const THEME_LABELS: Record<ThemeMode, string> = {
  dark: 'Dark',
  light: 'Light',
  d3: 'D3',
  payfast: 'Payfast',
};

export function isThemeMode(raw: string | null): raw is ThemeMode {
  return raw === 'light' || raw === 'dark' || raw === 'd3' || raw === 'payfast';
}

export function isLightScheme(theme: ThemeMode): boolean {
  return theme === 'light' || theme === 'payfast';
}
