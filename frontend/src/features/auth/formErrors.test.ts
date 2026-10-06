import { describe, expect, it } from 'vitest'
import { ApiError } from '../../shared/api/errors'
import { toFormErrors } from './formErrors'

describe('toFormErrors', () => {
  it('maps API field errors to their fields, keeping the first message of each', () => {
    const error = new ApiError(400, 'VALIDATION_FAILED', 'Request validation failed', [
      { field: 'email', message: 'must be a well-formed email address' },
      { field: 'email', message: 'second message' },
      { field: 'username', message: 'too short' },
    ])

    expect(toFormErrors(error)).toEqual({
      fields: { email: 'must be a well-formed email address', username: 'too short' },
    })
  })

  it.each([
    ['USERNAME_TAKEN', 'username'],
    ['EMAIL_TAKEN', 'email'],
    ['WEAK_PASSWORD', 'password'],
  ])('puts %s under the %s field', (code, field) => {
    expect(toFormErrors(new ApiError(409, code, 'message from the API'))).toEqual({ fields: { [field]: 'message from the API' } })
  })

  it('shows everything else as a general message', () => {
    expect(toFormErrors(new ApiError(401, 'INVALID_CREDENTIALS', 'Invalid username or password'))).toEqual({
      fields: {},
      general: 'Invalid username or password',
    })
    expect(toFormErrors(new Error('boom'))).toEqual({ fields: {}, general: 'Something unexpected happened. Please try again.' })
  })
})
