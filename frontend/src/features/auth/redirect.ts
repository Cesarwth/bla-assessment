/**
 * Where to go after logging in: the page the user came from, but only if it is a path inside this app.
 * Anything else (another site, a protocol-relative "//host") would turn the login into an open redirect.
 */
export function safeReturnPath(state: unknown): string {
  const from = (state as { from?: unknown } | null)?.from
  return typeof from === 'string' && from.startsWith('/') && !from.startsWith('//') ? from : '/'
}

/** Router state that makes the login page say the password was just reset. */
export const PASSWORD_UPDATED_STATE = { notice: 'password-updated' } as const

export function hasPasswordUpdatedNotice(state: unknown): boolean {
  return (state as { notice?: unknown } | null)?.notice === PASSWORD_UPDATED_STATE.notice
}
