import { useState } from 'react'
import type { FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../api'
import { useAppConfig } from '../useAppConfig'

export default function AdminPage() {
  const navigate = useNavigate()
  const [form, setForm] = useState({
    title: '', venue: '', startsAt: '', description: '',
    seatRows: 8, seatsPerRow: 12, premiumPrice: 3000, standardPrice: 1800, economyPrice: 900,
  })
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const config = useAppConfig()
  const [drafting, setDrafting] = useState(false)
  const [highlights, setHighlights] = useState<string[]>([])

  async function draftWithAi() {
    if (!form.title || !form.venue) {
      setError('Enter a title and venue first, then let AI draft the description.')
      return
    }
    setDrafting(true)
    setError(null)
    try {
      const copy = await api.draftEventCopy({
        title: form.title,
        venue: form.venue,
        startsAt: form.startsAt ? new Date(form.startsAt).toISOString() : undefined,
        notes: form.description || undefined,
      })
      setForm(f => ({ ...f, description: copy.description }))
      setHighlights(copy.highlights)
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setDrafting(false)
    }
  }

  const set = (key: keyof typeof form) => (e: { target: { value: string } }) =>
    setForm(f => ({ ...f, [key]: typeof f[key] === 'number' ? Number(e.target.value) : e.target.value }))

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      const created = await api.createEvent({ ...form, startsAt: new Date(form.startsAt).toISOString() })
      navigate(`/events/${created.id}`)
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="auth wide-form">
      <h1>Create event</h1>
      <form onSubmit={submit}>
        <label>Title<input value={form.title} onChange={set('title')} required maxLength={150} /></label>
        <label>Venue<input value={form.venue} onChange={set('venue')} required maxLength={150} /></label>
        <label>Starts at<input type="datetime-local" value={form.startsAt} onChange={set('startsAt')} required /></label>
        <label>
          <span className="label-row">
            Description
            {config?.aiEnabled && (
              <button type="button" className="chip" onClick={draftWithAi} disabled={drafting}>
                {drafting ? 'Drafting…' : '✦ Draft with AI'}
              </button>
            )}
          </span>
          <textarea value={form.description} onChange={set('description')} maxLength={1000} rows={4}
            placeholder={config?.aiEnabled ? 'Type a few notes, then press Draft with AI' : undefined} />
        </label>
        {highlights.length > 0 && (
          <p className="muted small">AI highlights: {highlights.join(' · ')}</p>
        )}
        <div className="grid-2">
          <label>Rows (max 26)<input type="number" min={1} max={26} value={form.seatRows} onChange={set('seatRows')} /></label>
          <label>Seats per row (max 40)<input type="number" min={1} max={40} value={form.seatsPerRow} onChange={set('seatsPerRow')} /></label>
        </div>
        <div className="grid-3">
          <label>Premium ₹<input type="number" min={1} value={form.premiumPrice} onChange={set('premiumPrice')} /></label>
          <label>Standard ₹<input type="number" min={1} value={form.standardPrice} onChange={set('standardPrice')} /></label>
          <label>Economy ₹<input type="number" min={1} value={form.economyPrice} onChange={set('economyPrice')} /></label>
        </div>
        {error && <p className="error">{error}</p>}
        <button className="btn wide" disabled={busy}>{busy ? 'Creating…' : 'Create event and seat map'}</button>
      </form>
    </section>
  )
}
