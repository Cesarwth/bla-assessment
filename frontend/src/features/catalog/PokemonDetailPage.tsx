import type { ReactNode } from 'react'
import { useParams } from 'react-router'
import { isApiError } from '../../shared/api/errors'
import type { PokemonDetail } from '../../shared/api/types'
import { BackLink } from '../../shared/ui/BackLink'
import { Card } from '../../shared/ui/Card'
import { ErrorState } from '../../shared/ui/ErrorState'
import { EvolutionTree } from '../../shared/ui/EvolutionTree'
import { LoadingStatus } from '../../shared/ui/LoadingStatus'
import { PokemonImage } from '../../shared/ui/PokemonImage'
import { StatBars } from '../../shared/ui/StatBars'
import { TypeChip } from '../../shared/ui/TypeChip'
import { formatHeight, formatId, formatList, formatName, formatWeight } from '../../shared/format'
import { useDocumentTitle } from '../../shared/useDocumentTitle'
import { usePokemonDetail } from './queries'

const MISSING_CODES = ['POKEMON_NOT_FOUND', 'INVALID_POKEMON_IDENTIFIER']

interface Props {
  /**
   * Extra actions shown under the facts. The app composes them in, so this feature does not depend on the
   * collection feature.
   */
  renderActions?: (pokemon: PokemonDetail) => ReactNode
}

export function PokemonDetailPage({ renderActions }: Props) {
  const { idOrName = '' } = useParams()
  const query = usePokemonDetail(idOrName)
  useDocumentTitle(query.data ? formatName(query.data.name) : undefined)

  if (query.isPending) return <LoadingStatus />

  if (query.isError) {
    if (isApiError(query.error) && MISSING_CODES.includes(query.error.code)) {
      return (
        <section className="py-8">
          <BackLink fallbackTo="/" fallbackLabel="the catalog" />
          <h1>We couldn’t find that Pokémon</h1>
          <p>
            There is nothing called “{idOrName}” in the catalog. Check the spelling or try its
            number.
          </p>
        </section>
      )
    }
    return <ErrorState error={query.error} onRetry={() => void query.refetch()} />
  }

  const pokemon = query.data
  const name = formatName(pokemon.name)
  const chain = pokemon.evolutionChain
  const evolves = chain !== null && chain.evolvesTo.length > 0

  return (
    <article>
      <BackLink fallbackTo="/" fallbackLabel="the catalog" />
      <div className="grid gap-6 md:grid-cols-[minmax(14rem,20rem)_1fr] md:items-start">
        <div
          className="mx-auto grid aspect-square w-full max-w-80 place-items-center rounded-lg border border-border p-4 type-glow-art md:max-w-none"
          data-type={pokemon.types[0]}
        >
          <PokemonImage
            src={pokemon.imageUrl ?? pokemon.spriteUrl}
            alt={name}
            size={320}
            fallback="No image available"
            priority
          />
        </div>

        <div>
          <div className="flex flex-wrap items-baseline gap-x-3 gap-y-2">
            <h1 className="mb-0 wrap-break-word">{name}</h1>
            <span className="font-bold text-muted">{formatId(pokemon.id)}</span>
          </div>
          {pokemon.genus && <p className="mb-3 text-muted">{pokemon.genus}</p>}
          <div className="mb-4 flex flex-wrap gap-2">
            {pokemon.types.map((type) => (
              <TypeChip key={type} type={type} />
            ))}
          </div>

          {/* Plain text on purpose: descriptions come from a third party and are never rendered as HTML. */}
          <p className="max-w-2xl text-[1.05rem]">
            {pokemon.description ?? 'No description available.'}
          </p>

          <dl className="my-4 grid grid-cols-[repeat(auto-fit,minmax(min(9rem,100%),1fr))] gap-3">
            <Card className="px-4 py-3">
              <dt className="text-xs text-muted">Weight</dt>
              <dd className="font-bold wrap-break-word">{formatWeight(pokemon.weightKg)}</dd>
            </Card>
            <Card className="px-4 py-3">
              <dt className="text-xs text-muted">Height</dt>
              <dd className="font-bold wrap-break-word">{formatHeight(pokemon.heightM)}</dd>
            </Card>
            <Card className="px-4 py-3">
              <dt className="text-xs text-muted">Abilities</dt>
              <dd className="font-bold wrap-break-word">{formatList(pokemon.abilities)}</dd>
            </Card>
          </dl>

          {renderActions?.(pokemon)}

          <section className="mt-8" aria-labelledby="stats-heading">
            <h2 id="stats-heading" className="text-lg">
              Base stats
            </h2>
            <StatBars stats={pokemon.stats} />
          </section>
        </div>
      </div>

      <section className="mt-8" aria-label="Evolution">
        <h2 className="text-lg">Evolution</h2>
        {evolves ? (
          <EvolutionTree chain={chain} currentId={pokemon.id} />
        ) : (
          <p className="text-muted">This Pokémon does not evolve.</p>
        )}
      </section>
    </article>
  )
}
