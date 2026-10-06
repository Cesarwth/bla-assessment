import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { makeToken, renderApp } from '../test/utils'
import { App } from './App'

describe('App shell', () => {
  it('offers login and register links to anonymous visitors', () => {
    renderApp(<App />)

    expect(screen.getByRole('link', { name: 'Log in' })).toHaveAttribute('href', '/login')
    expect(screen.getByRole('link', { name: 'Register' })).toHaveAttribute('href', '/register')
    expect(screen.queryByRole('button', { name: 'Log out' })).not.toBeInTheDocument()
  })

  it('has the main navigation', () => {
    renderApp(<App />)

    const nav = screen.getByRole('navigation', { name: 'Main' })
    expect(within(nav).getByRole('link', { name: 'Catalog' })).toHaveAttribute('href', '/')
    expect(within(nav).getByRole('link', { name: 'Collection' })).toHaveAttribute('href', '/collection')
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
})
