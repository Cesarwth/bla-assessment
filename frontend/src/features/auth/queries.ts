import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { changePassword, fetchProfile, updateProfile, type ProfileChanges } from './api'
import { useAuth } from '../../shared/session/useAuth'

const profileKey = ['auth', 'profile'] as const

export function useProfile() {
  return useQuery({ queryKey: profileKey, queryFn: ({ signal }) => fetchProfile(signal) })
}

/** On success the returned token becomes the session: the username is one of its claims. */
export function useUpdateProfile() {
  const queryClient = useQueryClient()
  const { adoptSession } = useAuth()
  return useMutation({
    mutationFn: (changes: ProfileChanges) => updateProfile(changes),
    onSuccess: ({ token, user }) => {
      adoptSession(token)
      queryClient.setQueryData(profileKey, user)
    },
  })
}

export function useChangePassword() {
  return useMutation({
    mutationFn: ({ current, next }: { current: string; next: string }) =>
      changePassword(current, next),
  })
}
