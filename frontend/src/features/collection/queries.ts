import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchCollectionPage, syncPokemon, syncRange } from './api'

export const collectionKeys = {
  all: ['collection'] as const,
  lists: () => [...collectionKeys.all, 'list'] as const,
  list: (page: number, size: number) => [...collectionKeys.lists(), page, size] as const,
  entry: (id: number) => [...collectionKeys.all, 'entry', id] as const,
}

export function useCollectionPage(page: number, size: number) {
  return useQuery({
    queryKey: collectionKeys.list(page, size),
    queryFn: ({ signal }) => fetchCollectionPage(page, size, signal),
    placeholderData: keepPreviousData,
  })
}

export function useSyncPokemon() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: syncPokemon,
    onSuccess: ({ data }) => {
      queryClient.setQueryData(collectionKeys.entry(data.id), data)
      return queryClient.invalidateQueries({ queryKey: collectionKeys.lists() })
    },
  })
}

export function useImportRange() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ from, to }: { from: number; to: number }) => syncRange(from, to),
    // New entries must show up in the list without a manual reload.
    onSuccess: () => queryClient.invalidateQueries({ queryKey: collectionKeys.lists() }),
  })
}
