import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { App } from '../../app/App'
import { expectNoA11yViolations } from '../../test/axe'
import { apiUrl, failWith } from '../../test/handlers'
import { server } from '../../test/server'
import { findFilledRegion, renderApp } from '../../test/utils'

const NEUTRAL =
  'If an account exists for that e-mail, we sent a link to reset the password. Check your inbox (the link expires in 30 minutes).'

const accepted = (onBody?: (body: unknown) => void) =>
  server.use(
    http.post(apiUrl('/auth/forgot-password'), async ({ request }) => {
      onBody?.(await request.json())
      return new HttpResponse(null, { status: 202 })
    }),
  )

async function submit(email: string) {
  if (email) await userEvent.type(screen.getByLabelText('Email'), email)
  await userEvent.click(screen.getByRole('button', { name: 'Send reset link' }))
}

describe('ForgotPasswordPage', () => {
  it('sends the e-mail and shows the neutral confirmation', async () => {
    let body: unknown
    accepted((b) => (body = b))
    renderApp(<App />, { route: '/forgot-password' })

    await submit('ash@example.com')

    expect(await findFilledRegion(screen, 'status')).toHaveTextContent(NEUTRAL)
    expect(body).toEqual({ email: 'ash@example.com' })
    expect(screen.queryByLabelText('Email')).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Back to log in' })).toHaveAttribute('href', '/login')
  })

  it('says exactly the same whether or not the account exists (the API answers 202 to both)', async () => {
    accepted()
    const first = renderApp(<App />, { route: '/forgot-password' })
    await submit('known@example.com')
    const known = (await findFilledRegion(screen, 'status')).textContent
    first.unmount()

    renderApp(<App />, { route: '/forgot-password' })
    await submit('nobody@example.com')

    expect((await findFilledRegion(screen, 'status')).textContent).toBe(known)
  })

  it('shows the field error of a malformed e-mail and keeps the form', async () => {
    server.use(
      http.post(apiUrl('/auth/forgot-password'), () =>
        failWith(400, 'VALIDATION_FAILED', 'Request validation failed', [
          { field: 'email', message: 'must be a well-formed email address' },
        ]),
      ),
    )
    renderApp(<App />, { route: '/forgot-password' })

    await submit('not-an-email')

    expect(await screen.findByLabelText('Email')).toHaveAccessibleDescription(
      'must be a well-formed email address',
    )
    expect(screen.getByRole('status')).toBeEmptyDOMElement()
  })

  it('asks for the e-mail before calling the API', async () => {
    renderApp(<App />, { route: '/forgot-password' })

    await submit('')

    expect(screen.getByLabelText('Email')).toHaveAccessibleDescription('Enter your e-mail address.')
  })

  it('shows a general error when the server cannot be reached', async () => {
    server.use(http.post(apiUrl('/auth/forgot-password'), () => HttpResponse.error()))
    renderApp(<App />, { route: '/forgot-password' })

    await submit('ash@example.com')

    expect(await screen.findByRole('alert')).toHaveTextContent('Cannot reach the server')
  })

  it('disables the button while the request is in flight', async () => {
    server.use(
      http.post(apiUrl('/auth/forgot-password'), async () => {
        await delay(60)
        return new HttpResponse(null, { status: 202 })
      }),
    )
    renderApp(<App />, { route: '/forgot-password' })

    await submit('ash@example.com')

    expect(screen.getByRole('button', { name: 'Sending…' })).toBeDisabled()
    expect(await findFilledRegion(screen, 'status')).toBeInTheDocument()
  })

  it('links back to the login', () => {
    renderApp(<App />, { route: '/forgot-password' })

    expect(screen.getByRole('link', { name: 'Back to log in' })).toHaveAttribute('href', '/login')
  })

  it('has no accessibility violations, before and after sending', async () => {
    accepted()
    const { container } = renderApp(<App />, { route: '/forgot-password' })
    await expectNoA11yViolations(container)

    await submit('ash@example.com')
    await findFilledRegion(screen, 'status')

    await expectNoA11yViolations(container)
  })
})
