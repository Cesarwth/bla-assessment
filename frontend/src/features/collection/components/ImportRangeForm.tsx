import { useState, type FormEvent } from 'react'
import { ErrorState } from '../../../shared/ui/ErrorState'
import { TextField } from '../../../shared/ui/TextField'
import { useImportRange } from '../queries'
import styles from './ImportRangeForm.module.css'

// Same limits as the API (it refuses wider ranges); checking here just saves a round trip.
const MAX_RANGE = 20

export function ImportRangeForm() {
  const [from, setFrom] = useState('1')
  const [to, setTo] = useState(String(MAX_RANGE))
  const [invalid, setInvalid] = useState(false)
  const importRange = useImportRange()

  function submit(event: FormEvent) {
    event.preventDefault()
    const first = Number(from)
    const last = Number(to)
    const valid = from !== '' && to !== '' && Number.isInteger(first) && Number.isInteger(last) && first >= 1 && last >= first && last - first + 1 <= MAX_RANGE
    setInvalid(!valid)
    if (valid) importRange.mutate({ from: first, to: last })
  }

  const report = importRange.data

  return (
    <section className={styles.panel}>
      <h2>Import Pokémon</h2>
      <p className={styles.hint}>Add several Pokémon at once by their Pokédex number, up to {MAX_RANGE} at a time.</p>
      <form className={styles.form} onSubmit={submit} noValidate aria-label="Import Pokémon by number">
        <TextField label="From" name="from" type="number" min={1} value={from} onChange={setFrom} />
        <TextField label="To" name="to" type="number" min={1} value={to} onChange={setTo} />
        <button type="submit" className={styles.submit} disabled={importRange.isPending}>
          {importRange.isPending ? 'Importing…' : 'Import'}
        </button>
      </form>
      {invalid && (
        <p role="alert" className={styles.alert}>
          Choose a range of at most {MAX_RANGE} numbers, starting at 1 or higher.
        </p>
      )}
      {importRange.isError && <ErrorState error={importRange.error} />}
      {report && (
        <div className={styles.report} role="status">
          <p>
            Added {report.created}, refreshed {report.updated}.
          </p>
          {report.abortedReason && <p>The import stopped early: {report.abortedReason}.</p>}
          {report.failures.length > 0 && (
            <ul className={styles.failures} aria-label="Not imported">
              {report.failures.map((failure) => (
                <li key={failure.identifier}>
                  #{failure.identifier}: {failure.reason}
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </section>
  )
}
