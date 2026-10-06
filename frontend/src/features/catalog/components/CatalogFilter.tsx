import { useEffect, useId, useRef, useState } from 'react'
import { MAX_FILTER_LENGTH } from '../../../shared/usePaging'
import { fieldClass } from '../../../shared/ui/fieldClass'
import { IconButton } from '../../../shared/ui/IconButton'

const DEBOUNCE_MS = 300

interface Props {
  /** The filter currently in the URL. */
  value: string
  onChange: (filter: string) => void
}

/**
 * The text is kept here while typing and only reaches the URL (and so the API) after a pause, so a fast typist
 * makes one request, not one per key. A change of the URL from outside (back button, "show all") flows back in.
 */
export function CatalogFilter({ value, onChange }: Props) {
  const inputId = useId()
  const inputRef = useRef<HTMLInputElement>(null)
  const [text, setText] = useState(value)
  const lastSent = useRef(value)
  // Held in a ref so a parent re-render does not restart the pause.
  const onChangeRef = useRef(onChange)
  useEffect(() => {
    onChangeRef.current = onChange
  })

  useEffect(() => {
    if (value !== lastSent.current) {
      lastSent.current = value
      setText(value)
    }
  }, [value])

  useEffect(() => {
    const wanted = text.trim()
    if (wanted === lastSent.current) return
    const timer = setTimeout(() => {
      lastSent.current = wanted
      onChangeRef.current(wanted)
    }, DEBOUNCE_MS)
    return () => clearTimeout(timer)
  }, [text])

  function clear() {
    lastSent.current = ''
    setText('')
    onChange('')
    inputRef.current?.focus()
  }

  return (
    <div role="search" className="my-4 flex max-w-104 flex-col gap-1">
      <label htmlFor={inputId} className="text-sm font-semibold">
        Filter by name or number
      </label>
      <div className="relative flex">
        <input
          ref={inputRef}
          id={inputId}
          type="text"
          className={`${fieldClass} flex-1 py-2 pr-12 pl-3`}
          value={text}
          maxLength={MAX_FILTER_LENGTH}
          onChange={(event) => setText(event.target.value)}
          autoComplete="off"
          placeholder="pikachu or 25"
        />
        {text && (
          <IconButton
            aria-label="Clear filter"
            className="absolute top-1/2 right-0 -translate-y-1/2 sm:right-1"
            onClick={clear}
          />
        )}
      </div>
    </div>
  )
}
