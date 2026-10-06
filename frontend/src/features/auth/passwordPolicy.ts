export const PASSWORD_HINT = 'Use at least 8 characters, at most 72 bytes, with letters and digits.'

/**
 * The same policy the server enforces (PasswordPolicy.java), checked early to save a round trip: at least 8 characters
 * (UTF-16 units, as Java's length()), at most 72 UTF-8 bytes (BCrypt's limit), one Unicode letter and one Unicode digit.
 * The server stays the authority: its WEAK_PASSWORD answer is shown too. Returns the problem, if any.
 */
export function checkNewPassword(password: string): string | undefined {
  const bytes = new TextEncoder().encode(password).length
  const valid =
    password.length >= 8 && bytes <= 72 && /\p{L}/u.test(password) && /\p{Nd}/u.test(password)
  return valid ? undefined : PASSWORD_HINT
}
