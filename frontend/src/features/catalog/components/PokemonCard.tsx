import { Link } from 'react-router'
import type { PokemonSummary } from '../../../shared/api/types'
import { formatId, formatList, formatName, formatWeight } from '../format'
import styles from './PokemonCard.module.css'
import { PokemonImage } from './PokemonImage'
import { TypeChip } from './TypeChip'

export function PokemonCard({ pokemon }: { pokemon: PokemonSummary }) {
  const name = formatName(pokemon.name)
  return (
    <article className={styles.card}>
      <div className={styles.art} data-type={pokemon.types[0]}>
        <PokemonImage src={pokemon.spriteUrl} alt={name} size={120} fallback="No image" />
      </div>
      <div>
        <span className={styles.id}>{formatId(pokemon.id)}</span>
        <h2 className={styles.title}>
          <Link className={styles.link} to={`/pokemon/${pokemon.id}`}>
            {name}
          </Link>
        </h2>
      </div>
      <div className={styles.types}>
        {pokemon.types.map((type) => (
          <TypeChip key={type} type={type} />
        ))}
      </div>
      <dl className={styles.facts}>
        <dt>Weight</dt>
        <dd>{formatWeight(pokemon.weightKg)}</dd>
        <dt>Abilities</dt>
        <dd>{formatList(pokemon.abilities)}</dd>
      </dl>
    </article>
  )
}
