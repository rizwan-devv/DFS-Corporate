export type ThemeMode = 'light' | 'dark' | 'demo';

/** Cycle order used by the theme switcher. */
export const THEME_ORDER: ThemeMode[] = ['light', 'dark', 'demo'];

export const THEME_LABELS: Record<ThemeMode, string> = {
  light: 'Light',
  dark: 'Dark',
  demo: 'Demo',
};

/**
 * Map stored / legacy themes to current modes.
 * `payfast` was the old light shell label.
 */
export function migrateTheme(raw: string | null): ThemeMode | null {
  if (raw === 'light' || raw === 'dark' || raw === 'demo') return raw;
  if (raw === 'payfast') return 'light';
  if (raw === 'd3') return 'dark';
  return null;
}

export function isThemeMode(raw: string | null): raw is ThemeMode {
  return raw === 'light' || raw === 'dark' || raw === 'demo';
}

export function isLightScheme(theme: ThemeMode): boolean {
  return theme === 'light' || theme === 'demo';
}

/** CSS `data-theme` value — demo reuses the light (payfast) shell colours. */
export function colorThemeAttr(theme: ThemeMode): 'payfast' | 'dark' {
  return theme === 'dark' ? 'dark' : 'payfast';
}

export function isDemoBrand(theme: ThemeMode): boolean {
  return theme === 'demo';
}
