import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation } from 'react-router'
import { Alert } from '../../shared/ui/Alert'
import { Button } from '../../shared/ui/Button'
import { Card } from '../../shared/ui/Card'
import { TextField } from '../../shared/ui/TextField'
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
    <div className="mx-auto my-4 max-w-[26rem] sm:my-8">
      <Card large raised className="p-6">
        <form className="grid gap-4" onSubmit={submit} noValidate>
          <h1 className="mb-0">Log in</h1>
          {errors.general && <Alert>{errors.general}</Alert>}
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
          <Button type="submit" variant="primary" size="lg" disabled={submitting}>
            {submitting ? 'Logging in…' : 'Log in'}
          </Button>
          <p className="mb-0 text-center text-sm">
            New trainer? <Link to="/register">Create your profile</Link>
          </p>
        </form>
      </Card>
    </div>
  )
}
