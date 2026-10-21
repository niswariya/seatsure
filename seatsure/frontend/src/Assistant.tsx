import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api } from './api'
import { useAuth } from './auth'
import { formatMoney } from './format'
import type { SeatOption } from './types'
import { useAppConfig } from './useAppConfig'

interface Message {
  role: 'user' | 'assistant'
  text: string
  eventId?: number | null
  suggestions?: SeatOption[]
  error?: boolean
}

const STARTERS = [
  'What events are coming up?',
  '2 seats together for Indie Night under ₹3000',
  'What is the refund policy?',
]

function conversationId(): string {
  // One conversation per browser tab; the server scopes memory by user + this id.
  try {
    let id = sessionStorage.getItem('seatsure.chat')
    if (!id) {
      id = crypto.randomUUID()
      sessionStorage.setItem('seatsure.chat', id)
    }
    return id
  } catch {
    return crypto.randomUUID()
  }
}

export default function Assistant() {
  const config = useAppConfig()
  const { user } = useAuth()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const [messages, setMessages] = useState<Message[]>([])
  const [input, setInput] = useState('')
  const [busy, setBusy] = useState(false)
  const convId = useRef(conversationId())
  const bottom = useRef<HTMLDivElement>(null)

  useEffect(() => {
    bottom.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, busy])

  if (!config?.aiEnabled) return null

  async function send(text: string) {
    const message = text.trim()
    if (!message || busy) return
    setInput('')
    setMessages(m => [...m, { role: 'user', text: message }])
    setBusy(true)
    try {
      const res = await api.aiChat(convId.current, message)
      setMessages(m => [...m, { role: 'assistant', text: res.reply, eventId: res.eventId, suggestions: res.suggestions }])
    } catch (e) {
      setMessages(m => [...m, { role: 'assistant', text: (e as Error).message, error: true }])
    } finally {
      setBusy(false)
    }
  }

  function submit(e: FormEvent) {
    e.preventDefault()
    void send(input)
  }

  function pick(eventId: number, option: SeatOption) {
    setOpen(false)
    navigate(`/events/${eventId}?seats=${option.seatIds.join(',')}`)
  }

  return (
    <>
      <button className="ai-fab" onClick={() => setOpen(o => !o)} aria-expanded={open}>
        {open ? 'Close' : '✦ Ask SeatSure AI'}
      </button>

      {open && (
        <div className="ai-drawer" role="dialog" aria-label="SeatSure AI assistant">
          <header>
            <strong>SeatSure assistant</strong>
            <span className="muted small">Powered by Claude on Amazon Bedrock</span>
          </header>

          {!user ? (
            <div className="ai-empty">
              <p>Sign in to chat with the assistant. It can find the best seats for you and answer policy questions.</p>
              <Link to="/login" className="btn" onClick={() => setOpen(false)}>Sign in</Link>
            </div>
          ) : (
            <>
              <div className="ai-messages">
                {messages.length === 0 && (
                  <div className="ai-empty">
                    <p className="muted">Ask about events, seats or policies. Try:</p>
                    {STARTERS.map(s => (
                      <button key={s} className="chip" onClick={() => void send(s)}>{s}</button>
                    ))}
                  </div>
                )}
                {messages.map((m, i) => (
                  <div key={i} className={`bubble ${m.role} ${m.error ? 'error' : ''}`}>
                    <p>{m.text}</p>
                    {m.suggestions && m.suggestions.length > 0 && m.eventId != null && (
                      <div className="suggestions">
                        {m.suggestions.map(o => (
                          <button key={o.seatIds.join('-')} className="suggestion" onClick={() => pick(m.eventId!, o)}>
                            <span>{o.description}</span>
                            <span className="muted small">{o.tier.toLowerCase()} · {formatMoney(o.totalPrice)}</span>
                          </button>
                        ))}
                      </div>
                    )}
                  </div>
                ))}
                {busy && <div className="bubble assistant typing"><span /><span /><span /></div>}
                <div ref={bottom} />
              </div>
              <form className="ai-input" onSubmit={submit}>
                <input
                  value={input}
                  onChange={e => setInput(e.target.value)}
                  placeholder="e.g. 3 premium seats together"
                  maxLength={500}
                  aria-label="Message"
                />
                <button className="btn" disabled={busy || !input.trim()}>Send</button>
              </form>
              <p className="ai-note">AI suggestions can be wrong. You always choose and pay for seats yourself.</p>
            </>
          )}
        </div>
      )}
    </>
  )
}
