import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { fetchCatalogPage, fetchPokemonDetail } from './api'

export const catalogKeys = {
  all: ['catalog'] as const,
  page: (page: number, size: number) => [...catalogKeys.all, 'page', page, size] as const,
  detail: (idOrName: string) => [...catalogKeys.all, 'detail', idOrName.toLowerCase()] as const,
}

/** `page` is 0-based, as in the API. The previous page stays visible while the next one loads. */
export function useCatalogPage(page: number, size: number) {
  return useQuery({
    queryKey: catalogKeys.page(page, size),
    queryFn: ({ signal }) => fetchCatalogPage(page, size, signal),
    placeholderData: keepPreviousData,
  })
}

export function usePokemonDetail(idOrName: string) {
  return useQuery({
    queryKey: catalogKeys.detail(idOrName),
    queryFn: ({ signal }) => fetchPokemonDetail(idOrName, signal),
  })
}
