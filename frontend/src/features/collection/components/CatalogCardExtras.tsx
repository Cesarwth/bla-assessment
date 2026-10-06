import type { PokemonSummary } from '../../../shared/api/types'
import { formatName } from '../../../shared/format'
import { useAuth } from '../../../shared/session/useAuth'
import { useCollectionIds } from '../queries'
import { useSelection } from '../useSelection'
import { InCollectionBadge } from './InCollectionBadge'

/**
 * What a trainer sees on a catalog card: the mark when the Pokémon is already collected, otherwise, only while
 * selection mode is on, a checkbox to select it. Nothing for visitors, and nothing while we cannot tell (the
 * catalog must work without it).
 */
export function CatalogCardExtras({ pokemon }: { pokemon: PokemonSummary }) {
  const { user } = useAuth()
  const ids = useCollectionIds(user !== null)
  const selection = useSelection()
  if (!ids.data) return null
  if (ids.data.includes(pokemon.id)) return <InCollectionBadge pokemonId={pokemon.id} />
  if (!selection.active) return null

  const selected = selection.has(pokemon.id)
  return (
    // Above the card-wide link (its ::after), so ticking never opens the detail page.
    <label
      className={`absolute top-3 right-3 z-1 inline-flex min-h-11 cursor-pointer items-center gap-2 rounded-full border bg-surface px-3 py-1 text-sm font-semibold text-fg shadow-sm sm:min-h-0 ${selected ? 'border-primary ring-1 ring-primary' : 'border-border'}`}
    >
      <input
        type="checkbox"
        className="m-0 size-6 cursor-pointer accent-primary disabled:cursor-not-allowed"
        aria-label={`Select ${formatName(pokemon.name)}`}
        checked={selected}
        disabled={!selected && selection.isFull}
        onChange={() => selection.toggle({ id: pokemon.id, name: pokemon.name })}
      />
      <span aria-hidden="true">{selected ? 'Selected' : 'Select'}</span>
    </label>
  )
}
