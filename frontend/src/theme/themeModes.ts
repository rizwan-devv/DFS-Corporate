export type ThemeMode = 'dark' | 'light' | 'd3';

/** Cycle order used by the theme switcher. */
export const THEME_ORDER: ThemeMode[] = ['dark', 'light', 'd3'];

export const THEME_LABELS: Record<ThemeMode, string> = {
  dark: 'Dark',
  light: 'Light',
  d3: 'D3',
};

export function isThemeMode(raw: string | null): raw is ThemeMode {
  return raw === 'light' || raw === 'dark' || raw === 'd3';
}
