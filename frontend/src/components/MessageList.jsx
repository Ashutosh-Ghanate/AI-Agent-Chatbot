import { useEffect, useRef } from 'react'
import ResultChart from './ResultChart'
import ResultTable from './ResultTable'

function Message({ role, text, columns, rows, chart }) {
  const hasTable = role !== 'user' && Array.isArray(columns) && columns.length > 0

  return (
    <div className={`message ${role === 'user' ? 'message-user' : 'message-ai'}`}>
      <div className="message-body">
        <div className={`bubble ${role === 'user' ? 'bubble-user' : 'bubble-ai'}`}>{text}</div>
        {hasTable && <ResultChart chart={chart} columns={columns} rows={rows} />}
        {hasTable && <ResultTable columns={columns} rows={rows} />}
      </div>
    </div>
  )
}

function MessageList({ messages, loading }) {
  const bottomRef = useRef(null)

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, loading])

  return (
    <div className="chat-messages">
      {messages.map((m) => (
        <Message key={m.id} role={m.role} text={m.text} columns={m.columns} rows={m.rows} chart={m.chart} />
      ))}
      {loading && (
        <div className="message message-ai">
          <div className="message-body">
            <div className="bubble bubble-ai typing">Assistant is typing…</div>
          </div>
        </div>
      )}
      <div ref={bottomRef} />
    </div>
  )
}

export default MessageList