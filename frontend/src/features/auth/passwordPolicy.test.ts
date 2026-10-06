import { describe, expect, it } from 'vitest'
import { PASSWORD_HINT, checkNewPassword } from './passwordPolicy'

describe('checkNewPassword', () => {
  it('says what the policy is in the hint', () => {
    expect(PASSWORD_HINT).toBe(
      'Use at least 8 characters, at most 72 bytes, with letters and digits.',
    )
  })

  it('accepts letters and digits within 8 characters and 72 bytes', () => {
    expect(checkNewPassword('Pikachu123')).toBeUndefined()
    expect(checkNewPassword('a1'.repeat(36))).toBeUndefined()
    // The minimum counts characters, not bytes: eight 2-byte letters plus a digit is fine.
    expect(checkNewPassword('éééééééé1')).toBeUndefined()
    // Any Unicode letter and digit count, as in the server's Character.isLetter / isDigit.
    expect(checkNewPassword('パスワードです٣')).toBeUndefined()
  })

  it.each([
    ['too short', 'Pika123'],
    ['short in characters even though it has 8 or more bytes', 'éé1abc'],
    ['without digits', 'Pikachuuu'],
    ['without letters', '12345678'],
    ['longer than 72 bytes', 'a1'.repeat(37)],
    ['longer than 72 bytes in UTF-8 even if it has fewer characters', 'é1'.repeat(25)],
  ])('rejects a password %s with the hint', (_name, password) => {
    expect(checkNewPassword(password)).toBe(PASSWORD_HINT)
  })
})
