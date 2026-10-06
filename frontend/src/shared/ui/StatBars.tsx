import type { PokemonStat } from '../api/types'
import { formatLabel } from '../format'
import styles from './StatBars.module.css'

// 255 is the highest base stat any Pokémon has, so bars across different Pokémon are comparable.
const MAX_BASE_STAT = 255

const statLabel = (name: string) => (name === 'hp' ? 'HP' : formatLabel(name))

export function StatBars({ stats }: { stats: PokemonStat[] }) {
  return (
    <ul className={styles.list} aria-label="Base stats">
      {stats.map((stat) => (
        <li key={stat.name} className={styles.row}>
          <span className={styles.name}>{statLabel(stat.name)}</span>
          <span className={styles.value}>{stat.baseValue}</span>
          <meter
            className={styles.meter}
            min={0}
            max={MAX_BASE_STAT}
            value={stat.baseValue}
            aria-label={`${statLabel(stat.name)}: ${stat.baseValue}`}
          />
        </li>
      ))}
    </ul>
  )
}
