import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { authApi, type AuthResponse, type User } from '../api/client'

type AuthValue = { user: User | null; accessToken: string | null; login: (identifier: string, password: string) => Promise<User>; register: (data: Parameters<typeof authApi.register>[0]) => Promise<User>; logout: () => Promise<void> }
const AuthContext = createContext<AuthValue | null>(null)
const STORAGE_KEY = 'logetd.session'

function loadSession(): AuthResponse | null {
  try { return JSON.parse(localStorage.getItem(STORAGE_KEY) || 'null') as AuthResponse | null } catch { return null }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<AuthResponse | null>(loadSession)
  const navigate = useNavigate()
  useEffect(() => {
    const unauthorized = () => { localStorage.removeItem(STORAGE_KEY); setSession(null); navigate('/login', { replace: true }) }
    const forbidden = () => navigate('/access-denied', { replace: true })
    window.addEventListener('logetd:unauthorized', unauthorized)
    window.addEventListener('logetd:forbidden', forbidden)
    return () => { window.removeEventListener('logetd:unauthorized', unauthorized); window.removeEventListener('logetd:forbidden', forbidden) }
  }, [navigate])
  const login = useCallback(async (identifier: string, password: string) => {
    const next = await authApi.login(identifier, password)
    localStorage.setItem(STORAGE_KEY, JSON.stringify(next)); setSession(next); return next.user
  }, [])
  const register = useCallback(async (data: Parameters<typeof authApi.register>[0]) => authApi.register(data), [])
  const logout = useCallback(async () => {
    if (session?.refreshToken) { try { await authApi.logout(session.refreshToken) } catch { /* local logout still completes */ } }
    localStorage.removeItem(STORAGE_KEY); setSession(null); navigate('/login', { replace: true })
  }, [navigate, session])
  const value = useMemo(() => ({ user: session?.user ?? null, accessToken: session?.accessToken ?? null, login, register, logout }), [session, login, register, logout])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const value = useContext(AuthContext)
  if (!value) throw new Error('useAuth must be used inside AuthProvider')
  return value
}
