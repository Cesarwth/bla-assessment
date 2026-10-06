import type { ReactNode } from 'react'
import { Link, useParams } from 'react-router'
import { isApiError } from '../../shared/api/errors'
import type { PokemonDetail } from '../../shared/api/types'
import { ErrorState } from '../../shared/ui/ErrorState'
import { EvolutionTree } from '../../shared/ui/EvolutionTree'
import { LoadingStatus } from '../../shared/ui/LoadingStatus'
import { PokemonImage } from '../../shared/ui/PokemonImage'
import { StatBars } from '../../shared/ui/StatBars'
import { TypeChip } from '../../shared/ui/TypeChip'
import { formatHeight, formatId, formatList, formatName, formatWeight } from '../../shared/format'
import styles from './PokemonDetailPage.module.css'
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
        <section className={styles.missing}>
          <h1>We couldn’t find that Pokémon</h1>
          <p>
            There is nothing called “{idOrName}” in the catalog. Check the spelling or try its
            number.
          </p>
          <Link to="/">Back to the catalog</Link>
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
      <div className={styles.layout}>
        <div className={styles.artCard} data-type={pokemon.types[0]}>
          <PokemonImage
            src={pokemon.imageUrl ?? pokemon.spriteUrl}
            alt={name}
            size={320}
            fallback="No image available"
            priority
          />
        </div>

        <div>
          <div className={styles.headline}>
            <h1>{name}</h1>
            <span className={styles.number}>{formatId(pokemon.id)}</span>
          </div>
          {pokemon.genus && <p className={styles.genus}>{pokemon.genus}</p>}
          <div className={styles.types}>
            {pokemon.types.map((type) => (
              <TypeChip key={type} type={type} />
            ))}
          </div>

          {/* Plain text on purpose: descriptions come from a third party and are never rendered as HTML. */}
          <p className={styles.description}>{pokemon.description ?? 'No description available.'}</p>

          <dl className={styles.facts}>
            <div className={styles.fact}>
              <dt>Weight</dt>
              <dd>{formatWeight(pokemon.weightKg)}</dd>
            </div>
            <div className={styles.fact}>
              <dt>Height</dt>
              <dd>{formatHeight(pokemon.heightM)}</dd>
            </div>
            <div className={styles.fact}>
              <dt>Abilities</dt>
              <dd>{formatList(pokemon.abilities)}</dd>
            </div>
          </dl>

          {renderActions?.(pokemon)}

          <section className={styles.section} aria-labelledby="stats-heading">
            <h2 id="stats-heading">Base stats</h2>
            <StatBars stats={pokemon.stats} />
          </section>
        </div>
      </div>

      <section className={styles.section} aria-label="Evolution">
        <h2>Evolution</h2>
        {evolves ? (
          <EvolutionTree chain={chain} currentId={pokemon.id} />
        ) : (
          <p className={styles.muted}>This Pokémon does not evolve.</p>
        )}
      </section>
    </article>
  )
}
