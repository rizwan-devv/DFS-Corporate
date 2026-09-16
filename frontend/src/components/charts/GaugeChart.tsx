import { useId } from 'react';
import { polar } from './chartUtils';

type Props = {
  /** 0–100. */
  percent: number;
  size?: number;
  label?: string;
  caption?: string;
  color?: string;
};

/** 270° arc gauge, matching the reference "Earnings" dial. */
export function GaugeChart({
  percent,
  size = 190,
  label,
  caption,
  color = 'var(--accent)',
}: Props) {
  const uid = useId().replace(/[^a-zA-Z0-9]/g, '');
  const pct = Math.max(0, Math.min(100, Number.isFinite(percent) ? percent : 0));

  const stroke = Math.max(12, size * 0.1);
  const r = (size - stroke) / 2 - 2;
  const cx = size / 2;
  const cy = size / 2;
  const start = Math.PI * 0.75;
  const sweep = Math.PI * 1.5;

  const p0 = polar(cx, cy, r, start);
  const p1 = polar(cx, cy, r, start + sweep);
  const arc = `M ${p0.x} ${p0.y} A ${r} ${r} 0 1 1 ${p1.x} ${p1.y}`;
  const len = r * sweep;

  return (
    <div className="gauge" style={{ width: size }}>
      <svg width={size} height={size * 0.82} viewBox={`0 0 ${size} ${size * 0.82}`} role="img">
        <defs>
          <linearGradient id={`${uid}-gg`} x1="0" y1="1" x2="1" y2="0">
            <stop offset="0%" style={{ stopColor: color, stopOpacity: 0.55 }} />
            <stop offset="100%" style={{ stopColor: color, stopOpacity: 1 }} />
          </linearGradient>
        </defs>
        <path
          d={arc}
          className="gauge-track"
          fill="none"
          strokeWidth={stroke}
          strokeLinecap="round"
        />
        <path
          d={arc}
          fill="none"
          stroke={`url(#${uid}-gg)`}
          strokeWidth={stroke}
          strokeLinecap="round"
          strokeDasharray={`${len} ${len}`}
          strokeDashoffset={len * (1 - pct / 100)}
          className="gauge-value"
        />
        <text x={cx} y={cy + 4} textAnchor="middle" className="gauge-pct">
          {Math.round(pct)}%
        </text>
        {label && (
          <text x={cx} y={cy + 26} textAnchor="middle" className="gauge-label">
            {label}
          </text>
        )}
      </svg>
      {caption && <p className="gauge-caption">{caption}</p>}
    </div>
  );
}
