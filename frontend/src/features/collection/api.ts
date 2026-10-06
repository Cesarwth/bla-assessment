import { api } from '../../shared/api/client'
import type { PageResponse, StoredPokemon, StoredPokemonSummary, SyncReport } from '../../shared/api/types'

export function fetchCollectionPage(page: number, size: number, signal?: AbortSignal) {
  return api.get<PageResponse<StoredPokemonSummary>>(`/collection?page=${page}&size=${size}`, signal)
}

/** 201 means the Pokémon was added now, 200 that it was already there and its PokeAPI data was refreshed. */
export function syncPokemon(id: number) {
  return api.postWithStatus<StoredPokemon>(`/collection/${id}/sync`)
}

export function syncRange(from: number, to: number) {
  return api.post<SyncReport>('/collection/sync', { from, to })
}
