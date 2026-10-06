import { createContext } from 'react'

/** The API takes at most this many ids per request. */
export const MAX_SELECTION = 50

export interface SelectionValue {
  /** In the order they were ticked. */
  ids: number[]
  isFull: boolean
  has: (id: number) => boolean
  toggle: (id: number) => void
  /** Adds what fits under the limit; `isFull` tells whether the limit was reached. */
  addMany: (ids: number[]) => void
  clear: () => void
}

export const SelectionContext = createContext<SelectionValue | null>(null)
