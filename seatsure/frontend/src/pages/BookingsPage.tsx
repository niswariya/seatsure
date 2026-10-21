import { useEffect, useState } from 'react'
import { api } from '../api'
import { formatDate, formatMoney } from '../format'
import type { Booking } from '../types'

export default function BookingsPage() {
  const [bookings, setBookings] = useState<Booking[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api.myBookings().then(setBookings).catch(e => setError(e.message))
  }, [])

  if (error) return <p className="error">{error}</p>
  if (!bookings) return <p className="muted">Loading…</p>

  return (
    <section>
      <h1>My bookings</h1>
      {bookings.length === 0 && <p className="muted">No bookings yet.</p>}
      <div className="booking-list">
        {bookings.map(b => (
          <article key={b.reference} className="booking">
            <div>
              <h3>{b.eventTitle}</h3>
              <p className="muted">{b.venue} · {formatDate(b.startsAt)}</p>
              <p>Seats {b.seats.join(', ')}</p>
            </div>
            <div className="booking-side">
              <code>{b.reference}</code>
              <strong>{formatMoney(b.totalAmount)}</strong>
            </div>
          </article>
        ))}
      </div>
    </section>
  )
}
