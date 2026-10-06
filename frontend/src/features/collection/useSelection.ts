import { useContext } from 'react'
import { SelectionContext, type SelectionValue } from './SelectionContext'

export function useSelection(): SelectionValue {
  const context = useContext(SelectionContext)
  if (!context) throw new Error('useSelection must be used inside <SelectionProvider>')
  return context
}
