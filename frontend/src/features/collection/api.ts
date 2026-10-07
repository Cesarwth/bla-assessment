import { api } from '../../shared/api/client'
import type {
  PageResponse,
  StoredPokemon,
  StoredPokemonSummary,
  SyncReport,
} from '../../shared/api/types'

/** `version` is the one the client last read; fields left out stay as they are, empty ones are cleared. */
export interface EntryChanges {
  version: number
  nickname?: string
  region?: string
  tags?: string[]
}

export function fetchCollectionPage(page: number, size: number, signal?: AbortSignal) {
  return api.get<PageResponse<StoredPokemonSummary>>(
    `/collection?page=${page}&size=${size}`,
    signal,
  )
}

/** 201 means the Pokémon was added now, 200 that it was already there and its PokeAPI data was refreshed. */
export function syncPokemon(id: number) {
  return api.postWithStatus<StoredPokemon>(`/collection/${id}/sync`)
}

export function syncSelection(ids: number[]) {
  return api.post<SyncReport>('/collection/sync', { ids })
}

/** The PokeAPI ids in the caller's collection, ascending. */
export function fetchCollectionIds(signal?: AbortSignal) {
  return api.get<number[]>('/collection/ids', signal)
}

export function fetchCollectionEntry(id: number, signal?: AbortSignal) {
  return api.get<StoredPokemon>(`/collection/${id}`, signal)
}

export function updateCollectionEntry(id: number, changes: EntryChanges) {
  return api.patch<StoredPokemon>(`/collection/${id}`, changes)
}

export function deleteCollectionEntry(id: number) {
  return api.delete(`/collection/${id}`)
}
