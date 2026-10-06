import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation } from 'react-router'
import { api } from '../../shared/api/client'
import type { RegisteredUser } from '../../shared/api/types'
import { TextField } from '../../shared/ui/TextField'
import styles from './AuthForm.module.css'
import { toFormErrors, type FormErrors } from './formErrors'
import { safeReturnPath } from './redirect'
import { useDocumentTitle } from '../../shared/useDocumentTitle'
import { useAuth } from './useAuth'

export function RegisterPage() {
  const { user, login } = useAuth()
  const location = useLocation()
  useDocumentTitle('Create profile')
  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [errors, setErrors] = useState<FormErrors>({ fields: {} })
  const [submitting, setSubmitting] = useState(false)

  if (user) return <Navigate to={safeReturnPath(location.state)} replace />

  async function submit(event: FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setErrors({ fields: {} })
    try {
      await api.post<RegisteredUser>('/auth/register', { username, email, password })
      // The account exists now: sign in with the same credentials so the user does not have to type them again.
      await login(username, password)
    } catch (error) {
      setErrors(toFormErrors(error))
      setSubmitting(false)
    }
  }

  return (
    <div className={styles.page}>
      <form className={styles.card} onSubmit={submit} noValidate>
        <h1>Create your trainer profile</h1>
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
          label="Email"
          name="email"
          type="email"
          value={email}
          onChange={setEmail}
          autoComplete="email"
          error={errors.fields.email}
        />
        <TextField
          label="Password"
          name="password"
          type="password"
          value={password}
          onChange={setPassword}
          autoComplete="new-password"
          hint="Use at least 8 characters, with letters and digits."
          error={errors.fields.password}
        />
        <button type="submit" className={styles.submit} disabled={submitting}>
          {submitting ? 'Creating profile…' : 'Create profile'}
        </button>
        <p className={styles.switch}>
          <Link to="/login">I already have a profile</Link>
        </p>
      </form>
    </div>
  )
}
