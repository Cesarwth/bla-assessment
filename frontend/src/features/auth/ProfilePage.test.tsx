import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { App } from '../../app/App'
import { expectNoA11yViolations } from '../../test/axe'
import { bulbasaur, pageOf } from '../../test/fixtures'
import {
  apiUrl,
  failWith,
  me,
  mockCatalog,
  mockCollectionIds,
  mockProfile,
} from '../../test/handlers'
import { server } from '../../test/server'
import { makeToken, renderApp } from '../../test/utils'
import { SESSION_KEY } from './storage'

const session = () => makeToken({ uid: 1, sub: 'ash' })

/** What PATCH /users/me answers: the saved trainer and a token carrying the new username. */
const patched = (username: string, email = me.email) => ({
  token: makeToken({ uid: 1, sub: username }),
  tokenType: 'Bearer',
  expiresAt: '2030-01-01T00:00:00Z',
  user: { ...me, username, email },
})

const details = () => screen.getByRole('region', { name: 'Your details' })
const passwordCard = () => screen.getByRole('region', { name: 'Change password' })

async function openProfile() {
  renderApp(<App />, { route: '/profile', token: session() })
  await screen.findByLabelText('Username')
}

describe('ProfilePage', () => {
  beforeEach(() => {
    mockProfile()
    mockCatalog(() => pageOf([bulbasaur]))
    mockCollectionIds([])
  })

  it('sends anonymous visitors to the login', async () => {
    renderApp(<App />, { route: '/profile' })

    expect(await screen.findByRole('heading', { name: 'Log in' })).toBeInTheDocument()
    expect(screen.queryByLabelText('Email')).not.toBeInTheDocument()
  })

  it('prefills the details from the API', async () => {
    await openProfile()

    expect(within(details()).getByLabelText('Username')).toHaveValue('ash')
    expect(within(details()).getByLabelText('Email')).toHaveValue('ash@example.com')
    expect(within(details()).queryByLabelText('Current password')).not.toBeInTheDocument()
  })

  it('shows an error with a retry when the profile cannot be loaded', async () => {
    mockProfile(failWith(500, 'INTERNAL_ERROR', 'Something broke'))
    renderApp(<App />, { route: '/profile', token: session() })

    expect(await screen.findByRole('alert')).toHaveTextContent('Something broke')
    mockProfile()
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByLabelText('Username')).toHaveValue('ash')
  })

  describe('your details', () => {
    it('renames the trainer: the returned token becomes the session and the header shows the new name', async () => {
      let body: unknown
      const response = patched('misty')
      server.use(
        http.patch(apiUrl('/users/me'), async ({ request }) => {
          body = await request.json()
          return HttpResponse.json(response)
        }),
      )
      await openProfile()

      await userEvent.clear(screen.getByLabelText('Username'))
      await userEvent.type(screen.getByLabelText('Username'), 'misty')
      await userEvent.click(screen.getByRole('button', { name: 'Save details' }))

      expect(await within(details()).findByRole('status')).toHaveTextContent(
        'Your details were updated.',
      )
      expect(body).toEqual({ username: 'misty' })
      const header = screen.getByRole('banner')
      expect(within(header).getByRole('link', { name: /misty/ })).toHaveAttribute(
        'href',
        '/profile',
      )
      expect(within(header).queryByText('ash')).not.toBeInTheDocument()
      expect(sessionStorage.getItem(SESSION_KEY)).toBe(response.token)
    })

    it('uses the new token for the requests that follow', async () => {
      const response = patched('misty')
      server.use(http.patch(apiUrl('/users/me'), () => HttpResponse.json(response)))
      await openProfile()
      await userEvent.clear(screen.getByLabelText('Username'))
      await userEvent.type(screen.getByLabelText('Username'), 'misty')
      await userEvent.click(screen.getByRole('button', { name: 'Save details' }))
      await within(details()).findByRole('status')

      let auth: string | null = null
      server.use(
        http.patch(apiUrl('/users/me'), ({ request }) => {
          auth = request.headers.get('authorization')
          return HttpResponse.json(response)
        }),
      )
      await userEvent.type(screen.getByLabelText('Username'), 'x')
      await userEvent.click(screen.getByRole('button', { name: 'Save details' }))

      await waitFor(() => expect(auth).toBe(`Bearer ${response.token}`))
    })

    it('asks for the current password only when the e-mail changes', async () => {
      await openProfile()

      await userEvent.type(screen.getByLabelText('Email'), 'x')
      expect(within(details()).getByLabelText('Current password')).toBeInTheDocument()

      await userEvent.clear(screen.getByLabelText('Email'))
      await userEvent.type(screen.getByLabelText('Email'), 'ash@example.com')
      expect(within(details()).queryByLabelText('Current password')).not.toBeInTheDocument()
    })

    it('sends the new e-mail with the current password', async () => {
      let body: unknown
      server.use(
        http.patch(apiUrl('/users/me'), async ({ request }) => {
          body = await request.json()
          return HttpResponse.json(patched('ash', 'new@example.com'))
        }),
      )
      await openProfile()

      await userEvent.clear(screen.getByLabelText('Email'))
      await userEvent.type(screen.getByLabelText('Email'), 'new@example.com')
      await userEvent.type(within(details()).getByLabelText('Current password'), 'Pikachu123')
      await userEvent.click(screen.getByRole('button', { name: 'Save details' }))

      expect(await within(details()).findByRole('status')).toBeInTheDocument()
      expect(body).toEqual({ email: 'new@example.com', currentPassword: 'Pikachu123' })
      expect(within(details()).queryByLabelText('Current password')).not.toBeInTheDocument()
      expect(within(details()).getByLabelText('Email')).toHaveValue('new@example.com')
    })

    it('does not call the API for a changed e-mail without the current password', async () => {
      await openProfile()

      await userEvent.clear(screen.getByLabelText('Email'))
      await userEvent.type(screen.getByLabelText('Email'), 'new@example.com')
      await userEvent.click(screen.getByRole('button', { name: 'Save details' }))

      expect(within(details()).getByLabelText('Current password')).toHaveAccessibleDescription(
        'Enter your current password to change your e-mail.',
      )
    })

    it('says so when nothing changed, without calling the API', async () => {
      await openProfile()

      await userEvent.click(screen.getByRole('button', { name: 'Save details' }))

      expect(within(details()).getByRole('alert')).toHaveTextContent(
        'Change your username or e-mail first.',
      )
    })

    it.each([
      ['USERNAME_TAKEN', 'Username', 'That username is taken'],
      ['EMAIL_TAKEN', 'Email', 'That e-mail is taken'],
    ])('shows %s next to its field', async (code, label, message) => {
      server.use(http.patch(apiUrl('/users/me'), () => failWith(409, code, message)))
      await openProfile()
      await userEvent.clear(screen.getByLabelText('Username'))
      await userEvent.type(screen.getByLabelText('Username'), 'misty')
      if (label === 'Email') {
        await userEvent.clear(screen.getByLabelText('Email'))
        await userEvent.type(screen.getByLabelText('Email'), 'new@example.com')
        await userEvent.type(within(details()).getByLabelText('Current password'), 'Pikachu123')
      }
      await userEvent.click(screen.getByRole('button', { name: 'Save details' }))

      expect(await within(details()).findByLabelText(label)).toHaveAccessibleDescription(message)
    })

    it('keeps the user logged in when the current password is wrong (a 400, not a 401)', async () => {
      server.use(
        http.patch(apiUrl('/users/me'), () =>
          failWith(400, 'INVALID_CURRENT_PASSWORD', 'The current password is incorrect'),
        ),
      )
      await openProfile()
      await userEvent.clear(screen.getByLabelText('Email'))
      await userEvent.type(screen.getByLabelText('Email'), 'new@example.com')
      await userEvent.type(within(details()).getByLabelText('Current password'), 'Wrong12345')
      await userEvent.click(screen.getByRole('button', { name: 'Save details' }))

      expect(
        await within(details()).findByLabelText('Current password'),
      ).toHaveAccessibleDescription('The current password is incorrect')
      expect(
        within(screen.getByRole('banner')).getByRole('button', { name: 'Log out' }),
      ).toBeInTheDocument()
      expect(screen.queryByText(/session expired/i)).not.toBeInTheDocument()
      expect(sessionStorage.getItem(SESSION_KEY)).not.toBeNull()
    })

    it('shows validation errors of the API per field', async () => {
      server.use(
        http.patch(apiUrl('/users/me'), () =>
          failWith(400, 'VALIDATION_FAILED', 'Request validation failed', [
            { field: 'username', message: 'size must be between 3 and 30' },
          ]),
        ),
      )
      await openProfile()
      await userEvent.clear(screen.getByLabelText('Username'))
      await userEvent.type(screen.getByLabelText('Username'), 'ab')
      await userEvent.click(screen.getByRole('button', { name: 'Save details' }))

      expect(await within(details()).findByLabelText('Username')).toHaveAccessibleDescription(
        'size must be between 3 and 30',
      )
    })

    it('disables the button while saving', async () => {
      server.use(
        http.patch(apiUrl('/users/me'), async () => {
          await delay(60)
          return HttpResponse.json(patched('misty'))
        }),
      )
      await openProfile()
      await userEvent.type(screen.getByLabelText('Username'), 'x')
      await userEvent.click(screen.getByRole('button', { name: 'Save details' }))

      expect(screen.getByRole('button', { name: 'Saving…' })).toBeDisabled()
      expect(await within(details()).findByRole('status')).toBeInTheDocument()
    })

    it('ends the session on a 401 like everywhere else', async () => {
      server.use(http.patch(apiUrl('/users/me'), () => HttpResponse.json({}, { status: 401 })))
      await openProfile()
      await userEvent.type(screen.getByLabelText('Username'), 'x')
      await userEvent.click(screen.getByRole('button', { name: 'Save details' }))

      expect(await screen.findByRole('heading', { name: 'Log in' })).toBeInTheDocument()
      expect(sessionStorage.getItem(SESSION_KEY)).toBeNull()
    })
  })

  describe('change password', () => {
    async function fill(current: string, next: string, confirm = next) {
      await userEvent.type(within(passwordCard()).getByLabelText('Current password'), current)
      await userEvent.type(within(passwordCard()).getByLabelText('New password'), next)
      await userEvent.type(within(passwordCard()).getByLabelText('Confirm new password'), confirm)
      await userEvent.click(screen.getByRole('button', { name: 'Change password' }))
    }

    it('changes the password, confirms and clears the fields', async () => {
      let body: unknown
      server.use(
        http.post(apiUrl('/users/me/password'), async ({ request }) => {
          body = await request.json()
          return new HttpResponse(null, { status: 204 })
        }),
      )
      await openProfile()

      await fill('Pikachu123', 'Raichu4567')

      expect(await within(passwordCard()).findByRole('status')).toHaveTextContent(
        'Password changed.',
      )
      expect(body).toEqual({ currentPassword: 'Pikachu123', newPassword: 'Raichu4567' })
      expect(within(passwordCard()).getByLabelText('Current password')).toHaveValue('')
      expect(within(passwordCard()).getByLabelText('New password')).toHaveValue('')
      expect(within(passwordCard()).getByLabelText('Confirm new password')).toHaveValue('')
    })

    it('keeps the user logged in when the current password is wrong', async () => {
      server.use(
        http.post(apiUrl('/users/me/password'), () =>
          failWith(400, 'INVALID_CURRENT_PASSWORD', 'The current password is incorrect'),
        ),
      )
      await openProfile()

      await fill('Wrong12345', 'Raichu4567')

      expect(
        await within(passwordCard()).findByLabelText('Current password'),
      ).toHaveAccessibleDescription('The current password is incorrect')
      expect(screen.getByRole('button', { name: 'Log out' })).toBeInTheDocument()
      expect(screen.queryByText(/session expired/i)).not.toBeInTheDocument()
      expect(sessionStorage.getItem(SESSION_KEY)).not.toBeNull()
    })

    it('shows the weak-password answer under the new password', async () => {
      server.use(
        http.post(apiUrl('/users/me/password'), () =>
          failWith(400, 'WEAK_PASSWORD', 'The new password must differ from the current one'),
        ),
      )
      await openProfile()

      await fill('Pikachu123', 'Pikachu123')

      expect(
        await within(passwordCard()).findByLabelText('New password'),
      ).toHaveAccessibleDescription('The new password must differ from the current one')
    })

    it('checks the policy and the confirmation before calling the API', async () => {
      await openProfile()

      await fill('Pikachu123', 'short', 'different')

      expect(within(passwordCard()).getByLabelText('New password')).toHaveAttribute(
        'aria-invalid',
        'true',
      )
      expect(
        within(passwordCard()).getByLabelText('Confirm new password'),
      ).toHaveAccessibleDescription('The passwords do not match.')
    })

    it('asks for the current password', async () => {
      await openProfile()

      await userEvent.type(within(passwordCard()).getByLabelText('New password'), 'Raichu4567')
      await userEvent.type(
        within(passwordCard()).getByLabelText('Confirm new password'),
        'Raichu4567',
      )
      await userEvent.click(screen.getByRole('button', { name: 'Change password' }))

      expect(within(passwordCard()).getByLabelText('Current password')).toHaveAccessibleDescription(
        'Enter your current password.',
      )
    })

    it('disables the button while saving', async () => {
      server.use(
        http.post(apiUrl('/users/me/password'), async () => {
          await delay(60)
          return new HttpResponse(null, { status: 204 })
        }),
      )
      await openProfile()

      await fill('Pikachu123', 'Raichu4567')

      expect(screen.getByRole('button', { name: 'Changing…' })).toBeDisabled()
      expect(await within(passwordCard()).findByRole('status')).toBeInTheDocument()
    })
  })

  it('is linked from the username in the header', async () => {
    renderApp(<App />, { token: session() })

    await userEvent.click(within(screen.getByRole('banner')).getByRole('link', { name: /ash/ }))

    expect(await screen.findByLabelText('Username')).toHaveValue('ash')
  })

  it('has no accessibility violations', async () => {
    renderApp(<App />, { route: '/profile', token: session() })
    await screen.findByLabelText('Username')
    await userEvent.type(screen.getByLabelText('Email'), 'x')

    await expectNoA11yViolations(document.body)
  })
})
