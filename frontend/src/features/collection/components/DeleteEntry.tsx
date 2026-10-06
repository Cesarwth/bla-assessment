import { useState } from 'react'
import { useNavigate } from 'react-router'
import { formatName } from '../../../shared/format'
import type { StoredPokemon } from '../../../shared/api/types'
import { ErrorState } from '../../../shared/ui/ErrorState'
import { useDeleteEntry } from '../queries'
import styles from './DeleteEntry.module.css'

// An inline confirmation instead of window.confirm or <dialog>: it stays in the page, is announced to screen
// readers as part of the content and needs nothing the test environment lacks.
export function DeleteEntry({ entry }: { entry: StoredPokemon }) {
  const [confirming, setConfirming] = useState(false)
  const remove = useDeleteEntry(entry.id)
  const navigate = useNavigate()

  if (!confirming) {
    return (
      <div className={styles.delete}>
        <button type="button" className={styles.trigger} onClick={() => setConfirming(true)}>
          Remove from my collection
        </button>
      </div>
    )
  }

  return (
    <div className={styles.delete}>
      <div role="group" aria-label="Confirm removal" className={styles.box}>
        <p>Remove {formatName(entry.name)} from your collection? This can’t be undone.</p>
        <div className={styles.buttons}>
          <button
            type="button"
            className={styles.confirm}
            disabled={remove.isPending}
            onClick={() => remove.mutate(undefined, { onSuccess: () => navigate('/collection') })}
          >
            {remove.isPending ? 'Removing…' : 'Yes, remove it'}
          </button>
          <button type="button" className={styles.cancel} onClick={() => setConfirming(false)}>
            Cancel
          </button>
        </div>
        {remove.isError && <ErrorState error={remove.error} />}
      </div>
    </div>
  )
}
