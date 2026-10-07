import type { Role } from '../../shared/api/types'
import type { SessionUser } from '../../shared/session/AuthContext'

export interface TokenInfo {
  user: SessionUser
  /** Milliseconds since the epoch. */
  expiresAt: number
}

function decodeBase64Url(part: string): string {
  const binary = atob(part.replaceAll('-', '+').replaceAll('_', '/'))
  return new TextDecoder().decode(Uint8Array.from(binary, (char) => char.charCodeAt(0)))
}

function isRole(value: unknown): value is Role {
  return value === 'USER' || value === 'ADMIN'
}

/**
 * Reads who the token says we are (claims sub, uid, role, exp). The signature is not checked here: the server
 * is the authority and rejects bad tokens; this only drives what the UI shows.
 */
export function decodeToken(token: string): TokenInfo | null {
  const parts = token.split('.')
  if (parts.length !== 3) return null
  try {
    const claims: unknown = JSON.parse(decodeBase64Url(parts[1] ?? ''))
    if (typeof claims !== 'object' || claims === null) return null
    const {
      uid: userId,
      sub: username,
      role,
      exp: expiresAtSeconds,
    } = claims as Record<string, unknown>
    if (
      !Number.isInteger(userId) ||
      typeof username !== 'string' ||
      username === '' ||
      !isRole(role) ||
      typeof expiresAtSeconds !== 'number'
    ) {
      return null
    }
    return { user: { id: userId as number, username, role }, expiresAt: expiresAtSeconds * 1000 }
  } catch {
    return null
  }
}

export function isExpired(tokenInfo: TokenInfo, now: number = Date.now()): boolean {
  return tokenInfo.expiresAt <= now
}
