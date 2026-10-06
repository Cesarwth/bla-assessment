import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { bulbasaur, pageOf, pikachu } from '../test/fixtures'
import { apiUrl, failWith, mockCatalog, mockCollection, mockCollectionIds, mockDetail } from '../test/handlers'
import { server } from '../test/server'
import { makeToken, renderApp } from '../test/utils'
import { App } from './App'

describe('App shell', () => {
  // The home route is the catalog; these tests are about the shell around it.
  beforeEach(() => {
    mockCatalog(() => pageOf([bulbasaur]))
    mockCollectionIds([]) // logged-in trainers also ask which of these they already have
  })

  it('offers login and register links to anonymous visitors', () => {
    renderApp(<App />)

    expect(screen.getByRole('link', { name: 'Log in' })).toHaveAttribute('href', '/login')
    expect(screen.getByRole('link', { name: 'Create profile' })).toHaveAttribute('href', '/register')
    expect(screen.queryByRole('button', { name: 'Log out' })).not.toBeInTheDocument()
  })

  it('has the main navigation, with the collection only for logged-in trainers', () => {
    const { unmount } = renderApp(<App />)
    const anonymous = screen.getByRole('navigation', { name: 'Main' })
    expect(within(anonymous).getByRole('link', { name: 'Catalog' })).toHaveAttribute('href', '/')
    expect(within(anonymous).queryByRole('link', { name: 'My collection' })).not.toBeInTheDocument()
    unmount()

    renderApp(<App />, { token: makeToken() })
    const nav = screen.getByRole('navigation', { name: 'Main' })
    expect(within(nav).getByRole('link', { name: 'My collection' })).toHaveAttribute('href', '/collection')
  })

  it('shows the user and a logout button once logged in', () => {
    renderApp(<App />, { token: makeToken({ sub: 'ash' }) })

    expect(screen.getByText('ash')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Log out' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Log in' })).not.toBeInTheDocument()
    expect(screen.queryByText('Admin')).not.toBeInTheDocument()
  })

  it('labels admins', () => {
    renderApp(<App />, { token: makeToken({ sub: 'boss', role: 'ADMIN' }) })

    expect(screen.getByText('Admin')).toBeInTheDocument()
  })

  it('logging out returns to the anonymous header', async () => {
    renderApp(<App />, { token: makeToken() })

    await userEvent.click(screen.getByRole('button', { name: 'Log out' }))

    expect(screen.getByRole('link', { name: 'Log in' })).toBeInTheDocument()
  })

  it('shows a friendly page for unknown routes', () => {
    renderApp(<App />, { route: '/nope' })

    expect(screen.getByRole('heading', { name: 'Page not found' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Back to the catalog' })).toHaveAttribute('href', '/')
  })

  it('has a skip link that targets the main landmark', () => {
    renderApp(<App />)

    expect(screen.getByRole('link', { name: 'Skip to content' })).toHaveAttribute('href', '#main')
    expect(screen.getByRole('main')).toHaveAttribute('id', 'main')
  })

  describe('session expiry', () => {
    it('logs the user out, explains why, and sends them to the login page', async () => {
      mockCollection(() => failWith(401, 'UNAUTHORIZED', 'Authentication is required'))
      renderApp(<App />, { route: '/collection', token: makeToken() })

      expect(await screen.findByText(/Your session expired/)).toBeInTheDocument()
      expect(await screen.findByRole('heading', { name: 'Log in' })).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Log out' })).not.toBeInTheDocument() // header back to anonymous
    })

    it('the notice can be dismissed and goes away after logging in', async () => {
      mockCollection(() => failWith(401, 'UNAUTHORIZED', 'x'))
      renderApp(<App />, { route: '/collection', token: makeToken() })
      await screen.findByText(/Your session expired/)

      await userEvent.click(screen.getByRole('button', { name: 'Dismiss' }))

      expect(screen.queryByText(/Your session expired/)).not.toBeInTheDocument()
    })
  })

  describe('page titles', () => {
    it('follow the page the user is on', async () => {
      mockCollection(() => pageOf([], { totalItems: 0 }))
      mockDetail('25', pikachu)
      renderApp(<App />, { route: '/', token: makeToken() })
      await waitFor(() => expect(document.title).toBe('Catalog · Pokédex'))

      await userEvent.click(screen.getByRole('link', { name: 'My collection' }))
      await waitFor(() => expect(document.title).toBe('My collection · Pokédex'))
    })

    it('use the name of the Pokémon on its detail page', async () => {
      mockDetail('25', pikachu)
      renderApp(<App />, { route: '/pokemon/25' })

      await waitFor(() => expect(document.title).toBe('Pikachu · Pokédex'))
    })

    it('cover the login, register and not-found pages', async () => {
      renderApp(<App />, { route: '/login' })
      await waitFor(() => expect(document.title).toBe('Log in · Pokédex'))
    })

    it('say when a page does not exist', async () => {
      renderApp(<App />, { route: '/nope' })
      await waitFor(() => expect(document.title).toBe('Page not found · Pokédex'))
    })
  })

  it('moves the focus to the main content when the page changes, so keyboard and screen-reader users notice', async () => {
    mockCollection(() => pageOf([], { totalItems: 0 }))
    renderApp(<App />, { token: makeToken() })

    await userEvent.click(screen.getByRole('link', { name: 'My collection' }))

    await waitFor(() => expect(screen.getByRole('main')).toHaveFocus())
  })

  it('does not steal the focus on the very first render', () => {
    renderApp(<App />)

    expect(screen.getByRole('main')).not.toHaveFocus()
  })

  it('still reaches the API with the token after an unrelated 401 on a public page was handled', async () => {
    server.use(http.get(apiUrl('/pokemon'), () => HttpResponse.json(pageOf([bulbasaur]))))
    renderApp(<App />, { token: makeToken() })

    expect(await screen.findByText('Bulbasaur')).toBeInTheDocument()
  })
})
