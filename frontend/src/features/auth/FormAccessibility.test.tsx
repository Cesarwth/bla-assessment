import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { App } from '../../app/App'
import { expectNoA11yViolations } from '../../test/axe'
import { bulbasaur, pageOf } from '../../test/fixtures'
import { apiUrl, errorBody, mockCatalog, mockCollectionIds, mockProfile } from '../../test/handlers'
import { server } from '../../test/server'
import { makeToken, renderApp } from '../../test/utils'

const click = (name: string) => userEvent.click(screen.getByRole('button', { name }))
const focusedLabel = () =>
  (document.activeElement as HTMLInputElement | null)?.labels?.[0]?.textContent

describe('form errors move focus to the first invalid field', () => {
  beforeEach(() => {
    mockCatalog(() => pageOf([bulbasaur]))
    mockCollectionIds([])
    mockProfile()
  })

  it('login: the first empty field, then the next one', async () => {
    renderApp(<App />, { route: '/login' })

    await click('Log in')
    expect(screen.getByLabelText('Username')).toHaveFocus()
    expect(screen.getByLabelText('Username')).toHaveAttribute('aria-invalid', 'true')

    await userEvent.type(screen.getByLabelText('Username'), 'ash')
    await click('Log in')
    expect(screen.getByLabelText('Password')).toHaveFocus()
  })

  it('register: the first field the server rejected', async () => {
    server.use(
      http.post(apiUrl('/auth/register'), () =>
        HttpResponse.json(
          errorBody(400, 'VALIDATION_FAILED', 'Invalid request', [
            { field: 'email', message: 'must be a well-formed email address' },
            { field: 'password', message: 'too weak' },
          ]),
          { status: 400 },
        ),
      ),
    )
    renderApp(<App />, { route: '/register' })

    await click('Create profile')

    await waitFor(() => expect(screen.getByLabelText('Email')).toHaveFocus())
  })

  it('forgot password: the e-mail field', async () => {
    renderApp(<App />, { route: '/forgot-password' })

    await click('Send reset link')

    expect(screen.getByLabelText('Email')).toHaveFocus()
  })

  it('reset password: the weak password, then the mismatching confirmation', async () => {
    renderApp(<App />, { route: '/reset-password?token=abc' })

    await userEvent.type(await screen.findByLabelText('New password'), 'short')
    await click('Update password')
    expect(screen.getByLabelText('New password')).toHaveFocus()

    await userEvent.clear(screen.getByLabelText('New password'))
    await userEvent.type(screen.getByLabelText('New password'), 'Pikachu123')
    await click('Update password')
    expect(screen.getByLabelText('Confirm new password')).toHaveFocus()
  })

  it('profile details: the current password the e-mail change needs', async () => {
    renderApp(<App />, { route: '/profile', token: makeToken({ sub: 'ash' }) })
    const details = await screen.findByRole('region', { name: 'Your details' })

    await userEvent.type(within(details).getByLabelText('Email'), 'x')
    await userEvent.click(within(details).getByRole('button', { name: 'Save details' }))

    expect(within(details).getByLabelText('Current password')).toHaveFocus()
  })

  it('profile details: a field error from the server', async () => {
    server.use(
      http.patch(apiUrl('/users/me'), () =>
        HttpResponse.json(errorBody(409, 'USERNAME_TAKEN', 'That username is taken'), {
          status: 409,
        }),
      ),
    )
    renderApp(<App />, { route: '/profile', token: makeToken({ sub: 'ash' }) })
    const details = await screen.findByRole('region', { name: 'Your details' })

    await userEvent.type(within(details).getByLabelText('Username'), 'x')
    await userEvent.click(within(details).getByRole('button', { name: 'Save details' }))

    await waitFor(() => expect(within(details).getByLabelText('Username')).toHaveFocus())
  })

  it('change password: the first invalid field', async () => {
    renderApp(<App />, { route: '/profile', token: makeToken({ sub: 'ash' }) })
    const card = await screen.findByRole('region', { name: 'Change password' })

    await userEvent.click(within(card).getByRole('button', { name: 'Change password' }))

    expect(focusedLabel()).toBe('Current password')
  })

  it('focuses again when the same mistake is repeated', async () => {
    renderApp(<App />, { route: '/login' })
    await click('Log in')
    await userEvent.tab({ shift: true })
    expect(screen.getByLabelText('Username')).not.toHaveFocus()

    await click('Log in')

    expect(screen.getByLabelText('Username')).toHaveFocus()
  })
})

describe('messages live in regions that exist before they have text', () => {
  beforeEach(() => {
    mockCatalog(() => pageOf([bulbasaur]))
    mockCollectionIds([])
    mockProfile()
  })

  it('login: the alert is empty first, then says what went wrong', async () => {
    server.use(
      http.post(apiUrl('/auth/login'), () =>
        HttpResponse.json(errorBody(401, 'INVALID_CREDENTIALS', 'Invalid username or password'), {
          status: 401,
        }),
      ),
    )
    renderApp(<App />, { route: '/login' })
    const alert = screen.getByRole('alert')
    expect(alert).toBeEmptyDOMElement()

    await userEvent.type(screen.getByLabelText('Username'), 'ash')
    await userEvent.type(screen.getByLabelText('Password'), 'wrong-pass1')
    await click('Log in')

    expect(screen.getByRole('alert')).toBe(alert)
    await waitFor(() => expect(alert).toHaveTextContent('Invalid username or password'))
  })

  it('forgot password: the status exists before the confirmation arrives', async () => {
    server.use(
      http.post(apiUrl('/auth/forgot-password'), () => new HttpResponse(null, { status: 204 })),
    )
    renderApp(<App />, { route: '/forgot-password' })
    const status = screen.getByRole('status')
    expect(status).toBeEmptyDOMElement()

    await userEvent.type(screen.getByLabelText('Email'), 'ash@example.com')
    await click('Send reset link')

    await waitFor(() => expect(status).toHaveTextContent(/we sent a link/))
    expect(screen.getByRole('status')).toBe(status)
  })

  it('profile: both cards have an empty status that fills in after saving', async () => {
    server.use(
      http.post(apiUrl('/users/me/password'), () => new HttpResponse(null, { status: 204 })),
    )
    renderApp(<App />, { route: '/profile', token: makeToken({ sub: 'ash' }) })
    const card = await screen.findByRole('region', { name: 'Change password' })
    const status = within(card).getByRole('status')
    expect(status).toBeEmptyDOMElement()
    expect(
      within(await screen.findByRole('region', { name: 'Your details' })).getByRole('status'),
    ).toBeEmptyDOMElement()

    await userEvent.type(within(card).getByLabelText('Current password'), 'Pikachu123')
    await userEvent.type(within(card).getByLabelText('New password'), 'Raichu12345')
    await userEvent.type(within(card).getByLabelText('Confirm new password'), 'Raichu12345')
    await userEvent.click(within(card).getByRole('button', { name: 'Change password' }))

    await waitFor(() => expect(status).toHaveTextContent('Password changed.'))
  })

  it('stays free of accessibility violations with the empty regions and with errors', async () => {
    const { container } = renderApp(<App />, { route: '/login' })
    await expectNoA11yViolations(container)

    await click('Log in')

    await expectNoA11yViolations(container)
  })
})
