import type { ReactNode } from 'react'
import { Link, Navigate, NavLink, Route, Routes, useLocation } from 'react-router-dom'
import Assistant from './Assistant'
import { useAuth } from './auth'
import AdminPage from './pages/AdminPage'
import BookingsPage from './pages/BookingsPage'
import EventPage from './pages/EventPage'
import EventsPage from './pages/EventsPage'
import LoginPage from './pages/LoginPage'

function RequireAuth({ children, admin = false }: { children: ReactNode; admin?: boolean }) {
  const { user } = useAuth()
  const location = useLocation()
  if (!user) return <Navigate to={`/login?next=${encodeURIComponent(location.pathname)}`} replace />
  if (admin && user.role !== 'ADMIN') return <Navigate to="/" replace />
  return <>{children}</>
}

export default function App() {
  const { user, logout } = useAuth()
  return (
    <>
      <header className="topbar">
        <Link to="/" className="brand">Seat<span>Sure</span></Link>
        <nav>
          <NavLink to="/" end>Events</NavLink>
          {user && <NavLink to="/bookings">My bookings</NavLink>}
          {user?.role === 'ADMIN' && <NavLink to="/admin">Admin</NavLink>}
          {user
            ? <button className="link" onClick={logout}>Sign out</button>
            : <NavLink to="/login">Sign in</NavLink>}
        </nav>
      </header>
      <main>
        <Routes>
          <Route path="/" element={<EventsPage />} />
          <Route path="/events/:id" element={<EventPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/bookings" element={<RequireAuth><BookingsPage /></RequireAuth>} />
          <Route path="/admin" element={<RequireAuth admin><AdminPage /></RequireAuth>} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
      <Assistant />
    </>
  )
}
