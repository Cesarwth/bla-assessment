export const PASSWORD_HINT = 'Use at least 8 characters (at most 72), with letters and digits.'

/**
 * The same policy the server enforces (8 to 72 bytes, letters and digits), checked early to save a round trip.
 * The server stays the authority: its WEAK_PASSWORD answer is shown too. Returns the problem, if any.
 */
export function checkNewPassword(password: string): string | undefined {
  const bytes = new TextEncoder().encode(password).length
  const valid = bytes >= 8 && bytes <= 72 && /\p{L}/u.test(password) && /\d/.test(password)
  return valid ? undefined : PASSWORD_HINT
}
