import { createContext } from 'react'

/** The API takes at most this many ids per request. */
export const MAX_SELECTION = 50

/** The name travels with the id so the tray can list Pokémon that are on another page or filtered out. */
export interface SelectedPokemon {
  id: number
  name: string
}

export interface SelectionValue {
  /** Whether the catalog is showing checkboxes. */
  active: boolean
  setActive: (active: boolean) => void
  /** In the order they were ticked. */
  items: SelectedPokemon[]
  ids: number[]
  isFull: boolean
  has: (id: number) => boolean
  toggle: (pokemon: SelectedPokemon) => void
  remove: (id: number) => void
  /** Adds what fits under the limit; `isFull` tells whether the limit was reached. */
  addMany: (pokemon: SelectedPokemon[]) => void
  clear: () => void
}

export const SelectionContext = createContext<SelectionValue | null>(null)
