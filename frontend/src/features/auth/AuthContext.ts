import { createContext } from 'react'
import type { SessionUser } from './token'

export interface AuthContextValue {
  user: SessionUser | null
  token: string | null
  isAdmin: boolean
  /** True after the API rejected a token we had; the UI can invite the user to log in again. */
  sessionExpired: boolean
  login: (username: string, password: string) => Promise<void>
  logout: () => void
  dismissSessionExpired: () => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)
