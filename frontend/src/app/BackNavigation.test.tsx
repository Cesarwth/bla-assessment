import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { expectNoA11yViolations } from '../test/axe'
import { pageOf, pikachu, stored, storedSummary, summary } from '../test/fixtures'
import {
  mockCatalog,
  mockCollection,
  mockCollectionIds,
  mockDetail,
  mockEntry,
} from '../test/handlers'
import { makeToken, renderApp } from '../test/utils'
import { App } from './App'

const charmander = summary(4, 'charmander', ['fire'])
const charmanderDetail = { ...pikachu, id: 4, name: 'charmander', types: ['fire'] }

describe('Back arrow on the detail pages', () => {
  describe('catalog detail', () => {
    it('returns to the list exactly as it was left: same page, size and filter', async () => {
      mockCatalog(() => pageOf([charmander], { page: 1, totalItems: 60 }))
      mockDetail('4', charmanderDetail)
      renderApp(<App />, { route: '/?page=2&size=12&q=char' })

      await userEvent.click(await screen.findByRole('link', { name: 'Charmander' }))
      expect(await screen.findByRole('heading', { name: 'Charmander' })).toBeInTheDocument()
      await userEvent.click(screen.getByRole('link', { name: 'Back' }))

      expect(await screen.findByRole('heading', { name: 'Catalog' })).toBeInTheDocument()
      expect(screen.getByRole('textbox', { name: 'Filter by name or number' })).toHaveValue('char')
      expect(await screen.findByText('Page 2 of 5')).toBeInTheDocument()
    })

    it('returns to my collection when that is where the user came from', async () => {
      mockCollection(() => pageOf([storedSummary(25, 'pikachu')], { totalItems: 1 }))
      mockCollectionIds([25])
      mockEntry(25, stored(25, 'pikachu'))
      mockDetail('25', pikachu)
      renderApp(<App />, { route: '/collection', token: makeToken() })

      await userEvent.click(await screen.findByRole('link', { name: 'Pikachu' }))
      await userEvent.click(await screen.findByRole('link', { name: 'See it in the catalog' }))
      await screen.findByRole('heading', { name: 'Pikachu' })
      await userEvent.click(screen.getByRole('link', { name: 'Back' }))

      expect(await screen.findByRole('heading', { name: 'Pikachu' })).toBeInTheDocument()
      expect(screen.getByRole('link', { name: 'See it in the catalog' })).toBeInTheDocument()
    })

    it('is a plain link to the catalog when there is no history (deep link or reload)', async () => {
      mockDetail('25', pikachu)
      renderApp(<App />, { route: '/pokemon/25' })

      await screen.findByRole('heading', { name: 'Pikachu' })
      expect(screen.getByRole('link', { name: 'Back to the catalog' })).toHaveAttribute('href', '/')
      expect(screen.queryByRole('link', { name: 'Back' })).not.toBeInTheDocument()
    })

    it('shows the arrow as decoration and the word "Back" as the visible text', async () => {
      mockDetail('25', pikachu)
      renderApp(<App />, { route: '/pokemon/25' })

      const back = await screen.findByRole('link', { name: 'Back to the catalog' })
      expect(back.querySelector('svg')).toHaveAttribute('aria-hidden', 'true')
      expect(back).toHaveTextContent(/^Back/)
    })

    it('has no accessibility violations', async () => {
      mockDetail('25', pikachu)
      const { container } = renderApp(<App />, { route: '/pokemon/25' })
      await screen.findByRole('link', { name: 'Back to the catalog' })
      await expectNoA11yViolations(container)
    })
  })

  describe('collection entry', () => {
    const asTrainer = { token: makeToken() }

    it('returns to my collection page the user came from', async () => {
      mockCollection(() => pageOf([storedSummary(25, 'pikachu')], { page: 1, totalItems: 30 }))
      mockCollectionIds([25])
      mockEntry(25, stored(25, 'pikachu'))
      renderApp(<App />, { route: '/collection?page=2&size=12', ...asTrainer })

      await userEvent.click(await screen.findByRole('link', { name: 'Pikachu' }))
      await screen.findByRole('heading', { name: 'Pikachu' })
      await userEvent.click(screen.getByRole('link', { name: 'Back' }))

      expect(await screen.findByRole('heading', { name: 'My collection' })).toBeInTheDocument()
      expect(await screen.findByText('Page 2 of 3')).toBeInTheDocument()
    })

    it('is a plain link to my collection on a deep link', async () => {
      mockEntry(25, stored(25, 'pikachu'))
      renderApp(<App />, { route: '/collection/25', ...asTrainer })

      expect(await screen.findByRole('link', { name: 'Back to my collection' })).toHaveAttribute(
        'href',
        '/collection',
      )
    })

    it('has no accessibility violations', async () => {
      mockEntry(25, stored(25, 'pikachu'))
      const { container } = renderApp(<App />, { route: '/collection/25', ...asTrainer })
      await screen.findByRole('link', { name: 'Back to my collection' })
      await expectNoA11yViolations(container)
    })
  })
})
