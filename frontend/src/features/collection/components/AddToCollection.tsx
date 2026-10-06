import { Link, useLocation } from 'react-router'
import { ApiError } from '../../../shared/api/errors'
import { ErrorState } from '../../../shared/ui/ErrorState'
import { useAuth } from '../../auth/useAuth'
import { useCollectionEntry, useSyncPokemon } from '../queries'
import styles from './AddToCollection.module.css'

/** Whoever adds a Pokémon first owns it, so the button only exists while nobody has claimed it. */
export function AddToCollection({ pokemonId }: { pokemonId: number }) {
  const { user } = useAuth()
  const location = useLocation()
  const entry = useCollectionEntry(pokemonId, user !== null)
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

  if (entry.data) {
    const { ownerId } = entry.data
    const message =
      sync.data?.status === 201
        ? 'Added to the collection.'
        : ownerId === user.id
          ? 'In the collection, added by you.'
          : ownerId === null
            ? 'In the collection, with no owner (only admins can edit it).'
            : 'In the collection, added by another trainer.'
    return (
      <div className={styles.box}>
        <p role="status" className={styles.status}>
          {message}
        </p>
        <Link to={`/collection/${pokemonId}`}>View it in the collection</Link>
      </div>
    )
  }

  if (entry.isPending) {
    return <p className={styles.box}>Checking the collection…</p>
  }

  if (!(entry.error instanceof ApiError && entry.error.status === 404)) {
    return (
      <div className={styles.box}>
        <ErrorState error={entry.error} onRetry={() => void entry.refetch()} />
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
