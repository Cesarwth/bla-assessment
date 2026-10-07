import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { fetchCatalogPage, fetchPokemonDetail } from './api'

const catalogKeys = {
  all: ['catalog'] as const,
  page: (page: number, size: number, filter: string) =>
    [...catalogKeys.all, 'page', page, size, filter] as const,
  detail: (idOrName: string) => [...catalogKeys.all, 'detail', idOrName.toLowerCase()] as const,
}

/** `page` is 0-based, as in the API. The previous results stay visible while the next ones load. */
export function useCatalogPage(page: number, size: number, filter: string) {
  return useQuery({
    queryKey: catalogKeys.page(page, size, filter),
    queryFn: ({ signal }) => fetchCatalogPage(page, size, filter, signal),
    placeholderData: keepPreviousData,
  })
}

export function usePokemonDetail(idOrName: string) {
  return useQuery({
    queryKey: catalogKeys.detail(idOrName),
    queryFn: ({ signal }) => fetchPokemonDetail(idOrName, signal),
  })
}
