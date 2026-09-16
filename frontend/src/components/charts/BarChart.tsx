import { useState } from 'react';
import { compactNumber, niceCeil, useChartWidth, type ChartSeries } from './chartUtils';

type Props = {
  labels: string[];
  series: ChartSeries[];
  height?: number;
  yTicks?: number;
  format?: (n: number) => string;
  showLegend?: boolean;
};

const PAD = { t: 14, r: 10, b: 26, l: 40 };

export function BarChart({
  labels,
  series,
  height = 210,
  yTicks = 3,
  format = compactNumber,
  showLegend = true,
}: Props) {
  const { ref, width } = useChartWidth(340);
  const [hover, setHover] = useState<number | null>(null);

  const n = labels.length;
  const innerW = Math.max(width - PAD.l - PAD.r, 10);
  const innerH = Math.max(height - PAD.t - PAD.b, 10);

  const all = series.flatMap((s) => s.points).filter((v) => Number.isFinite(v));
  const hi = niceCeil(all.length ? Math.max(...all) : 1);

  const groupW = n > 0 ? innerW / n : innerW;
  const barW = Math.max(
    3,
    Math.min(16, (groupW - Math.max(6, groupW * 0.25)) / Math.max(series.length, 1)),
  );
  const yAt = (v: number) => PAD.t + innerH - (v / (hi || 1)) * innerH;
  const ticks = Array.from({ length: yTicks + 1 }, (_, i) => (hi / yTicks) * i);
  const labelStep = n > 10 ? Math.ceil(n / 7) : 1;

  return (
    <div className="chart-wrap" ref={ref}>
      <svg className="chart-svg" width={width} height={height} role="img">
        {ticks.map((t) => (
          <line
            key={t}
            className="chart-grid"
            x1={PAD.l}
            x2={PAD.l + innerW}
            y1={yAt(t)}
            y2={yAt(t)}
          />
        ))}
        {ticks.map((t) => (
          <text key={`l${t}`} className="chart-axis" x={PAD.l - 8} y={yAt(t) + 4} textAnchor="end">
            {format(t)}
          </text>
        ))}

        {labels.map((label, i) => {
          const groupX = PAD.l + groupW * i;
          const totalW = barW * series.length + 3 * (series.length - 1);
          const startX = groupX + (groupW - totalW) / 2;
          return (
            <g
              key={`${label}-${i}`}
              onMouseEnter={() => setHover(i)}
              onMouseLeave={() => setHover(null)}
            >
              <rect
                x={groupX}
                y={PAD.t}
                width={groupW}
                height={innerH}
                fill="transparent"
                className={hover === i ? 'chart-bar-hit is-hover' : 'chart-bar-hit'}
              />
              {series.map((s, si) => {
                const v = Number.isFinite(s.points[i]) ? s.points[i] : 0;
                const y = yAt(v);
                const h = Math.max(PAD.t + innerH - y, v > 0 ? 2 : 0);
                return (
                  <rect
                    key={s.name}
                    x={startX + si * (barW + 3)}
                    y={y}
                    width={barW}
                    height={h}
                    rx={Math.min(barW / 2, 5)}
                    fill={s.color}
                    opacity={hover == null || hover === i ? 1 : 0.35}
                    className="chart-bar"
                  />
                );
              })}
              {i % labelStep === 0 && (
                <text className="chart-axis" x={groupX + groupW / 2} y={height - 8} textAnchor="middle">
                  {label}
                </text>
              )}
            </g>
          );
        })}
      </svg>

      {hover != null && (
        <div className="chart-tip chart-tip--static">
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
