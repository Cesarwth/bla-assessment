import { api } from '../../shared/api/client'
import type { RegisteredUser, UpdatedProfile } from '../../shared/api/types'

/** Always answers 202 for a well-formed address, so the caller cannot learn whether the account exists. */
export function requestPasswordReset(email: string) {
  return api.post<null>('/auth/forgot-password', { email })
}

export function resetPassword(token: string, newPassword: string) {
  return api.post<null>('/auth/reset-password', { token, newPassword })
}

export function fetchProfile(signal?: AbortSignal) {
  return api.get<RegisteredUser>('/users/me', signal)
}

/** Only the fields that changed; `currentPassword` is required by the server when the e-mail changes. */
export interface ProfileChanges {
  username?: string
  email?: string
  currentPassword?: string
}

export function updateProfile(changes: ProfileChanges) {
  return api.patch<UpdatedProfile>('/users/me', changes)
}

export function changePassword(currentPassword: string, newPassword: string) {
  return api.post<null>('/users/me/password', { currentPassword, newPassword })
}
