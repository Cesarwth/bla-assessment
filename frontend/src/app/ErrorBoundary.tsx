import { Component, type ReactNode } from 'react'
import styles from './ErrorBoundary.module.css'

interface State {
  failed: boolean
}

/**
 * Last line of defense: a bug while rendering a page shows this message instead of a blank screen. It must be a
 * class because React offers no hook for it. The error itself is reported by React (and never shown to users).
 */
export class ErrorBoundary extends Component<{ children: ReactNode }, State> {
  state: State = { failed: false }

  static getDerivedStateFromError(): State {
    return { failed: true }
  }

  render() {
    if (!this.state.failed) return this.props.children
    return (
      <div role="alert" className={styles.fallback}>
        <h2>Something went wrong</h2>
        <p>An unexpected error stopped this page from loading. Your data is safe.</p>
        <div className={styles.actions}>
          <button
            type="button"
            className={styles.retry}
            onClick={() => this.setState({ failed: false })}
          >
            Try again
          </button>
          <a href="/">Go to the catalog</a>
        </div>
      </div>
    )
  }
}
