import { useEffect, useRef } from 'react'

/**
 * After a failed submit, moves focus to the first field marked `aria-invalid` (WCAG 3.3.1: people using a keyboard or
 * a screen reader land on the problem instead of hunting for it). Pass the object holding the field errors: every
 * failed submit stores a new one, so repeating the same mistake focuses again. Attach the returned ref to the form.
 */
export function useFocusFirstInvalid(fieldErrors: Record<string, string>) {
  const form = useRef<HTMLFormElement>(null)
  useEffect(() => {
    if (Object.keys(fieldErrors).length === 0) return
    form.current?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus()
  }, [fieldErrors])
  return form
}
