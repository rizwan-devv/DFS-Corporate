import type { ReactNode } from 'react';

type KpiTileProps = {
  label: string;
  value: ReactNode;
  icon?: ReactNode;
  /** Small caption under the value, e.g. "vs last statement". */
  hint?: string;
  /** Signed delta rendered as a chip. */
  delta?: { text: string; tone: 'up' | 'down' | 'flat' };
  trend?: ReactNode;
  accent?: 'accent' | 'teal' | 'success' | 'warning';
};

export function KpiTile({ label, value, icon, hint, delta, trend, accent = 'accent' }: KpiTileProps) {
  return (
    <div className={`kpi-tile kpi-tile--${accent}`}>
      <div className="kpi-top">
        {icon && <span className="kpi-icon">{icon}</span>}
        {delta && <span className={`kpi-delta kpi-delta--${delta.tone}`}>{delta.text}</span>}
      </div>
      <strong className="kpi-value">{value}</strong>
      <span className="kpi-label">{label}</span>
      {hint && <span className="kpi-hint">{hint}</span>}
      {trend && <div className="kpi-trend">{trend}</div>}
    </div>
  );
}

type ChartCardProps = {
  title: string;
  subtitle?: string;
  actions?: ReactNode;
  children: ReactNode;
  className?: string;
};

export function ChartCard({ title, subtitle, actions, children, className = '' }: ChartCardProps) {
  return (
    <section className={`chart-card ${className}`}>
      <header className="chart-card-head">
        <div>
          <h3 className="chart-card-title">{title}</h3>
          {subtitle && <p className="chart-card-sub">{subtitle}</p>}
        </div>
        {actions && <div className="chart-card-actions">{actions}</div>}
      </header>
      <div className="chart-card-body">{children}</div>
    </section>
  );
}

type RankRowProps = {
  index: number;
  name: string;
  meta?: string;
  /** 0–100 fill of the popularity bar. */
  percent: number;
  value: string;
  color?: string;
};

export function RankRow({ index, name, meta, percent, value, color = 'var(--accent)' }: RankRowProps) {
  const pct = Math.max(0, Math.min(100, Number.isFinite(percent) ? percent : 0));
  return (
    <div className="rank-row">
      <span className="rank-idx">{String(index).padStart(2, '0')}</span>
      <div className="rank-name">
        <strong>{name}</strong>
        {meta && <span className="muted">{meta}</span>}
      </div>
      <div className="rank-bar">
        <span style={{ width: `${pct}%`, background: color }} />
      </div>
      <span className="rank-value" style={{ color, borderColor: color }}>
        {value}
      </span>
    </div>
  );
}

export function ChartEmpty({ message }: { message: string }) {
  return (
    <div className="chart-empty">
      <span className="chart-empty-glyph" aria-hidden>
        ◠
      </span>
      <p className="muted">{message}</p>
    </div>
  );
}
