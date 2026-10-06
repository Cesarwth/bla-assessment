import { useId, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router'
import styles from './GoToBox.module.css'

// The API has no search endpoint, so this opens a Pokémon directly by the same names/numbers it accepts.
const VALID_IDENTIFIER = /^[a-z0-9-]+$/

export function GoToBox() {
  const navigate = useNavigate()
  const inputId = useId()
  const errorId = useId()
  const [value, setValue] = useState('')
  const [invalid, setInvalid] = useState(false)

  function submit(event: FormEvent) {
    event.preventDefault()
    const identifier = value.trim().toLowerCase()
    if (!VALID_IDENTIFIER.test(identifier)) {
      setInvalid(true)
      return
    }
    setInvalid(false)
    navigate(`/pokemon/${identifier}`)
  }

  return (
    <form role="search" className={styles.form} onSubmit={submit} noValidate>
      <div className={styles.field}>
        <label htmlFor={inputId}>Find a Pokémon by name or number</label>
        <input
          id={inputId}
          className={styles.input}
          value={value}
          onChange={(event) => setValue(event.target.value)}
          autoComplete="off"
          aria-invalid={invalid}
          aria-describedby={invalid ? errorId : undefined}
          placeholder="pikachu or 25"
        />
      </div>
      <button type="submit" className={styles.submit}>
        Go
      </button>
      {invalid && (
        <p id={errorId} role="alert" className={styles.error}>
          Use a name or a number, like pikachu or 25.
        </p>
      )}
    </form>
  )
}
