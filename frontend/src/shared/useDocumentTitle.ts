import { useEffect } from 'react'

const SITE = 'Pokédex'

/** Sets the browser tab title ("Pikachu · Pokédex"); without a page title it is just the site name. */
export function useDocumentTitle(title?: string): void {
  useEffect(() => {
    document.title = title ? `${title} · ${SITE}` : SITE
  }, [title])
}
