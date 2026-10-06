import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation } from 'react-router'
import { Alert } from '../../shared/ui/Alert'
import { Button } from '../../shared/ui/Button'
import { Card } from '../../shared/ui/Card'
import { TextField } from '../../shared/ui/TextField'
import { toFormErrors, type FormErrors } from './formErrors'
import { hasPasswordUpdatedNotice, safeReturnPath } from './redirect'
import { StatusNote } from '../../shared/ui/StatusNote'
import { useFocusFirstInvalid } from '../../shared/useFocusFirstInvalid'
import { useDocumentTitle } from '../../shared/useDocumentTitle'
import { useAuth } from '../../shared/session/useAuth'

export function LoginPage() {
  const { user, login } = useAuth()
  const location = useLocation()
  useDocumentTitle('Log in')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [errors, setErrors] = useState<FormErrors>({ fields: {} })
  const [submitting, setSubmitting] = useState(false)
  const formRef = useFocusFirstInvalid(errors.fields)

  // Logging in (or already being logged in) leaves this page: back to where the user came from.
  if (user) return <Navigate to={safeReturnPath(location.state)} replace />

  async function submit(event: FormEvent) {
    event.preventDefault()
    const missing: Record<string, string> = {}
    if (!username.trim()) missing.username = 'Enter your username.'
    if (!password) missing.password = 'Enter your password.'
    if (Object.keys(missing).length > 0) {
      setErrors({ fields: missing })
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
        <form ref={formRef} className="grid gap-4" onSubmit={submit} noValidate>
          <h1 className="mb-0">Log in</h1>
          <StatusNote>
            {hasPasswordUpdatedNotice(location.state) && 'Password updated, you can log in.'}
          </StatusNote>
          <Alert>{errors.general}</Alert>
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
            <Link to="/forgot-password">Forgot your password?</Link>
          </p>
          <p className="mb-0 text-center text-sm">
            New trainer? <Link to="/register">Create your profile</Link>
          </p>
        </form>
      </Card>
    </div>
  )
}
