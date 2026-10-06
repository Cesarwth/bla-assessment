import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { useLocation } from 'react-router'
import { describe, expect, it } from 'vitest'
import { App } from '../../app/App'
import { expectNoA11yViolations } from '../../test/axe'
import { apiUrl, failWith } from '../../test/handlers'
import { server } from '../../test/server'
import { renderApp } from '../../test/utils'

function Location() {
  const { pathname, search } = useLocation()
  return <p data-testid="location">{pathname + search}</p>
}

const page = () => (
  <>
    <App />
    <Location />
  </>
)

async function fill(password: string, confirm = password) {
  await userEvent.type(screen.getByLabelText('New password'), password)
  await userEvent.type(screen.getByLabelText('Confirm new password'), confirm)
  await userEvent.click(screen.getByRole('button', { name: 'Update password' }))
}

describe('ResetPasswordPage', () => {
  it('sends the token of the link with the new password and goes to the login with a notice', async () => {
    let body: unknown
    server.use(
      http.post(apiUrl('/auth/reset-password'), async ({ request }) => {
        body = await request.json()
        return new HttpResponse(null, { status: 204 })
      }),
    )
    renderApp(page(), { route: '/reset-password?token=abc123' })

    await fill('Pikachu123')

    expect(await screen.findByRole('heading', { name: 'Log in' })).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Password updated, you can log in.')
    expect(body).toEqual({ token: 'abc123', newPassword: 'Pikachu123' })
  })

  it('removes the token from the address once it has been read', async () => {
    renderApp(page(), { route: '/reset-password?token=abc123' })

    expect(await screen.findByLabelText('New password')).toBeInTheDocument()
    expect(screen.getByTestId('location')).toHaveTextContent(/^\/reset-password$/)
  })

  it('still uses the token after it left the address', async () => {
    let body: { token?: string } = {}
    server.use(
      http.post(apiUrl('/auth/reset-password'), async ({ request }) => {
        body = (await request.json()) as { token?: string }
        return new HttpResponse(null, { status: 204 })
      }),
    )
    renderApp(page(), { route: '/reset-password?token=abc123' })
    await screen.findByLabelText('New password')

    await fill('Pikachu123')

    await screen.findByRole('heading', { name: 'Log in' })
    expect(body.token).toBe('abc123')
  })

  it('shows the invalid-link state without calling the API when the link has no token', () => {
    renderApp(page(), { route: '/reset-password' })

    expect(screen.getByRole('heading', { name: 'This reset link is invalid' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Request a new link' })).toHaveAttribute(
      'href',
      '/forgot-password',
    )
    expect(screen.queryByLabelText('New password')).not.toBeInTheDocument()
  })

  it('shows the invalid-link state when the API rejects the token', async () => {
    server.use(
      http.post(apiUrl('/auth/reset-password'), () =>
        failWith(400, 'INVALID_RESET_TOKEN', 'This reset link is invalid or has expired'),
      ),
    )
    renderApp(page(), { route: '/reset-password?token=old' })

    await fill('Pikachu123')

    expect(
      await screen.findByRole('heading', { name: 'This reset link is invalid' }),
    ).toBeInTheDocument()
    expect(screen.getByText('This reset link is invalid or has expired')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Request a new link' })).toHaveAttribute(
      'href',
      '/forgot-password',
    )
  })

  it('shows the weak-password answer of the API under the field and keeps the form', async () => {
    server.use(
      http.post(apiUrl('/auth/reset-password'), () =>
        failWith(400, 'WEAK_PASSWORD', 'Password is too weak'),
      ),
    )
    renderApp(page(), { route: '/reset-password?token=abc123' })

    await fill('Pikachu123')

    expect(await screen.findByLabelText('New password')).toHaveAccessibleDescription(
      'Password is too weak',
    )
  })

  it('checks the policy before calling the API, with the hint of the register page', async () => {
    renderApp(page(), { route: '/reset-password?token=abc123' })
    expect(screen.getByLabelText('New password')).toHaveAccessibleDescription(
      /at least 8 characters.*letters and digits/i,
    )

    await fill('short')

    expect(screen.getByLabelText('New password')).toHaveAttribute('aria-invalid', 'true')
  })

  it('asks to repeat the same password', async () => {
    renderApp(page(), { route: '/reset-password?token=abc123' })

    await fill('Pikachu123', 'Pikachu124')

    expect(screen.getByLabelText('Confirm new password')).toHaveAccessibleDescription(
      'The passwords do not match.',
    )
  })

  it('has no accessibility violations in any of its states', async () => {
    const form = renderApp(page(), { route: '/reset-password?token=abc123' })
    await expectNoA11yViolations(form.container)
    form.unmount()

    const invalid = renderApp(page(), { route: '/reset-password' })
    await expectNoA11yViolations(invalid.container)
  })
})
