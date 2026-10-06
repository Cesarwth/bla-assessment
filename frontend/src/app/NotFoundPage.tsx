import { Link } from 'react-router'
import { useDocumentTitle } from '../shared/useDocumentTitle'

export function NotFoundPage() {
  useDocumentTitle('Page not found')
  return (
    <section>
      <h1>Page not found</h1>
      <p>The page you are looking for does not exist or has moved.</p>
      <Link to="/">Back to the catalog</Link>
    </section>
  )
}
