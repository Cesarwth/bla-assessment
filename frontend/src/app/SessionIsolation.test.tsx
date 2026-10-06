import { act, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { bulbasaur, pageOf, summary, storedSummary } from '../test/fixtures'
import { apiUrl, mockCatalog } from '../test/handlers'
import { server } from '../test/server'
import { makeToken, renderApp } from '../test/utils'
import { App } from './App'

const tokenA = makeToken({ uid: 1, sub: 'ash' })
const tokenB = makeToken({ uid: 2, sub: 'misty' })

/** Which trainer a request belongs to, from the bearer token the app attached. */
const trainerOf = (request: Request) =>
  request.headers.get('authorization') === `Bearer ${tokenA}` ? 'A' : 'B'

/** A request the test releases when it wants. */
function gate() {
  let open: () => void = () => {}
  const opened = new Promise<void>((resolve) => (open = resolve))
  return { opened, open }
}

async function logInAs(token: string, username: string) {
  server.use(
    http.post(apiUrl('/auth/login'), () =>
      HttpResponse.json({ token, tokenType: 'Bearer', expiresAt: '2030-01-01T00:00:00Z' }),
    ),
  )
  await userEvent.type(await screen.findByLabelText('Username'), username)
  await userEvent.type(screen.getByLabelText('Password'), 'Pikachu123')
  await userEvent.click(screen.getByRole('button', { name: 'Log in' }))
}

describe('one trainer after another in the same tab', () => {
  it('never shows the previous trainer’s collection or collected marks', async () => {
    const bData = gate()
    mockCatalog(() => pageOf([bulbasaur]))
    server.use(
      http.get(apiUrl('/collection/ids'), async ({ request }) => {
        if (trainerOf(request) === 'B') await bData.opened
        return HttpResponse.json(trainerOf(request) === 'A' ? [1] : [])
      }),
      http.get(apiUrl('/collection'), async ({ request }) => {
        if (trainerOf(request) === 'B') await bData.opened
        return HttpResponse.json(
          trainerOf(request) === 'A'
            ? pageOf([storedSummary(25, 'pikachu', { nickname: 'AshsPika' })], { totalItems: 1 })
            : pageOf([storedSummary(7, 'squirtle', { nickname: 'MistysSquirtle' })], {
                totalItems: 1,
              }),
        )
      }),
    )
    renderApp(<App />, { route: '/collection', token: tokenA })
    expect(await screen.findByText(/AshsPika/)).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Log out' }))
    expect(screen.queryByText(/AshsPika/)).not.toBeInTheDocument()
    await logInAs(tokenB, 'misty')

    // B is on /collection and B's rows have not arrived yet: nothing of A may be on screen.
    expect(await screen.findByRole('heading', { name: 'My collection' })).toBeInTheDocument()
    expect(screen.queryByText(/AshsPika/)).not.toBeInTheDocument()
    await act(async () => {
      bData.open()
      await delay(20)
    })
    expect(await screen.findByText(/MistysSquirtle/)).toBeInTheDocument()
    expect(screen.queryByText(/AshsPika/)).not.toBeInTheDocument()

    // The catalog marks come from B's ids, never from A's cached answer.
    await userEvent.click(screen.getByRole('link', { name: 'Catalog' }))
    const card = (await screen.findByRole('link', { name: /Bulbasaur/ })).closest('article')!
    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Select Pokémon' })).toBeVisible(),
    )
    expect(within(card).queryByText('In my collection')).not.toBeInTheDocument()
  })

  it('starts the next trainer with an empty selection and selection mode off', async () => {
    mockCatalog(() => pageOf([bulbasaur, summary(2, 'ivysaur', ['grass'])]))
    server.use(http.get(apiUrl('/collection/ids'), () => HttpResponse.json([])))
    renderApp(<App />, { token: tokenA })
    await userEvent.click(await screen.findByRole('button', { name: 'Select Pokémon' }))
    await userEvent.click(await screen.findByRole('checkbox', { name: 'Select Ivysaur' }))
    expect(screen.getByRole('region', { name: 'Your selection' })).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Log out' }))
    await userEvent.click(screen.getByRole('link', { name: 'Log in' }))
    await logInAs(tokenB, 'misty')

    expect(await screen.findByRole('button', { name: 'Select Pokémon' })).toHaveAttribute(
      'aria-pressed',
      'false',
    )
    expect(screen.queryByRole('region', { name: 'Your selection' })).not.toBeInTheDocument()
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument()
  })

  it('does not hand the previous trainer’s add report to the next one', async () => {
    mockCatalog(() => pageOf([bulbasaur, summary(2, 'ivysaur', ['grass'])]))
    server.use(
      http.get(apiUrl('/collection/ids'), () => HttpResponse.json([])),
      http.post(apiUrl('/collection/sync'), () =>
        HttpResponse.json({ created: 1, updated: 0, failures: [], abortedReason: null }),
      ),
    )
    renderApp(<App />, { token: tokenA })
    await userEvent.click(await screen.findByRole('button', { name: 'Select Pokémon' }))
    await userEvent.click(await screen.findByRole('checkbox', { name: 'Select Ivysaur' }))
    await userEvent.click(screen.getByRole('button', { name: 'Add 1 to my collection' }))
    expect(await screen.findByText('Added 1 Pokémon to your collection.')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Log out' }))
    await userEvent.click(screen.getByRole('link', { name: 'Log in' }))
    await logInAs(tokenB, 'misty')

    expect(await screen.findByRole('button', { name: 'Select Pokémon' })).toBeInTheDocument()
    expect(screen.queryByText('Added 1 Pokémon to your collection.')).not.toBeInTheDocument()
  })
})
