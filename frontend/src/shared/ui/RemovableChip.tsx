import type { ReactNode } from 'react'
import { IconButton } from './IconButton'

interface Props {
  children: ReactNode
  /** Accessible name of the remove button, e.g. "Remove tag fast". */
  removeLabel: string
  onRemove: () => void
  disabled?: boolean
}

/** A pill with a remove button, for the tags being edited and for the selection tray. */
export function RemovableChip({ children, removeLabel, onRemove, disabled }: Props) {
  return (
    <li className="inline-flex max-w-full items-center gap-1 rounded-full bg-surface-muted py-0.5 pr-0.5 pl-3 text-sm font-semibold">
      <span className="min-w-0 wrap-break-word">{children}</span>
      <IconButton aria-label={removeLabel} disabled={disabled} onClick={onRemove} />
    </li>
  )
}
