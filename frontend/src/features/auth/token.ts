import type { Role } from '../../shared/api/types'

export interface SessionUser {
  id: number
  username: string
  role: Role
}

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
    const { uid, sub, role, exp } = claims as Record<string, unknown>
    if (!Number.isInteger(uid) || typeof sub !== 'string' || sub === '' || !isRole(role) || typeof exp !== 'number') {
      return null
    }
    return { user: { id: uid as number, username: sub, role }, expiresAt: exp * 1000 }
  } catch {
    return null
  }
}

export function isExpired(info: TokenInfo, now: number = Date.now()): boolean {
  return info.expiresAt <= now
}
