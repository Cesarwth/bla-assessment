import { useEffect, useRef } from 'react'
import { Link, Outlet, useLocation } from 'react-router'
import { useAuth } from '../features/auth/useAuth'
import { IconButton } from '../shared/ui/IconButton'
import { ErrorBoundary } from './ErrorBoundary'
import { Header } from './Header'

export function Layout() {
  const { sessionExpired, dismissSessionExpired } = useAuth()
  const { pathname } = useLocation()
  const main = useRef<HTMLElement>(null)
  const firstRender = useRef(true)

  // In a single-page app nothing tells assistive technology that the page changed: move the focus to the content
  // when the route does (but not on first load, and not when only the query string changes, e.g. paging).
  useEffect(() => {
    if (firstRender.current) {
      firstRender.current = false
      return
    }
    main.current?.focus()
  }, [pathname])

  return (
    <div className="flex min-h-dvh flex-col">
      <a
        className="absolute -top-16 left-3 z-10 rounded-sm bg-surface px-4 py-2 shadow-md focus:top-3"
        href="#main"
      >
        Skip to content
      </a>
      <Header />
      {sessionExpired && (
        <div
          role="alert"
          className="flex items-center justify-center gap-3 border-b border-border bg-warning-surface px-4 py-3"
        >
          <p className="mb-0">
            Your session expired. <Link to="/login">Log in again</Link> to continue.
          </p>
          <IconButton aria-label="Dismiss" onClick={dismissSessionExpired} />
        </div>
      )}
      <main
        id="main"
        ref={main}
        className="mx-auto w-full max-w-content flex-1 px-4 pt-6 pb-12 focus:outline-none"
        tabIndex={-1}
      >
        {/* keyed by route so a broken page does not keep the fallback after the user navigates away */}
        <ErrorBoundary key={pathname}>
          <Outlet />
        </ErrorBoundary>
      </main>
      <footer className="px-4 py-6 text-center text-sm text-muted">
        Data from <a href="https://pokeapi.co">PokéAPI</a>. Pokémon is a trademark of Nintendo; this
        is a learning project.
      </footer>
    </div>
  )
}
