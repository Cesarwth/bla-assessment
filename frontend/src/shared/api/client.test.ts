import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import { server } from '../../test/server'
import { api, configureClient } from './client'
import { ApiError } from './errors'

describe('api client', () => {
  it('sends the bearer token when there is a session', async () => {
    configureClient({ getToken: () => 'abc' })
    server.use(
      http.get('*/api/v1/ping', ({ request }) =>
        HttpResponse.json({ auth: request.headers.get('authorization') }),
      ),
    )

    expect(await api.get('/ping')).toEqual({ auth: 'Bearer abc' })
  })

  it('sends no authorization header for anonymous callers', async () => {
    server.use(
      http.get('*/api/v1/ping', ({ request }) =>
        HttpResponse.json({ auth: request.headers.get('authorization') }),
      ),
    )

    expect(await api.get('/ping')).toEqual({ auth: null })
  })

  it('sends JSON bodies with the right content type', async () => {
    server.use(
      http.patch('*/api/v1/things/1', async ({ request }) =>
        HttpResponse.json({
          type: request.headers.get('content-type'),
          body: await request.json(),
        }),
      ),
    )

    expect(await api.patch('/things/1', { version: 2, nickname: 'x' })).toEqual({
      type: 'application/json',
      body: { version: 2, nickname: 'x' },
    })
  })

  it('maps the error body of the API to an ApiError', async () => {
    server.use(
      http.post('*/api/v1/things', () =>
        HttpResponse.json(
          {
            timestamp: '2026-10-06T00:00:00Z',
            status: 400,
            code: 'VALIDATION_FAILED',
            message: 'Request validation failed',
            fieldErrors: [{ field: 'nickname', message: 'too long' }],
          },
          { status: 400 },
        ),
      ),
    )

    const error = await api.post('/things', {}).catch((failure: unknown) => failure)

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({
      status: 400,
      code: 'VALIDATION_FAILED',
      message: 'Request validation failed',
      fieldErrors: [{ field: 'nickname', message: 'too long' }],
    })
  })

  it('maps a network failure to an ApiError with status 0', async () => {
    server.use(http.get('*/api/v1/ping', () => HttpResponse.error()))

    const error = await api.get('/ping').catch((failure: unknown) => failure)

    expect(error).toMatchObject({ status: 0, code: 'NETWORK_ERROR' })
  })

  it('copes with error responses that are not the API error shape', async () => {
    server.use(http.get('*/api/v1/ping', () => new HttpResponse('Bad Gateway', { status: 502 })))

    const error = await api.get('/ping').catch((failure: unknown) => failure)

    expect(error).toMatchObject({ status: 502, code: 'HTTP_502' })
  })

  it('exposes the status code when the caller needs it (201 created vs 200 refreshed)', async () => {
    server.use(http.post('*/api/v1/things', () => HttpResponse.json({ id: 1 }, { status: 201 })))

    expect(await api.postWithStatus('/things')).toEqual({ status: 201, data: { id: 1 } })
  })

  it('returns null for 204 No Content', async () => {
    server.use(http.delete('*/api/v1/things/1', () => new HttpResponse(null, { status: 204 })))

    expect(await api.delete('/things/1')).toBeNull()
  })

  it('returns null for a 202 Accepted without a body', async () => {
    server.use(http.post('*/api/v1/things', () => new HttpResponse(null, { status: 202 })))

    expect(await api.post('/things', {})).toBeNull()
  })

  it('reports a 401 that follows a request made with a token', async () => {
    const onUnauthorized = vi.fn()
    configureClient({ getToken: () => 'expired', onUnauthorized })
    server.use(http.get('*/api/v1/ping', () => HttpResponse.json({}, { status: 401 })))

    await api.get('/ping').catch(() => undefined)

    expect(onUnauthorized).toHaveBeenCalledOnce()
  })

  it('does not report a 401 for anonymous calls, e.g. a failed login', async () => {
    const onUnauthorized = vi.fn()
    configureClient({ onUnauthorized })
    server.use(http.post('*/api/v1/auth/login', () => HttpResponse.json({}, { status: 401 })))

    await api.post('/auth/login', {}).catch(() => undefined)

    expect(onUnauthorized).not.toHaveBeenCalled()
  })
})
