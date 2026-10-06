import { useState, type FormEvent } from 'react'
import type { RegisteredUser } from '../../../shared/api/types'
import { Alert } from '../../../shared/ui/Alert'
import { Button } from '../../../shared/ui/Button'
import { Card } from '../../../shared/ui/Card'
import { StatusNote } from '../../../shared/ui/StatusNote'
import { TextField } from '../../../shared/ui/TextField'
import { useFocusFirstInvalid } from '../../../shared/useFocusFirstInvalid'
import type { ProfileChanges } from '../api'
import { toFormErrors, type FormErrors } from '../formErrors'
import { useUpdateProfile } from '../queries'

export function DetailsCard({ profile }: { profile: RegisteredUser }) {
  const [username, setUsername] = useState(profile.username)
  const [email, setEmail] = useState(profile.email)
  const [currentPassword, setCurrentPassword] = useState('')
  const [errors, setErrors] = useState<FormErrors>({ fields: {} })
  const [saved, setSaved] = useState(false)
  const update = useUpdateProfile()
  const formRef = useFocusFirstInvalid(errors.fields)

  // The server asks for the password only when the e-mail changes, so the field appears only then.
  const emailChanged = email.trim() !== profile.email

  async function submit(event: FormEvent) {
    event.preventDefault()
    setSaved(false)
    const changes: ProfileChanges = {}
    if (username.trim() !== profile.username) changes.username = username.trim()
    if (emailChanged) {
      if (!currentPassword) {
        setErrors({
          fields: { currentPassword: 'Enter your current password to change your e-mail.' },
        })
        return
      }
      changes.email = email.trim()
      changes.currentPassword = currentPassword
    }
    if (Object.keys(changes).length === 0) {
      setErrors({ fields: {}, general: 'Change your username or e-mail first.' })
      return
    }
    setErrors({ fields: {} })
    try {
      const result = await update.mutateAsync(changes)
      setUsername(result.user.username)
      setEmail(result.user.email)
      setCurrentPassword('')
      setSaved(true)
    } catch (error) {
      setErrors(toFormErrors(error))
    }
  }

  return (
    <Card as="section" large raised className="p-6" aria-labelledby="details-title">
      <form ref={formRef} className="grid gap-4" onSubmit={submit} noValidate>
        <h2 id="details-title" className="mb-0">
          Your details
        </h2>
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
        {emailChanged && (
          <TextField
            label="Current password"
            name="currentPassword"
            type="password"
            value={currentPassword}
            onChange={setCurrentPassword}
            autoComplete="current-password"
            hint="Needed to change your e-mail."
            error={errors.fields.currentPassword}
          />
        )}
        <StatusNote>{saved && 'Your details were updated.'}</StatusNote>
        <Button type="submit" variant="primary" disabled={update.isPending}>
          {update.isPending ? 'Saving…' : 'Save details'}
        </Button>
      </form>
    </Card>
  )
}
