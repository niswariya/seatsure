import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api'
import { formatDate, formatMoney } from '../format'
import type { EventSummary } from '../types'

export default function EventsPage() {
  const [events, setEvents] = useState<EventSummary[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api.events().then(setEvents).catch(e => setError(e.message))
  }, [])

  if (error) return <p className="error">{error}</p>
  if (!events) return <p className="muted">Loading events…</p>

  return (
    <section>
      <h1>Upcoming events</h1>
      <p className="muted">Pick seats, hold them for 5 minutes, pay once. No double bookings, ever.</p>
      {events.length === 0 && <p className="muted">No upcoming events yet.</p>}
      <div className="event-grid">
        {events.map(e => {
          const pct = e.totalSeats ? Math.round((e.availableSeats / e.totalSeats) * 100) : 0
          return (
            <Link key={e.id} to={`/events/${e.id}`} className="event-card">
              <span className="event-date">{formatDate(e.startsAt)}</span>
              <h2>{e.title}</h2>
              <p className="muted">{e.venue}</p>
              <div className="availability">
                <div className="bar"><span style={{ width: `${pct}%` }} /></div>
                <span>{e.availableSeats} of {e.totalSeats} seats left</span>
              </div>
              <span className="from">from {formatMoney(e.fromPrice)}</span>
            </Link>
          )
        })}
      </div>
    </section>
  )
}
