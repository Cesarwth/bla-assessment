import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { App } from '../../app/App'
import { expectNoA11yViolations } from '../../test/axe'
import { pageOf, storedSummary } from '../../test/fixtures'
import { apiUrl, errorBody, failWith, mockCollection } from '../../test/handlers'
import { server } from '../../test/server'
import { makeToken, renderApp } from '../../test/utils'

const entries = () =>
  pageOf([
    storedSummary(25, 'pikachu', { nickname: 'Pika', region: 'Kanto', tags: ['starter', 'electric'] }),
    storedSummary(26, 'raichu'),
  ], { totalItems: 2 })

describe('CollectionPage', () => {
  it('is the trainer’s own list, with the way to import more', async () => {
    mockCollection(entries)
    renderApp(<App />, { route: '/collection', token: makeToken() })

    expect(await screen.findByRole('heading', { name: 'My collection' })).toBeInTheDocument()
    expect(screen.getByText(/Your Pokémon\./)).toBeInTheDocument()
    expect(screen.getByRole('form', { name: 'Import Pokémon by number' })).toBeInTheDocument()
    expect(screen.queryByText('Yours')).not.toBeInTheDocument()
  })

  it('sends anonymous visitors to the login page and brings them back after logging in', async () => {
    mockCollection(entries)
    server.use(
      http.post(apiUrl('/auth/login'), () => HttpResponse.json({ token: makeToken(), tokenType: 'Bearer', expiresAt: 'x' })),
    )
    renderApp(<App />, { route: '/collection?page=1&size=12' })

    expect(await screen.findByRole('heading', { name: 'Log in' })).toBeInTheDocument()
    await userEvent.type(screen.getByLabelText('Username'), 'ash')
    await userEvent.type(screen.getByLabelText('Password'), 'Pikachu123')
    await userEvent.click(screen.getByRole('button', { name: 'Log in' }))

    expect(await screen.findByRole('heading', { name: 'My collection' })).toBeInTheDocument()
    expect(await screen.findByRole('link', { name: /Pikachu/ })).toBeInTheDocument()
  })

  it('shows a loading state and then the entries', async () => {
    mockCollection(entries)
    renderApp(<App />, { route: '/collection', token: makeToken() })

    expect(screen.getByRole('status')).toHaveTextContent('Loading')
    expect(await screen.findByRole('link', { name: /Pikachu/ })).toBeInTheDocument()
  })

  it('shows what makes an entry ours: nickname, region and tags', async () => {
    mockCollection(entries)
    renderApp(<App />, { route: '/collection', token: makeToken() })

    const card = (await screen.findByRole('link', { name: /Pikachu/ })).closest('article')!
    expect(within(card).getByText('“Pika”')).toBeInTheDocument()
    expect(within(card).getByText('Kanto')).toBeInTheDocument()
    expect(within(card).getByText('starter')).toBeInTheDocument()
    expect(within(card).getByText('electric')).toBeInTheDocument()
    expect(within(card).getByRole('link', { name: /Pikachu/ })).toHaveAttribute('href', '/collection/25')
  })

  it('an entry without local data just shows the Pokémon', async () => {
    mockCollection(entries)
    renderApp(<App />, { route: '/collection', token: makeToken() })

    const card = (await screen.findByRole('link', { name: /Raichu/ })).closest('article')!
    expect(within(card).queryByText(/“/)).not.toBeInTheDocument()
  })

  it('pages through the collection like the catalog does', async () => {
    const requests: string[] = []
    mockCollection(() => pageOf([storedSummary(25, 'pikachu')], { totalItems: 30 }), requests)
    renderApp(<App />, { route: '/collection', token: makeToken() })

    expect(await screen.findByText('Page 1 of 3')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Next page' })).toHaveAttribute('href', '/collection?page=2&size=12')
    expect(requests).toEqual(['page=0&size=12'])
  })

  it('invites people to fill an empty collection', async () => {
    mockCollection(() => pageOf([], { totalItems: 0 }))
    renderApp(<App />, { route: '/collection', token: makeToken() })

    expect(await screen.findByText('Your collection is empty')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Browse the catalog' })).toHaveAttribute('href', '/')
  })

  it('explains a page beyond the end and offers a way back, like the catalog does', async () => {
    mockCollection(() => pageOf([], { page: 99, totalItems: 30 }))
    renderApp(<App />, { route: '/collection?page=100', token: makeToken() })

    expect(await screen.findByText('No entries on this page')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Go to the first page' })).toHaveAttribute('href', '/collection?page=1&size=12')
  })

  it('reports failures and lets the user retry', async () => {
    let calls = 0
    mockCollection(() => {
      calls += 1
      return calls === 1 ? failWith(500, 'INTERNAL_ERROR', 'Unexpected error') : entries()
    })
    renderApp(<App />, { route: '/collection', token: makeToken() })

    expect(await screen.findByRole('alert')).toHaveTextContent('Unexpected error')
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))

    expect(await screen.findByRole('link', { name: /Pikachu/ })).toBeInTheDocument()
  })

  describe('importing a range', () => {
    const syncRangeReturns = (report: object) => server.use(http.post(apiUrl('/collection/sync'), () => HttpResponse.json(report)))

    async function importRange(from: string, to: string) {
      const form = screen.getByRole('form', { name: 'Import Pokémon by number' })
      for (const [label, value] of [['From', from], ['To', to]] as const) {
        await userEvent.clear(within(form).getByLabelText(label))
        if (value) await userEvent.type(within(form).getByLabelText(label), value)
      }
      await userEvent.click(within(form).getByRole('button', { name: 'Import' }))
    }

    it('sends the range, shows the report with the ids that could not be imported and refreshes the list', async () => {
      const requests: string[] = []
      let body: unknown
      mockCollection(entries, requests)
      server.use(
        http.post(apiUrl('/collection/sync'), async ({ request }) => {
          body = await request.json()
          return HttpResponse.json({
            created: 2,
            updated: 1,
            failures: [{ identifier: '1026', reason: 'Not found upstream' }],
            abortedReason: null,
          })
        }),
      )
      renderApp(<App />, { route: '/collection', token: makeToken() })
      await screen.findByRole('link', { name: /Pikachu/ })

      await importRange('1024', '1026')

      expect(await screen.findByText('Added 2, refreshed 1.')).toBeInTheDocument()
      expect(body).toEqual({ from: 1024, to: 1026 })
      expect(screen.getByRole('list', { name: 'Not imported' })).toHaveTextContent('#1026: Not found upstream')
      expect(requests).toHaveLength(2) // the list was loaded again so new entries show up
    })

    it('says so when the import stopped early', async () => {
      mockCollection(entries)
      syncRangeReturns({ created: 1, updated: 0, failures: [], abortedReason: 'Catalog unavailable at id 2' })
      renderApp(<App />, { route: '/collection', token: makeToken() })
      await screen.findByRole('link', { name: /Pikachu/ })

      await importRange('1', '5')

      expect(await screen.findByText('The import stopped early: Catalog unavailable at id 2.')).toBeInTheDocument()
    })

    it.each([
      ['0', '5'],
      ['5', '4'],
      ['1', '21'],
      ['', '3'],
    ])('refuses the range %j to %j before calling the API', async (from, to) => {
      mockCollection(entries)
      renderApp(<App />, { route: '/collection', token: makeToken() })
      await screen.findByRole('link', { name: /Pikachu/ })

      await importRange(from, to)

      expect(screen.getByRole('alert')).toHaveTextContent('Choose a range of at most 20 numbers, starting at 1 or higher.')
    })

    it('shows API failures such as an outage', async () => {
      mockCollection(entries)
      server.use(http.post(apiUrl('/collection/sync'), () => HttpResponse.json(errorBody(502, 'CATALOG_UNAVAILABLE', 'x'), { status: 502 })))
      renderApp(<App />, { route: '/collection', token: makeToken() })
      await screen.findByRole('link', { name: /Pikachu/ })

      await importRange('1', '3')

      expect(await screen.findByRole('alert')).toHaveTextContent('not answering right now')
    })
  })

  it('has no accessibility violations', async () => {
    mockCollection(entries)
    const { container } = renderApp(<App />, { route: '/collection', token: makeToken() })
    await screen.findByRole('link', { name: /Pikachu/ })

    await expectNoA11yViolations(container)
  })
})
