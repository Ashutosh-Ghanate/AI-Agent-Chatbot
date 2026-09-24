import { useEffect, useId, useMemo, useRef, useState } from 'react'
import { PIE_COLORS, axisLabel, buildBarData, buildPieData } from '../utils/chartData'
import { buildLineData, lineAxisLabels } from '../utils/chartData'
import { shouldUseHorizontalBars, thinTickIndexes, tickBudget } from '../utils/chartData'
import { buildScatterData, chartTitle } from '../utils/chartData'
import { formatAxisValue, formatFullDateLabel, formatValue } from '../utils/chartData'
import { niceTicks, toNumber, truncateLabel } from '../utils/chartData'

export function isDrawableChart(chart, columns) {
  if (!chart || typeof chart.type !== 'string') return false
  const have = new Set((Array.isArray(columns) ? columns : []).map((c) => String(c).toLowerCase()))
  const has = (c) => typeof c === 'string' && c !== '' && have.has(c.toLowerCase())
  if (chart.type === 'pie') return has(chart.nameColumn) && has(chart.valueColumn)
  return has(chart.xColumn) && has(chart.yColumn)
}

/**
 * Measured width of the chart container. The chatbot panel is narrow
 * (~30-35% of the screen), so plots use this to thin tick labels instead of
 * assuming a desktop width. Falls back to 360px before measurement.
 */
function useChartWidth() {
  const ref = useRef(null)
  const [width, setWidth] = useState(360)
  useEffect(() => {
    const node = ref.current
    if (!node) return undefined
    const update = () => {
      const measured = node.clientWidth
      if (Number.isFinite(measured) && measured > 0) setWidth(measured)
    }
    update()
    if (typeof ResizeObserver !== 'undefined') {
      const observer = new ResizeObserver(update)
      observer.observe(node)
      return () => observer.disconnect()
    }
    window.addEventListener('resize', update)
    return () => window.removeEventListener('resize', update)
  }, [])
  return [ref, width]
}

const AXIS = '#57606a'
const TITLE_FILL = '#24292f'
function BarPlot({ data, title, xColumn, yColumn }) {
  const titleId = useId()
  const [wrapRef, width] = useChartWidth()
  const bars = data.slice(0, 25)
  const horizontal = shouldUseHorizontalBars(bars)
  const xTitle = axisLabel(xColumn)
  const yTitle = axisLabel(yColumn)
  if (!horizontal) {
    const mx = Math.max(0, ...bars.map((b) => b.value))
    const s = niceTicks(0, mx <= 0 ? 1 : mx)
    const ticks = s.ticks.slice(0, 6)
    // Width-aware thinning: only visible tick labels are reduced, never data.
    const maxTicks = tickBudget(width, 64, 6, 2)
    const tickIndexes = thinTickIndexes(bars.length, maxTicks)
    const tickSet = new Set(tickIndexes)
    const left = 52
    const plotW = 520 - left - 12
    const slot = plotW / Math.max(bars.length, 1)
    const bw = Math.max(6, Math.min(44, slot * 0.6))
    const top = 12
    const plotH = 208
    const bottom = 300 - 52
    const fy = (v) => top + ((s.max - v) / (s.max - s.min || 1)) * plotH
    const maxChars = Math.max(4, Math.min(14, Math.floor(slot / 7)))
    return (
      <div ref={wrapRef} className="chart-svg-wrap">
      <svg viewBox="0 0 520 300" role="img" aria-labelledby={titleId} className="chart-svg">
        <g>
          <title id={titleId}>{title}</title>
          <rect x={1} y={1} width={518} height={298} rx={10} fill="#fff" stroke="#e3e8f0" />
        </g>
        <g>
          {ticks.map((t) => {
            const y = fy(t)
            return (
              <g key={t}>
                <line x1={left} x2={left + plotW} y1={y} y2={y} stroke="#eef1f6" />
                <text x={left - 6} y={y + 4} textAnchor="end" fontSize={11} fill={AXIS}>{formatAxisValue(t)}</text>
              </g>
            )
          })}
        </g>
        {bars.map((bar, i) => {
          const hh = s.max > s.min ? ((bar.value - s.min) / (s.max - s.min)) * plotH : 0
          const x = left + slot * i + (slot - bw) / 2
          const y = top + (plotH - hh)
          return (
            <g key={i}>
              <title>{`${bar.label}: ${formatValue(bar.value)}`}</title>
              <rect x={x} y={y} width={bw} height={Math.max(hh, 2)} rx={4} fill="#1f6feb" />
              {tickSet.has(i)
                ? <text x={x + bw / 2} y={bottom - 30} textAnchor="middle" fontSize={11} fill={AXIS}>{truncateLabel(bar.label, maxChars)}</text>
                : null}
            </g>
          )
        })}
        <text x={left + plotW / 2} y={292} textAnchor="middle" fontSize={12} fontWeight={600} fill={TITLE_FILL}>{xTitle}</text>
        <text x={14} y={top + plotH / 2} textAnchor="middle" fontSize={12} fontWeight={600} fill={TITLE_FILL} transform={`rotate(-90 14 ${top + plotH / 2})`}>{truncateLabel(yTitle, 18)}</text>
      </svg>
      </div>
    )
  }
  // Crowded/long categories: horizontal bars keep every label readable.
  const mx = Math.max(0, ...bars.map((b) => b.value))
  const s = niceTicks(0, mx <= 0 ? 1 : mx)
  const ticks = s.ticks.slice(0, 6)
  const rowH = Math.max(22, Math.min(30, 232 / Math.max(bars.length, 1)))
  const height = 60 + bars.length * rowH + 34
  const labelW = 150
  const left = labelW + 10
  const plotW = 520 - left - 12
  const top = 12
  const maxChars = 22
  return (
    <div ref={wrapRef} className="chart-svg-wrap">
    <svg viewBox={`0 0 520 ${height}`} role="img" aria-labelledby={titleId} className="chart-svg">
      <g>
        <title id={titleId}>{title}</title>
        <rect x={1} y={1} width={518} height={height - 2} rx={10} fill="#fff" stroke="#e3e8f0" />
      </g>
      {bars.map((bar, i) => {
        const y = top + i * rowH
        const w = s.max > s.min ? ((bar.value - s.min) / (s.max - s.min)) * plotW : 0
        return (
          <g key={i}>
            <title>{`${bar.label}: ${formatValue(bar.value)}`}</title>
            <text x={labelW} y={y + rowH / 2 + 4} textAnchor="end" fontSize={11} fill={AXIS}>{truncateLabel(bar.label, maxChars)}</text>
            <rect x={left} y={y + 3} width={Math.max(w, 2)} height={rowH - 8} rx={4} fill="#1f6feb" />
          </g>
        )
      })}
      <g fontSize={11} fill={AXIS}>
        {ticks.map((t) => {
          const x = left + (s.max > s.min ? ((t - s.min) / (s.max - s.min)) * plotW : 0)
          return <text key={t} x={x} y={top + bars.length * rowH + 16} textAnchor="middle">{formatAxisValue(t)}</text>
        })}
      </g>
      <text x={left + plotW / 2} y={top + bars.length * rowH + 30} textAnchor="middle" fontSize={12} fontWeight={600} fill={TITLE_FILL}>{yTitle}</text>
    </svg>
    </div>
  )
}

function LinePlot({ data, title, xColumn, yColumn }) {
  const titleId = useId()
  const [wrapRef, width] = useChartWidth()
  const pts = data.slice(0, 100)
  const vals = pts.map((p) => p.value)
  const s = niceTicks(Math.min(...vals), Math.max(...vals))
  const ticks = s.ticks.slice(0, 6)
  // X positions follow the actual date/time when present (true time scale);
  // otherwise the points are spaced evenly in chronological/sorted order.
  const left = 52
  const top = 12
  const plotH = 208
  const plotRight = 504
  const xs = pts.map((p) => (Number.isFinite(p?.x) ? p.x : null))
  const hasTimeScale = xs.every((x) => x !== null) && pts.some((p) => p.isDate)
  const minX = hasTimeScale ? Math.min(...xs) : 0
  const maxX = hasTimeScale ? Math.max(...xs) : Math.max(pts.length - 1, 1)
  const fx = (point, index) => {
    if (hasTimeScale) {
      const span = maxX - minX || 1
      return left + ((point.x - minX) / span) * (plotRight - left)
    }
    const step = pts.length > 1 ? (plotRight - left) / (pts.length - 1) : 0
    return left + step * index
  }
  const fy = (v) => top + ((s.max - v) / (s.max - s.min || 1)) * plotH
  const d = pts.map((p, i) => `${i === 0 ? 'M' : 'L'}${fx(p, i).toFixed(1)},${fy(p.value).toFixed(1)}`).join(' ')
  // Existing date formatting is reused; only the visible tick count adapts
  // to the measured panel width - every data point is still drawn.
  const { labels } = lineAxisLabels(pts)
  const maxTicks = tickBudget(width, 64, 6, 2)
  const tickIndexes = thinTickIndexes(pts.length, maxTicks)
  const tickSet = new Set(tickIndexes)
  const xTitle = axisLabel(xColumn)
  const yTitle = axisLabel(yColumn)
  return (
    <div ref={wrapRef} className="chart-svg-wrap">
    <svg viewBox="0 0 520 300" role="img" aria-labelledby={titleId} className="chart-svg">
      <g>
        <title id={titleId}>{title}</title>
        <rect x={1} y={1} width={518} height={298} rx={10} fill="#fff" stroke="#e3e8f0" />
      </g>
      <g>
        {ticks.map((t) => {
          const y = top + ((s.max - t) / (s.max - s.min || 1)) * plotH
          return (
            <g key={t}>
              <line x1={left} x2={504} y1={y} y2={y} stroke="#eef1f6" />
              <text x={left - 6} y={y + 4} textAnchor="end" fontSize={11} fill={AXIS}>{formatAxisValue(t)}</text>
            </g>
          )
        })}
      </g>
      <path d={d} fill="none" stroke="#1f6feb" strokeWidth={2.5} strokeLinejoin="round" strokeLinecap="round" />
      {pts.map((p, i) => (
        <g key={i}>
          <title>{`${p.isDate && Number.isFinite(p.x) ? formatFullDateLabel(p.x) : p.label}: ${formatValue(p.value)}`}</title>
          <circle cx={fx(p, i)} cy={fy(p.value)} r={3.5} fill="#1f6feb" stroke="#fff" strokeWidth={1.5} />
        </g>
      ))}
      <g fontSize={11} fill={AXIS}>
        {pts.map((p, i) => (
          tickSet.has(i) ? (
            <text key={i} x={fx(p, i)} y={262} textAnchor="middle">{truncateLabel(labels[i], 12)}</text>
          ) : null
        ))}
      </g>
      <text x={(left + 504) / 2} y={292} textAnchor="middle" fontSize={12} fontWeight={600} fill={TITLE_FILL}>{xTitle}</text>
      <text x={14} y={top + plotH / 2} textAnchor="middle" fontSize={12} fontWeight={600} fill={TITLE_FILL} transform={`rotate(-90 14 ${top + plotH / 2})`}>{truncateLabel(yTitle, 18)}</text>
    </svg>
    </div>
  )
}

function PiePlot({ data, title }) {
  const titleId = useId()
  const [wrapRef, width] = useChartWidth()
  const slices = data.slice(0, 12)
  const total = slices.reduce((s, x) => s + x.value, 0)
  const narrow = width < 340
  const r = narrow ? 78 : 88
  const cx = narrow ? 130 : 140
  const cy = narrow ? 150 : 152
  let a = -Math.PI / 2
  const arcs = slices.map((slice, index) => {
    const share = total > 0 ? slice.value / total : 0
    const s0 = a
    a += share * Math.PI * 2
    const e0 = a
    const big = e0 - s0 > Math.PI ? 1 : 0
    const x1 = cx + r * Math.cos(s0)
    const y1 = cy + r * Math.sin(s0)
    const x2 = cx + r * Math.cos(e0)
    const y2 = cy + r * Math.sin(e0)
    return { slice, index, share, d: `M${cx},${cy} L${x1.toFixed(1)},${y1.toFixed(1)} A${r},${r} 0 ${big} 1 ${x2.toFixed(1)},${y2.toFixed(1)} Z` }
  })
  // Crowded slices move to a wrapping legend below the pie: no slice or
  // label is removed, so nothing overlaps and tooltips keep full values.
  const legendX = narrow ? 236 : 252
  const legendW = 520 - legendX - 12
  const maxChars = Math.max(10, Math.min(24, Math.floor(legendW / 8.5)))
  return (
    <div ref={wrapRef} className="chart-svg-wrap">
    <svg viewBox="0 0 520 300" role="img" aria-labelledby={titleId} className="chart-svg">
      <g>
        <title id={titleId}>{title}</title>
        <rect x={1} y={1} width={518} height={298} rx={10} fill="#fff" stroke="#e3e8f0" />
      </g>
      {arcs.map((x) => (
        <g key={x.index}>
          <title>{`${x.slice.label}: ${formatValue(x.slice.value)} (${Math.round(x.share * 100)}%)`}</title>
          <path d={x.d} fill={PIE_COLORS[x.index % PIE_COLORS.length]} stroke="#fff" strokeWidth={2} />
        </g>
      ))}
      <g fontSize={11} fill={TITLE_FILL}>
        {slices.map((s, i) => (
          <g key={i}>
            <rect x={legendX} y={36 + i * 20} width={12} height={12} rx={3} fill={PIE_COLORS[i % PIE_COLORS.length]} />
            <text x={legendX + 18} y={46 + i * 20}>{truncateLabel(`${s.label} (${Math.round(total > 0 ? (s.value / total) * 100 : 0)}%)`, maxChars)}</text>
          </g>
        ))}
      </g>
    </svg>
    <ul className="chart-legend" aria-label={`${title} details`}>
      {slices.map((s, i) => (
        <li key={i}>
          <span className="chart-legend-swatch" style={{ backgroundColor: PIE_COLORS[i % PIE_COLORS.length] }} />
          <span className="chart-legend-label">{s.label}</span>
          <span className="chart-legend-value">{`${formatValue(s.value)} (${Math.round(total > 0 ? (s.value / total) * 100 : 0)}%)`}</span>
        </li>
      ))}
    </ul>
    </div>
  )
}

function ScatterPlot({ data, xLabel, yLabel, title }) {
  const titleId = useId()
  const [wrapRef, width] = useChartWidth()
  const pts = data.slice(0, 500)
  const xTitle = axisLabel(xLabel)
  const yTitle = axisLabel(yLabel)
  const pad = 6
  const xs = niceTicks(Math.min(...pts.map((p) => p.x)), Math.max(...pts.map((p) => p.x)))
  const ys = niceTicks(Math.min(...pts.map((p) => p.y)), Math.max(...pts.map((p) => p.y)))
  // Padded point radius keeps every point inside the SVG (no clipping) and
  // tick labels use the short numeric form so large values stay readable.
  const pointR = width < 340 ? 3 : 4
  const left = 56
  const right = 508
  const top = 12
  const bottom = 246
  const fx = (v) => left + pad + ((v - xs.min) / (xs.max - xs.min || 1)) * (right - left - pad * 2)
  const fy = (v) => top + pad + ((ys.max - v) / (ys.max - ys.min || 1)) * (bottom - top - pad * 2)
  const xTicks = xs.ticks.slice(0, 6)
  const yTicks = ys.ticks.slice(0, 6)
  return (
    <div ref={wrapRef} className="chart-svg-wrap">
    <svg viewBox="0 0 520 300" role="img" aria-labelledby={titleId} className="chart-svg">
      <g>
        <title id={titleId}>{title}</title>
        <rect x={1} y={1} width={518} height={298} rx={10} fill="#fff" stroke="#e3e8f0" />
      </g>
      <g>
        {yTicks.map((t) => {
          const y = top + ((ys.max - t) / (ys.max - ys.min || 1)) * (bottom - top)
          return (
            <g key={t}>
              <line x1={left} x2={right} y1={y} y2={y} stroke="#eef1f6" />
              <text x={left - 6} y={y + 4} textAnchor="end" fontSize={11} fill={AXIS}>{formatAxisValue(t)}</text>
            </g>
          )
        })}
      </g>
      <g fontSize={11} fill={AXIS}>
        {xTicks.map((t) => {
          const x = left + ((t - xs.min) / (xs.max - xs.min || 1)) * (right - left)
          return <text key={t} x={x} y={bottom + 18} textAnchor="middle">{formatAxisValue(t)}</text>
        })}
      </g>
      {pts.map((p, i) => (
        <g key={i}>
          <title>{`${xTitle}: ${formatValue(p.x)}, ${yTitle}: ${formatValue(p.y)}`}</title>
          <circle cx={fx(p.x)} cy={fy(p.y)} r={pointR} fill="#1f6feb" opacity={0.75} stroke="#fff" strokeWidth={1} />
        </g>
      ))}
      <text x={(left + right) / 2} y={292} textAnchor="middle" fontSize={12} fontWeight={600} fill={TITLE_FILL}>{xTitle}</text>
      <text x={14} y={(top + bottom) / 2} textAnchor="middle" fontSize={12} fontWeight={600} fill={TITLE_FILL} transform={`rotate(-90 14 ${(top + bottom) / 2})`}>{truncateLabel(yTitle, 18)}</text>
    </svg>
    </div>
  )
}

export default function ResultChart({ chart, columns, rows }) {
  const safeRows = useMemo(() => (Array.isArray(rows) ? rows : []), [rows])
  const title = chartTitle(chart)
  if (!isDrawableChart(chart, columns) || safeRows.length === 0) return null
  if (chart.type === 'bar') {
    const d = buildBarData(chart, safeRows)
    if (!d.length) return null
    return (
      <figure className="result-chart">
        <figcaption className="result-chart-title">{title}</figcaption>
        <BarPlot data={d} title={title} xColumn={chart.xColumn} yColumn={chart.yColumn} />
      </figure>
    )
  }
  if (chart.type === 'line') {
    const d = buildLineData(chart, safeRows)
    if (!d.length) return null
    return (
      <figure className="result-chart">
        <figcaption className="result-chart-title">{title}</figcaption>
        <LinePlot data={d} title={title} xColumn={chart.xColumn} yColumn={chart.yColumn} />
      </figure>
    )
  }
  if (chart.type === 'pie') {
    const d = buildPieData(chart, safeRows)
    if (!d.length || d.some((s) => toNumber(s.value) === null || toNumber(s.value) < 0)) return null
    return (
      <figure className="result-chart">
        <figcaption className="result-chart-title">{title}</figcaption>
        <PiePlot data={d} title={title} />
      </figure>
    )
  }
  if (chart.type === 'scatter') {
    const d = buildScatterData(chart, safeRows)
    if (!d.length) return null
    return (
      <figure className="result-chart">
        <figcaption className="result-chart-title">{title}</figcaption>
        <ScatterPlot data={d} xLabel={chart.xColumn} yLabel={chart.yColumn} title={title} />
      </figure>
    )
  }
  return null
}

