import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { App } from '../../app/App'
import { expectNoA11yViolations } from '../../test/axe'
import { bulbasaur, pageOf } from '../../test/fixtures'
import { apiUrl, errorBody, mockCatalog, mockCollection } from '../../test/handlers'
import { server } from '../../test/server'
import { makeToken, renderApp } from '../../test/utils'
import { SESSION_KEY } from './storage'

const loginOk = (token = makeToken({ uid: 5, sub: 'ash' })) =>
  http.post(apiUrl('/auth/login'), () =>
    HttpResponse.json({ token, tokenType: 'Bearer', expiresAt: '2030-01-01T00:00:00Z' }),
  )

async function fillAndSubmit(username: string, password: string) {
  if (username) await userEvent.type(screen.getByLabelText('Username'), username)
  if (password) await userEvent.type(screen.getByLabelText('Password'), password)
  await userEvent.click(screen.getByRole('button', { name: 'Log in' }))
}

describe('LoginPage', () => {
  beforeEach(() => {
    mockCatalog(() => pageOf([bulbasaur]))
    mockCollection(() => pageOf([]))
  })

  it('logs in and lands on the catalog', async () => {
    server.use(loginOk())
    renderApp(<App />, { route: '/login' })

    await fillAndSubmit('ash', 'Pikachu123')

    expect(await screen.findByRole('heading', { name: 'Catalog' })).toBeInTheDocument()
    expect(screen.getByText('ash')).toBeInTheDocument()
  })

  it('sends the typed credentials', async () => {
    let body: unknown
    server.use(
      http.post(apiUrl('/auth/login'), async ({ request }) => {
        body = await request.json()
        return HttpResponse.json({ token: makeToken(), tokenType: 'Bearer', expiresAt: 'x' })
      }),
    )
    renderApp(<App />, { route: '/login' })

    await fillAndSubmit('ash', 'Pikachu123')

    await screen.findByRole('heading', { name: 'Catalog' })
    expect(body).toEqual({ username: 'ash', password: 'Pikachu123' })
  })

  it('returns to the page the user came from', async () => {
    server.use(loginOk())
    renderApp(<App />, { route: { pathname: '/login', state: { from: '/collection' } } })

    await fillAndSubmit('ash', 'Pikachu123')

    expect(await screen.findByRole('heading', { name: 'My collection' })).toBeInTheDocument()
  })

  it('never follows a return address that leaves the site', async () => {
    server.use(loginOk())
    renderApp(<App />, { route: { pathname: '/login', state: { from: '//evil.test/phish' } } })

    await fillAndSubmit('ash', 'Pikachu123')

    expect(await screen.findByRole('heading', { name: 'Catalog' })).toBeInTheDocument()
  })

  it('shows the message of the API when the credentials are wrong and keeps the user anonymous', async () => {
    server.use(
      http.post(apiUrl('/auth/login'), () =>
        HttpResponse.json(errorBody(401, 'INVALID_CREDENTIALS', 'Invalid username or password'), {
          status: 401,
        }),
      ),
    )
    renderApp(<App />, { route: '/login' })

    await fillAndSubmit('ash', 'wrong-pass1')

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid username or password')
    expect(screen.getByLabelText('Username')).toHaveValue('ash')
    expect(sessionStorage.getItem(SESSION_KEY)).toBeNull()
    expect(screen.queryByRole('heading', { name: 'Catalog' })).not.toBeInTheDocument()
  })

  it('does not treat a failed login as an expired session', async () => {
    server.use(
      http.post(apiUrl('/auth/login'), () =>
        HttpResponse.json(errorBody(401, 'INVALID_CREDENTIALS', 'Invalid username or password'), {
          status: 401,
        }),
      ),
    )
    renderApp(<App />, { route: '/login' })

    await fillAndSubmit('ash', 'wrong-pass1')

    await screen.findByRole('alert')
    expect(screen.queryByText(/session expired/i)).not.toBeInTheDocument()
  })

  it('shows field errors from the API next to their field', async () => {
    server.use(
      http.post(apiUrl('/auth/login'), () =>
        HttpResponse.json(
          errorBody(400, 'VALIDATION_FAILED', 'Request validation failed', [
            { field: 'username', message: 'must not be blank' },
          ]),
          { status: 400 },
        ),
      ),
    )
    renderApp(<App />, { route: '/login' })

    await fillAndSubmit('ash', 'x')

    const username = await screen.findByLabelText('Username')
    expect(username).toHaveAccessibleDescription('must not be blank')
    expect(username).toHaveAttribute('aria-invalid', 'true')
  })

  it.each([
    ['username', '', 'Pikachu123'],
    ['password', 'ash', ''],
  ])('asks for the %s before calling the API', async (_field, username, password) => {
    renderApp(<App />, { route: '/login' })

    await fillAndSubmit(username, password)

    expect(screen.getByRole('alert')).toHaveTextContent('Enter your username and password')
  })

  it('disables the button while the request is in flight', async () => {
    server.use(
      http.post(apiUrl('/auth/login'), async () => {
        await delay(60)
        return HttpResponse.json({ token: makeToken(), tokenType: 'Bearer', expiresAt: 'x' })
      }),
    )
    renderApp(<App />, { route: '/login' })

    await fillAndSubmit('ash', 'Pikachu123')

    expect(screen.getByRole('button', { name: 'Logging in…' })).toBeDisabled()
    expect(await screen.findByRole('heading', { name: 'Catalog' })).toBeInTheDocument()
  })

  it('sends people who are already logged in away from the form', async () => {
    renderApp(<App />, { route: '/login', token: makeToken() })

    expect(await screen.findByRole('heading', { name: 'Catalog' })).toBeInTheDocument()
  })

  it('links to the registration page', () => {
    renderApp(<App />, { route: '/login' })

    expect(screen.getByText('New trainer?')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Create your profile' })).toHaveAttribute(
      'href',
      '/register',
    )
  })

  it('has no accessibility violations', async () => {
    const { container } = renderApp(<App />, { route: '/login' })

    await expectNoA11yViolations(container)
  })
})
