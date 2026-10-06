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
    return (
      <div className={styles.box}>
        <p role="status" className={styles.status}>
          {sync.data.status === 201 ? 'Added to the collection.' : 'Already in the collection. Its data was refreshed.'}
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
