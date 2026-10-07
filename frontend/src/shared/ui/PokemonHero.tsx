import type { ReactNode } from 'react'
import { formatId } from '../format'
import { PokemonImage } from './PokemonImage'
import { TypeChip } from './TypeChip'

interface Props {
  id: number
  name: string
  types: string[]
  imageSrc: string | null
  genus?: string | null
  children?: ReactNode
}

export function PokemonHero({ id, name, types, imageSrc, genus, children }: Props) {
  return (
    <div
      className="grid items-center gap-4 overflow-hidden rounded-3xl border-4 border-white/60 type-card p-5 shadow-xl sm:p-8 md:grid-cols-[auto_1fr] md:gap-8"
      data-type={types[0] ?? 'unknown'}
    >
      <div className="relative mx-auto grid size-52 place-items-center sm:size-64">
        <div className="absolute inset-2 rounded-full bg-white/30 blur-2xl" aria-hidden="true" />
        <div className="relative size-full drop-shadow-xl">
          <PokemonImage
            src={imageSrc}
            alt={name}
            size={256}
            fallback="No image available"
            priority
          />
        </div>
      </div>
      <div className="min-w-0">
        <div className="flex flex-wrap items-center gap-x-3 gap-y-2">
          <h1 className="mb-0 text-3xl wrap-break-word capitalize sm:text-4xl">{name}</h1>
          <span className="type-glass rounded-full px-3 py-1 text-sm font-bold tabular-nums">
            {formatId(id)}
          </span>
        </div>
        {genus && <p className="mt-1 mb-0 font-semibold">{genus}</p>}
        <div className="mt-4 flex flex-wrap gap-2">
          {types.map((type) => (
            <TypeChip key={type} type={type} />
          ))}
        </div>
        {children}
      </div>
    </div>
  )
}
