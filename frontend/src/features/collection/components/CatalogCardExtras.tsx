import type { PokemonSummary } from '../../../shared/api/types'
import { formatName } from '../../../shared/format'
import { useAuth } from '../../auth/useAuth'
import { useCollectionIds } from '../queries'
import { useSelection } from '../useSelection'
import styles from './CatalogCardExtras.module.css'
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
    <label className={styles.pick} data-selected={selected}>
      <input
        type="checkbox"
        className={styles.checkbox}
        aria-label={`Select ${formatName(pokemon.name)}`}
        checked={selected}
        disabled={!selected && selection.isFull}
        onChange={() => selection.toggle({ id: pokemon.id, name: pokemon.name })}
      />
      <span aria-hidden="true">{selected ? 'Selected' : 'Select'}</span>
    </label>
  )
}
