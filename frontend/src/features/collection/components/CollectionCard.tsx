import { Link } from 'react-router'
import { formatName } from '../../../shared/format'
import type { StoredPokemonSummary } from '../../../shared/api/types'
import { PokemonImage } from '../../../shared/ui/PokemonImage'
import { TypeChip } from '../../../shared/ui/TypeChip'
import styles from './CollectionCard.module.css'

export function CollectionCard({ entry }: { entry: StoredPokemonSummary }) {
  const name = formatName(entry.name)
  return (
    <article className={styles.card}>
      <div className={styles.top}>
        <div className={styles.art} data-type={entry.types[0]}>
          <PokemonImage src={entry.spriteUrl} alt={name} size={80} fallback="No image" />
        </div>
        <div>
          <h2 className={styles.title}>
            <Link className={styles.link} to={`/collection/${entry.id}`}>
              {name}
            </Link>
          </h2>
          {entry.nickname && <p className={styles.nickname}>“{entry.nickname}”</p>}
        </div>
      </div>
      <div>
        {entry.types.map((type) => (
          <TypeChip key={type} type={type} />
        ))}
      </div>
      {entry.region && <p className={styles.region}>{entry.region}</p>}
      {entry.tags.length > 0 && (
        <ul className={styles.tags} aria-label="Tags">
          {entry.tags.map((tag) => (
            <li key={tag} className={styles.tag}>
              {tag}
            </li>
          ))}
        </ul>
      )}
    </article>
  )
}
