import { formatLabel } from '../format'

/** Capitals come from CSS only, so the accessible name stays "Fire" and not "FIRE". */
export function TypeChip({ type }: { type: string }) {
  return (
    <span
      className="type-pill inline-flex items-center rounded-full px-3 py-1 text-xs font-bold tracking-wider whitespace-nowrap uppercase"
      data-type={type}
    >
      {formatLabel(type)}
    </span>
  )
}
