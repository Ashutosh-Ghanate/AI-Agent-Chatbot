/**
 * Dynamic table for structured backend results.
 *
 * The backend executes the SQL it generated internally and returns `columns` + `rows`,
 * so the headers and cells are built entirely from the response - no column name such
 * as `applicant_name` is hardcoded here and any future query works unchanged.
 *
 * Charts are drawn separately by ResultChart from an optional backend `chart`
 * config that only names which of these columns to plot (never separate data).
 */

const MAX_ROWS = 200

function formatCell(value) {
  if (value === null || value === undefined) return '—'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

function ResultTable({ columns, rows }) {
  const safeColumns = Array.isArray(columns) ? columns : []
  const safeRows = Array.isArray(rows) ? rows : []

  if (safeColumns.length === 0) {
    return null
  }

  const visibleRows = safeRows.slice(0, MAX_ROWS)

  return (
    <div className="result-table-wrap">
      <table className="result-table">
        <thead>
          <tr>
            {safeColumns.map((column, index) => (
              <th key={`${column}-${index}`} scope="col">
                {column}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {visibleRows.length === 0 ? (
            <tr>
              <td className="result-empty" colSpan={safeColumns.length}>
                No rows returned.
              </td>
            </tr>
          ) : (
            visibleRows.map((row, rowIndex) => (
              <tr key={rowIndex}>
                {safeColumns.map((column, columnIndex) => (
                  <td key={`${column}-${columnIndex}`}>{formatCell(row?.[column])}</td>
                ))}
              </tr>
            ))
          )}
        </tbody>
      </table>
      {safeRows.length > MAX_ROWS && (
        <p className="result-note">
          Showing the first {MAX_ROWS} of {safeRows.length} rows.
        </p>
      )}
    </div>
  )
}

export default ResultTable
