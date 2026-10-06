import { describe, expect, it } from 'vitest'
import { PASSWORD_HINT, checkNewPassword } from './passwordPolicy'

describe('checkNewPassword', () => {
  it('accepts letters and digits within 8 to 72 bytes', () => {
    expect(checkNewPassword('Pikachu123')).toBeUndefined()
    expect(checkNewPassword('a1'.repeat(36))).toBeUndefined()
  })

  it.each([
    ['too short', 'Pika123'],
    ['without digits', 'Pikachuuu'],
    ['without letters', '12345678'],
    ['longer than 72 bytes', 'a1'.repeat(37)],
    ['longer than 72 bytes in UTF-8 even if it has fewer characters', 'é1'.repeat(25)],
  ])('rejects a password %s with the hint', (_name, password) => {
    expect(checkNewPassword(password)).toBe(PASSWORD_HINT)
  })
})
