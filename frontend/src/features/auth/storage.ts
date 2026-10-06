/** The session lives in sessionStorage: it survives a reload but ends with the tab (no persistent token). */
export const SESSION_KEY = 'bla.session.token'

// Storage can be unavailable (privacy modes, quota); the app then simply works without a persisted session.
export function readToken(): string | null {
  try {
    return sessionStorage.getItem(SESSION_KEY)
  } catch {
    return null
  }
}

export function storeToken(token: string): void {
  try {
    sessionStorage.setItem(SESSION_KEY, token)
  } catch {
    // keep going without persistence
  }
}

export function clearToken(): void {
  try {
    sessionStorage.removeItem(SESSION_KEY)
  } catch {
    // nothing to clean
  }
}
