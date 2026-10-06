import { useState } from 'react'
import { useNavigate } from 'react-router'
import { formatName } from '../../../shared/format'
import type { StoredPokemon } from '../../../shared/api/types'
import { Button } from '../../../shared/ui/Button'
import { ErrorState } from '../../../shared/ui/ErrorState'
import { useDeleteEntry } from '../queries'

// An inline confirmation instead of window.confirm or <dialog>: it stays in the page, is announced to screen
// readers as part of the content and needs nothing the test environment lacks.
export function DeleteEntry({ entry }: { entry: StoredPokemon }) {
  const [confirming, setConfirming] = useState(false)
  const remove = useDeleteEntry(entry.id)
  const navigate = useNavigate()

  if (!confirming) {
    return (
      <div className="mt-6">
        <Button variant="danger-outline" onClick={() => setConfirming(true)}>
          Remove from my collection
        </Button>
      </div>
    )
  }

  return (
    <div className="mt-6">
      <div
        role="group"
        aria-label="Confirm removal"
        className="grid max-w-120 gap-3 rounded-md border border-danger bg-danger-surface p-4"
      >
        <p className="mb-0">
          Remove {formatName(entry.name)} from your collection? This can’t be undone.
        </p>
        <div className="flex flex-wrap gap-3">
          <Button
            variant="danger"
            disabled={remove.isPending}
            onClick={() => remove.mutate(undefined, { onSuccess: () => navigate('/collection') })}
          >
            {remove.isPending ? 'Removing…' : 'Yes, remove it'}
          </Button>
          <Button onClick={() => setConfirming(false)}>Cancel</Button>
        </div>
        {remove.isError && <ErrorState error={remove.error} />}
      </div>
    </div>
  )
}
