import type { AppConfig, AuthResponse, Booking, ChatReply, EventCopy, EventDetail, EventSummary, Hold, Problem } from './types'

const TOKEN_KEY = 'seatsure.token'

export const tokenStore = {
  get: (): string | null => {
    try {
      return localStorage.getItem(TOKEN_KEY)
    } catch {
      return null
    }
  },
  set: (token: string | null) => {
    try {
      if (token) localStorage.setItem(TOKEN_KEY, token)
      else localStorage.removeItem(TOKEN_KEY)
    } catch {
      /* storage unavailable: session-only login */
    }
  },
}

export class ApiError extends Error {
  readonly status: number
  readonly problem: Problem
  constructor(problem: Problem) {
    super(problem.detail ?? problem.title ?? `Request failed (${problem.status})`)
    this.status = problem.status
    this.problem = problem
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  if (init.body) headers.set('Content-Type', 'application/json')
  const token = tokenStore.get()
  if (token) headers.set('Authorization', `Bearer ${token}`)

  const res = await fetch(path, { ...init, headers })
  if (res.status === 204) return undefined as T
  const body = await res.json().catch(() => ({}))
  if (!res.ok) {
    if (res.status === 401 && token) {
      // Token expired: sign out locally, and retry public reads anonymously.
      tokenStore.set(null)
      window.dispatchEvent(new Event('seatsure:logout'))
      if (!init.method || init.method === 'GET') return request<T>(path, init)
    }
    throw new ApiError({ status: res.status, ...body })
  }
  return body as T
}

const json = (data: unknown) => JSON.stringify(data)

export const api = {
  register: (email: string, password: string, fullName: string) =>
    request<AuthResponse>('/api/auth/register', { method: 'POST', body: json({ email, password, fullName }) }),
  login: (email: string, password: string) =>
    request<AuthResponse>('/api/auth/login', { method: 'POST', body: json({ email, password }) }),

  events: () => request<EventSummary[]>('/api/events'),
  event: (id: number) => request<EventDetail>(`/api/events/${id}`),
  createEvent: (body: Record<string, unknown>) =>
    request<EventSummary>('/api/admin/events', { method: 'POST', body: json(body) }),

  hold: (eventId: number, seatIds: number[]) =>
    request<Hold>('/api/holds', { method: 'POST', body: json({ eventId, seatIds }) }),
  releaseHold: (holdId: string) => request<void>(`/api/holds/${holdId}`, { method: 'DELETE' }),
  confirm: (holdId: string, idempotencyKey: string) =>
    request<Booking>(`/api/holds/${holdId}/confirm`, {
      method: 'POST',
      headers: { 'Idempotency-Key': idempotencyKey },
    }),

  myBookings: () => request<Booking[]>('/api/bookings/me'),

  config: () => request<AppConfig>('/api/config'),
  aiChat: (conversationId: string, message: string) =>
    request<ChatReply>('/api/ai/chat', { method: 'POST', body: json({ conversationId, message }) }),
  draftEventCopy: (body: { title: string; venue: string; startsAt?: string; notes?: string }) =>
    request<EventCopy>('/api/admin/ai/event-copy', { method: 'POST', body: json(body) }),
}
