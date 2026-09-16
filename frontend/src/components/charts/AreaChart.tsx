import { useId, useState } from 'react';
import {
  compactNumber,
  linePath,
  niceCeil,
  useChartWidth,
  type ChartSeries,
  type Point,
} from './chartUtils';

type Props = {
  labels: string[];
  series: ChartSeries[];
  height?: number;
  yTicks?: number;
  /** Tooltip / axis formatter. Defaults to a compact number. */
  format?: (n: number) => string;
  showLegend?: boolean;
  /** Renders straight segments instead of curves. */
  straight?: boolean;
};

const PAD = { t: 16, r: 16, b: 28, l: 48 };

export function AreaChart({
  labels,
  series,
  height = 240,
  yTicks = 4,
  format = compactNumber,
  showLegend = true,
  straight = false,
}: Props) {
  const { ref, width } = useChartWidth();
  const uid = useId().replace(/[^a-zA-Z0-9]/g, '');
  const [hover, setHover] = useState<number | null>(null);

  const n = labels.length;
  const innerW = Math.max(width - PAD.l - PAD.r, 10);
  const innerH = Math.max(height - PAD.t - PAD.b, 10);

  const all = series.flatMap((s) => s.points).filter((v) => Number.isFinite(v));
  const hi = niceCeil(all.length ? Math.max(...all) : 1);
  const lo = 0;

  const xAt = (i: number) => PAD.l + (n <= 1 ? innerW / 2 : (innerW * i) / (n - 1));
  const yAt = (v: number) => PAD.t + innerH - ((v - lo) / (hi - lo || 1)) * innerH;

  const ticks = Array.from({ length: yTicks + 1 }, (_, i) => (hi / yTicks) * i);
  const labelStep = n > 12 ? Math.ceil(n / 8) : 1;

  const onMove = (e: React.MouseEvent<SVGSVGElement>) => {
    if (n === 0) return;
    const rect = e.currentTarget.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const raw = n <= 1 ? 0 : ((x - PAD.l) / innerW) * (n - 1);
    setHover(Math.max(0, Math.min(n - 1, Math.round(raw))));
  };

  const tipLeft = hover == null ? 0 : Math.min(Math.max(xAt(hover), 70), Math.max(width - 70, 70));

  return (
    <div className="chart-wrap" ref={ref}>
      <svg
        className="chart-svg"
        width={width}
        height={height}
        role="img"
        onMouseMove={onMove}
        onMouseLeave={() => setHover(null)}
      >
        <defs>
          {series.map((s, si) => (
            <linearGradient key={s.name} id={`${uid}-g${si}`} x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" style={{ stopColor: s.color, stopOpacity: 0.42 }} />
              <stop offset="100%" style={{ stopColor: s.color, stopOpacity: 0 }} />
            </linearGradient>
          ))}
        </defs>

        {ticks.map((t) => (
          <g key={t}>
            <line
              className="chart-grid"
              x1={PAD.l}
              x2={PAD.l + innerW}
              y1={yAt(t)}
              y2={yAt(t)}
            />
            <text className="chart-axis" x={PAD.l - 10} y={yAt(t) + 4} textAnchor="end">
              {format(t)}
            </text>
          </g>
        ))}

        {labels.map((label, i) =>
          i % labelStep === 0 ? (
            <text
              key={`${label}-${i}`}
              className="chart-axis"
              x={xAt(i)}
              y={height - 8}
              textAnchor="middle"
            >
              {label}
            </text>
          ) : null,
        )}

        {series.map((s, si) => {
          const pts: Point[] = s.points.map((v, i) => ({
            x: xAt(i),
            y: yAt(Number.isFinite(v) ? v : 0),
          }));
          if (pts.length === 0) return null;
          const line = linePath(pts, !straight);
          const area = `${line} L ${pts[pts.length - 1].x} ${PAD.t + innerH} L ${pts[0].x} ${
            PAD.t + innerH
          } Z`;
          return (
            <g key={s.name}>
              <path d={area} fill={`url(#${uid}-g${si})`} />
              <path
                d={line}
                fill="none"
                stroke={s.color}
                strokeWidth="2.25"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            </g>
          );
        })}

        {hover != null && (
          <g>
            <line
              className="chart-cursor"
              x1={xAt(hover)}
              x2={xAt(hover)}
              y1={PAD.t}
              y2={PAD.t + innerH}
            />
            {series.map((s) => (
              <circle
                key={s.name}
                cx={xAt(hover)}
                cy={yAt(Number.isFinite(s.points[hover]) ? s.points[hover] : 0)}
                r="4.5"
                fill={s.color}
                className="chart-dot"
              />
            ))}
          </g>
        )}
      </svg>

      {hover != null && (
        <div className="chart-tip" style={{ left: tipLeft }}>
          <span className="chart-tip-label">{labels[hover]}</span>
          {series.map((s) => (
            <span key={s.name} className="chart-tip-row">
              <i className="legend-dot" style={{ background: s.color }} />
              {s.name}
              <strong>{format(Number.isFinite(s.points[hover]) ? s.points[hover] : 0)}</strong>
            </span>
          ))}
        </div>
      )}

      {showLegend && (
        <div className="chart-legend">
          {series.map((s) => (
            <span key={s.name} className="chart-legend-item">
              <i className="legend-dot" style={{ background: s.color }} />
              {s.name}
            </span>
          ))}
        </div>
      )}
    </div>
  );
}
