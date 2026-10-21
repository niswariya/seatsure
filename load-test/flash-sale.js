// k6 load test: 200 virtual users rush the same 10 seats at once.
// Run:  k6 run load-test/flash-sale.js
// Expect: exactly 10 holds succeed (201), every other attempt gets 409, zero 5xx.
import http from 'k6/http'
import { check } from 'k6'
import { Counter } from 'k6/metrics'

const BASE = __ENV.BASE_URL || 'http://localhost:8080'
const won = new Counter('holds_won')
const lost = new Counter('holds_lost')

export const options = {
  scenarios: {
    rush: { executor: 'per-vu-iterations', vus: 200, iterations: 1, maxDuration: '1m' },
  },
  thresholds: { 'checks{type:no5xx}': ['rate==1'] },  // 409s are expected; 5xx are not
}

export function setup() {
  const events = http.get(`${BASE}/api/events`).json()
  const detail = http.get(`${BASE}/api/events/${events[0].id}`).json()
  const free = detail.seats.filter(s => s.status === 'AVAILABLE').slice(0, 10).map(s => s.id)
  return { eventId: detail.id, seats: free }
}

export default function (data) {
  const email = `vu${__VU}-${Date.now()}@load.test`
  const auth = http.post(`${BASE}/api/auth/register`,
    JSON.stringify({ email, password: 'Password@1', fullName: `VU ${__VU}` }),
    { headers: { 'Content-Type': 'application/json' } }).json()

  const seat = data.seats[__VU % data.seats.length]
  const res = http.post(`${BASE}/api/holds`, JSON.stringify({ eventId: data.eventId, seatIds: [seat] }), {
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${auth.token}` },
  })
  check(res, { 'no 5xx': r => r.status < 500 }, { type: 'no5xx' })
  if (res.status === 201) won.add(1)
  else if (res.status === 409) lost.add(1)
}
