import { useEffect, useRef } from 'react'
import { Link, Outlet, useLocation } from 'react-router'
import { useAuth } from '../features/auth/useAuth'
import { ErrorBoundary } from './ErrorBoundary'
import { Header } from './Header'
import styles from './Layout.module.css'

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
    <div className={styles.shell}>
      <a className={styles.skipLink} href="#main">
        Skip to content
      </a>
      <Header />
      {sessionExpired && (
        <div role="alert" className={styles.notice}>
          <p>
            Your session expired. <Link to="/login">Log in again</Link> to continue.
          </p>
          <button type="button" className={styles.dismiss} aria-label="Dismiss" onClick={dismissSessionExpired}>
            <span aria-hidden="true">×</span>
          </button>
        </div>
      )}
      <main id="main" ref={main} className={styles.main} tabIndex={-1}>
        {/* keyed by route so a broken page does not keep the fallback after the user navigates away */}
        <ErrorBoundary key={pathname}>
          <Outlet />
        </ErrorBoundary>
      </main>
      <footer className={styles.footer}>
        Data from <a href="https://pokeapi.co">PokéAPI</a>. Pokémon is a trademark of Nintendo; this is a learning project.
      </footer>
    </div>
  )
}
