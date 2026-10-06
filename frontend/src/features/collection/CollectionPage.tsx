import { Link } from 'react-router'
import { Card } from '../../shared/ui/Card'
import { ErrorState } from '../../shared/ui/ErrorState'
import { LoadingStatus } from '../../shared/ui/LoadingStatus'
import { Pagination } from '../../shared/ui/Pagination'
import { PAGE_SIZES, usePaging } from '../../shared/usePaging'
import { CollectionCard } from './components/CollectionCard'
import { useDocumentTitle } from '../../shared/useDocumentTitle'
import { useCollectionPage } from './queries'

const gridClass = 'grid grid-cols-[repeat(auto-fill,minmax(min(16rem,100%),1fr))] gap-4'

export function CollectionPage() {
  const { page, apiPage, size, hrefFor, setSize } = usePaging()
  const query = useCollectionPage(apiPage, size)
  useDocumentTitle('My collection')

  function renderResults() {
    if (query.isError)
      return <ErrorState error={query.error} onRetry={() => void query.refetch()} />
    if (query.isPending) {
      return (
        <>
          <LoadingStatus label="Loading your collection…" />
          <ul className={gridClass} aria-hidden="true">
            {Array.from({ length: 6 }, (_, index) => (
              <li key={index} className="h-44 rounded-md bg-surface-muted" />
            ))}
          </ul>
        </>
      )
    }
    if (query.data.totalItems === 0) {
      return (
        <Card glass className="px-4 py-8 text-center text-muted">
          <h2 className="text-fg">Your collection is empty</h2>
          <p>
            Select Pokémon in the catalog to add several at once, then give them a nickname, a
            region and tags.
          </p>
          <Link to="/">Browse the catalog</Link>
        </Card>
      )
    }
    if (query.data.items.length === 0) {
      // Beyond the last page (a stale bookmark, or the last entry of the last page was just deleted).
      return (
        <Card glass className="px-4 py-8 text-center text-muted">
          <h2 className="text-fg">No entries on this page</h2>
          <Link to={hrefFor(1)}>Go to the first page</Link>
        </Card>
      )
    }
    return (
      <>
        <ul className={`${gridClass} aria-busy:opacity-60`} aria-busy={query.isPlaceholderData}>
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
      <Card glass className="mb-4 px-4 py-4 sm:px-6">
        <h1>My collection</h1>
        <p className="mb-0 max-w-2xl text-muted">
          Your Pokémon. Select Pokémon in the catalog to add several at once.
        </p>
      </Card>
      {renderResults()}
    </section>
  )
}
