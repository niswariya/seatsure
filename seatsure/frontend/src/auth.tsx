import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { api, tokenStore } from './api'
import type { User } from './types'

const USER_KEY = 'seatsure.user'

interface AuthState {
  user: User | null
  login: (email: string, password: string) => Promise<void>
  register: (email: string, password: string, fullName: string) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthState | null>(null)

function readUser(): User | null {
  try {
    const raw = localStorage.getItem(USER_KEY)
    return raw && tokenStore.get() ? (JSON.parse(raw) as User) : null
  } catch {
    return null
  }
}

function saveUser(user: User | null) {
  try {
    if (user) localStorage.setItem(USER_KEY, JSON.stringify(user))
    else localStorage.removeItem(USER_KEY)
  } catch {
    /* ignore */
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(readUser)

  const accept = useCallback((token: string, u: User) => {
    tokenStore.set(token)
    saveUser(u)
    setUser(u)
  }, [])

  const login = useCallback(async (email: string, password: string) => {
    const res = await api.login(email, password)
    accept(res.token, res.user)
  }, [accept])

  const register = useCallback(async (email: string, password: string, fullName: string) => {
    const res = await api.register(email, password, fullName)
    accept(res.token, res.user)
  }, [accept])

  const logout = useCallback(() => {
    tokenStore.set(null)
    saveUser(null)
    setUser(null)
  }, [])

  // The API layer fires this when the server rejects an expired token.
  useEffect(() => {
    const onLogout = () => logout()
    window.addEventListener('seatsure:logout', onLogout)
    return () => window.removeEventListener('seatsure:logout', onLogout)
  }, [logout])

  const value = useMemo(() => ({ user, login, register, logout }), [user, login, register, logout])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider')
  return ctx
}
