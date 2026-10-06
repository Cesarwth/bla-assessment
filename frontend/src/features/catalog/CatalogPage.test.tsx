import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { App } from '../../app/App'
import { expectNoA11yViolations } from '../../test/axe'
import { bulbasaur, pageOf, pikachu, summary } from '../../test/fixtures'
import { failWith, mockCatalog, mockCollectionIds, mockDetail } from '../../test/handlers'
import { makeToken, renderApp } from '../../test/utils'

const firstPage = () => pageOf([bulbasaur, summary(2, 'ivysaur', ['grass', 'poison']), summary(3, 'venusaur', ['grass'])])

describe('CatalogPage', () => {
  it('shows a loading state and then the cards', async () => {
    mockCatalog(() => {
      return firstPage()
    })
    renderApp(<App />)

    expect(screen.getByRole('status')).toHaveTextContent('Loading Pokémon')
    expect(await screen.findByRole('link', { name: /Bulbasaur/ })).toBeInTheDocument()
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('asks the API for the first page with the default size', async () => {
    const requests: string[] = []
    mockCatalog(firstPage, requests)
    renderApp(<App />)

    await screen.findByText('Bulbasaur')

    expect(requests).toEqual(['page=0&size=12'])
  })

  it('each card shows sprite, category (types), mass and abilities and links to the detail', async () => {
    mockCatalog(firstPage)
    renderApp(<App />)

    const card = (await screen.findByRole('link', { name: /Bulbasaur/ })).closest('article')!
    expect(card).not.toBeNull()
    expect(within(card).getByRole('img', { name: 'Bulbasaur' })).toHaveAttribute('src', 'https://img.test/sprites/1.png')
    expect(within(card).getByText('#001')).toBeInTheDocument()
    expect(within(card).getByText('Grass')).toHaveAttribute('data-type', 'grass')
    expect(within(card).getByText('Poison')).toBeInTheDocument()
    expect(within(card).getByText('6.9 kg')).toBeInTheDocument()
    expect(within(card).getByText('Overgrow, Chlorophyll')).toBeInTheDocument()
    expect(within(card).getByRole('link', { name: /Bulbasaur/ })).toHaveAttribute('href', '/pokemon/1')
  })

  it('copes with a missing sprite', async () => {
    mockCatalog(() => pageOf([{ ...bulbasaur, spriteUrl: null }]))
    renderApp(<App />)

    expect(await screen.findByText('No image')).toBeInTheDocument()
  })

  it('shows where you are and offers links to the neighbouring pages', async () => {
    mockCatalog(() => pageOf([bulbasaur], { totalItems: 30, size: 12 }))
    renderApp(<App />)

    expect(await screen.findByText('Page 1 of 3')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Next page' })).toHaveAttribute('href', '/?page=2&size=12')
    expect(screen.queryByRole('link', { name: 'Previous page' })).not.toBeInTheDocument()
    expect(screen.getByText('Previous')).toHaveAttribute('aria-disabled', 'true')
  })

  it('moving to the next page requests it and keeps the previous cards on screen meanwhile', async () => {
    const requests: string[] = []
    mockCatalog(async (params) => {
      if (params.get('page') === '1') {
        await delay(80)
        return pageOf([summary(13, 'caterpie', ['bug'])], { page: 1, totalItems: 30 })
      }
      return pageOf([bulbasaur], { totalItems: 30 })
    }, requests)
    renderApp(<App />)
    await screen.findByText('Bulbasaur')

    await userEvent.click(screen.getByRole('link', { name: 'Next page' }))

    expect(screen.getByText('Bulbasaur')).toBeInTheDocument() // no flash of an empty page
    expect(await screen.findByText('Caterpie')).toBeInTheDocument()
    expect(screen.getByText('Page 2 of 3')).toBeInTheDocument()
    expect(screen.queryByText('Next')).toBeInTheDocument()
    expect(requests).toEqual(['page=0&size=12', 'page=1&size=12'])
  })

  it('the last page has no next link', async () => {
    mockCatalog(() => pageOf([bulbasaur], { page: 2, totalItems: 30 }))
    renderApp(<App />, { route: '/?page=3' })

    expect(await screen.findByText('Page 3 of 3')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Next page' })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Previous page' })).toHaveAttribute('href', '/?page=2&size=12')
  })

  it('changing the page size goes back to the first page', async () => {
    const requests: string[] = []
    mockCatalog((params) => pageOf([bulbasaur], { page: Number(params.get('page')), size: Number(params.get('size')) }), requests)
    renderApp(<App />, { route: '/?page=4&size=12' })
    await screen.findByText('Bulbasaur')

    await userEvent.selectOptions(screen.getByLabelText('Per page'), '24')

    await waitFor(() => expect(requests.at(-1)).toBe('page=0&size=24'))
  })

  it('ignores nonsense in the URL instead of sending it to the API', async () => {
    const requests: string[] = []
    mockCatalog(firstPage, requests)
    renderApp(<App />, { route: '/?page=-3&size=999' })

    await screen.findByText('Bulbasaur')

    expect(requests).toEqual(['page=0&size=12'])
  })

  it('explains an empty page and offers a way back', async () => {
    mockCatalog(() => pageOf([], { page: 99, totalItems: 30 }))
    renderApp(<App />, { route: '/?page=100' })

    expect(await screen.findByText('No Pokémon on this page')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Go to the first page' })).toHaveAttribute('href', '/?page=1&size=12')
  })

  it('says the catalog is down in plain words and lets the user retry', async () => {
    let calls = 0
    mockCatalog(() => {
      calls += 1
      return calls === 1 ? failWith(502, 'CATALOG_UNAVAILABLE', 'The Pokemon catalog is temporarily unavailable') : firstPage()
    })
    renderApp(<App />)

    expect(await screen.findByRole('alert')).toHaveTextContent('not answering right now')
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))

    expect(await screen.findByText('Bulbasaur')).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('reports a network failure', async () => {
    mockCatalog(() => HttpResponse.error())
    renderApp(<App />)

    expect(await screen.findByRole('alert')).toHaveTextContent('Cannot reach the server')
  })

  describe('go to a Pokémon', () => {
    it('opens the detail page of a name typed in any case', async () => {
      mockCatalog(firstPage)
      mockDetail('pikachu', pikachu)
      renderApp(<App />)
      await screen.findByText('Bulbasaur')

      await userEvent.type(screen.getByLabelText('Find a Pokémon by name or number'), '  Pikachu {Enter}')

      expect(await screen.findByRole('heading', { name: 'Pikachu' })).toBeInTheDocument()
    })

    it('accepts numbers', async () => {
      mockCatalog(firstPage)
      mockDetail('25', pikachu)
      renderApp(<App />)
      await screen.findByText('Bulbasaur')

      await userEvent.type(screen.getByLabelText('Find a Pokémon by name or number'), '25')
      await userEvent.click(screen.getByRole('button', { name: 'Go' }))

      expect(await screen.findByRole('heading', { name: 'Pikachu' })).toBeInTheDocument()
    })

    it.each(['', '   ', '../admin', 'pika chu', 'a/b'])('refuses %j with an inline hint and does not navigate', async (input) => {
      mockCatalog(firstPage)
      renderApp(<App />)
      await screen.findByText('Bulbasaur')

      if (input.trim()) await userEvent.type(screen.getByLabelText('Find a Pokémon by name or number'), input)
      await userEvent.click(screen.getByRole('button', { name: 'Go' }))

      expect(screen.getByRole('alert')).toHaveTextContent('Use a name or a number, like pikachu or 25')
      expect(screen.getByLabelText('Find a Pokémon by name or number')).toHaveAttribute('aria-invalid', 'true')
      expect(screen.getByText('Bulbasaur')).toBeInTheDocument()
    })
  })

  it('has no accessibility violations', async () => {
    mockCatalog(firstPage)
    const { container } = renderApp(<App />)
    await screen.findByText('Bulbasaur')

    await expectNoA11yViolations(container)
  })
  describe('what you already collected', () => {
    it('marks the Pokémon that are in your collection', async () => {
      mockCatalog(firstPage)
      mockCollectionIds([1, 3])
      renderApp(<App />, { token: makeToken() })

      const bulbasaur = (await screen.findByRole('link', { name: /Bulbasaur/ })).closest('article')!
      expect(await within(bulbasaur).findByText('In my collection')).toBeInTheDocument()
      const venusaur = screen.getByRole('link', { name: /Venusaur/ }).closest('article')!
      expect(within(venusaur).getByText('In my collection')).toBeInTheDocument()
      const ivysaur = screen.getByRole('link', { name: /Ivysaur/ }).closest('article')!
      expect(within(ivysaur).queryByText('In my collection')).not.toBeInTheDocument()
    })

    it('shows no marks, and asks for nothing, to visitors who are not logged in', async () => {
      const requests: string[] = []
      mockCatalog(firstPage)
      mockCollectionIds([1], requests)
      renderApp(<App />)

      await screen.findByText('Bulbasaur')

      expect(screen.queryByText('In my collection')).not.toBeInTheDocument()
      expect(requests).toEqual([])
    })

    it('still shows the whole catalog when the marks cannot be loaded', async () => {
      mockCatalog(firstPage)
      mockCollectionIds(failWith(500, 'INTERNAL_ERROR', 'x'))
      renderApp(<App />, { token: makeToken() })

      expect(await screen.findByRole('link', { name: /Bulbasaur/ })).toBeInTheDocument()
      expect(screen.queryByText('In my collection')).not.toBeInTheDocument()
      expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    })

    it('has no accessibility violations with the marks', async () => {
      mockCatalog(firstPage)
      mockCollectionIds([1])
      const { container } = renderApp(<App />, { token: makeToken() })
      await screen.findByText('In my collection')

      await expectNoA11yViolations(container)
    })
  })
})
