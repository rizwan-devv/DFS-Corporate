import { useTheme } from '../theme/ThemeContext';
import { THEME_LABELS, THEME_ORDER, type ThemeMode } from '../theme/themeModes';

function Icon({ mode }: { mode: ThemeMode }) {
  if (mode === 'dark') {
    return (
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden>
        <path d="M21 14.5A8.5 8.5 0 0 1 9.5 3 7 7 0 1 0 21 14.5z" />
      </svg>
    );
  }
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden>
      <rect x="3" y="5" width="18" height="14" rx="3" stroke="currentColor" strokeWidth="2" />
      <path d="M3 10h18" stroke="currentColor" strokeWidth="2" />
      <circle cx="8" cy="15" r="1.4" fill="currentColor" />
      <path d="M12 14.5h6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
    </svg>
  );
}

export function ThemeToggle({ className = '' }: { className?: string }) {
  const { theme, setTheme } = useTheme();

  return (
    <div className={`theme-seg ${className}`} role="group" aria-label="Colour theme">
      {THEME_ORDER.map((mode) => (
        <button
          key={mode}
          type="button"
          className={`theme-seg-btn ${theme === mode ? 'is-active' : ''}`}
          onClick={() => setTheme(mode)}
          aria-pressed={theme === mode}
          title={`${THEME_LABELS[mode]} theme`}
        >
          <Icon mode={mode} />
          <span className="theme-seg-label">{THEME_LABELS[mode]}</span>
        </button>
      ))}
    </div>
  );
}
