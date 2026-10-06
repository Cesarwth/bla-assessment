import type { PokemonStat } from '../api/types'
import { formatLabel } from '../format'

// 255 is the highest base stat any Pokémon has, so bars across different Pokémon are comparable.
const MAX_BASE_STAT = 255
// Below LOW is a weak stat, above HIGH a strong one; the browser colors the <meter> by these parts.
const LOW = 60
const HIGH = 100

const statLabel = (name: string) => (name === 'hp' ? 'HP' : formatLabel(name))

export function StatBars({ stats }: { stats: PokemonStat[] }) {
  return (
    <ul className="grid gap-2" aria-label="Base stats">
      {stats.map((stat) => (
        <li
          key={stat.name}
          className="grid grid-cols-[6.5rem_2rem_1fr] items-center gap-2 sm:grid-cols-[8rem_2.5rem_1fr] sm:gap-3"
        >
          <span className="text-sm text-muted">{statLabel(stat.name)}</span>
          <span className="text-right font-bold tabular-nums">{stat.baseValue}</span>
          <meter
            className="stat-meter h-2 w-full overflow-hidden rounded-full border-0"
            min={0}
            max={MAX_BASE_STAT}
            low={LOW}
            high={HIGH}
            optimum={MAX_BASE_STAT}
            value={stat.baseValue}
            aria-label={`${statLabel(stat.name)}: ${stat.baseValue}`}
          />
        </li>
      ))}
    </ul>
  )
}
