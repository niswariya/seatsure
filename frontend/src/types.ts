export type Role = 'USER' | 'ADMIN'
export type SeatStatus = 'AVAILABLE' | 'HELD' | 'BOOKED'
export type SeatTier = 'PREMIUM' | 'STANDARD' | 'ECONOMY'

export interface User {
  id: number
  email: string
  fullName: string
  role: Role
}

export interface AuthResponse {
  token: string
  expiresInSeconds: number
  user: User
}

export interface EventSummary {
  id: number
  title: string
  venue: string
  startsAt: string
  totalSeats: number
  availableSeats: number
  fromPrice: number
}

export interface Seat {
  id: number
  row: string
  number: number
  tier: SeatTier
  price: number
  status: SeatStatus
  version: number
}

export interface EventDetail {
  id: number
  title: string
  venue: string
  startsAt: string
  description: string | null
  seatRows: number
  seatsPerRow: number
  seats: Seat[]
}

export interface Hold {
  holdId: string
  eventId: number
  expiresAt: string
  seats: Seat[]
  totalAmount: number
}

export interface Booking {
  reference: string
  eventId: number
  eventTitle: string
  venue: string
  startsAt: string
  seats: string[]
  totalAmount: number
  status: string
  bookedAt: string
}

/** Pushed over WebSocket by the backend outbox publisher. */
export interface SeatsChangedMessage {
  eventId: number
  seats: { id: number; status: SeatStatus; version: number }[]
}

/** RFC 9457 problem details returned for every API error. */
export interface Problem {
  status: number
  title?: string
  detail?: string
  errors?: Record<string, string>
}

export interface AppConfig {
  aiEnabled: boolean
  holdSeconds: number
}

/** One seat suggestion produced by the backend's findSeats tool. */
export interface SeatOption {
  description: string
  seatIds: number[]
  seatLabels: string[]
  tier: SeatTier
  totalPrice: number
}

export interface ChatReply {
  reply: string
  eventId: number | null
  suggestions: SeatOption[]
}

export interface EventCopy {
  description: string
  highlights: string[]
}
