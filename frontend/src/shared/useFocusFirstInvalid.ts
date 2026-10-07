import { useEffect, useRef } from 'react'

/**
 * Focuses the first `aria-invalid` field after a failed submit (WCAG 3.3.1). Pass the field-errors object: every
 * failed submit stores a new one, so repeating the same mistake focuses again.
 */
export function useFocusFirstInvalid(fieldErrors: Record<string, string>) {
  const form = useRef<HTMLFormElement>(null)
  useEffect(() => {
    if (Object.keys(fieldErrors).length === 0) return
    form.current?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus()
  }, [fieldErrors])
  return form
}
