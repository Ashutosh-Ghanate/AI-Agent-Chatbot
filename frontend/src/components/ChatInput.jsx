import { useState } from 'react'

function ChatInput({ onSend, disabled }) {
  const [text, setText] = useState('')

  function handleSubmit(e) {
    e.preventDefault()
    const value = text.trim()
    if (!value || disabled) return
    onSend(value)
    setText('')
  }

  return (
    <form className="chat-input" onSubmit={handleSubmit}>
      <input
        type="text"
        value={text}
        placeholder="Ask me anything about student loans…"
        onChange={(e) => setText(e.target.value)}
        disabled={disabled}
        autoFocus
      />
      <button type="submit" disabled={disabled || !text.trim()}>
        {disabled ? 'Sending…' : 'Send'}
      </button>
    </form>
  )
}

export default ChatInput