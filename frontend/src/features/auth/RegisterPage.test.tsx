import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { App } from '../../app/App'
import { expectNoA11yViolations } from '../../test/axe'
import { bulbasaur, pageOf } from '../../test/fixtures'
import { apiUrl, errorBody, mockCatalog } from '../../test/handlers'
import { server } from '../../test/server'
import { makeToken, renderApp } from '../../test/utils'

async function fill(username: string, email: string, password: string) {
  await userEvent.type(screen.getByLabelText('Username'), username)
  await userEvent.type(screen.getByLabelText('Email'), email)
  await userEvent.type(screen.getByLabelText('Password'), password)
  await userEvent.click(screen.getByRole('button', { name: 'Create profile' }))
}

const registerFails = (
  status: number,
  code: string,
  message: string,
  fieldErrors: { field: string; message: string }[] = [],
) =>
  server.use(
    http.post(apiUrl('/auth/register'), () =>
      HttpResponse.json(errorBody(status, code, message, fieldErrors), { status }),
    ),
  )

describe('RegisterPage', () => {
  beforeEach(() => {
    mockCatalog(() => pageOf([bulbasaur]))
  })

  it('creates the account, logs the user in and goes to the catalog', async () => {
    const calls: string[] = []
    server.use(
      http.post(apiUrl('/auth/register'), async ({ request }) => {
        calls.push(`register ${JSON.stringify(await request.json())}`)
        return HttpResponse.json(
          { id: 9, username: 'misty', email: 'misty@cerulean.city', role: 'USER' },
          { status: 201 },
        )
      }),
      http.post(apiUrl('/auth/login'), async ({ request }) => {
        calls.push(`login ${JSON.stringify(await request.json())}`)
        return HttpResponse.json({
          token: makeToken({ uid: 9, sub: 'misty' }),
          tokenType: 'Bearer',
          expiresAt: 'x',
        })
      }),
    )
    renderApp(<App />, { route: '/register' })

    await fill('misty', 'misty@cerulean.city', 'Starmie2024')

    expect(await screen.findByRole('heading', { name: 'Catalog' })).toBeInTheDocument()
    expect(screen.getByText('misty')).toBeInTheDocument()
    expect(calls).toEqual([
      'register {"username":"misty","email":"misty@cerulean.city","password":"Starmie2024"}',
      'login {"username":"misty","password":"Starmie2024"}',
    ])
  })

  it('tells people the password rules up front', () => {
    renderApp(<App />, { route: '/register' })

    expect(screen.getByLabelText('Password')).toHaveAccessibleDescription(
      /at least 8 characters.*letters and digits/i,
    )
  })

  it('shows "username taken" under the username field', async () => {
    registerFails(409, 'USERNAME_TAKEN', 'Username is already taken')
    renderApp(<App />, { route: '/register' })

    await fill('ash', 'ash@pallet.town', 'Pikachu123')

    expect(await screen.findByLabelText('Username')).toHaveAccessibleDescription(
      'Username is already taken',
    )
    expect(screen.getByLabelText('Username')).toHaveAttribute('aria-invalid', 'true')
  })

  it('shows "email taken" under the email field', async () => {
    registerFails(409, 'EMAIL_TAKEN', 'Email is already registered')
    renderApp(<App />, { route: '/register' })

    await fill('ash2', 'ash@pallet.town', 'Pikachu123')

    expect(await screen.findByLabelText('Email')).toHaveAccessibleDescription(
      'Email is already registered',
    )
  })

  it('shows a weak password under the password field', async () => {
    registerFails(400, 'WEAK_PASSWORD', 'Password must have 8 to 72 bytes, with letters and digits')
    renderApp(<App />, { route: '/register' })

    await fill('ash', 'ash@pallet.town', 'abc')

    expect(await screen.findByLabelText('Password')).toHaveAccessibleDescription(
      /Password must have 8 to 72 bytes/,
    )
  })

  it('maps validation errors to every field they refer to', async () => {
    registerFails(400, 'VALIDATION_FAILED', 'Request validation failed', [
      { field: 'email', message: 'must be a well-formed email address' },
      { field: 'username', message: 'size must be between 3 and 50' },
    ])
    renderApp(<App />, { route: '/register' })

    await fill('as', 'nope', 'Pikachu123')

    expect(await screen.findByLabelText('Email')).toHaveAccessibleDescription(
      'must be a well-formed email address',
    )
    expect(screen.getByLabelText('Username')).toHaveAccessibleDescription(
      'size must be between 3 and 50',
    )
  })

  it('shows unexpected failures as a general alert', async () => {
    registerFails(500, 'INTERNAL_ERROR', 'Unexpected error')
    renderApp(<App />, { route: '/register' })

    await fill('ash', 'ash@pallet.town', 'Pikachu123')

    expect(await screen.findByRole('alert')).toHaveTextContent('Unexpected error')
  })

  it('links to the login page', () => {
    renderApp(<App />, { route: '/register' })

    expect(screen.getByRole('heading', { name: 'Create your trainer profile' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'I already have a profile' })).toHaveAttribute(
      'href',
      '/login',
    )
  })

  it('has no accessibility violations', async () => {
    const { container } = renderApp(<App />, { route: '/register' })

    await expectNoA11yViolations(container)
  })
})
