import { useEffect, useState } from 'react'
import ResultTable from './ResultTable'
import { buildTable } from '../utils/studentTable'

/**
 * Left/main panel: the student loan records that already exist in PostgreSQL.
 *
 * The data comes from the existing GET /api/students endpoint - no new backend code.
 * Column names are never hardcoded: the headers are derived from the keys of the
 * records themselves (see ../utils/studentTable), exactly like the chat tables are
 * built from the `columns` returned by /api/ai/chat.
 */

const API_URL = 'http://localhost:8081/api/students'

function StudentDataPanel() {
  const [columns, setColumns] = useState([])
  const [rows, setRows] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    let ignore = false

    async function loadStudents() {
      try {
        const res = await fetch(API_URL)
        if (!res.ok) {
          throw new Error(`Request failed with status ${res.status}`)
        }

        const data = await res.json()
        if (ignore) return

        const table = buildTable(data)
        setColumns(table.columns)
        setRows(table.rows)
      } catch (err) {
        if (ignore) return
        const detail = err instanceof Error && err.message ? ` (${err.message})` : ''
        setError(
          `Could not load the student loan records. Please make sure the backend is running on port 8081 and try again.${detail}`
        )
        console.error(err)
      } finally {
        if (!ignore) setLoading(false)
      }
    }

    loadStudents()

    return () => {
      ignore = true
    }
  }, [])

  return (
    <section className="data-panel" id="main">
      <header className="data-panel-header">
        <h1>Student Loan Applications</h1>
        {!loading && !error && (
          <span className="data-panel-count">
            {rows.length} {rows.length === 1 ? 'record' : 'records'}
          </span>
        )}
      </header>

      <div className="data-panel-body">
        {loading && <p className="data-panel-status">Loading student loan records…</p>}
        {error && <p className="data-panel-status data-panel-error">{error}</p>}
        {!loading && !error && columns.length === 0 && (
          <p className="data-panel-status">No student loan records were returned.</p>
        )}
        {!loading && !error && <ResultTable columns={columns} rows={rows} />}
      </div>
    </section>
  )
}

export default StudentDataPanel
