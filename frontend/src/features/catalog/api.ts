import { api } from '../../shared/api/client'
import type { PageResponse, PokemonDetail, PokemonSummary } from '../../shared/api/types'

/** `filter` is a name fragment or a number; the API ignores it when empty, so it is left out. */
export function fetchCatalogPage(page: number, size: number, filter: string, signal?: AbortSignal) {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  if (filter) params.set('q', filter)
  return api.get<PageResponse<PokemonSummary>>(`/pokemon?${params}`, signal)
}

export function fetchPokemonDetail(idOrName: string, signal?: AbortSignal) {
  return api.get<PokemonDetail>(`/pokemon/${encodeURIComponent(idOrName)}`, signal)
}
