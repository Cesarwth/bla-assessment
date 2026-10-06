import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { App } from '../../app/App'
import { expectNoA11yViolations } from '../../test/axe'
import { bulbasaur, pageOf, summary } from '../../test/fixtures'
import { apiUrl, failWith, mockCatalog, mockCollectionIds } from '../../test/handlers'
import { server } from '../../test/server'
import { makeToken, renderApp } from '../../test/utils'

const firstPage = () => pageOf([bulbasaur, summary(2, 'ivysaur', ['grass']), summary(3, 'venusaur', ['grass'])])

const checkbox = (name: string) => screen.findByRole('checkbox', { name: `Select ${name}` })

/** Answers POST /collection/sync with `respond` and records the bodies. */
function mockSync(respond: () => Response | Promise<Response>, bodies: unknown[] = []) {
  server.use(
    http.post(apiUrl('/collection/sync'), async ({ request }) => {
      bodies.push(await request.json())
      return respond()
    }),
  )
}

const report = (over: object = {}) => HttpResponse.json({ created: 2, updated: 0, failures: [], abortedReason: null, ...over })

describe('selecting Pokémon in the catalog', () => {
  it('gives every not yet collected card a checkbox, and none to the collected ones', async () => {
    mockCatalog(firstPage)
    mockCollectionIds([1])
    renderApp(<App />, { token: makeToken() })

    expect(await checkbox('Ivysaur')).not.toBeChecked()
    expect(screen.getByRole('checkbox', { name: 'Select Venusaur' })).toBeInTheDocument()
    expect(screen.queryByRole('checkbox', { name: 'Select Bulbasaur' })).not.toBeInTheDocument()
    expect(screen.getByText('In my collection')).toBeInTheDocument()
  })

  it('shows visitors exactly the old catalog: no checkboxes, no toolbar, no request', async () => {
    const requests: string[] = []
    mockCatalog(firstPage)
    mockCollectionIds([1], requests)
    renderApp(<App />)

    await screen.findByText('Bulbasaur')

    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Select Pokémon to add' })).not.toBeInTheDocument()
    expect(requests).toEqual([])
  })

  it('ticking does not navigate, and the card stays a link to the detail', async () => {
    mockCatalog(firstPage)
    mockCollectionIds([])
    renderApp(<App />, { token: makeToken() })

    await userEvent.click(await checkbox('Ivysaur'))

    expect(screen.getByRole('checkbox', { name: 'Select Ivysaur' })).toBeChecked()
    expect(screen.getByRole('heading', { name: 'Catalog' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Ivysaur/ })).toHaveAttribute('href', '/pokemon/2')
  })

  it('counts the selection and unticking removes it', async () => {
    mockCatalog(firstPage)
    mockCollectionIds([])
    renderApp(<App />, { token: makeToken() })

    await userEvent.click(await checkbox('Ivysaur'))
    expect(screen.getByText('1 selected')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Add 1 to my collection' })).toBeInTheDocument()

    await userEvent.click(screen.getByRole('checkbox', { name: 'Select Ivysaur' }))
    expect(screen.getByText('0 selected')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^Add/ })).not.toBeInTheDocument()
  })

  it('selects all not yet collected cards of the page, and clears', async () => {
    mockCatalog(firstPage)
    mockCollectionIds([1])
    renderApp(<App />, { token: makeToken() })
    await checkbox('Ivysaur')

    await userEvent.click(screen.getByRole('button', { name: 'Select all on this page' }))
    expect(screen.getByText('2 selected')).toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: 'Select Ivysaur' })).toBeChecked()
    expect(screen.getByRole('checkbox', { name: 'Select Venusaur' })).toBeChecked()

    await userEvent.click(screen.getByRole('button', { name: 'Clear' }))
    expect(screen.getByText('0 selected')).toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: 'Select Ivysaur' })).not.toBeChecked()
  })

  it('keeps the selection while paging', async () => {
    mockCatalog((params) =>
      params.get('page') === '1' ? pageOf([summary(4, 'charmander')], { page: 1 }) : firstPage(),
    )
    mockCollectionIds([])
    renderApp(<App />, { token: makeToken() })
    await userEvent.click(await checkbox('Ivysaur'))

    await userEvent.click(screen.getByRole('link', { name: /Next/ }))
    await userEvent.click(await checkbox('Charmander'))

    expect(screen.getByText('2 selected')).toBeInTheDocument()
  })

  describe('adding', () => {
    it('sends exactly the ids in the order ticked, reports, clears and flips the cards', async () => {
      const bodies: unknown[] = []
      let collected: number[] = []
      mockCatalog(firstPage)
      mockCollectionIds(() => collected)
      mockSync(() => {
        collected = [3, 1]
        return report()
      }, bodies)
      renderApp(<App />, { token: makeToken() })
      await userEvent.click(await checkbox('Venusaur'))
      await userEvent.click(screen.getByRole('checkbox', { name: 'Select Bulbasaur' }))

      await userEvent.click(screen.getByRole('button', { name: 'Add 2 to my collection' }))

      expect(await screen.findByText('Added 2 Pokémon to your collection.')).toBeInTheDocument()
      expect(bodies).toEqual([{ ids: [3, 1] }])
      expect(screen.getByText('0 selected')).toBeInTheDocument()
      await waitFor(() => expect(screen.queryByRole('checkbox', { name: 'Select Venusaur' })).not.toBeInTheDocument())
      const venusaur = screen.getByRole('link', { name: /Venusaur/ }).closest('article')!
      expect(within(venusaur).getByText('In my collection')).toBeInTheDocument()
    })

    it('disables the button while the request is pending', async () => {
      mockCatalog(firstPage)
      mockCollectionIds([])
      mockSync(async () => {
        await delay(50)
        return report({ created: 1 })
      })
      renderApp(<App />, { token: makeToken() })
      await userEvent.click(await checkbox('Ivysaur'))

      await userEvent.click(screen.getByRole('button', { name: 'Add 1 to my collection' }))

      expect(screen.getByRole('button', { name: 'Adding…' })).toBeDisabled()
      expect(await screen.findByText('Added 1 Pokémon to your collection.')).toBeInTheDocument()
    })

    it('lists what failed, what was refreshed and why it stopped', async () => {
      mockCatalog(firstPage)
      mockCollectionIds([])
      mockSync(() =>
        report({
          created: 1,
          updated: 1,
          failures: [{ identifier: '3', reason: 'Not found upstream' }],
          abortedReason: 'Catalog unavailable at id 3',
        }),
      )
      renderApp(<App />, { token: makeToken() })
      await userEvent.click(await checkbox('Ivysaur'))
      await userEvent.click(screen.getByRole('button', { name: 'Add 1 to my collection' }))

      expect(await screen.findByText('Added 1 Pokémon to your collection.')).toBeInTheDocument()
      expect(screen.getByText('1 were already there and were refreshed.')).toBeInTheDocument()
      expect(screen.getByText('Adding stopped early: Catalog unavailable at id 3')).toBeInTheDocument()
      expect(screen.getByRole('list', { name: 'Not added' })).toHaveTextContent('3: Not found upstream')
    })

    it('keeps the selection on error and retries with the same ids', async () => {
      const bodies: unknown[] = []
      let fail = true
      mockCatalog(firstPage)
      mockCollectionIds([])
      mockSync(() => (fail ? failWith(502, 'CATALOG_UNAVAILABLE', 'x') : report({ created: 1 })), bodies)
      renderApp(<App />, { token: makeToken() })
      await userEvent.click(await checkbox('Ivysaur'))
      await userEvent.click(screen.getByRole('button', { name: 'Add 1 to my collection' }))

      expect(await screen.findByRole('alert')).toHaveTextContent('not answering right now')
      expect(screen.getByText('1 selected')).toBeInTheDocument()
      expect(screen.getByRole('checkbox', { name: 'Select Ivysaur' })).toBeChecked()

      fail = false
      await userEvent.click(screen.getByRole('button', { name: 'Try again' }))

      expect(await screen.findByText('Added 1 Pokémon to your collection.')).toBeInTheDocument()
      expect(bodies).toEqual([{ ids: [2] }, { ids: [2] }])
    })
  })

  it('stops at 50 with a clear message', async () => {
    const many = (from: number, count: number) =>
      Array.from({ length: count }, (_, index) => summary(from + index, `mon${from + index}`))
    mockCatalog((params) =>
      params.get('page') === '1' ? pageOf(many(49, 4), { page: 1, size: 48 }) : pageOf(many(1, 48), { size: 48 }),
    )
    mockCollectionIds([])
    renderApp(<App />, { route: '/?page=1&size=48', token: makeToken() })
    await checkbox('Mon1')
    await userEvent.click(screen.getByRole('button', { name: 'Select all on this page' }))
    await userEvent.click(screen.getByRole('link', { name: /Next/ }))
    await userEvent.click(await checkbox('Mon49'))
    expect(screen.queryByText(/up to 50/)).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('checkbox', { name: 'Select Mon50' }))

    expect(screen.getByText('50 selected')).toBeInTheDocument()
    expect(screen.getByText(/You can add up to 50 Pokémon at a time/)).toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: 'Select Mon51' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Select all on this page' })).toBeDisabled()
  })

  it('has no accessibility violations with a selection', async () => {
    mockCatalog(firstPage)
    mockCollectionIds([1])
    const { container } = renderApp(<App />, { token: makeToken() })
    await userEvent.click(await checkbox('Ivysaur'))

    await expectNoA11yViolations(container)
  })
})
