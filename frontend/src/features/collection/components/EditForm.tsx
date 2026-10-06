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

function sameValue(field: keyof Draft, a: Draft[keyof Draft], b: Draft[keyof Draft]): boolean {
  return field === 'tags' ? (a as string[]).join('\n') === (b as string[]).join('\n') : (a as string).trim() === (b as string).trim()
}

/**
 * Only what the user actually edited is sent (empty text or an empty list tells the API to clear a field). A field
 * left alone is never part of the request, so it cannot overwrite somebody else's change.
 */
function changesOf(entry: StoredPokemon, edits: Partial<Draft>): Omit<EntryChanges, 'version'> {
  const base = toDraft(entry)
  const changes: Omit<EntryChanges, 'version'> = {}
  if (edits.nickname !== undefined && !sameValue('nickname', edits.nickname, base.nickname)) changes.nickname = edits.nickname.trim()
  if (edits.region !== undefined && !sameValue('region', edits.region, base.region)) changes.region = edits.region.trim()
  if (edits.tags !== undefined && !sameValue('tags', edits.tags, base.tags)) changes.tags = edits.tags
  return changes
}

interface Props {
  entry: StoredPokemon
  /** Refetches the entry from the API (used after a conflict). */
  onReload: () => void
}

export function EditForm({ entry, onReload }: Props) {
  // `edits` holds only the fields the user touched; every other field shows the entry as it is now, so after a
  // reload the form follows the latest version while keeping what the user typed.
  const [edits, setEdits] = useState<Partial<Draft>>({})
  const update = useUpdateEntry(entry.id)
  const base = toDraft(entry)
  const changes = changesOf(entry, edits)
  const dirty = Object.keys(changes).length > 0
  const conflict = isApiError(update.error) && update.error.code === 'CONCURRENT_MODIFICATION'

  const valueOf = <K extends keyof Draft>(field: K): Draft[K] => (edits[field] ?? base[field]) as Draft[K]
  const edit = <K extends keyof Draft>(field: K, value: Draft[K]) => setEdits((current) => ({ ...current, [field]: value }))

  function submit(event: FormEvent) {
    event.preventDefault()
    if (!dirty) return
    const sent = changes
    update.mutate(
      { version: entry.version, ...sent },
      {
        // The saved values now are the entry: forget those edits, but keep anything typed while the request ran.
        onSuccess: () =>
          setEdits((current) => {
            const remaining = { ...current }
            if (sent.nickname !== undefined && current.nickname?.trim() === sent.nickname) delete remaining.nickname
            if (sent.region !== undefined && current.region?.trim() === sent.region) delete remaining.region
            if (sent.tags !== undefined && current.tags && sameValue('tags', current.tags, sent.tags)) delete remaining.tags
            return remaining
          }),
      },
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
        value={valueOf('nickname')}
        onChange={(nickname) => edit('nickname', nickname)}
        maxLength={50}
      />
      <TextField
        label="Region"
        name="region"
        value={valueOf('region')}
        onChange={(region) => edit('region', region)}
        maxLength={50}
        hint="For example Kanto, or the place where you met it."
      />
      <TagInput tags={valueOf('tags')} onChange={(tags) => edit('tags', tags)} />

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
