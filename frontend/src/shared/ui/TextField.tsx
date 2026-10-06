import { useId } from 'react'
import { fieldClass } from './fieldClass'

interface Props {
  label: string
  name: string
  value: string
  onChange: (value: string) => void
  type?: 'text' | 'email' | 'password' | 'number'
  autoComplete?: string
  /** Shown while there is no error; an error replaces it so the field has one clear description. */
  hint?: string
  error?: string
  min?: number
  maxLength?: number
}

export function TextField({
  label,
  name,
  value,
  onChange,
  type = 'text',
  autoComplete,
  hint,
  error,
  min,
  maxLength,
}: Props) {
  const id = useId()
  const hintId = `${id}-hint`
  const errorId = `${id}-error`
  const describedBy = error ? errorId : hint ? hintId : undefined

  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={id} className="text-sm font-semibold">
        {label}
      </label>
      <input
        id={id}
        name={name}
        className={`${fieldClass} p-3`}
        type={type}
        value={value}
        min={min}
        maxLength={maxLength}
        autoComplete={autoComplete}
        onChange={(event) => onChange(event.target.value)}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
      />
      {error ? (
        <p id={errorId} className="mb-0 text-sm text-danger">
          {error}
        </p>
      ) : (
        hint && (
          <p id={hintId} className="mb-0 text-xs text-muted">
            {hint}
          </p>
        )
      )}
    </div>
  )
}
