import { formatLabel } from '../format'

export function TypeChip({ type }: { type: string }) {
  return (
    <span
      className="type-tint inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-xs font-semibold whitespace-nowrap text-fg"
      data-type={type}
    >
      <span className="size-2 rounded-full bg-(--type-color)" aria-hidden="true" />
      {formatLabel(type)}
    </span>
  )
}
