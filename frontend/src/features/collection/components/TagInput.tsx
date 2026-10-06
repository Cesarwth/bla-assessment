import { useId, type KeyboardEvent } from 'react'
import { Button } from '../../../shared/ui/Button'
import { fieldClass } from '../../../shared/ui/fieldClass'
import { RemovableChip } from '../../../shared/ui/RemovableChip'
import { addTag } from '../tags'

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
    <div className="grid gap-2">
      <label htmlFor={inputId} className="text-sm font-semibold">
        Add a tag
      </label>
      {tags.length > 0 && (
        <ul className="flex flex-wrap gap-2" aria-label="Tags">
          {tags.map((tag) => (
            <RemovableChip
              key={tag}
              removeLabel={`Remove tag ${tag}`}
              onRemove={() => onChange(tags.filter((existing) => existing !== tag))}
            >
              {tag}
            </RemovableChip>
          ))}
        </ul>
      )}
      <div className="flex gap-2">
        <input
          id={inputId}
          className={`${fieldClass} flex-1 px-3 py-2`}
          value={text}
          onChange={(event) => onTextChange(event.target.value)}
          onKeyDown={onKeyDown}
          autoComplete="off"
        />
        <Button onClick={add}>Add tag</Button>
      </div>
      {error && (
        <p role="alert" className="mb-0 text-sm text-danger">
          {error}
        </p>
      )}
    </div>
  )
}
