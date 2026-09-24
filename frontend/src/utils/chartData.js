/**
 * Turns a backend chart configuration plus the result rows into the points the SVG charts draw.
 *
 * The chart never carries data of its own: it only names columns, and every point below comes
 * from the very same `rows` that ResultTable renders. The backend already validated the
 * configuration against those rows (see ChartConfigService), so nothing is invented here - a
 * value that is not a number is simply not plotted, and a series that cannot be drawn returns
 * an empty array so the chart renders nothing while the table stays visible.
 */

/** Slice colours for pie charts, starting with the app's blue. */
export const PIE_COLORS = [
  '#1f6feb', '#2da44e', '#bf8700', '#cf222e', '#8250df', '#0969da',
  '#57606a', '#db6d28', '#1a7f37', '#a40e26', '#6639ba', '#116329',
]

/** Numeric value of a result cell, or null when the cell is not a number. */
export function toNumber(value) {
  if (typeof value === 'number') {
    return Number.isFinite(value) ? value : null
  }
  if (typeof value === 'string' && value.trim() !== '') {
    const parsed = Number(value)
    return Number.isFinite(parsed) ? parsed : null
  }
  return null
}

/** Short month names for human-readable date axis labels. */
const MONTH_NAMES = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']

/**
 * Numeric timestamp (ms since epoch) for a date/time cell, or null when the
 * cell is not a date.
 *
 * Accepts PostgreSQL `date` strings ("2026-01-05"), ISO datetimes
 * ("2026-01-05T10:30:00", including the space-separated "2026-01-05 10:30:00"
 * form the JDBC driver can produce), month buckets ("2026-01") and real
 * JavaScript `Date` objects. Never uses `Number(dateString)` - that returns
 * NaN for dates and is what broke the line chart axis.
 */
export function parseDateValue(value) {
  if (value === null || value === undefined) return null
  if (value instanceof Date) {
    const time = value.getTime()
    return Number.isFinite(time) ? time : null
  }
  // Numbers (and numeric strings like "2024") belong to toNumber(), never to
  // dates - otherwise a year or count would be mistaken for a timestamp.
  if (typeof value === 'number') return null
  if (typeof value === 'string') {
    const text = value.trim()
    if (text === '') return null
    // Pure numbers are handled by toNumber(), not as dates (a year like
    // "2024" must stay numeric so numeric line charts keep working).
    if (/^-?\d+(\.\d+)?$/.test(text)) return null
    // PostgreSQL renders timestamps with a space ("2026-01-05 10:30:00")
    // which `new Date` parses inconsistently, so normalise to ISO `T` first.
    const iso = /^\d{4}-\d{2}-\d{2} \d/.test(text) ? text.replace(' ', 'T') : text
    const time = new Date(iso).getTime()
    return Number.isFinite(time) ? time : null
  }
  return null
}

/** True for month-bucket strings such as "2026-01" (DATE_TRUNC results). */
export function isMonthValue(value) {
  return typeof value === 'string' && /^\d{4}-\d{2}$/.test(value.trim())
}

/** "2026-01-05" -> "Jan 5": short day-level X-axis label. */
export function formatDayLabel(timestamp) {
  if (!Number.isFinite(timestamp)) return ''
  const date = new Date(timestamp)
  if (Number.isNaN(date.getTime())) return ''
  return `${MONTH_NAMES[date.getMonth()]} ${date.getDate()}`
}

/** "2026-01" -> "Jan 2026": short month-level X-axis label. */
export function formatMonthLabel(timestamp) {
  if (!Number.isFinite(timestamp)) return ''
  const date = new Date(timestamp)
  if (Number.isNaN(date.getTime())) return ''
  return `${MONTH_NAMES[date.getMonth()]} ${date.getFullYear()}`
}

/** Timestamp -> "Jan 5, 2026": full date for tooltips. */
export function formatFullDateLabel(timestamp) {
  if (!Number.isFinite(timestamp)) return ''
  const date = new Date(timestamp)
  if (Number.isNaN(date.getTime())) return ''
  return new Intl.DateTimeFormat('en-US', { month: 'short', day: 'numeric', year: 'numeric' }).format(date)
}

/** Full value for tooltips and labels, e.g. "1,250,000". */
export function formatValue(value) {
  const number = toNumber(value)
  if (number === null) {
    return value === null || value === undefined ? '—' : String(value)
  }
  return new Intl.NumberFormat('en-US', { maximumFractionDigits: 2 }).format(number)
}

/** Short value for axis ticks, e.g. "1.3M". */
export function formatAxisValue(value) {
  const number = toNumber(value)
  if (number === null) return ''
  const magnitude = Math.abs(number)
  const trim = (scaled) => String(Number(scaled.toFixed(2)))
  if (magnitude >= 1000000) return `${trim(number / 1000000)}M`
  if (magnitude >= 1000) return `${trim(number / 1000)}k`
  return trim(number)
}

export function labelOf(value) {
  return value === null || value === undefined ? '—' : String(value)
}

/** Shortens a long category label so it stays readable on the axis. */
export function truncateLabel(value, max = 16) {
  const text = labelOf(value)
  return text.length > max ? `${text.slice(0, max - 1)}…` : text
}

/** "loan_amount" -> "Loan amount", for axis captions. */
export function humanize(column) {
  const text = String(column || '').replace(/_/g, ' ').trim()
  return text ? text.charAt(0).toUpperCase() + text.slice(1) : ''
}

/**
 * Human-readable display label for a result column, reused by every chart
 * type for axis titles and tooltips. Known columns get a semantic label with
 * units where appropriate (loan_amount -> "Loan amount (₹)"); anything else
 * falls back to `humanize`, so no chart ever shows a raw `snake_case` db name
 * and no caller hardcodes a particular column pair.
 */
const AXIS_LABELS = {
  loan_amount: 'Loan amount (₹)',
  credit_score: 'Credit score',
  interest_rate: 'Interest rate (%)',
  tenure_months: 'Tenure (months)',
  emi: 'EMI (₹)',
  student_count: 'Student count',
  application_count: 'Application count',
  applications: 'Applications',
  average_loan_amount: 'Average loan amount (₹)',
  avg_loan_amount: 'Average loan amount (₹)',
  total_loan_amount: 'Total loan amount (₹)',
  loan_count: 'Loan count',
  university: 'University',
  course: 'Course',
  status: 'Status',
  applied_on: 'Application date',
  applicant_name: 'Applicant name',
  month: 'Month',
}

export function axisLabel(column) {
  if (column === null || column === undefined) return ''
  const key = String(column).trim().toLowerCase()
  if (!key) return ''
  if (Object.hasOwn(AXIS_LABELS, key)) return AXIS_LABELS[key]
  return humanize(column)
}

/** Rounded tick positions covering [min, max]. */
export function niceTicks(min, max, count = 5) {
  const low = Number.isFinite(min) ? min : 0
  const high = Number.isFinite(max) ? max : 1
  const round = (value) => Number(value.toPrecision(12))

  if (low === high) {
    const pad = Math.abs(low) > 0 ? Math.abs(low) * 0.1 : 1
    return { min: round(low - pad), max: round(high + pad), ticks: [round(low - pad), low, round(high + pad)] }
  }

  const rough = (high - low) / count
  const magnitude = 10 ** Math.floor(Math.log10(rough))
  const step = [1, 2, 2.5, 5, 10].map((factor) => factor * magnitude).find((size) => size >= rough) || magnitude * 10
  const start = Math.floor(low / step) * step
  const end = Math.ceil(high / step) * step

  const ticks = []
  for (let value = start; value <= end + step / 2; value += step) {
    ticks.push(round(value))
  }
  return { min: round(start), max: round(end), ticks }
}

/** Title from the backend, with a column-named fallback in case it is missing. */
export function chartTitle(chart) {
  const title = typeof chart?.title === 'string' ? chart.title.trim() : ''
  if (title) return title
  const [first, second] = chart?.type === 'pie'
    ? [chart?.valueColumn, chart?.nameColumn]
    : [chart?.yColumn, chart?.xColumn]
  if (first && second) {
    const text = `${humanize(first).toLowerCase()} by ${humanize(second).toLowerCase()}`
    return text.charAt(0).toUpperCase() + text.slice(1)
  }
  return 'Result chart'
}

/** One bar per row: { label, value }. */
export function buildBarData(chart, rows) {
  if (!chart?.xColumn || !chart?.yColumn) return []
  const data = []
  for (const row of rows) {
    const value = toNumber(row?.[chart.yColumn])
    if (value === null) continue
    data.push({ label: labelOf(row?.[chart.xColumn]), value })
  }
  return data
}

/** Trend points sorted chronologically: { label, value, x, isDate }. */
export function buildLineData(chart, rows) {
  if (!chart?.xColumn || !chart?.yColumn) return []
  const points = []
  for (const row of rows) {
    const value = toNumber(row?.[chart.yColumn])
    if (value === null) continue
    const raw = row?.[chart.xColumn]
    const time = parseDateValue(raw)
    if (time !== null) {
      points.push({
        label: labelOf(raw),
        value,
        x: time,
        ordered: time,
        isDate: true,
        month: isMonthValue(raw),
      })
      continue
    }
    const numeric = toNumber(raw)
    if (numeric !== null) {
      points.push({ label: labelOf(raw), value, x: numeric, ordered: numeric, isDate: false })
      continue
    }
    // Non-numeric, non-date x (e.g. a category): keep it but order it after
    // dated/numeric points so it never breaks the chronological order.
    points.push({ label: labelOf(raw), value, x: null, ordered: null, isDate: false })
  }

  return points.sort((left, right) => {
    if (left.ordered !== null && right.ordered !== null) return left.ordered - right.ordered
    if (left.ordered !== null) return -1
    if (right.ordered !== null) return 1
    return left.label.localeCompare(right.label)
  })
}

/**
 * Readable axis labels for a sorted line series. Returns every point's own
 * label plus the indexes that should be drawn as ticks (a subset when the
 * series is long). Ticks are spread evenly across the series; data is never
 * dropped - only the drawn tick labels are thinned.
 */
export function lineAxisLabels(points, maxTicks = 6) {
  if (!Array.isArray(points) || points.length === 0) return { labels: [], tickIndexes: [] }
  const labels = points.map((point, index) => {
    if (point?.isDate && Number.isFinite(point?.x)) {
      return point.month ? formatMonthLabel(point.x) : formatDayLabel(point.x)
    }
    return labelOf(point?.label ?? point?.x ?? index)
  })
  const step = Math.max(1, Math.ceil(points.length / Math.max(maxTicks, 1)))
  const tickIndexes = []
  for (let index = 0; index < points.length; index += step) {
    tickIndexes.push(index)
  }
  if (tickIndexes[tickIndexes.length - 1] !== points.length - 1) {
    tickIndexes.push(points.length - 1)
  }
  return { labels, tickIndexes }
}

/**
 * How many tick labels fit in the available width without overlapping.
 * The chatbot panel is narrow (~30-35% of the screen), so callers pass the
 * measured container width and draw at most this many ticks.
 */
export function tickBudget(availableWidth, minSpacing = 60, max = 6, min = 2) {
  const width = Number.isFinite(availableWidth) ? availableWidth : 360
  return Math.max(min, Math.min(max, Math.floor(width / minSpacing)))
}

/**
 * Evenly spread tick indexes covering [0, count), always including the last
 * index. Only the *visible labels* are thinned - data points are never
 * removed, and tooltips still expose every underlying value.
 */
export function thinTickIndexes(count, maxTicks = 6) {
  if (!Number.isFinite(count) || count <= 0) return []
  const step = Math.max(1, Math.ceil(count / Math.max(maxTicks, 1)))
  const indexes = []
  for (let index = 0; index < count; index += step) {
    indexes.push(index)
  }
  if (indexes[indexes.length - 1] !== count - 1) {
    indexes.push(count - 1)
  }
  return indexes
}

/**
 * True when vertical bars would squeeze their category labels: many
 * categories or long category names. The caller then renders horizontal bars
 * (labels down the left side) instead of overlapping vertical tick labels.
 */
export function shouldUseHorizontalBars(data) {
  if (!Array.isArray(data) || data.length === 0) return false
  const longest = data.reduce((max, bar) => Math.max(max, String(bar?.label ?? '').length), 0)
  return data.length > 8 || longest > 14
}

/** Pie slices with their share of the total: { label, value, share }. */
export function buildPieData(chart, rows) {
  if (!chart?.nameColumn || !chart?.valueColumn) return []
  const slices = []
  for (const row of rows) {
    const value = toNumber(row?.[chart.valueColumn])
    if (value === null || value < 0) continue
    slices.push({ label: labelOf(row?.[chart.nameColumn]), value })
  }
  const total = slices.reduce((sum, slice) => sum + slice.value, 0)
  return slices.map((slice) => ({ ...slice, share: total > 0 ? slice.value / total : 0 }))
}

/** Scatter points: { x, y } from two numeric columns. */
export function buildScatterData(chart, rows) {
  if (!chart?.xColumn || !chart?.yColumn) return []
  const points = []
  for (const row of rows) {
    const x = toNumber(row?.[chart.xColumn])
    const y = toNumber(row?.[chart.yColumn])
    if (x === null || y === null) continue
    points.push({ x, y })
  }
  return points
}
