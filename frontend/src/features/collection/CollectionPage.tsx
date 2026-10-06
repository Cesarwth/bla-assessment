import { Link } from 'react-router'
import { ErrorState } from '../../shared/ui/ErrorState'
import { LoadingStatus } from '../../shared/ui/LoadingStatus'
import { Pagination } from '../../shared/ui/Pagination'
import { PAGE_SIZES, usePaging } from '../../shared/usePaging'
import { useAuth } from '../auth/useAuth'
import { CollectionCard } from './components/CollectionCard'
import { ImportRangeForm } from './components/ImportRangeForm'
import styles from './CollectionPage.module.css'
import { useCollectionPage } from './queries'

export function CollectionPage() {
  const { user } = useAuth()
  const { page, apiPage, size, hrefFor, setSize } = usePaging()
  const query = useCollectionPage(apiPage, size)

  function renderResults() {
    if (query.isError) return <ErrorState error={query.error} onRetry={() => void query.refetch()} />
    if (query.isPending) {
      return (
        <>
          <LoadingStatus label="Loading the collection…" />
          <ul className={styles.grid} aria-hidden="true">
            {Array.from({ length: 6 }, (_, index) => (
              <li key={index} className={styles.skeleton} />
            ))}
          </ul>
        </>
      )
    }
    if (query.data.totalItems === 0) {
      return (
        <div className={styles.empty}>
          <h2>The collection is empty</h2>
          <p>Add Pokémon from the catalog and give them a nickname, a region and tags.</p>
          <Link to="/">Browse the catalog</Link>
        </div>
      )
    }
    return (
      <>
        <ul className={styles.grid} aria-busy={query.isPlaceholderData}>
          {query.data.items.map((entry) => (
            <li key={entry.id}>
              <CollectionCard entry={entry} mine={user !== null && entry.ownerId === user.id} />
            </li>
          ))}
        </ul>
        <Pagination
          page={page}
          totalPages={query.data.totalPages}
          size={size}
          sizes={PAGE_SIZES}
          hrefFor={hrefFor}
          onSizeChange={setSize}
        />
      </>
    )
  }

  return (
    <section>
      <h1>Collection</h1>
      <p className={styles.intro}>
        Pokémon saved by trainers. Everyone can look; you can edit the ones you added.
      </p>
      {user ? (
        <ImportRangeForm />
      ) : (
        <p className={styles.loginHint}>
          <Link to="/login" state={{ from: '/collection' }}>
            Log in to import Pokémon
          </Link>{' '}
          and build the collection.
        </p>
      )}
      {renderResults()}
    </section>
  )
}
