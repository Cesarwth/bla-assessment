import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { App } from '../../app/App'
import { expectNoA11yViolations } from '../../test/axe'
import { pageOf, pikachu, stored, storedSummary } from '../../test/fixtures'
import { apiUrl, failWith, mockCollection, mockDetail, mockEntry } from '../../test/handlers'
import { server } from '../../test/server'
import { makeToken, renderApp } from '../../test/utils'

const OWNER = 5
const entry = (over = {}) =>
  stored(25, 'pikachu', { version: 3, ownerId: OWNER, nickname: 'Pika', region: 'Kanto', tags: ['starter'], ...over })

const asOwner = () => ({ route: '/collection/25', token: makeToken({ uid: OWNER, sub: 'ash' }) })
const asStranger = () => ({ route: '/collection/25', token: makeToken({ uid: 99, sub: 'gary' }) })
const asAdmin = () => ({ route: '/collection/25', token: makeToken({ uid: 1, sub: 'boss', role: 'ADMIN' }) })

/** Records the body of every PATCH and answers with the entry plus the changes, one version higher. */
function mockPatch(base = entry()) {
  const bodies: Record<string, unknown>[] = []
  let current = base
  server.use(
    http.patch(apiUrl('/collection/25'), async ({ request }) => {
      const body = (await request.json()) as Record<string, unknown>
      bodies.push(body)
      const { version, ...changes } = body
      void version
      current = { ...current, ...changes, version: current.version + 1 } as typeof current
      return HttpResponse.json(current)
    }),
  )
  return bodies
}

describe('CollectionEntryPage', () => {
  it('shows a loading state, then the full record with its trainer notes', async () => {
    mockEntry(25, entry())
    renderApp(<App />, { route: '/collection/25' })

    expect(screen.getByRole('status')).toHaveTextContent('Loading')
    expect(await screen.findByRole('heading', { name: 'Pikachu' })).toBeInTheDocument()
    expect(screen.getByText('“Pika”')).toBeInTheDocument()
    expect(screen.getByText('Kanto')).toBeInTheDocument()
    expect(within(screen.getByRole('list', { name: 'Tags' })).getByText('starter')).toBeInTheDocument()
    expect(screen.getByRole('list', { name: 'Base stats' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'See it in the catalog' })).toHaveAttribute('href', '/pokemon/25')
  })

  it('says who added it', async () => {
    mockEntry(25, entry())
    renderApp(<App />, asOwner())
    expect(await screen.findByText('Added by you')).toBeInTheDocument()
  })

  it('says it was added by another trainer for everybody else', async () => {
    mockEntry(25, entry())
    renderApp(<App />, asStranger())
    expect(await screen.findByText('Added by another trainer')).toBeInTheDocument()
  })

  it('explains that an entry without owner is only editable by admins', async () => {
    mockEntry(25, entry({ ownerId: null }))
    renderApp(<App />, asStranger())
    expect(await screen.findByText(/No owner/)).toBeInTheDocument()
    expect(screen.queryByRole('form', { name: 'Edit trainer notes' })).not.toBeInTheDocument()
  })

  describe('who can edit', () => {
    it('the owner sees the edit form with the current values and no delete button', async () => {
      mockEntry(25, entry())
      renderApp(<App />, asOwner())

      const form = await screen.findByRole('form', { name: 'Edit trainer notes' })
      expect(within(form).getByLabelText('Nickname')).toHaveValue('Pika')
      expect(within(form).getByLabelText('Region')).toHaveValue('Kanto')
      expect(within(form).getByRole('button', { name: 'Remove tag starter' })).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Delete from the collection' })).not.toBeInTheDocument()
    })

    it('another trainer sees it read-only with an explanation', async () => {
      mockEntry(25, entry())
      renderApp(<App />, asStranger())

      expect(await screen.findByText(/You can’t edit this entry because another trainer added it/)).toBeInTheDocument()
      expect(screen.queryByRole('form', { name: 'Edit trainer notes' })).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Delete from the collection' })).not.toBeInTheDocument()
    })

    it('an admin can edit anything and is the only one who can delete', async () => {
      mockEntry(25, entry())
      renderApp(<App />, asAdmin())

      expect(await screen.findByRole('form', { name: 'Edit trainer notes' })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Delete from the collection' })).toBeInTheDocument()
    })

    it('anonymous visitors only read, and are invited to log in', async () => {
      mockEntry(25, entry())
      renderApp(<App />, { route: '/collection/25' })

      expect(await screen.findByRole('link', { name: 'Log in to edit your entries' })).toHaveAttribute('href', '/login')
      expect(screen.queryByRole('form', { name: 'Edit trainer notes' })).not.toBeInTheDocument()
    })
  })

  describe('editing', () => {
    // Saving leads to the Pokémon's page, which loads its PokeAPI detail.
    beforeEach(() => mockDetail('25', pikachu))

    it('starts with nothing to save', async () => {
      mockEntry(25, entry())
      renderApp(<App />, asOwner())

      expect(await screen.findByRole('button', { name: 'Save changes' })).toBeDisabled()
    })

    it('sends only the changed fields together with the version that was read', async () => {
      mockEntry(25, entry())
      const bodies = mockPatch()
      renderApp(<App />, asOwner())
      const nickname = await screen.findByLabelText('Nickname')

      await userEvent.clear(nickname)
      await userEvent.type(nickname, 'Sparky')
      await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))

      expect(await screen.findByText(/Saved changes to Pikachu\./)).toBeInTheDocument()
      expect(bodies).toEqual([{ version: 3, nickname: 'Sparky' }])
    })

    it('clearing a field sends an empty value, which the API understands as "clear"', async () => {
      mockEntry(25, entry())
      const bodies = mockPatch()
      renderApp(<App />, asOwner())

      await userEvent.clear(await screen.findByLabelText('Nickname'))
      await userEvent.clear(screen.getByLabelText('Region'))
      await userEvent.click(screen.getByRole('button', { name: 'Remove tag starter' }))
      await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))

      await screen.findByText(/Saved changes to Pikachu\./)
      expect(bodies).toEqual([{ version: 3, nickname: '', region: '', tags: [] }])
    })

    it('disables saving while the request is in flight', async () => {
      mockEntry(25, entry())
      server.use(
        http.patch(apiUrl('/collection/25'), async () => {
          await delay(60)
          return HttpResponse.json(entry({ version: 4, nickname: 'x' }))
        }),
      )
      renderApp(<App />, asOwner())
      await userEvent.type(await screen.findByLabelText('Nickname'), 'x')

      await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))

      expect(screen.getByRole('button', { name: 'Saving…' })).toBeDisabled()
      await screen.findByText(/Saved changes to Pikachu\./)
    })

    it('normalizes tags as they are added and ignores duplicates', async () => {
      mockEntry(25, entry())
      const bodies = mockPatch()
      renderApp(<App />, asOwner())
      const input = await screen.findByLabelText('Add a tag')

      await userEvent.type(input, '  Electric {Enter}')
      await userEvent.type(input, 'ELECTRIC{Enter}')
      await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))

      await screen.findByText(/Saved changes to Pikachu\./)
      expect(bodies).toEqual([{ version: 3, tags: ['starter', 'electric'] }])
    })

    it('stops at ten tags and says why', async () => {
      mockEntry(25, entry({ tags: Array.from({ length: 10 }, (_, index) => `tag${index}`) }))
      renderApp(<App />, asOwner())

      await userEvent.type(await screen.findByLabelText('Add a tag'), 'eleventh{Enter}')

      expect(screen.getByRole('alert')).toHaveTextContent('You can have up to 10 tags.')
      expect(screen.queryByText('eleventh')).not.toBeInTheDocument()
    })

    it('shows the explanation of the API when it refuses the values', async () => {
      mockEntry(25, entry())
      server.use(
        http.patch(apiUrl('/collection/25'), () =>
          failWith(400, 'INVALID_LOCAL_ATTRIBUTES', 'nickname must have at most 50 characters'),
        ),
      )
      renderApp(<App />, asOwner())
      await userEvent.type(await screen.findByLabelText('Nickname'), 'x')

      await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))

      expect(await screen.findByRole('alert')).toHaveTextContent('nickname must have at most 50 characters')
    })

    it('tells the user when the entry cannot be edited by them after all (403)', async () => {
      mockEntry(25, entry())
      server.use(
        http.patch(apiUrl('/collection/25'), () =>
          failWith(403, 'FORBIDDEN_NOT_OWNER', 'Pokemon 25 can only be edited by the user who added it or by an admin'),
        ),
      )
      renderApp(<App />, asOwner())
      await userEvent.type(await screen.findByLabelText('Nickname'), 'x')

      await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))

      expect(await screen.findByRole('alert')).toHaveTextContent('can only be edited by the user who added it')
    })

    it('on a conflict (409) offers to load the latest version and keeps what the user typed', async () => {
      let reads = 0
      server.use(
        http.get(apiUrl('/collection/25'), () => {
          reads += 1
          return HttpResponse.json(reads === 1 ? entry() : entry({ version: 4, region: 'Johto' }))
        }),
      )
      const bodies: Record<string, unknown>[] = []
      server.use(
        http.patch(apiUrl('/collection/25'), async ({ request }) => {
          const body = (await request.json()) as Record<string, unknown>
          bodies.push(body)
          return bodies.length === 1
            ? failWith(409, 'CONCURRENT_MODIFICATION', 'The record was modified by someone else; reload it and try again')
            : HttpResponse.json(entry({ version: 5, nickname: 'Sparky', region: 'Johto' }))
        }),
      )
      renderApp(<App />, asOwner())
      const nickname = await screen.findByLabelText('Nickname')
      await userEvent.clear(nickname)
      await userEvent.type(nickname, 'Sparky')
      await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))

      expect(await screen.findByRole('alert')).toHaveTextContent('Someone else changed this entry')
      await userEvent.click(screen.getByRole('button', { name: 'Load the latest version' }))

      expect(await screen.findByLabelText('Region')).toHaveValue('Johto') // what the user did not touch follows the latest version...
      expect(screen.queryByRole('alert')).not.toBeInTheDocument()
      expect(nickname).toHaveValue('Sparky') // ...while what the user typed is kept
      await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))
      await screen.findByText(/Saved changes to Pikachu\./)
      expect(bodies[1]).toEqual({ version: 4, nickname: 'Sparky' }) // based on the latest version, and only what the user changed
    })

    it('never overwrites, after a conflict, a field the user did not touch (the other person’s edit survives)', async () => {
      let reads = 0
      server.use(
        http.get(apiUrl('/collection/25'), () => {
          reads += 1
          // meanwhile somebody else renamed the Pokémon
          return HttpResponse.json(reads === 1 ? entry() : entry({ version: 4, nickname: 'Renamed by someone else' }))
        }),
      )
      const bodies: Record<string, unknown>[] = []
      server.use(
        http.patch(apiUrl('/collection/25'), async ({ request }) => {
          bodies.push((await request.json()) as Record<string, unknown>)
          return bodies.length === 1
            ? failWith(409, 'CONCURRENT_MODIFICATION', 'modified by someone else')
            : HttpResponse.json(entry({ version: 5, nickname: 'Renamed by someone else', region: 'Hoenn' }))
        }),
      )
      renderApp(<App />, asOwner())
      const region = await screen.findByLabelText('Region')
      await userEvent.clear(region)
      await userEvent.type(region, 'Hoenn') // the user only edits the region
      await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))
      await userEvent.click(await screen.findByRole('button', { name: 'Load the latest version' }))

      expect(await screen.findByLabelText('Nickname')).toHaveValue('Renamed by someone else')
      await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))
      await screen.findByText(/Saved changes to Pikachu\./)
      expect(bodies[1]).toEqual({ version: 4, region: 'Hoenn' }) // the nickname is not sent: nobody's change is lost
    })

    it('goes to the Pokémon page and confirms the save there', async () => {
      mockEntry(25, entry())
      mockPatch()
      renderApp(<App />, asOwner())

      await userEvent.type(await screen.findByLabelText('Nickname'), 'x')
      await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))

      expect(await screen.findByText(/Saved changes to Pikachu\. In the collection, added by you\./)).toBeInTheDocument()
      expect(screen.getByRole('heading', { name: 'Pikachu' })).toBeInTheDocument()
      expect(screen.getByRole('link', { name: 'View it in the collection' })).toHaveAttribute('href', '/collection/25')
    })

    it('saves a tag that was typed but not confirmed with “Add tag”', async () => {
      mockEntry(25, entry())
      const bodies = mockPatch()
      renderApp(<App />, asOwner())

      await userEvent.type(await screen.findByLabelText('Add a tag'), 'Test')
      await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))

      await screen.findByText(/Saved changes to Pikachu\./)
      expect(bodies).toEqual([{ version: 3, tags: ['starter', 'test'] }])
    })

    it('does not save while the typed tag is invalid, and says why', async () => {
      mockEntry(25, entry())
      renderApp(<App />, asOwner())

      await userEvent.type(await screen.findByLabelText('Add a tag'), 'x'.repeat(31))

      expect(screen.getByRole('alert')).toHaveTextContent('Tags can have at most 30 characters.')
      expect(screen.getByRole('button', { name: 'Save changes' })).toBeDisabled()
    })

    it('undoing a change leaves nothing to save', async () => {
      mockEntry(25, entry())
      renderApp(<App />, asOwner())
      const nickname = await screen.findByLabelText('Nickname')

      await userEvent.type(nickname, 'x')
      expect(screen.getByRole('button', { name: 'Save changes' })).toBeEnabled()
      await userEvent.type(nickname, '{Backspace}')

      expect(screen.getByRole('button', { name: 'Save changes' })).toBeDisabled()
    })
  })

  describe('deleting', () => {
    it('asks for confirmation, then deletes and goes back to the collection', async () => {
      mockEntry(25, entry())
      mockCollection(() => pageOf([storedSummary(26, 'raichu')], { totalItems: 1 }))
      let deleted = false
      server.use(
        http.delete(apiUrl('/collection/25'), () => {
          deleted = true
          return new HttpResponse(null, { status: 204 })
        }),
      )
      renderApp(<App />, asAdmin())

      await userEvent.click(await screen.findByRole('button', { name: 'Delete from the collection' }))
      expect(deleted).toBe(false)
      expect(screen.getByText('Delete Pikachu from the collection? This can’t be undone.')).toBeInTheDocument()
      await userEvent.click(screen.getByRole('button', { name: 'Yes, delete it' }))

      expect(await screen.findByRole('heading', { name: 'Collection' })).toBeInTheDocument()
      expect(deleted).toBe(true)
    })

    it('cancelling does nothing', async () => {
      mockEntry(25, entry())
      renderApp(<App />, asAdmin())

      await userEvent.click(await screen.findByRole('button', { name: 'Delete from the collection' }))
      await userEvent.click(screen.getByRole('button', { name: 'Cancel' }))

      expect(screen.queryByText(/This can’t be undone/)).not.toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Delete from the collection' })).toBeInTheDocument()
    })

    it('reports a failed deletion and stays on the page', async () => {
      mockEntry(25, entry())
      server.use(http.delete(apiUrl('/collection/25'), () => failWith(500, 'INTERNAL_ERROR', 'Unexpected error')))
      renderApp(<App />, asAdmin())

      await userEvent.click(await screen.findByRole('button', { name: 'Delete from the collection' }))
      await userEvent.click(screen.getByRole('button', { name: 'Yes, delete it' }))

      expect(await screen.findByRole('alert')).toHaveTextContent('Unexpected error')
      expect(screen.getByRole('heading', { name: 'Pikachu' })).toBeInTheDocument()
    })
  })

  it('shows a friendly page when the entry does not exist', async () => {
    mockEntry(25, failWith(404, 'STORED_POKEMON_NOT_FOUND', 'Pokemon 25 is not in the local collection'))
    renderApp(<App />, { route: '/collection/25' })

    expect(await screen.findByRole('heading', { name: /isn.t in the collection/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Back to the collection' })).toHaveAttribute('href', '/collection')
  })

  it('reports other failures with a retry', async () => {
    let calls = 0
    server.use(
      http.get(apiUrl('/collection/25'), () => {
        calls += 1
        return calls === 1 ? failWith(500, 'INTERNAL_ERROR', 'Unexpected error') : HttpResponse.json(entry())
      }),
    )
    renderApp(<App />, { route: '/collection/25' })

    expect(await screen.findByRole('alert')).toHaveTextContent('Unexpected error')
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))

    expect(await screen.findByRole('heading', { name: 'Pikachu' })).toBeInTheDocument()
  })

  it('has no accessibility violations (owner view with the form)', async () => {
    mockEntry(25, entry())
    const { container } = renderApp(<App />, asOwner())
    await screen.findByRole('form', { name: 'Edit trainer notes' })

    await expectNoA11yViolations(container)
  })

  it('has no accessibility violations (admin view with the delete confirmation)', async () => {
    mockEntry(25, entry())
    const { container } = renderApp(<App />, asAdmin())
    await userEvent.click(await screen.findByRole('button', { name: 'Delete from the collection' }))

    await expectNoA11yViolations(container)
  })
})
