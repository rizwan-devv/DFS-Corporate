import { useId } from 'react';
import { linePath, type Point } from './chartUtils';

type Props = {
  points: number[];
  width?: number;
  height?: number;
  color?: string;
};

/** Tiny inline trend line for KPI tiles. */
export function Sparkline({ points, width = 96, height = 34, color = 'var(--accent)' }: Props) {
  const uid = useId().replace(/[^a-zA-Z0-9]/g, '');
  const clean = points.filter((v) => Number.isFinite(v));
  if (clean.length < 2) return <div className="sparkline sparkline--empty" style={{ width, height }} />;

  const hi = Math.max(...clean);
  const lo = Math.min(...clean);
  const span = hi - lo || 1;
  const pad = 3;
  const pts: Point[] = clean.map((v, i) => ({
    x: (width * i) / (clean.length - 1),
    y: pad + (height - pad * 2) * (1 - (v - lo) / span),
  }));
  const line = linePath(pts);
  const area = `${line} L ${pts[pts.length - 1].x} ${height} L ${pts[0].x} ${height} Z`;

  return (
    <svg className="sparkline" width={width} height={height} aria-hidden>
      <defs>
        <linearGradient id={`${uid}-s`} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" style={{ stopColor: color, stopOpacity: 0.35 }} />
          <stop offset="100%" style={{ stopColor: color, stopOpacity: 0 }} />
        </linearGradient>
      </defs>
      <path d={area} fill={`url(#${uid}-s)`} />
      <path d={line} fill="none" stroke={color} strokeWidth="1.8" strokeLinecap="round" />
      <circle cx={pts[pts.length - 1].x} cy={pts[pts.length - 1].y} r="2.6" fill={color} />
    </svg>
  );
}
