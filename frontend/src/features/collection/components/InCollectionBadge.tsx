import { useAuth } from '../../auth/useAuth'
import { useCollectionIds } from '../queries'
import styles from './InCollectionBadge.module.css'

/** Marks a catalog card with a Pokémon the trainer already collected. Quiet on purpose: no mark if it cannot tell. */
export function InCollectionBadge({ pokemonId }: { pokemonId: number }) {
  const { user } = useAuth()
  const ids = useCollectionIds(user !== null)
  if (!ids.data?.includes(pokemonId)) return null
  return <span className={styles.badge}>In my collection</span>
}
