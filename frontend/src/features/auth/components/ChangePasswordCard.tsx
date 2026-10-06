import { useState, type FormEvent } from 'react'
import { Alert } from '../../../shared/ui/Alert'
import { Button } from '../../../shared/ui/Button'
import { Card } from '../../../shared/ui/Card'
import { StatusNote } from '../../../shared/ui/StatusNote'
import { TextField } from '../../../shared/ui/TextField'
import { useFocusFirstInvalid } from '../../../shared/useFocusFirstInvalid'
import { toFormErrors, type FormErrors } from '../formErrors'
import { PASSWORD_HINT, checkNewPassword } from '../passwordPolicy'
import { useChangePassword } from '../queries'

export function ChangePasswordCard() {
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [confirm, setConfirm] = useState('')
  const [errors, setErrors] = useState<FormErrors>({ fields: {} })
  const [saved, setSaved] = useState(false)
  const change = useChangePassword()
  const formRef = useFocusFirstInvalid(errors.fields)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setSaved(false)
    const fields: Record<string, string> = {}
    if (!current) fields.currentPassword = 'Enter your current password.'
    const weak = checkNewPassword(next)
    if (weak) fields.newPassword = weak
    if (confirm !== next) fields.confirm = 'The passwords do not match.'
    if (Object.keys(fields).length > 0) {
      setErrors({ fields })
      return
    }
    setErrors({ fields: {} })
    try {
      await change.mutateAsync({ current, next })
      setCurrent('')
      setNext('')
      setConfirm('')
      setSaved(true)
    } catch (error) {
      setErrors(toFormErrors(error, { WEAK_PASSWORD: 'newPassword' }))
    }
  }

  return (
    <Card as="section" large raised className="p-6" aria-labelledby="password-title">
      <form ref={formRef} className="grid gap-4" onSubmit={submit} noValidate>
        <h2 id="password-title" className="mb-0">
          Change password
        </h2>
        <Alert>{errors.general}</Alert>
        <TextField
          label="Current password"
          name="currentPassword"
          type="password"
          value={current}
          onChange={setCurrent}
          autoComplete="current-password"
          error={errors.fields.currentPassword}
        />
        <TextField
          label="New password"
          name="newPassword"
          type="password"
          value={next}
          onChange={setNext}
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
        <StatusNote>{saved && 'Password changed.'}</StatusNote>
        <Button type="submit" variant="primary" disabled={change.isPending}>
          {change.isPending ? 'Changing…' : 'Change password'}
        </Button>
      </form>
    </Card>
  )
}
