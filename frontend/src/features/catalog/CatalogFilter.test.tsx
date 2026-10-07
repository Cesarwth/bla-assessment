import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay } from 'msw'
import { useLocation, useNavigate } from 'react-router'
import { describe, expect, it } from 'vitest'
import { App } from '../../app/App'
import { expectNoA11yViolations } from '../../test/axe'
import { bulbasaur, pageOf, summary } from '../../test/fixtures'
import { mockCatalog } from '../../test/handlers'
import { renderApp } from '../../test/utils'

const pikachu = summary(25, 'pikachu', ['electric'])
const everything = [bulbasaur, pikachu]

/** Behaves like the API: no q lists everything, otherwise a case-insensitive match on the name or the exact number. */
function filtering(searchParams: URLSearchParams) {
  const query = (searchParams.get('q') ?? '').toLowerCase()
  const matches = everything.filter(
    (pokemon) =>
      !query || (/^\d+$/.test(query) ? String(pokemon.id) === query : pokemon.name.includes(query)),
  )
  return pageOf(matches, { totalItems: matches.length })
}

const field = () => screen.getByLabelText('Filter by name or number')

describe('filtering the catalog', () => {
  it('offers a labelled search field in a search landmark, limited to 50 characters', async () => {
    mockCatalog(filtering)
    renderApp(<App />)
    await screen.findByText('Bulbasaur')

    expect(screen.getByRole('search')).toContainElement(field())
    expect(field()).toHaveAttribute('maxlength', '50')
    expect(screen.queryByRole('button', { name: 'Clear filter' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Go' })).not.toBeInTheDocument()
  })

  it('waits for a pause in typing, then asks once for the matches and narrows the list in place', async () => {
    const requests: string[] = []
    mockCatalog(filtering, requests)
    renderApp(<App />)
    await screen.findByText('Bulbasaur')

    await userEvent.type(field(), 'pika')
    expect(requests).toEqual(['page=0&size=12'])

    await waitFor(() => expect(screen.queryByText('Bulbasaur')).not.toBeInTheDocument())
    expect(screen.getByRole('link', { name: /Pikachu/ })).toBeInTheDocument()
    expect(requests).toEqual(['page=0&size=12', 'page=0&size=12&q=pika'])
  })

  it('ignores surrounding spaces and the case is left to the API', async () => {
    const requests: string[] = []
    mockCatalog(filtering, requests)
    renderApp(<App />)
    await screen.findByText('Bulbasaur')

    await userEvent.type(field(), '  PIKA ')

    await expectRequest(requests, 'page=0&size=12&q=PIKA')
  })

  it('takes the filter from the URL, so a shared link shows the same list', async () => {
    const requests: string[] = []
    mockCatalog(filtering, requests)
    renderApp(<App />, { route: '/?q=25' })

    expect(await screen.findByRole('link', { name: /Pikachu/ })).toBeInTheDocument()
    expect(field()).toHaveValue('25')
    expect(requests).toEqual(['page=0&size=12&q=25'])
  })

  it('a new filter goes back to the first page and keeps the page size', async () => {
    const requests: string[] = []
    mockCatalog(filtering, requests)
    renderApp(<App />, { route: '/?page=3&size=24' })
    await screen.findByText('Bulbasaur')

    await userEvent.type(field(), 'b')

    await screen.findByText('Bulbasaur')
    await expectRequest(requests, 'page=0&size=24&q=b')
  })

  it('keeps the previous cards on screen while the matches load', async () => {
    const requests: string[] = []
    mockCatalog(async (searchParams) => {
      if (searchParams.get('q')) await delay(150)
      return filtering(searchParams)
    }, requests)
    renderApp(<App />)
    await screen.findByText('Pikachu')

    await userEvent.type(field(), 'bulba')
    await waitFor(() => expect(requests).toHaveLength(2))

    expect(screen.getByText('Pikachu')).toBeInTheDocument() // the old list, not an empty page, while waiting
    await waitFor(() => expect(screen.queryByText('Pikachu')).not.toBeInTheDocument())
    expect(screen.getByText('Bulbasaur')).toBeInTheDocument()
  })

  it('pagination links keep the filter and the page size', async () => {
    mockCatalog(() => pageOf([bulbasaur], { totalItems: 30 }))
    renderApp(<App />, { route: '/?q=a&size=12' })

    expect(await screen.findByRole('link', { name: 'Next page' })).toHaveAttribute(
      'href',
      '/?page=2&size=12&q=a',
    )
  })

  it('changing the page size keeps the filter', async () => {
    const requests: string[] = []
    mockCatalog(() => pageOf([bulbasaur], { totalItems: 30 }), requests)
    renderApp(<App />, { route: '/?q=a' })
    await screen.findByText('Bulbasaur')

    await userEvent.selectOptions(screen.getByLabelText('Per page'), '24')

    await expectRequest(requests, 'page=0&size=24&q=a')
  })

  it('says nothing matched and offers to show everything again', async () => {
    const requests: string[] = []
    mockCatalog(filtering, requests)
    renderApp(<App />, { route: '/?q=xyz' })

    expect(
      await screen.findByRole('heading', { name: 'No Pokémon match “xyz”' }),
    ).toBeInTheDocument()
    expect(screen.queryByText('No Pokémon on this page')).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('link', { name: 'Show all Pokémon' }))

    expect(await screen.findByText('Bulbasaur')).toBeInTheDocument()
    expect(field()).toHaveValue('')
    expect(requests.at(-1)).toBe('page=0&size=12')
  })

  it('the clear button empties the field at once, refocuses it and lists everything', async () => {
    const requests: string[] = []
    mockCatalog(filtering, requests)
    renderApp(<App />, { route: '/?q=pika' })
    await screen.findByText('Pikachu')

    await userEvent.click(screen.getByRole('button', { name: 'Clear filter' }))

    expect(field()).toHaveValue('')
    expect(field()).toHaveFocus()
    expect(await screen.findByText('Bulbasaur')).toBeInTheDocument()
    expect(requests.at(-1)).toBe('page=0&size=12')
    expect(screen.queryByRole('button', { name: 'Clear filter' })).not.toBeInTheDocument()
  })

  it('erasing the text by hand also lists everything', async () => {
    const requests: string[] = []
    mockCatalog(filtering, requests)
    renderApp(<App />, { route: '/?q=pika' })
    await screen.findByText('Pikachu')

    await userEvent.clear(field())

    expect(await screen.findByText('Bulbasaur')).toBeInTheDocument()
    expect(requests.at(-1)).toBe('page=0&size=12')
  })

  it('does not add a history entry per typed pause: Back leaves the catalog', async () => {
    mockCatalog(filtering)
    renderApp(
      <>
        <BackProbe />
        <App />
      </>,
      { route: '/', previous: '/login' },
    )
    await screen.findByText('Bulbasaur')

    await userEvent.type(field(), 'pi')
    await waitFor(() => expect(screen.getByTestId('where')).toHaveTextContent('q=pi'))
    await userEvent.type(field(), 'ka')
    await waitFor(() => expect(screen.getByTestId('where')).toHaveTextContent('q=pika'))
    await userEvent.click(screen.getByRole('button', { name: 'history back' }))

    expect(screen.getByTestId('where')).toHaveTextContent('/login')
  })

  it('has no accessibility violations, with a filter and with no match', async () => {
    mockCatalog(filtering)
    const { container } = renderApp(<App />, { route: '/?q=pika' })
    await screen.findByText('Pikachu')
    await expectNoA11yViolations(container)

    await userEvent.clear(field())
    await userEvent.type(field(), 'zzz')
    await screen.findByRole('heading', { name: 'No Pokémon match “zzz”' })
    await expectNoA11yViolations(container)
  })
})

function expectRequest(requests: string[], expected: string) {
  return waitFor(() => expect(requests.at(-1)).toBe(expected))
}

function BackProbe() {
  const navigate = useNavigate()
  const location = useLocation()
  return (
    <>
      <p data-testid="where">{location.pathname + location.search}</p>
      <button onClick={() => void navigate(-1)}>history back</button>
    </>
  )
}
