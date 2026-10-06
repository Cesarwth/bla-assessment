import { useState, type FormEvent } from 'react'
import { describeError, isApiError } from '../../../shared/api/errors'
import type { StoredPokemon } from '../../../shared/api/types'
import { TextField } from '../../../shared/ui/TextField'
import type { EntryChanges } from '../api'
import { useUpdateEntry } from '../queries'
import styles from './EditForm.module.css'
import { TagInput } from './TagInput'

interface Draft {
  nickname: string
  region: string
  tags: string[]
}

const toDraft = (entry: StoredPokemon): Draft => ({
  nickname: entry.nickname ?? '',
  region: entry.region ?? '',
  tags: entry.tags,
})

/** Only what differs from the saved entry is sent; an empty text or list tells the API to clear the field. */
function changesOf(entry: StoredPokemon, draft: Draft): Omit<EntryChanges, 'version'> {
  const changes: Omit<EntryChanges, 'version'> = {}
  if (draft.nickname.trim() !== (entry.nickname ?? '')) changes.nickname = draft.nickname.trim()
  if (draft.region.trim() !== (entry.region ?? '')) changes.region = draft.region.trim()
  if (draft.tags.join('\n') !== entry.tags.join('\n')) changes.tags = draft.tags
  return changes
}

interface Props {
  entry: StoredPokemon
  /** Refetches the entry from the API (used after a conflict). */
  onReload: () => void
}

export function EditForm({ entry, onReload }: Props) {
  const [draft, setDraft] = useState<Draft>(() => toDraft(entry))
  const update = useUpdateEntry(entry.id)
  const changes = changesOf(entry, draft)
  const dirty = Object.keys(changes).length > 0
  const conflict = isApiError(update.error) && update.error.code === 'CONCURRENT_MODIFICATION'

  function submit(event: FormEvent) {
    event.preventDefault()
    if (!dirty) return
    update.mutate(
      { version: entry.version, ...changes },
      // The API normalizes what it stores (trimmed, lower-cased tags): show exactly what was saved.
      { onSuccess: (saved) => setDraft(toDraft(saved)) },
    )
  }

  function loadLatest() {
    update.reset()
    onReload()
  }

  return (
    <form className={styles.form} onSubmit={submit} noValidate aria-label="Edit trainer notes">
      <TextField
        label="Nickname"
        name="nickname"
        value={draft.nickname}
        onChange={(nickname) => setDraft({ ...draft, nickname })}
        maxLength={50}
      />
      <TextField
        label="Region"
        name="region"
        value={draft.region}
        onChange={(region) => setDraft({ ...draft, region })}
        maxLength={50}
        hint="For example Kanto, or the place where you met it."
      />
      <TagInput tags={draft.tags} onChange={(tags) => setDraft({ ...draft, tags })} />

      {conflict ? (
        <p role="alert" className={styles.alert}>
          Someone else changed this entry while you were editing it. Your changes are still here.
          <button type="button" onClick={loadLatest}>
            Load the latest version
          </button>
        </p>
      ) : (
        update.isError && (
          <p role="alert" className={styles.alert}>
            {describeError(update.error)}
          </p>
        )
      )}

      <div className={styles.actions}>
        <button type="submit" className={styles.save} disabled={!dirty || update.isPending}>
          {update.isPending ? 'Saving…' : 'Save changes'}
        </button>
        {update.isSuccess && !dirty && (
          <p role="status" className={styles.saved}>
            Saved.
          </p>
        )}
      </div>
    </form>
  )
}
