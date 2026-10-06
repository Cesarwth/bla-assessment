// Mirrors the backend DTOs (backend/src/main/java/.../infrastructure/web/dto). Optional text from PokeAPI is nullable.

export type Role = 'USER' | 'ADMIN'

export interface PageResponse<T> {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export interface PokemonSummary {
  id: number
  name: string
  spriteUrl: string | null
  /** Official artwork; optional so a payload from before it existed still renders (the sprite is the fallback). */
  imageUrl?: string | null
  types: string[]
  weightKg: number
  abilities: string[]
}

export interface PokemonStat {
  name: string
  baseValue: number
}

export interface EvolutionNode {
  id: number
  name: string
  spriteUrl: string | null
  requirements: string[]
  evolvesTo: EvolutionNode[]
}

export interface PokemonDetail {
  id: number
  name: string
  imageUrl: string | null
  spriteUrl: string | null
  types: string[]
  weightKg: number
  heightM: number
  abilities: string[]
  stats: PokemonStat[]
  genus: string | null
  description: string | null
  evolutionChain: EvolutionNode | null
}

export interface StoredPokemonSummary extends PokemonSummary {
  nickname: string | null
  region: string | null
  tags: string[]
}

export interface StoredPokemon extends PokemonDetail {
  version: number
  nickname: string | null
  region: string | null
  tags: string[]
  syncedAt: string
}

export interface SyncReport {
  created: number
  updated: number
  failures: { identifier: string; reason: string }[]
  abortedReason: string | null
}

export interface AuthResponse {
  token: string
  tokenType: string
  expiresAt: string
}

export interface RegisteredUser {
  id: number
  username: string
  email: string
  role: Role
}

/** The answer of PATCH /users/me: the trainer as saved and a token for the new username. */
export interface UpdatedProfile extends AuthResponse {
  user: RegisteredUser
}
