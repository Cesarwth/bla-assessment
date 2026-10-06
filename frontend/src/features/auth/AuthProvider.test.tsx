import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { useState } from 'react'
import { describe, expect, it } from 'vitest'
import { api } from '../../shared/api/client'
import { server } from '../../test/server'
import { makeToken, renderApp } from '../../test/utils'
import { SESSION_KEY } from './storage'
import { useAuth } from './useAuth'

function Probe() {
  const { user, isAdmin, sessionExpired, login, logout, adoptSession } = useAuth()
  const [error, setError] = useState('')
  return (
    <div>
      <p>user: {user ? `${user.username} #${user.id} ${user.role}` : 'anonymous'}</p>
      <p>admin: {String(isAdmin)}</p>
      <p>expired: {String(sessionExpired)}</p>
      <p>error: {error}</p>
      <button onClick={() => login('ash', 'Pikachu123').catch((e: Error) => setError(e.message))}>
        login
      </button>
      <button onClick={logout}>logout</button>
      <button onClick={() => adoptSession(makeToken({ uid: 5, sub: 'misty', role: 'ADMIN' }))}>
        adopt
      </button>
      <button
        onClick={() => {
          try {
            adoptSession('garbage')
          } catch (e) {
            setError((e as Error).message)
          }
        }}
      >
        adopt garbage
      </button>
      <button onClick={() => api.get('/anything').catch(() => undefined)}>call api</button>
    </div>
  )
}

const loginReturns = (token: string) =>
  server.use(
    http.post('*/api/v1/auth/login', () =>
      HttpResponse.json({ token, tokenType: 'Bearer', expiresAt: '2030-01-01T00:00:00Z' }),
    ),
  )

describe('AuthProvider', () => {
  it('login stores the session and exposes the user', async () => {
    const token = makeToken({ uid: 5, sub: 'ash' })
    loginReturns(token)
    renderApp(<Probe />)

    await userEvent.click(screen.getByText('login'))

    expect(await screen.findByText('user: ash #5 USER')).toBeInTheDocument()
    expect(sessionStorage.getItem(SESSION_KEY)).toBe(token)
  })

  it('knows when the user is an admin', async () => {
    renderApp(<Probe />, { token: makeToken({ role: 'ADMIN' }) })

    expect(await screen.findByText('admin: true')).toBeInTheDocument()
  })

  it('a failed login keeps the user anonymous and surfaces the API message', async () => {
    server.use(
      http.post('*/api/v1/auth/login', () =>
        HttpResponse.json(
          {
            status: 401,
            code: 'INVALID_CREDENTIALS',
            message: 'Invalid username or password',
            fieldErrors: [],
          },
          { status: 401 },
        ),
      ),
    )
    renderApp(<Probe />)

    await userEvent.click(screen.getByText('login'))

    expect(await screen.findByText('error: Invalid username or password')).toBeInTheDocument()
    expect(screen.getByText('user: anonymous')).toBeInTheDocument()
    expect(screen.getByText('expired: false')).toBeInTheDocument()
  })

  it('logout clears the session', async () => {
    renderApp(<Probe />, { token: makeToken() })
    expect(await screen.findByText(/user: ash/)).toBeInTheDocument()

    await userEvent.click(screen.getByText('logout'))

    expect(screen.getByText('user: anonymous')).toBeInTheDocument()
    expect(sessionStorage.getItem(SESSION_KEY)).toBeNull()
  })

  it('restores the session from this tab after a reload', () => {
    renderApp(<Probe />, { token: makeToken({ uid: 9, sub: 'brock' }) })

    expect(screen.getByText('user: brock #9 USER')).toBeInTheDocument()
  })

  it('starts logged out and cleans the storage when the stored token already expired', () => {
    renderApp(<Probe />, { token: makeToken({ exp: 1 }) })

    expect(screen.getByText('user: anonymous')).toBeInTheDocument()
    expect(sessionStorage.getItem(SESSION_KEY)).toBeNull()
  })

  it('ignores garbage left in the storage', () => {
    sessionStorage.setItem(SESSION_KEY, 'garbage')
    renderApp(<Probe />)

    expect(screen.getByText('user: anonymous')).toBeInTheDocument()
  })

  it('logs out and flags the expiry when the API answers 401 to an authenticated call', async () => {
    server.use(http.get('*/api/v1/anything', () => HttpResponse.json({}, { status: 401 })))
    renderApp(<Probe />, { token: makeToken() })

    await userEvent.click(screen.getByText('call api'))

    await waitFor(() => expect(screen.getByText('user: anonymous')).toBeInTheDocument())
    expect(screen.getByText('expired: true')).toBeInTheDocument()
    expect(sessionStorage.getItem(SESSION_KEY)).toBeNull()
  })

  it('adopts a new token as the session: same identity, new claims, stored for reloads', async () => {
    renderApp(<Probe />, { token: makeToken({ uid: 5, sub: 'ash' }) })

    await userEvent.click(screen.getByText('adopt'))

    expect(screen.getByText('user: misty #5 ADMIN')).toBeInTheDocument()
    expect(JSON.parse(atob(sessionStorage.getItem(SESSION_KEY)!.split('.')[1]!)).sub).toBe('misty')
  })

  it('sends the adopted token with the next request', async () => {
    let auth: string | null = null
    server.use(
      http.get('*/api/v1/anything', ({ request }) => {
        auth = request.headers.get('authorization')
        return HttpResponse.json({})
      }),
    )
    renderApp(<Probe />, { token: makeToken({ sub: 'ash' }) })

    await userEvent.click(screen.getByText('adopt'))
    await userEvent.click(screen.getByText('call api'))

    await waitFor(() => expect(auth).toBe(`Bearer ${sessionStorage.getItem(SESSION_KEY)}`))
  })

  it('refuses a token it cannot read and keeps the current session', async () => {
    renderApp(<Probe />, { token: makeToken({ sub: 'ash' }) })

    await userEvent.click(screen.getByText('adopt garbage'))

    expect(screen.getByText(/cannot read/)).toBeInTheDocument()
    expect(screen.getByText('user: ash #1 USER')).toBeInTheDocument()
  })
})
