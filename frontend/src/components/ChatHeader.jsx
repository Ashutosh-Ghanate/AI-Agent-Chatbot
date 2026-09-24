function ChatHeader({ onNewChat }) {
  return (
    <header className="chat-header">
      <span className="chat-header-logo">🎓</span>
      <h1>Student Loan Assistant</h1>
      <button type="button" className="chat-new-button" onClick={onNewChat} title="Start a new conversation">
        New Chat
      </button>
    </header>
  )
}

export default ChatHeader