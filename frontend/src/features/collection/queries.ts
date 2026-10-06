import {
  keepPreviousData,
  useMutation,
  useMutationState,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query'
import type { SyncReport } from '../../shared/api/types'
import {
  deleteCollectionEntry,
  fetchCollectionEntry,
  fetchCollectionIds,
  fetchCollectionPage,
  syncPokemon,
  syncSelection,
  updateCollectionEntry,
  type EntryChanges,
} from './api'

export const collectionKeys = {
  all: ['collection'] as const,
  lists: () => [...collectionKeys.all, 'list'] as const,
  list: (page: number, size: number) => [...collectionKeys.lists(), page, size] as const,
  ids: () => [...collectionKeys.all, 'ids'] as const,
  entry: (id: number) => [...collectionKeys.all, 'entry', id] as const,
}

export function useCollectionPage(page: number, size: number) {
  return useQuery({
    queryKey: collectionKeys.list(page, size),
    queryFn: ({ signal }) => fetchCollectionPage(page, size, signal),
    placeholderData: keepPreviousData,
  })
}

/** Which Pokémon are in the trainer's collection: one cheap request that lets the detail page decide without a 404. */
export function useCollectionIds(enabled: boolean) {
  return useQuery({
    queryKey: collectionKeys.ids(),
    queryFn: ({ signal }) => fetchCollectionIds(signal),
    enabled,
  })
}

export function useSyncPokemon() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: syncPokemon,
    onSuccess: ({ data }) => {
      queryClient.setQueryData(collectionKeys.entry(data.id), data)
      return Promise.all([
        queryClient.invalidateQueries({ queryKey: collectionKeys.lists() }),
        queryClient.invalidateQueries({ queryKey: collectionKeys.ids() }),
      ])
    },
  })
}

const syncSelectionKey = [...collectionKeys.all, 'sync-selection'] as const

/**
 * Adding a selection can take minutes, so everything that must happen afterwards is configured here, on the
 * mutation, and not in `mutate(..., { onSuccess })`: those callbacks are dropped when the component unmounts, and the
 * trainer may well be on another page by then. `onAdded` receives the ids that were sent so the caller can drop
 * exactly those (what was ticked meanwhile stays). The outcome is read from the mutation cache, so it is still there
 * when the toolbar mounts again; the session change clears that cache.
 */
export function useSyncSelection(onAdded: (ids: number[]) => void) {
  const queryClient = useQueryClient()
  const { mutate } = useMutation({
    mutationKey: syncSelectionKey,
    mutationFn: (ids: number[]) => syncSelection(ids),
    onSuccess: (_report, ids) => onAdded(ids),
    // Settled, not success: a gateway timeout after the server saved some rows must still refresh the marks.
    onSettled: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: collectionKeys.lists() }),
        queryClient.invalidateQueries({ queryKey: collectionKeys.ids() }),
      ]),
  })
  const latest = useMutationState({
    filters: { mutationKey: syncSelectionKey },
    select: (mutation) => mutation.state,
  }).at(-1)
  return {
    mutate,
    isPending: latest?.status === 'pending',
    isError: latest?.status === 'error',
    error: (latest?.error as Error | null | undefined) ?? null,
    data: latest?.data as SyncReport | undefined,
  }
}

export function useCollectionEntry(id: number, enabled = true) {
  return useQuery({
    queryKey: collectionKeys.entry(id),
    queryFn: ({ signal }) => fetchCollectionEntry(id, signal),
    enabled,
  })
}

export function useUpdateEntry(id: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (changes: EntryChanges) => updateCollectionEntry(id, changes),
    onSuccess: (saved) => {
      // The response carries the new version: the next save must be based on it.
      queryClient.setQueryData(collectionKeys.entry(id), saved)
      return queryClient.invalidateQueries({ queryKey: collectionKeys.lists() })
    },
  })
}

export function useDeleteEntry(id: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => deleteCollectionEntry(id),
    onSuccess: () => {
      // Only mark the entry stale: removing it would make this still-mounted page fetch it again (and get a 404).
      void queryClient.invalidateQueries({
        queryKey: collectionKeys.entry(id),
        refetchType: 'none',
      })
      return Promise.all([
        queryClient.invalidateQueries({ queryKey: collectionKeys.lists() }),
        queryClient.invalidateQueries({ queryKey: collectionKeys.ids() }),
      ])
    },
  })
}
