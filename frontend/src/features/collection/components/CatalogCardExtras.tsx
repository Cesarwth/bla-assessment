import type { PokemonSummary } from '../../../shared/api/types'
import { formatName } from '../../../shared/format'
import { useAuth } from '../../auth/useAuth'
import { useCollectionIds } from '../queries'
import { useSelection } from '../useSelection'
import styles from './CatalogCardExtras.module.css'
import { InCollectionBadge } from './InCollectionBadge'

/**
 * What a trainer sees on a catalog card: the mark when the Pokémon is already collected, otherwise a checkbox
 * to select it. Nothing for visitors, and nothing while we cannot tell (the catalog must work without it).
 */
export function CatalogCardExtras({ pokemon }: { pokemon: PokemonSummary }) {
  const { user } = useAuth()
  const ids = useCollectionIds(user !== null)
  const selection = useSelection()
  if (!ids.data) return null
  if (ids.data.includes(pokemon.id)) return <InCollectionBadge pokemonId={pokemon.id} />

  const selected = selection.has(pokemon.id)
  return (
    <input
      type="checkbox"
      className={styles.checkbox}
      aria-label={`Select ${formatName(pokemon.name)}`}
      checked={selected}
      disabled={!selected && selection.isFull}
      onChange={() => selection.toggle(pokemon.id)}
    />
  )
}
