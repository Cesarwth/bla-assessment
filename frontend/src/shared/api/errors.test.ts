import { describe, expect, it } from 'vitest'
import { ApiError, describeError } from './errors'

describe('describeError', () => {
  it('explains catalog outages in plain language', () => {
    expect(describeError(new ApiError(502, 'CATALOG_UNAVAILABLE', 'The Pokemon catalog is temporarily unavailable'))).toMatch(
      /not answering right now/,
    )
    expect(describeError(new ApiError(504, 'CATALOG_TIMEOUT', 'The Pokemon catalog took too long to answer'))).toMatch(
      /not answering right now/,
    )
  })

  it('keeps the message of other API errors, which the backend already writes for people', () => {
    expect(describeError(new ApiError(409, 'USERNAME_TAKEN', 'Username is already taken'))).toBe('Username is already taken')
  })

  it('does not leak the details of unexpected errors', () => {
    expect(describeError(new TypeError('x is undefined'))).toBe('Something unexpected happened. Please try again.')
    expect(describeError('boom')).toBe('Something unexpected happened. Please try again.')
  })

  it('handles generic HTTP failures without an API body', () => {
    expect(describeError(new ApiError(500, 'HTTP_500', 'Something went wrong (HTTP 500)'))).toBe('Something went wrong (HTTP 500)')
  })
})
