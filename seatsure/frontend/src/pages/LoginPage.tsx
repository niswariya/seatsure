import { useState } from 'react'
import type { FormEvent } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { ApiError } from '../api'
import { useAuth } from '../auth'

export default function LoginPage() {
  const { login, register } = useAuth()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [fullName, setFullName] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      if (mode === 'login') await login(email, password)
      else await register(email, password, fullName)
      const next = params.get('next')
      navigate(next && next.startsWith('/') ? next : '/')
    } catch (err) {
      const fieldErrors = err instanceof ApiError ? err.problem.errors : undefined
      setError(fieldErrors ? Object.entries(fieldErrors).map(([f, m]) => `${f}: ${m}`).join(' · ') : (err as Error).message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="auth">
      <h1>{mode === 'login' ? 'Sign in' : 'Create account'}</h1>
      <form onSubmit={submit}>
        {mode === 'register' && (
          <label>Full name<input value={fullName} onChange={e => setFullName(e.target.value)} required /></label>
        )}
        <label>Email<input type="email" value={email} onChange={e => setEmail(e.target.value)} required autoComplete="email" /></label>
        <label>Password<input type="password" value={password} onChange={e => setPassword(e.target.value)} required minLength={mode === 'register' ? 8 : 1} autoComplete={mode === 'login' ? 'current-password' : 'new-password'} /></label>
        {error && <p className="error">{error}</p>}
        <button className="btn wide" disabled={busy}>{busy ? 'Please wait…' : mode === 'login' ? 'Sign in' : 'Create account'}</button>
      </form>
      <button className="link" onClick={() => { setMode(mode === 'login' ? 'register' : 'login'); setError(null) }}>
        {mode === 'login' ? 'New here? Create an account' : 'Already have an account? Sign in'}
      </button>
      <p className="muted small">Demo admin: admin@seatsure.dev / Admin@123</p>
    </section>
  )
}
