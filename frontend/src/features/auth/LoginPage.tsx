import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation } from 'react-router'
import { TextField } from '../../shared/ui/TextField'
import styles from './AuthForm.module.css'
import { toFormErrors, type FormErrors } from './formErrors'
import { safeReturnPath } from './redirect'
import { useDocumentTitle } from '../../shared/useDocumentTitle'
import { useAuth } from './useAuth'

export function LoginPage() {
  const { user, login } = useAuth()
  const location = useLocation()
  useDocumentTitle('Log in')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [errors, setErrors] = useState<FormErrors>({ fields: {} })
  const [submitting, setSubmitting] = useState(false)

  // Logging in (or already being logged in) leaves this page: back to where the user came from.
  if (user) return <Navigate to={safeReturnPath(location.state)} replace />

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!username.trim() || !password) {
      setErrors({ fields: {}, general: 'Enter your username and password.' })
      return
    }
    setSubmitting(true)
    setErrors({ fields: {} })
    try {
      await login(username, password)
    } catch (error) {
      setErrors(toFormErrors(error))
      setSubmitting(false)
    }
  }

  return (
    <div className={styles.page}>
      <form className={styles.card} onSubmit={submit} noValidate>
        <h1>Log in</h1>
        {errors.general && (
          <p role="alert" className={styles.alert}>
            {errors.general}
          </p>
        )}
        <TextField
          label="Username"
          name="username"
          value={username}
          onChange={setUsername}
          autoComplete="username"
          error={errors.fields.username}
        />
        <TextField
          label="Password"
          name="password"
          type="password"
          value={password}
          onChange={setPassword}
          autoComplete="current-password"
          error={errors.fields.password}
        />
        <button type="submit" className={styles.submit} disabled={submitting}>
          {submitting ? 'Logging in…' : 'Log in'}
        </button>
        <p className={styles.switch}>
          <Link to="/register">Create an account</Link>
        </p>
      </form>
    </div>
  )
}
