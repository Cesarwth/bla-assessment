import { useCallback, useMemo, useState, type ReactNode } from 'react'
import { useAuth } from '../auth/useAuth'
import { MAX_SELECTION, SelectionContext, type SelectionValue } from './SelectionContext'

function SelectionState({ children }: { children: ReactNode }) {
  const [ids, setIds] = useState<number[]>([])

  const toggle = useCallback((id: number) => {
    setIds((current) => {
      if (current.includes(id)) return current.filter((other) => other !== id)
      return current.length >= MAX_SELECTION ? current : [...current, id]
    })
  }, [])
  const addMany = useCallback((more: number[]) => {
    setIds((current) => {
      const fresh = more.filter((id) => !current.includes(id))
      return [...current, ...fresh].slice(0, MAX_SELECTION)
    })
  }, [])
  const clear = useCallback(() => setIds([]), [])

  const value = useMemo<SelectionValue>(
    () => ({ ids, isFull: ids.length >= MAX_SELECTION, has: (id) => ids.includes(id), toggle, addMany, clear }),
    [ids, toggle, addMany, clear],
  )
  return <SelectionContext.Provider value={value}>{children}</SelectionContext.Provider>
}

/**
 * Which Pokémon the trainer ticked in the catalog. It lives above the routes so the selection survives paging
 * (and a visit to a detail page); the key drops it when the session changes, so one trainer's ticks never leak to the next.
 */
export function SelectionProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  return <SelectionState key={user?.username ?? 'anonymous'}>{children}</SelectionState>
}
