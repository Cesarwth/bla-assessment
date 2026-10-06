import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { App } from '../../app/App'
import { expectNoA11yViolations } from '../../test/axe'
import { ditto, eevee, mew, pikachu, stored } from '../../test/fixtures'
import { apiUrl, failWith, mockDetail } from '../../test/handlers'
import { server } from '../../test/server'
import { makeToken, renderApp } from '../../test/utils'

describe('PokemonDetailPage', () => {
  it('shows a loading state first', async () => {
    server.use(http.get(apiUrl('/pokemon/25'), async () => {
      await delay(50)
      return HttpResponse.json(pikachu)
    }))
    renderApp(<App />, { route: '/pokemon/25' })

    expect(screen.getByRole('status')).toHaveTextContent('Loading Pokémon')
    expect(await screen.findByRole('heading', { name: 'Pikachu' })).toBeInTheDocument()
  })

  it('shows image, name, number, category, types, and the narrative description', async () => {
    mockDetail('25', pikachu)
    renderApp(<App />, { route: '/pokemon/25' })

    expect(await screen.findByRole('heading', { name: 'Pikachu' })).toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'Pikachu' })).toHaveAttribute('src', 'https://img.test/artwork/25.png')
    expect(screen.getByText('#025')).toBeInTheDocument()
    expect(screen.getByText('Mouse Pokémon')).toBeInTheDocument()
    expect(screen.getByText('Electric')).toHaveAttribute('data-type', 'electric')
    expect(screen.getByText(/their electricity could build/)).toBeInTheDocument()
  })

  it('shows weight, height and abilities', async () => {
    mockDetail('25', pikachu)
    renderApp(<App />, { route: '/pokemon/25' })

    await screen.findByRole('heading', { name: 'Pikachu' })

    expect(screen.getByText('6.0 kg')).toBeInTheDocument()
    expect(screen.getByText('0.4 m')).toBeInTheDocument()
    expect(screen.getByText('Static, Lightning rod')).toBeInTheDocument()
  })

  it('shows every core statistic with a bar that reflects its value', async () => {
    mockDetail('25', pikachu)
    renderApp(<App />, { route: '/pokemon/25' })
    await screen.findByRole('heading', { name: 'Pikachu' })

    const stats = screen.getByRole('list', { name: 'Base stats' })
    expect(within(stats).getAllByRole('listitem')).toHaveLength(6)
    const speed = within(stats).getByRole('meter', { name: 'Speed: 90' })
    expect(speed).toHaveAttribute('value', '90')
    expect(speed).toHaveAttribute('max', '255')
    expect(within(stats).getByText('Special attack')).toBeInTheDocument()
  })

  it('draws a linear evolution line and marks the current Pokémon', async () => {
    mockDetail('25', pikachu)
    renderApp(<App />, { route: '/pokemon/25' })
    await screen.findByRole('heading', { name: 'Pikachu' })

    const evolution = screen.getByRole('region', { name: 'Evolution' })
    const links = within(evolution).getAllByRole('link')
    expect(links.map((link) => link.textContent)).toEqual(
      expect.arrayContaining([expect.stringContaining('Pichu'), expect.stringContaining('Pikachu'), expect.stringContaining('Raichu')]),
    )
    expect(within(evolution).getByText('Level up (happiness 220)')).toBeInTheDocument()
    expect(within(evolution).getByText('Use thunder-stone')).toBeInTheDocument()
    expect(within(evolution).getByRole('link', { name: /Pikachu/ })).toHaveAttribute('aria-current', 'page')
    expect(within(evolution).getByRole('link', { name: /Raichu/ })).toHaveAttribute('href', '/pokemon/26')
  })

  it('draws branching evolutions (Eevee) with the requirement of each branch', async () => {
    mockDetail('eevee', eevee)
    renderApp(<App />, { route: '/pokemon/eevee' })
    await screen.findByRole('heading', { name: 'Eevee' })

    const evolution = screen.getByRole('region', { name: 'Evolution' })
    for (const [name, requirement] of [
      ['Vaporeon', 'Use water-stone'],
      ['Jolteon', 'Use thunder-stone'],
      ['Flareon', 'Use fire-stone'],
    ] as const) {
      expect(within(evolution).getByRole('link', { name: new RegExp(name) })).toBeInTheDocument()
      expect(within(evolution).getByText(requirement)).toBeInTheDocument()
    }
  })

  it('says so when a Pokémon does not evolve', async () => {
    mockDetail('mew', mew)
    renderApp(<App />, { route: '/pokemon/mew' })
    await screen.findByRole('heading', { name: 'Mew' })

    expect(screen.getByText('This Pokémon does not evolve.')).toBeInTheDocument()
  })

  it('copes with every optional field missing', async () => {
    mockDetail('ditto', ditto)
    renderApp(<App />, { route: '/pokemon/ditto' })

    expect(await screen.findByRole('heading', { name: 'Ditto' })).toBeInTheDocument()
    expect(screen.getByText('No image available')).toBeInTheDocument()
    expect(screen.getByText('No description available.')).toBeInTheDocument()
    expect(screen.getByText('This Pokémon does not evolve.')).toBeInTheDocument()
  })

  it('falls back to the sprite when there is no official artwork', async () => {
    mockDetail('25', { ...pikachu, imageUrl: null })
    renderApp(<App />, { route: '/pokemon/25' })

    expect(await screen.findByRole('img', { name: 'Pikachu' })).toHaveAttribute('src', 'https://img.test/sprites/25.png')
  })

  it('renders descriptions as plain text, never as HTML', async () => {
    const hostile = '<img src="x" onerror="alert(1)"> <b>bold</b>'
    mockDetail('25', { ...pikachu, description: hostile })
    const { container } = renderApp(<App />, { route: '/pokemon/25' })

    expect(await screen.findByText(hostile)).toBeInTheDocument()
    expect(container.querySelector('img[src="x"]')).toBeNull()
    expect(container.querySelector('main b')).toBeNull()
  })

  it('shows a friendly page when the Pokémon does not exist', async () => {
    mockDetail('missingno', failWith(404, 'POKEMON_NOT_FOUND', 'Pokemon not found: missingno'))
    renderApp(<App />, { route: '/pokemon/missingno' })

    expect(await screen.findByRole('heading', { name: /couldn.t find/i })).toBeInTheDocument()
    expect(screen.getByText(/missingno/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Back to the catalog' })).toHaveAttribute('href', '/')
  })

  it('treats a malformed identifier like a missing Pokémon', async () => {
    mockDetail('bad!', failWith(400, 'INVALID_POKEMON_IDENTIFIER', 'Pokemon id or name is not valid'))
    renderApp(<App />, { route: '/pokemon/bad!' })

    expect(await screen.findByRole('heading', { name: /couldn.t find/i })).toBeInTheDocument()
  })

  it('reports an outage with a retry button that works', async () => {
    let calls = 0
    server.use(http.get(apiUrl('/pokemon/25'), () => {
      calls += 1
      return calls === 1 ? failWith(504, 'CATALOG_TIMEOUT', 'The Pokemon catalog took too long to answer') : HttpResponse.json(pikachu)
    }))
    renderApp(<App />, { route: '/pokemon/25' })

    expect(await screen.findByRole('alert')).toHaveTextContent('not answering right now')
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))

    expect(await screen.findByRole('heading', { name: 'Pikachu' })).toBeInTheDocument()
  })

  it('lets you move between evolutions', async () => {
    mockDetail('25', pikachu)
    mockDetail('26', { ...pikachu, id: 26, name: 'raichu', evolutionChain: pikachu.evolutionChain })
    renderApp(<App />, { route: '/pokemon/25' })
    await screen.findByRole('heading', { name: 'Pikachu' })

    await userEvent.click(within(screen.getByRole('region', { name: 'Evolution' })).getByRole('link', { name: /Raichu/ }))

    expect(await screen.findByRole('heading', { name: 'Raichu' })).toBeInTheDocument()
  })

  it('has no accessibility violations', async () => {
    mockDetail('133', eevee)
    const { container } = renderApp(<App />, { route: '/pokemon/133' })
    await screen.findByRole('heading', { name: 'Eevee' })

    await expectNoA11yViolations(container)
  })

  describe('adding to the collection', () => {
    const syncReturns = (status: number, body: object) =>
      server.use(http.post(apiUrl('/collection/25/sync'), () => HttpResponse.json(body, { status })))
    const notInCollection = () =>
      server.use(http.get(apiUrl('/collection/25'), () => failWith(404, 'POKEMON_NOT_STORED', 'Not in the collection')))
    const inCollection = (ownerId: number | null) =>
      server.use(http.get(apiUrl('/collection/25'), () => HttpResponse.json(stored(25, 'pikachu', { ownerId }))))

    it('asks anonymous visitors to log in and remembers where they were', async () => {
      mockDetail('25', pikachu)
      renderApp(<App />, { route: '/pokemon/25' })
      await screen.findByRole('heading', { name: 'Pikachu' })

      expect(screen.queryByRole('button', { name: 'Add to my collection' })).not.toBeInTheDocument()
      expect(screen.getByRole('link', { name: 'Log in to add this Pokémon to the collection' })).toHaveAttribute('href', '/login')
    })

    it('adds it and links to the new entry', async () => {
      mockDetail('25', pikachu)
      notInCollection()
      syncReturns(201, stored(25, 'pikachu'))
      renderApp(<App />, { route: '/pokemon/25', token: makeToken() })
      await screen.findByRole('heading', { name: 'Pikachu' })

      await userEvent.click(await screen.findByRole('button', { name: 'Add to my collection' }))

      expect(await screen.findByRole('status')).toHaveTextContent('Added to the collection.')
      expect(screen.getByRole('link', { name: 'View it in the collection' })).toHaveAttribute('href', '/collection/25')
      expect(screen.queryByRole('button', { name: 'Add to my collection' })).not.toBeInTheDocument()
    })

    it('offers no button when you already added it', async () => {
      mockDetail('25', pikachu)
      inCollection(1)
      renderApp(<App />, { route: '/pokemon/25', token: makeToken({ uid: 1 }) })
      await screen.findByRole('heading', { name: 'Pikachu' })

      expect(await screen.findByText(/^In the collection/)).toHaveTextContent('In the collection, added by you.')
      expect(screen.queryByRole('button', { name: 'Add to my collection' })).not.toBeInTheDocument()
      expect(screen.getByRole('link', { name: 'View it in the collection' })).toHaveAttribute('href', '/collection/25')
    })

    it('offers no button when another trainer already has it', async () => {
      mockDetail('25', pikachu)
      inCollection(2)
      renderApp(<App />, { route: '/pokemon/25', token: makeToken({ uid: 1 }) })
      await screen.findByRole('heading', { name: 'Pikachu' })

      expect(await screen.findByText(/^In the collection/)).toHaveTextContent('In the collection, added by another trainer.')
      expect(screen.queryByRole('button', { name: 'Add to my collection' })).not.toBeInTheDocument()
    })

    it('says so when the entry has no owner', async () => {
      mockDetail('25', pikachu)
      inCollection(null)
      renderApp(<App />, { route: '/pokemon/25', token: makeToken({ uid: 1 }) })
      await screen.findByRole('heading', { name: 'Pikachu' })

      expect(await screen.findByText(/^In the collection/)).toHaveTextContent('In the collection, with no owner (only admins can edit it).')
      expect(screen.queryByRole('button', { name: 'Add to my collection' })).not.toBeInTheDocument()
    })

    it('disables the button while adding', async () => {
      mockDetail('25', pikachu)
      notInCollection()
      server.use(
        http.post(apiUrl('/collection/25/sync'), async () => {
          await delay(60)
          return HttpResponse.json(stored(25, 'pikachu'), { status: 201 })
        }),
      )
      renderApp(<App />, { route: '/pokemon/25', token: makeToken() })
      await screen.findByRole('heading', { name: 'Pikachu' })

      await userEvent.click(await screen.findByRole('button', { name: 'Add to my collection' }))

      expect(screen.getByRole('button', { name: 'Adding…' })).toBeDisabled()
      await screen.findByRole('status')
    })

    it('reports an outage while adding without losing the page', async () => {
      mockDetail('25', pikachu)
      notInCollection()
      server.use(http.post(apiUrl('/collection/25/sync'), () => failWith(502, 'CATALOG_UNAVAILABLE', 'x')))
      renderApp(<App />, { route: '/pokemon/25', token: makeToken() })
      await screen.findByRole('heading', { name: 'Pikachu' })

      await userEvent.click(await screen.findByRole('button', { name: 'Add to my collection' }))

      expect(await screen.findByRole('alert')).toHaveTextContent('not answering right now')
      expect(screen.getByRole('heading', { name: 'Pikachu' })).toBeInTheDocument()
    })

    it('does not offer to add when it cannot tell whether it is already there, and lets you retry', async () => {
      mockDetail('25', pikachu)
      server.use(http.get(apiUrl('/collection/25'), () => failWith(500, 'INTERNAL_ERROR', 'x')))
      renderApp(<App />, { route: '/pokemon/25', token: makeToken() })

      expect(await screen.findByRole('alert')).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Add to my collection' })).not.toBeInTheDocument()

      notInCollection()
      await userEvent.click(screen.getByRole('button', { name: 'Try again' }))

      expect(await screen.findByRole('button', { name: 'Add to my collection' })).toBeInTheDocument()
    })
  })
})
