import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { App } from '../../app/App'
import { expectNoA11yViolations } from '../../test/axe'
import { pageOf, storedSummary } from '../../test/fixtures'
import { apiUrl, failWith, mockCollection } from '../../test/handlers'
import { server } from '../../test/server'
import { makeToken, renderApp } from '../../test/utils'

const entries = () =>
  pageOf([
    storedSummary(25, 'pikachu', { nickname: 'Pika', region: 'Kanto', tags: ['starter', 'electric'] }),
    storedSummary(26, 'raichu'),
  ], { totalItems: 2 })

describe('CollectionPage', () => {
  it('is the trainer’s own list, and points to the catalog to add more', async () => {
    mockCollection(entries)
    renderApp(<App />, { route: '/collection', token: makeToken() })

    expect(await screen.findByRole('heading', { name: 'My collection' })).toBeInTheDocument()
    expect(screen.getByText(/Your Pokémon\./)).toBeInTheDocument()
    expect(screen.getByText(/Select Pokémon in the catalog to add several at once/)).toBeInTheDocument()
    expect(screen.queryByRole('form')).not.toBeInTheDocument()
    expect(screen.queryByText(/import/i)).not.toBeInTheDocument()
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

  it('has no accessibility violations', async () => {
    mockCollection(entries)
    const { container } = renderApp(<App />, { route: '/collection', token: makeToken() })
    await screen.findByRole('link', { name: /Pikachu/ })

    await expectNoA11yViolations(container)
  })
})
