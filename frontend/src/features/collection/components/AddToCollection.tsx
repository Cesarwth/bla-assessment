import { Link, useLocation } from 'react-router'
import { ErrorState } from '../../../shared/ui/ErrorState'
import { useAuth } from '../../auth/useAuth'
import { useSyncPokemon } from '../queries'
import styles from './AddToCollection.module.css'

export function AddToCollection({ pokemonId }: { pokemonId: number }) {
  const { user } = useAuth()
  const location = useLocation()
  const sync = useSyncPokemon()

  if (!user) {
    return (
      <p className={styles.box}>
        <Link to="/login" state={{ from: location.pathname }}>
          Log in to add this Pokémon to the collection
        </Link>
      </p>
    )
  }

  if (sync.isSuccess) {
    // Whoever syncs a Pokémon first owns it; a later add only refreshes its data, so say whose it stays.
    const ofSomeoneElse = sync.data.status !== 201 && sync.data.data.ownerId !== null && sync.data.data.ownerId !== user.id
    const message =
      sync.data.status === 201
        ? 'Added to the collection.'
        : ofSomeoneElse
          ? 'Already in the collection, added by another trainer, so it stays theirs. Its data was refreshed.'
          : 'Already in the collection. Its data was refreshed.'
    return (
      <div className={styles.box}>
        <p role="status" className={styles.status}>
          {message}
        </p>
        <Link to={`/collection/${pokemonId}`}>View it in the collection</Link>
      </div>
    )
  }

  return (
    <div className={styles.box}>
      <button type="button" className={styles.button} disabled={sync.isPending} onClick={() => sync.mutate(pokemonId)}>
        {sync.isPending ? 'Adding…' : 'Add to my collection'}
      </button>
      {sync.isError && <ErrorState error={sync.error} onRetry={() => sync.mutate(pokemonId)} />}
    </div>
  )
}
