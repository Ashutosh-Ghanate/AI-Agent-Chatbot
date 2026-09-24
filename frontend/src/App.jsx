import { useState } from 'react'
import ChatHeader from './components/ChatHeader'
import MessageList from './components/MessageList'
import ChatInput from './components/ChatInput'
import StudentDataPanel from './components/StudentDataPanel'

const API_URL = 'http://localhost:8081/api/ai/chat'

const DEFAULT_ANSWER = 'Here are the results I found:'

/** Short-term history cap: mirrors app.ai.conversation.max-messages. */
const MAX_HISTORY_MESSAGES = 10
/** Rows kept per assistant turn so huge result sets never enter the prompt. */
const MAX_HISTORY_ROWS = 5

const INITIAL_MESSAGE = {
  id: 0,
  role: 'assistant',
  text: 'Hi! I’m your Student Loan Assistant. Ask me anything about student loan applications.',
}

/**
 * Bounded conversation payload for the backend: the most recent turns with a
 * small row sample each. SQL/columns/rows are context only (never displayed).
 */
function buildConversationPayload(messages) {
  return messages
    .filter((m) => m.id !== INITIAL_MESSAGE.id && (m.role === 'user' || m.role === 'assistant'))
    .slice(-MAX_HISTORY_MESSAGES)
    .map((m) => {
      if (m.role === 'user') {
        return { role: 'user', content: m.text || '' }
      }
      const entry = {
        role: 'assistant',
        content: m.text || '',
        sql: m.sql || undefined,
        columns: Array.isArray(m.columns) ? m.columns : undefined,
        rows: Array.isArray(m.rows) ? m.rows.slice(0, MAX_HISTORY_ROWS) : undefined,
        chart: m.chart && typeof m.chart === 'object' ? m.chart : undefined,
      }
      Object.keys(entry).forEach((key) => entry[key] === undefined && delete entry[key])
      return entry
    })
}

function App() {
  const [messages, setMessages] = useState([INITIAL_MESSAGE])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  async function sendMessage(text) {
    setError(null)
    const userMessage = { id: Date.now(), role: 'user', text }
    const history = buildConversationPayload([...messages, userMessage])
    setMessages((prev) => [...prev, userMessage])
    setLoading(true)

    try {
      const res = await fetch(API_URL, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message: text, conversation: history }),
      })

      const data = await res.json().catch(() => ({}))

      if (!res.ok) {
        throw new Error(data.error || `Request failed with status ${res.status}`)
      }

      // The backend keeps SQL generation/execution internal: it returns a natural
      // language `answer` plus the structured result set (`columns` + `rows`).
      // `chart` is optional metadata naming which of those columns to plot -
      // never separate data - and `data.sql` is debug-only, never shown.
      setMessages((prev) => [
        ...prev,
        {
          id: Date.now() + 1,
          role: 'assistant',
          text: data.answer || DEFAULT_ANSWER,
          sql: typeof data.sql === 'string' ? data.sql : undefined,
          columns: Array.isArray(data.columns) ? data.columns : [],
          rows: Array.isArray(data.rows) ? data.rows : [],
          chart: data.chart && typeof data.chart === 'object' ? data.chart : null,
        },
      ])
    } catch (err) {
      const detail = err instanceof Error && err.message ? ` (${err.message})` : ''
      setError(
        `Sorry, I could not get an answer from the assistant. Please make sure the backend is running on port 8081 and try again.${detail}`
      )
      console.error(err)
    } finally {
      setLoading(false)
    }
  }

  function startNewChat() {
    setMessages([INITIAL_MESSAGE])
    setError(null)
  }

  return (
    <div className="app-shell">
      {/* Left/main panel: the student loan records already stored in PostgreSQL. */}
      <StudentDataPanel />

      {/* Right panel: the existing Student Loan Assistant chatbot, unchanged. */}
      <div className="chat-app">
        <ChatHeader onNewChat={startNewChat} />
        <MessageList messages={messages} loading={loading} />
        {error && <div className="chat-error">{error}</div>}
        <ChatInput onSend={sendMessage} disabled={loading} />
      </div>
    </div>
  )
}

export default App