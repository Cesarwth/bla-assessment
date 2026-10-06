import type { MouseEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'

interface Props {
  /** Where to go when the user did not come from inside the app (deep link, reload, new tab). */
  fallbackTo: string
  /** Names that fallback for assistive technology: "the catalog" makes the link read "Back to the catalog". */
  fallbackLabel: string
}

/**
 * Takes the user back to the list they came from, with its page, filter and selection untouched. Inside the app
 * that is the previous history entry; on a deep link there is none, so it is a normal link to `fallbackTo`.
 *
 * It is always an anchor with a real href (open in a new tab, copy the address...). The visible text is "Back";
 * only when the destination is certain (the fallback) is it spelled out, in text hidden from sight, so the
 * accessible name always starts with the visible text and never promises a place the history may not lead to.
 */
export function BackLink({ fallbackTo, fallbackLabel }: Props) {
  const navigate = useNavigate()
  // react-router gives the first entry of a session the key "default": nothing came before it in this app.
  const hasHistory = useLocation().key !== 'default'

  function goBack(event: MouseEvent<HTMLAnchorElement>) {
    const modified = event.metaKey || event.ctrlKey || event.shiftKey || event.altKey
    if (!hasHistory || modified || event.button !== 0) return
    event.preventDefault()
    void navigate(-1)
  }

  return (
    <Link
      to={fallbackTo}
      onClick={goBack}
      className="mb-4 inline-flex min-h-11 items-center gap-2 rounded-full border border-border bg-surface/80 px-4 py-2 font-medium text-accent no-underline backdrop-blur transition-colors hover:bg-surface-muted sm:min-h-0 sm:py-1"
    >
      <svg
        aria-hidden="true"
        viewBox="0 0 24 24"
        className="size-5 shrink-0"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
      >
        <path d="M19 12H5M12 19l-7-7 7-7" />
      </svg>
      <span>
        Back
        {!hasHistory && <span className="sr-only"> to {fallbackLabel}</span>}
      </span>
    </Link>
  )
}
