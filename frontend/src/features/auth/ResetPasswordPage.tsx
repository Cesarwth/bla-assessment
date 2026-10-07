import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { isApiError } from '../../shared/api/errors'
import { Alert } from '../../shared/ui/Alert'
import { Button } from '../../shared/ui/Button'
import { Card } from '../../shared/ui/Card'
import { TextField } from '../../shared/ui/TextField'
import { useFocusFirstInvalid } from '../../shared/useFocusFirstInvalid'
import { useDocumentTitle } from '../../shared/useDocumentTitle'
import { resetPassword } from './api'
import { toFormErrors, type FormErrors } from './formErrors'
import { PASSWORD_HINT, checkNewPassword } from './passwordPolicy'
import { PASSWORD_UPDATED_STATE } from './redirect'

function InvalidLink({ reason }: { reason?: string }) {
  return (
    <div className="mx-auto my-4 max-w-[26rem] sm:my-8">
      <Card large raised className="grid gap-4 p-6">
        <h1 className="mb-0">This reset link is invalid</h1>
        <p className="mb-0">{reason ?? 'This reset link is invalid or has expired.'}</p>
        <Link to="/forgot-password">Request a new link</Link>
      </Card>
    </div>
  )
}

export function ResetPasswordPage() {
  useDocumentTitle('Choose a new password')
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  // Read once into state: the address is cleaned right after, and the token must not stay in the history.
  const [token] = useState(() => searchParams.get('token'))
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [errors, setErrors] = useState<FormErrors>({ fields: {} })
  const [submitting, setSubmitting] = useState(false)
  const [rejected, setRejected] = useState<string>()
  const formRef = useFocusFirstInvalid(errors.fields)

  useEffect(() => {
    if (token) void navigate({ search: '' }, { replace: true })
  }, [token, navigate])

  if (!token) return <InvalidLink />
  if (rejected) return <InvalidLink reason={rejected} />

  async function submit(event: FormEvent) {
    event.preventDefault()
    const fields: Record<string, string> = {}
    const weak = checkNewPassword(password)
    if (weak) fields.newPassword = weak
    if (confirm !== password) fields.confirm = 'The passwords do not match.'
    if (Object.keys(fields).length > 0) {
      setErrors({ fields })
      return
    }
    setSubmitting(true)
    setErrors({ fields: {} })
    try {
      await resetPassword(token as string, password)
      void navigate('/login', { replace: true, state: PASSWORD_UPDATED_STATE })
    } catch (error) {
      if (isApiError(error) && error.code === 'INVALID_RESET_TOKEN') {
        setRejected(error.message)
        return
      }
      setErrors(toFormErrors(error, { WEAK_PASSWORD: 'newPassword' }))
      setSubmitting(false)
    }
  }

  return (
    <div className="mx-auto my-4 max-w-[26rem] sm:my-8">
      <Card large raised className="p-6">
        <form ref={formRef} className="grid gap-4" onSubmit={submit} noValidate>
          <h1 className="mb-0">Choose a new password</h1>
          <Alert>{errors.general}</Alert>
          <TextField
            label="New password"
            name="newPassword"
            type="password"
            value={password}
            onChange={setPassword}
            autoComplete="new-password"
            hint={PASSWORD_HINT}
            error={errors.fields.newPassword}
          />
          <TextField
            label="Confirm new password"
            name="confirm"
            type="password"
            value={confirm}
            onChange={setConfirm}
            autoComplete="new-password"
            error={errors.fields.confirm}
          />
          <Button type="submit" variant="primary" size="lg" disabled={submitting}>
            {submitting ? 'Updating…' : 'Update password'}
          </Button>
        </form>
      </Card>
    </div>
  )
}
