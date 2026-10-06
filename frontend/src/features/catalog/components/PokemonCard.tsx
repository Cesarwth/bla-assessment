import type { ReactNode } from 'react'
import { Link } from 'react-router'
import type { PokemonSummary } from '../../../shared/api/types'
import { formatList, formatName, formatWeight } from '../../../shared/format'
import { PokemonCardFrame } from '../../../shared/ui/PokemonCardFrame'
import { TypeChip } from '../../../shared/ui/TypeChip'

export function PokemonCard({ pokemon, extras }: { pokemon: PokemonSummary; extras?: ReactNode }) {
  const name = formatName(pokemon.name)
  return (
    <PokemonCardFrame
      type={pokemon.types[0]}
      id={pokemon.id}
      name={name}
      imageSrc={pokemon.imageUrl ?? pokemon.spriteUrl}
      extras={extras}
      title={
        // The title link covers the whole card, so the card is one big target with a single accessible name.
        <Link
          className="text-(--type-ink) no-underline after:absolute after:inset-0 after:content-[''] focus-visible:outline-(--type-ink)"
          to={`/pokemon/${pokemon.id}`}
        >
          {name}
        </Link>
      }
    >
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
    </PokemonCardFrame>
  )
}
