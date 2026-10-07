import { useCallback, useMemo, useState, type ReactNode } from 'react'
import { useAuth } from '../../shared/session/useAuth'
import {
  MAX_SELECTION,
  SelectionContext,
  type SelectedPokemon,
  type SelectionValue,
} from './SelectionContext'

function SelectionState({ children }: { children: ReactNode }) {
  const [active, setActive] = useState(false)
  const [items, setItems] = useState<SelectedPokemon[]>([])

  const toggle = useCallback((pokemon: SelectedPokemon) => {
    setItems((current) => {
      if (current.some((selected) => selected.id === pokemon.id))
        return current.filter((selected) => selected.id !== pokemon.id)
      return current.length >= MAX_SELECTION
        ? current
        : [...current, { id: pokemon.id, name: pokemon.name }]
    })
  }, [])
  const remove = useCallback(
    (id: number) => setItems((current) => current.filter((selected) => selected.id !== id)),
    [],
  )
  const addMany = useCallback((more: SelectedPokemon[]) => {
    setItems((current) => {
      const fresh = more.filter(
        (pokemon) => !current.some((selected) => selected.id === pokemon.id),
      )
      return [...current, ...fresh.map(({ id, name }) => ({ id, name }))].slice(0, MAX_SELECTION)
    })
  }, [])
  const removeMany = useCallback(
    (ids: number[]) =>
      setItems((current) => current.filter((selected) => !ids.includes(selected.id))),
    [],
  )
  const clear = useCallback(() => setItems([]), [])

  const value = useMemo<SelectionValue>(
    () => ({
      active,
      setActive,
      items,
      ids: items.map((pokemon) => pokemon.id),
      isFull: items.length >= MAX_SELECTION,
      has: (id) => items.some((pokemon) => pokemon.id === id),
      toggle,
      remove,
      addMany,
      removeMany,
      clear,
    }),
    [active, items, toggle, remove, addMany, removeMany, clear],
  )
  return <SelectionContext.Provider value={value}>{children}</SelectionContext.Provider>
}

/**
 * Which Pokémon the trainer chose in the catalog, and whether the catalog is in selection mode. It lives above the
 * routes so the selection survives paging, filtering and a visit to a detail page; the key drops it when the session
 * changes, so one trainer's choices never leak to the next.
 */
export function SelectionProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  return <SelectionState key={user?.id ?? 'anonymous'}>{children}</SelectionState>
}
