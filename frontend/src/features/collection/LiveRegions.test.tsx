import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { App } from '../../app/App'
import { expectNoA11yViolations } from '../../test/axe'
import { bulbasaur, pageOf, pikachu, stored, summary } from '../../test/fixtures'
import {
  apiUrl,
  failWith,
  mockCatalog,
  mockCollectionIds,
  mockDetail,
  mockEntry,
} from '../../test/handlers'
import { server } from '../../test/server'
import { findFilledRegion, makeToken, renderApp } from '../../test/utils'

describe('messages live in regions that exist before they have text', () => {
  it('the add box on the detail page: an empty status first, then the confirmation in the same element', async () => {
    let collected: number[] = []
    mockDetail('25', pikachu)
    mockCollectionIds(() => collected)
    server.use(
      http.post(apiUrl('/collection/25/sync'), () => {
        collected = [25]
        return HttpResponse.json(stored(25, 'pikachu'), { status: 201 })
      }),
    )
    renderApp(<App />, { route: '/pokemon/25', token: makeToken() })
    const button = await screen.findByRole('button', { name: 'Add to my collection' })
    const status = screen.getByRole('status')
    expect(status).toBeEmptyDOMElement()

    await userEvent.click(button)

    expect(await findFilledRegion(screen, 'status')).toBe(status)
    expect(status).toHaveTextContent('Added to your collection.')
  })

  it('the sync report: an empty status in the toolbar first, then the report in the same element', async () => {
    mockCatalog(() => pageOf([bulbasaur, summary(2, 'ivysaur', ['grass'])]))
    mockCollectionIds([])
    server.use(
      http.post(apiUrl('/collection/sync'), () =>
        HttpResponse.json({ created: 1, updated: 0, failures: [], abortedReason: null }),
      ),
    )
    const { container } = renderApp(<App />, { token: makeToken() })
    await userEvent.click(await screen.findByRole('button', { name: 'Select Pokémon' }))
    const toolbar = screen.getByRole('region', { name: 'Select Pokémon to add' })
    const status = within(toolbar).getByRole('status')
    expect(status).toBeEmptyDOMElement()
    await expectNoA11yViolations(container)

    await userEvent.click(await screen.findByRole('checkbox', { name: 'Select Ivysaur' }))
    await userEvent.click(screen.getByRole('button', { name: 'Add 1 to my collection' }))

    expect(await findFilledRegion(within(toolbar), 'status')).toBe(status)
    expect(status).toHaveTextContent('Added 1 Pokémon to your collection.')
  })

  it('the notes form: an empty alert first, then the refusal in the same element', async () => {
    mockEntry(25, stored(25, 'pikachu', { version: 3, nickname: 'Pika', tags: [] }))
    server.use(
      http.patch(apiUrl('/collection/25'), () =>
        failWith(400, 'INVALID_LOCAL_ATTRIBUTES', 'nickname must have at most 50 characters'),
      ),
    )
    renderApp(<App />, { route: '/collection/25', token: makeToken() })
    await userEvent.click(await screen.findByRole('button', { name: 'Edit notes' }))
    const form = await screen.findByRole('form', { name: 'Edit trainer notes' })
    const alert = within(form).getByRole('alert')
    expect(alert).toBeEmptyDOMElement()

    await userEvent.type(within(form).getByLabelText('Nickname'), 'x')
    await userEvent.click(within(form).getByRole('button', { name: 'Save changes' }))

    expect(await findFilledRegion(within(form), 'alert')).toBe(alert)
    expect(alert).toHaveTextContent('nickname must have at most 50 characters')
  })
})
