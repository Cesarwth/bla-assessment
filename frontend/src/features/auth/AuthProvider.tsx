import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useLayoutEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { api, configureClient, resetClient } from '../../shared/api/client'
import type { AuthResponse } from '../../shared/api/types'
import { AuthContext, type AuthContextValue } from './AuthContext'
import { clearToken, readToken, storeToken } from './storage'
import { decodeToken, isExpired, type SessionUser } from './token'

interface Session {
  token: string
  user: SessionUser
}

function restoreSession(): Session | null {
  const token = readToken()
  if (!token) return null
  const info = decodeToken(token)
  if (!info || isExpired(info)) {
    clearToken()
    return null
  }
  return { token, user: info.user }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(restoreSession)
  const [sessionExpired, setSessionExpired] = useState(false)
  // The API client reads the token from here so it never works with a stale closure.
  const tokenRef = useRef<string | null>(session?.token ?? null)
  const queryClient = useQueryClient()

  const clearSession = useCallback(() => {
    tokenRef.current = null
    clearToken()
    setSession(null)
  }, [])

  // Cached answers belong to an identity (the collection is private to each trainer): drop what is not on screen
  // and refetch what is, so nobody sees the previous trainer's data.
  const logout = useCallback(() => {
    clearSession()
    queryClient.removeQueries({ type: 'inactive' })
    void queryClient.invalidateQueries()
  }, [clearSession, queryClient])

  // A layout effect runs before the passive effects of the children, i.e. before the first request is sent.
  useLayoutEffect(() => {
    configureClient({
      getToken: () => tokenRef.current,
      onUnauthorized: () => {
        clearSession()
        setSessionExpired(true)
        // The rejected request is still in flight when this runs, and invalidate would just hand back that same
        // request (a query with no data is not restarted). Reset cancels it and asks again, now without the token.
        void queryClient.resetQueries()
      },
    })
    return resetClient
  }, [clearSession, queryClient])

  const login = useCallback(async (username: string, password: string) => {
    const { token } = await api.post<AuthResponse>('/auth/login', { username, password })
    const info = decodeToken(token)
    if (!info) throw new Error('The server returned a session this app cannot read.')
    tokenRef.current = token
    storeToken(token)
    setSession({ token, user: info.user })
    setSessionExpired(false)
    queryClient.removeQueries({ type: 'inactive' })
    void queryClient.invalidateQueries()
  }, [queryClient])

  const dismissSessionExpired = useCallback(() => setSessionExpired(false), [])

  const value = useMemo<AuthContextValue>(
    () => ({
      user: session?.user ?? null,
      token: session?.token ?? null,
      isAdmin: session?.user.role === 'ADMIN',
      sessionExpired,
      login,
      logout,
      dismissSessionExpired,
    }),
    [session, sessionExpired, login, logout, dismissSessionExpired],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
