import { useId, useState, type KeyboardEvent } from 'react'
import { addTag } from '../tags'
import styles from './TagInput.module.css'

interface Props {
  tags: string[]
  onChange: (tags: string[]) => void
}

export function TagInput({ tags, onChange }: Props) {
  const inputId = useId()
  const [text, setText] = useState('')
  const [error, setError] = useState<string>()

  function add() {
    const result = addTag(tags, text)
    setError(result.error)
    if (!result.error) {
      onChange(result.tags)
      setText('')
    }
  }

  function onKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    // Enter would submit the surrounding form; here it means "add this tag".
    if (event.key === 'Enter' || event.key === ',') {
      event.preventDefault()
      add()
    }
  }

  return (
    <div className={styles.group}>
      <label htmlFor={inputId} className={styles.label}>
        Add a tag
      </label>
      {tags.length > 0 && (
        <ul className={styles.chips} aria-label="Tags">
          {tags.map((tag) => (
            <li key={tag} className={styles.chip}>
              {tag}
              <button
                type="button"
                className={styles.remove}
                aria-label={`Remove tag ${tag}`}
                onClick={() => onChange(tags.filter((existing) => existing !== tag))}
              >
                <span aria-hidden="true">×</span>
              </button>
            </li>
          ))}
        </ul>
      )}
      <div className={styles.add}>
        <input id={inputId} value={text} onChange={(event) => setText(event.target.value)} onKeyDown={onKeyDown} autoComplete="off" />
        <button type="button" onClick={add}>
          Add tag
        </button>
      </div>
      {error && (
        <p role="alert" className={styles.error}>
          {error}
        </p>
      )}
    </div>
  )
}
