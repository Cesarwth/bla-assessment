import { Link, useLocation } from 'react-router'
import { Button } from '../../../shared/ui/Button'
import { ErrorState } from '../../../shared/ui/ErrorState'
import { StatusNote } from '../../../shared/ui/StatusNote'
import { useAuth } from '../../../shared/session/useAuth'
import { useCollectionIds, useSyncPokemon } from '../queries'

/** Each trainer has their own collection: the button exists while this Pokémon is not in the logged-in trainer's. */
const boxClass =
  'my-4 flex flex-wrap items-center gap-x-4 gap-y-3 rounded-md border border-border bg-surface/80 px-4 py-3 backdrop-blur'

export function AddToCollection({ pokemonId }: { pokemonId: number }) {
  const { user } = useAuth()
  const location = useLocation()
  // Set by the edit form when it sends you here after saving.
  const saved = (location.state as { saved?: string } | null)?.saved
  const ids = useCollectionIds(user !== null)
  const sync = useSyncPokemon()

  if (!user) {
    return (
      <p className={boxClass}>
        <Link to="/login" state={{ from: location.pathname }}>
          Log in to add this Pokémon to the collection
        </Link>
      </p>
    )
  }

  const collected = ids.data?.includes(pokemonId) === true
  const status = sync.data?.status === 201 ? 'Added to your collection.' : 'In your collection.'
  // One live region for the whole life of the box: the button turns into this message, and a region inserted
  // together with its text is easily missed by screen readers.
  const message = collected ? (saved ? `Saved changes to ${saved}. ${status}` : status) : undefined

  function renderBody() {
    if (collected) return <Link to={`/collection/${pokemonId}`}>View it in my collection</Link>
    if (ids.isPending) return 'Checking the collection…'
    if (ids.isError) return <ErrorState error={ids.error} onRetry={() => void ids.refetch()} />
    return (
      <>
        <Button
          variant="primary"
          size="lg"
          disabled={sync.isPending}
          onClick={() => sync.mutate(pokemonId)}
        >
          {sync.isPending ? 'Adding…' : 'Add to my collection'}
        </Button>
        {sync.isError && <ErrorState error={sync.error} onRetry={() => sync.mutate(pokemonId)} />}
      </>
    )
  }

  return (
    <div className={boxClass}>
      <StatusNote boxClass="rounded-sm bg-success-surface px-4 py-2">{message}</StatusNote>
      {renderBody()}
    </div>
  )
}
