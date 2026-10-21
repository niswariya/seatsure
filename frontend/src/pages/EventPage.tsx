import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { api, ApiError } from '../api'
import { useAuth } from '../auth'
import { formatCountdown, formatDate, formatMoney } from '../format'
import type { Booking, EventDetail, Hold, Seat, SeatsChangedMessage } from '../types'
import { useSeatUpdates } from '../useSeatUpdates'

const MAX_SEATS = 6

export default function EventPage() {
  const eventId = Number(useParams().id)
  const { user } = useAuth()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const preselectDone = useRef(false)

  const [event, setEvent] = useState<EventDetail | null>(null)
  const [seats, setSeats] = useState<Map<number, Seat>>(new Map())
  const [selected, setSelected] = useState<Set<number>>(new Set())
  const [hold, setHold] = useState<Hold | null>(null)
  const [booking, setBooking] = useState<Booking | null>(null)
  const [message, setMessage] = useState<{ kind: 'error' | 'info'; text: string } | null>(null)
  const [busy, setBusy] = useState(false)
  const [now, setNow] = useState(() => Date.now())
  // One key per hold: retries of "Pay" reuse it, so the server never charges twice.
  const idempotencyKey = useRef<string>('')

  const load = useCallback(async () => {
    const detail = await api.event(eventId)
    setEvent(detail)
    setSeats(new Map(detail.seats.map(s => [s.id, s])))
  }, [eventId])

  useEffect(() => {
    load().catch(e => setMessage({ kind: 'error', text: e.message }))
  }, [load])

  // Seats suggested by the AI assistant arrive as ?seats=1,2,3 — preselect the ones still free.
  useEffect(() => {
    if (preselectDone.current || seats.size === 0) return
    preselectDone.current = true
    const wanted = (params.get('seats') ?? '').split(',').map(Number).filter(n => Number.isInteger(n) && n > 0)
    const free = wanted.filter(id => seats.get(id)?.status === 'AVAILABLE').slice(0, MAX_SEATS)
    if (free.length) setSelected(new Set(free))
    if (wanted.length && free.length < wanted.length) {
      setMessage({ kind: 'info', text: 'Some suggested seats were taken in the meantime. Pick replacements on the map.' })
    }
  }, [seats, params])

  // Live updates: apply only if newer than what we have (messages can arrive twice or out of order).
  const onSeatsChanged = useCallback((msg: SeatsChangedMessage) => {
    setSeats(prev => {
      const next = new Map(prev)
      for (const change of msg.seats) {
        const current = next.get(change.id)
        if (current && change.version > current.version) {
          next.set(change.id, { ...current, status: change.status, version: change.version })
        }
      }
      return next
    })
    // Someone else grabbed a seat I had selected (but not yet held)? Drop it.
    setSelected(prev => {
      const taken = msg.seats.filter(c => c.status !== 'AVAILABLE').map(c => c.id)
      if (!taken.some(id => prev.has(id))) return prev
      const next = new Set(prev)
      taken.forEach(id => next.delete(id))
      return next
    })
  }, [])
  const live = useSeatUpdates(eventId, onSeatsChanged)

  // Countdown tick while a hold is active.
  useEffect(() => {
    if (!hold) return
    const t = setInterval(() => setNow(Date.now()), 500)
    return () => clearInterval(t)
  }, [hold])

  const remainingMs = hold ? new Date(hold.expiresAt).getTime() - now : 0
  useEffect(() => {
    if (hold && remainingMs <= 0) {
      setHold(null)
      setMessage({ kind: 'error', text: 'Your hold expired and the seats were released.' })
    }
  }, [hold, remainingMs])

  const myHeldIds = useMemo(() => new Set(hold?.seats.map(s => s.id) ?? []), [hold])

  const rows = useMemo(() => {
    const byRow = new Map<string, Seat[]>()
    for (const s of seats.values()) {
      const list = byRow.get(s.row) ?? []
      list.push(s)
      byRow.set(s.row, list)
    }
    return [...byRow.entries()]
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([row, list]) => [row, list.sort((a, b) => a.number - b.number)] as const)
  }, [seats])

  const selectedSeats = [...selected].map(id => seats.get(id)).filter((s): s is Seat => !!s)
  const selectedTotal = selectedSeats.reduce((sum, s) => sum + s.price, 0)

  function toggle(seat: Seat) {
    if (hold || seat.status !== 'AVAILABLE') return
    setMessage(null)
    setSelected(prev => {
      const next = new Set(prev)
      if (next.has(seat.id)) next.delete(seat.id)
      else if (next.size < MAX_SEATS) next.add(seat.id)
      else setMessage({ kind: 'info', text: `You can pick up to ${MAX_SEATS} seats.` })
      return next
    })
  }

  async function holdSeats() {
    if (!user) {
      navigate(`/login?next=/events/${eventId}`)
      return
    }
    setBusy(true)
    setMessage(null)
    try {
      const h = await api.hold(eventId, [...selected])
      idempotencyKey.current = crypto.randomUUID()
      setHold(h)
      setSelected(new Set())
    } catch (e) {
      setMessage({ kind: 'error', text: (e as Error).message })
      if (e instanceof ApiError && e.status === 409) {
        setSelected(new Set())
        await load()
      }
    } finally {
      setBusy(false)
    }
  }

  async function pay() {
    if (!hold) return
    setBusy(true)
    setMessage(null)
    try {
      const b = await api.confirm(hold.holdId, idempotencyKey.current)
      setBooking(b)
      setHold(null)
    } catch (e) {
      setMessage({ kind: 'error', text: (e as Error).message })
      if (e instanceof ApiError && e.status === 410) {
        setHold(null)
        await load()
      }
    } finally {
      setBusy(false)
    }
  }

  async function release() {
    if (!hold) return
    setBusy(true)
    try {
      await api.releaseHold(hold.holdId)
      setHold(null)
    } catch (e) {
      setMessage({ kind: 'error', text: (e as Error).message })
    } finally {
      setBusy(false)
    }
  }

  if (!event) return <p className="muted">{message?.text ?? 'Loading seat map…'}</p>

  if (booking) {
    return (
      <section className="ticket">
        <p className="eyebrow">Booking confirmed</p>
        <h1>{booking.eventTitle}</h1>
        <p className="muted">{booking.venue} · {formatDate(booking.startsAt)}</p>
        <div className="ticket-ref">{booking.reference}</div>
        <p>Seats <strong>{booking.seats.join(', ')}</strong> · Paid <strong>{formatMoney(booking.totalAmount)}</strong></p>
        <div className="actions">
          <Link to="/bookings" className="btn">My bookings</Link>
          <Link to="/" className="btn ghost">More events</Link>
        </div>
      </section>
    )
  }

  return (
    <section>
      <div className="event-head">
        <div>
          <p className="eyebrow">{formatDate(event.startsAt)}</p>
          <h1>{event.title}</h1>
          <p className="muted">{event.venue}</p>
        </div>
        <span className={`live live-${live}`}>{live === 'live' ? 'Live seat map' : live === 'connecting' ? 'Connecting…' : 'Reconnecting…'}</span>
      </div>

      <div className="layout">
        <div className="hall">
          <div className="stage">STAGE</div>
          <div className="seat-rows">
            {rows.map(([row, list]) => (
              <div className="seat-row" key={row}>
                <span className="row-label">{row}</span>
                {list.map(seat => {
                  const mine = myHeldIds.has(seat.id)
                  const cls = mine ? 'mine' : selected.has(seat.id) ? 'selected' : seat.status.toLowerCase()
                  return (
                    <button
                      key={seat.id}
                      className={`seat ${cls} tier-${seat.tier.toLowerCase()}`}
                      disabled={!!hold || seat.status !== 'AVAILABLE'}
                      onClick={() => toggle(seat)}
                      title={`${seat.row}${seat.number} · ${seat.tier} · ${formatMoney(seat.price)} · ${mine ? 'your hold' : seat.status}`}
                      aria-label={`Seat ${seat.row}${seat.number}, ${seat.tier}, ${seat.status}`}
                    >
                      {seat.number}
                    </button>
                  )
                })}
              </div>
            ))}
          </div>
          <div className="legend">
            <span><i className="seat available tier-premium" /> Premium</span>
            <span><i className="seat available tier-standard" /> Standard</span>
            <span><i className="seat available tier-economy" /> Economy</span>
            <span><i className="seat held" /> On hold</span>
            <span><i className="seat booked" /> Sold</span>
          </div>
        </div>

        <aside className="panel">
          {message && <p className={message.kind}>{message.text}</p>}

          {hold ? (
            <>
              <h3>Your seats are held</h3>
              <div className={`countdown ${remainingMs < 60_000 ? 'urgent' : ''}`}>{formatCountdown(remainingMs)}</div>
              <ul className="line-items">
                {hold.seats.map(s => (
                  <li key={s.id}><span>{s.row}{s.number} · {s.tier.toLowerCase()}</span><span>{formatMoney(s.price)}</span></li>
                ))}
              </ul>
              <div className="total"><span>Total</span><span>{formatMoney(hold.totalAmount)}</span></div>
              <button className="btn wide" onClick={pay} disabled={busy}>{busy ? 'Processing…' : 'Pay and confirm'}</button>
              <button className="btn ghost wide" onClick={release} disabled={busy}>Release seats</button>
            </>
          ) : (
            <>
              <h3>{selectedSeats.length ? `${selectedSeats.length} seat${selectedSeats.length > 1 ? 's' : ''} selected` : 'Select your seats'}</h3>
              {selectedSeats.length === 0 && <p className="muted">Tap available seats on the map. Up to {MAX_SEATS}.</p>}
              <ul className="line-items">
                {selectedSeats.map(s => (
                  <li key={s.id}><span>{s.row}{s.number} · {s.tier.toLowerCase()}</span><span>{formatMoney(s.price)}</span></li>
                ))}
              </ul>
              {selectedSeats.length > 0 && <div className="total"><span>Total</span><span>{formatMoney(selectedTotal)}</span></div>}
              <button className="btn wide" onClick={holdSeats} disabled={busy || selectedSeats.length === 0}>
                {busy ? 'Holding…' : user ? 'Hold seats for 5 min' : 'Sign in to book'}
              </button>
            </>
          )}
        </aside>
      </div>
    </section>
  )
}
