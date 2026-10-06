import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { deleteCollectionEntry, fetchCollectionEntry, fetchCollectionIds, fetchCollectionPage, syncPokemon, syncSelection, updateCollectionEntry, type EntryChanges } from './api'

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

export function useSyncSelection() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (ids: number[]) => syncSelection(ids),
    // New entries must show up in the lists, and the catalog cards must flip to "In my collection", without a reload.
    onSuccess: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: collectionKeys.lists() }),
        queryClient.invalidateQueries({ queryKey: collectionKeys.ids() }),
      ]),
  })
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
      void queryClient.invalidateQueries({ queryKey: collectionKeys.entry(id), refetchType: 'none' })
      return Promise.all([
        queryClient.invalidateQueries({ queryKey: collectionKeys.lists() }),
        queryClient.invalidateQueries({ queryKey: collectionKeys.ids() }),
      ])
    },
  })
}
