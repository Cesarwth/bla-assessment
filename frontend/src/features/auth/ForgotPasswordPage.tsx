import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { Alert } from '../../shared/ui/Alert'
import { Button } from '../../shared/ui/Button'
import { Card } from '../../shared/ui/Card'
import { TextField } from '../../shared/ui/TextField'
import { StatusNote } from '../../shared/ui/StatusNote'
import { useFocusFirstInvalid } from '../../shared/useFocusFirstInvalid'
import { useDocumentTitle } from '../../shared/useDocumentTitle'
import { requestPasswordReset } from './api'
import { toFormErrors, type FormErrors } from './formErrors'

export function ForgotPasswordPage() {
  useDocumentTitle('Reset your password')
  const [email, setEmail] = useState('')
  const [errors, setErrors] = useState<FormErrors>({ fields: {} })
  const [submitting, setSubmitting] = useState(false)
  const [sent, setSent] = useState(false)
  const formRef = useFocusFirstInvalid(errors.fields)

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!email.trim()) {
      setErrors({ fields: { email: 'Enter your e-mail address.' } })
      return
    }
    setSubmitting(true)
    setErrors({ fields: {} })
    try {
      await requestPasswordReset(email.trim())
      setSent(true)
    } catch (error) {
      setErrors(toFormErrors(error))
      setSubmitting(false)
    }
  }

  return (
    <div className="mx-auto my-4 max-w-[26rem] sm:my-8">
      <Card large raised className="p-6">
        <div className="grid gap-4">
          <h1 className="mb-0">{sent ? 'Check your inbox' : 'Reset your password'}</h1>
          {/* The same words whether or not the address belongs to an account: the page must not reveal it. This
              region stays in the page while the form is replaced, so the confirmation is announced. */}
          <StatusNote boxClass="">
            {sent &&
              'If an account exists for that e-mail, we sent a link to reset the password. Check your inbox (the link expires in 30 minutes).'}
          </StatusNote>
          {sent ? (
            <p className="mb-0 text-center text-sm">
              <Link to="/login">Back to log in</Link>
            </p>
          ) : (
            <form ref={formRef} className="grid gap-4" onSubmit={submit} noValidate>
              <p className="mb-0 text-muted">
                Enter the e-mail of your profile and we will send you a link to choose a new
                password.
              </p>
              <Alert>{errors.general}</Alert>
              <TextField
                label="Email"
                name="email"
                type="email"
                value={email}
                onChange={setEmail}
                autoComplete="email"
                error={errors.fields.email}
              />
              <Button type="submit" variant="primary" size="lg" disabled={submitting}>
                {submitting ? 'Sending…' : 'Send reset link'}
              </Button>
              <p className="mb-0 text-center text-sm">
                <Link to="/login">Back to log in</Link>
              </p>
            </form>
          )}
        </div>
      </Card>
    </div>
  )
}
