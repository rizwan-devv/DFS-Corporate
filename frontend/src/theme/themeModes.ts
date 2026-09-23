export type ThemeMode = 'dark' | 'payfast';

/** Cycle order used by the theme switcher. */
export const THEME_ORDER: ThemeMode[] = ['payfast', 'dark'];

export const THEME_LABELS: Record<ThemeMode, string> = {
  dark: 'Dark',
  payfast: 'Payfast',
};

/** Map legacy stored themes (light / d3) to the two remaining modes. */
export function migrateTheme(raw: string | null): ThemeMode | null {
  if (raw === 'payfast' || raw === 'dark') return raw;
  if (raw === 'light') return 'payfast';
  if (raw === 'd3') return 'dark';
  return null;
}

export function isThemeMode(raw: string | null): raw is ThemeMode {
  return raw === 'dark' || raw === 'payfast';
}

export function isLightScheme(theme: ThemeMode): boolean {
  return theme === 'payfast';
}
