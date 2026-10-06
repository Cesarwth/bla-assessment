import { Link } from 'react-router'
import { ErrorState } from '../../shared/ui/ErrorState'
import { LoadingStatus } from '../../shared/ui/LoadingStatus'
import { Pagination } from '../../shared/ui/Pagination'
import { PAGE_SIZES, usePaging } from '../../shared/usePaging'
import { CollectionCard } from './components/CollectionCard'
import styles from './CollectionPage.module.css'
import { useDocumentTitle } from '../../shared/useDocumentTitle'
import { useCollectionPage } from './queries'

export function CollectionPage() {
  const { page, apiPage, size, hrefFor, setSize } = usePaging()
  const query = useCollectionPage(apiPage, size)
  useDocumentTitle('My collection')

  function renderResults() {
    if (query.isError) return <ErrorState error={query.error} onRetry={() => void query.refetch()} />
    if (query.isPending) {
      return (
        <>
          <LoadingStatus label="Loading your collection…" />
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
          <h2>Your collection is empty</h2>
          <p>Select Pokémon in the catalog to add several at once, then give them a nickname, a region and tags.</p>
          <Link to="/">Browse the catalog</Link>
        </div>
      )
    }
    if (query.data.items.length === 0) {
      // Beyond the last page (a stale bookmark, or the last entry of the last page was just deleted).
      return (
        <div className={styles.empty}>
          <h2>No entries on this page</h2>
          <Link to={hrefFor(1)}>Go to the first page</Link>
        </div>
      )
    }
    return (
      <>
        <ul className={styles.grid} aria-busy={query.isPlaceholderData}>
          {query.data.items.map((entry) => (
            <li key={entry.id}>
              <CollectionCard entry={entry} />
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
      <h1>My collection</h1>
      <p className={styles.intro}>
        Your Pokémon. Select Pokémon in the catalog to add several at once.
      </p>
      {renderResults()}
    </section>
  )
}
