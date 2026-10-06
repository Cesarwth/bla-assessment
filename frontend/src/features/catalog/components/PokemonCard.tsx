import type { ReactNode } from 'react'
import { Link } from 'react-router'
import type { PokemonSummary } from '../../../shared/api/types'
import { formatId, formatList, formatName, formatWeight } from '../../../shared/format'
import { Card } from '../../../shared/ui/Card'
import { PokemonImage } from '../../../shared/ui/PokemonImage'
import { TypeChip } from '../../../shared/ui/TypeChip'

export function PokemonCard({ pokemon, extras }: { pokemon: PokemonSummary; extras?: ReactNode }) {
  const name = formatName(pokemon.name)
  return (
    <Card
      as="article"
      interactive
      className="relative flex h-full flex-col gap-3 p-4 has-[input:checked]:border-primary has-[input:checked]:ring-3 has-[input:checked]:ring-primary"
    >
      {extras}
      <div className="size-30 self-center rounded-full type-glow" data-type={pokemon.types[0]}>
        <PokemonImage src={pokemon.spriteUrl} alt={name} size={120} fallback="No image" />
      </div>
      <div>
        <span className="text-xs font-semibold text-muted">{formatId(pokemon.id)}</span>
        <h2 className="mb-0 text-lg">
          {/* The title link covers the whole card, so the card is one big target with a single accessible name. */}
          <Link
            className="text-fg no-underline after:absolute after:inset-0 after:rounded-md after:content-['']"
            to={`/pokemon/${pokemon.id}`}
          >
            {name}
          </Link>
        </h2>
      </div>
      <div className="flex flex-wrap gap-2">
        {pokemon.types.map((type) => (
          <TypeChip key={type} type={type} />
        ))}
      </div>
      <dl className="mb-0 grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 text-sm">
        <dt className="text-muted">Weight</dt>
        <dd>{formatWeight(pokemon.weightKg)}</dd>
        <dt className="text-muted">Abilities</dt>
        <dd className="wrap-break-word">{formatList(pokemon.abilities)}</dd>
      </dl>
    </Card>
  )
}
