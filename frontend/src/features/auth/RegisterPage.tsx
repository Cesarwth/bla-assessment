import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation } from 'react-router'
import { api } from '../../shared/api/client'
import type { RegisteredUser } from '../../shared/api/types'
import { Alert } from '../../shared/ui/Alert'
import { Button } from '../../shared/ui/Button'
import { Card } from '../../shared/ui/Card'
import { TextField } from '../../shared/ui/TextField'
import { toFormErrors, type FormErrors } from './formErrors'
import { PASSWORD_HINT } from './passwordPolicy'
import { safeReturnPath } from './redirect'
import { useFocusFirstInvalid } from '../../shared/useFocusFirstInvalid'
import { useDocumentTitle } from '../../shared/useDocumentTitle'
import { useAuth } from '../../shared/session/useAuth'

export function RegisterPage() {
  const { user, login } = useAuth()
  const location = useLocation()
  useDocumentTitle('Create profile')
  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [errors, setErrors] = useState<FormErrors>({ fields: {} })
  const [submitting, setSubmitting] = useState(false)
  const formRef = useFocusFirstInvalid(errors.fields)

  if (user) return <Navigate to={safeReturnPath(location.state)} replace />

  async function submit(event: FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setErrors({ fields: {} })
    try {
      await api.post<RegisteredUser>('/auth/register', { username, email, password })
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
          <h1 className="mb-0">Create your trainer profile</h1>
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
            hint={PASSWORD_HINT}
            error={errors.fields.password}
          />
          <Button type="submit" variant="primary" size="lg" disabled={submitting}>
            {submitting ? 'Creating profile…' : 'Create profile'}
          </Button>
          <p className="mb-0 text-center text-sm">
            <Link to="/login">I already have a profile</Link>
          </p>
        </form>
      </Card>
    </div>
  )
}
