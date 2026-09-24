/**
 * Plain-JS helpers that turn raw records into `columns` + `rows` for ResultTable.
 *
 * They are kept out of the React component so they can be imported by tooling/tests,
 * and they never reference a specific column name: headers are derived from the keys of
 * the records returned by the backend.
 */

/**
 * Display-only convention: /api/students returns camelCase JSON keys (applicantName)
 * while the chatbot tables use the PostgreSQL column names (applicant_name). Converting
 * the keys makes both panels read the same way. Already snake_case keys are unchanged.
 */
export function toSnakeCase(key) {
  return key.replace(/([a-z0-9])([A-Z])/g, '$1_$2').toLowerCase()
}

/** Builds `columns` + `rows` for ResultTable from the raw records. */
export function buildTable(records) {
  if (!Array.isArray(records) || records.length === 0) {
    return { columns: [], rows: [] }
  }

  // Union of the keys of every record, so a field missing from the first row cannot
  // hide a column.
  const keys = [...new Set(records.flatMap((record) => Object.keys(record || {})))]
  const columns = keys.map(toSnakeCase)

  const rows = records.map((record) => {
    const mapped = {}
    keys.forEach((key, index) => {
      mapped[columns[index]] = record?.[key]
    })
    return mapped
  })

  return { columns, rows }
}
