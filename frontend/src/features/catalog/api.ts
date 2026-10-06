import { api } from '../../shared/api/client'
import type { PageResponse, PokemonDetail, PokemonSummary } from '../../shared/api/types'

export function fetchCatalogPage(page: number, size: number, signal?: AbortSignal) {
  return api.get<PageResponse<PokemonSummary>>(`/pokemon?page=${page}&size=${size}`, signal)
}

export function fetchPokemonDetail(idOrName: string, signal?: AbortSignal) {
  return api.get<PokemonDetail>(`/pokemon/${encodeURIComponent(idOrName)}`, signal)
}
