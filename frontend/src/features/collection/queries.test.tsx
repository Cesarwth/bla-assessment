import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { renderHook, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import type { ReactNode } from 'react'
import { describe, expect, it } from 'vitest'
import { stored } from '../../test/fixtures'
import { apiUrl, mockCollectionIds } from '../../test/handlers'
import { server } from '../../test/server'
import {
  collectionKeys,
  useCollectionIds,
  useDeleteEntry,
  useSyncSelection,
  useSyncPokemon,
} from './queries'

function setup() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: 60_000 } },
  })
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )
  return { queryClient, wrapper }
}

/** The ids stay fresh for a minute, so every write that changes the collection must mark them stale. */
describe('the ids of the collection', () => {
  it('are loaded once and kept under the collection keys', async () => {
    mockCollectionIds([4, 25])
    const { queryClient, wrapper } = setup()

    const { result } = renderHook(() => useCollectionIds(true), { wrapper })

    await waitFor(() => expect(result.current.data).toEqual([4, 25]))
    expect(queryClient.getQueryData(collectionKeys.ids())).toEqual([4, 25])
  })

  it('are not requested when disabled', () => {
    const requests: string[] = []
    mockCollectionIds([], requests)
    const { wrapper } = setup()

    renderHook(() => useCollectionIds(false), { wrapper })

    expect(requests).toEqual([])
  })

  it.each([
    [
      'adding one Pokémon',
      () => useSyncPokemon(),
      (m: ReturnType<typeof useSyncPokemon>) => m.mutate(25),
    ],
    [
      'adding a selection',
      () => useSyncSelection(),
      (m: ReturnType<typeof useSyncSelection>) => m.mutate([1, 3]),
    ],
    [
      'removing an entry',
      () => useDeleteEntry(25),
      (m: ReturnType<typeof useDeleteEntry>) => m.mutate(),
    ],
  ] as const)('are marked stale after %s', async (_name, useHook, run) => {
    server.use(
      http.post(apiUrl('/collection/25/sync'), () =>
        HttpResponse.json(stored(25, 'pikachu'), { status: 201 }),
      ),
      http.post(apiUrl('/collection/sync'), () =>
        HttpResponse.json({ created: 3, updated: 0, failures: [], abortedReason: null }),
      ),
      http.delete(apiUrl('/collection/25'), () => new HttpResponse(null, { status: 204 })),
    )
    const { queryClient, wrapper } = setup()
    queryClient.setQueryData(collectionKeys.ids(), [4])
    const { result } = renderHook(() => useHook() as never, { wrapper })

    run(result.current as never)

    await waitFor(() =>
      expect(queryClient.getQueryState(collectionKeys.ids())?.isInvalidated).toBe(true),
    )
  })
})
