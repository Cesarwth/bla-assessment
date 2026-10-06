import { createContext } from 'react'
import type { Role } from '../api/types'

export interface SessionUser {
  id: number
  username: string
  role: Role
}

export interface AuthContextValue {
  user: SessionUser | null
  token: string | null
  isAdmin: boolean
  /** True after the API rejected a token we had; the UI can invite the user to log in again. */
  sessionExpired: boolean
  login: (username: string, password: string) => Promise<void>
  logout: () => void
  /** Replaces the session with a fresh token of the same trainer (the username is a claim, so renaming needs one). */
  adoptSession: (token: string) => void
  dismissSessionExpired: () => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)
