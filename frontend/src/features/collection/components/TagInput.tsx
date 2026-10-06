import { useId, type KeyboardEvent } from 'react'
import { addTag } from '../tags'
import styles from './TagInput.module.css'

interface Props {
  tags: string[]
  onChange: (tags: string[]) => void
  /** What is typed but not yet confirmed: the form saves it too, so it is owned by the form. */
  text: string
  onTextChange: (text: string) => void
}

export function TagInput({ tags, onChange, text, onTextChange }: Props) {
  const inputId = useId()
  const { error } = addTag(tags, text)

  function add() {
    const result = addTag(tags, text)
    if (!result.error) {
      onChange(result.tags)
      onTextChange('')
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
        <input
          id={inputId}
          value={text}
          onChange={(event) => onTextChange(event.target.value)}
          onKeyDown={onKeyDown}
          autoComplete="off"
        />
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
