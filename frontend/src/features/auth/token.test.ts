import { describe, expect, it } from 'vitest'
import { makeToken } from '../../test/utils'
import { decodeToken, isExpired } from './token'

describe('decodeToken', () => {
  it('reads the user and the expiry from the claims the API puts in every token', () => {
    const token = makeToken({ uid: 7, sub: 'misty', role: 'ADMIN', exp: 2_000_000_000 })

    expect(decodeToken(token)).toEqual({
      user: { id: 7, username: 'misty', role: 'ADMIN' },
      expiresAt: 2_000_000_000_000,
    })
  })

  it.each([
    ['not three parts', 'abc.def'],
    ['payload that is not base64', 'a.@@@.c'],
    ['payload that is not JSON', `a.${btoa('not json')}.c`],
    ['an empty string', ''],
  ])('rejects a token with %s', (_name, token) => {
    expect(decodeToken(token)).toBeNull()
  })

  it('rejects tokens that do not identify a user', () => {
    expect(decodeToken(makeToken({ role: 'GOD' }))).toBeNull()
    const noUid = [
      btoa('{}'),
      btoa(JSON.stringify({ sub: 'ash', role: 'USER', exp: 9e9 })),
      'x',
    ].join('.')
    expect(decodeToken(noUid)).toBeNull()
  })

  it('understands non-ASCII usernames', () => {
    expect(decodeToken(makeToken({ sub: 'pokémon' }))?.user.username).toBe('pokémon')
  })
})

describe('isExpired', () => {
  it('compares the expiry with the current time', () => {
    const tokenInfo = decodeToken(makeToken({ exp: 1_000 }))!

    expect(isExpired(tokenInfo, 999_999)).toBe(false)
    expect(isExpired(tokenInfo, 1_000_000)).toBe(true)
    expect(isExpired(tokenInfo, 2_000_000)).toBe(true)
  })
})
