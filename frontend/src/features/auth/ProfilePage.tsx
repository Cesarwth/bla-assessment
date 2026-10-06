import { BackLink } from '../../shared/ui/BackLink'
import { Card } from '../../shared/ui/Card'
import { ErrorState } from '../../shared/ui/ErrorState'
import { LoadingStatus } from '../../shared/ui/LoadingStatus'
import { useDocumentTitle } from '../../shared/useDocumentTitle'
import { ChangePasswordCard } from './components/ChangePasswordCard'
import { DetailsCard } from './components/DetailsCard'
import { useProfile } from './queries'

export function ProfilePage() {
  useDocumentTitle('Your profile')
  const profile = useProfile()

  return (
    <section className="mx-auto max-w-[34rem]">
      <BackLink fallbackTo="/" fallbackLabel="the catalog" />
      <Card glass className="mb-4 px-4 py-3 sm:px-6">
        <h1 className="mb-0">Your profile</h1>
      </Card>
      {profile.isPending && <LoadingStatus label="Loading your profile…" />}
      {profile.isError && (
        <ErrorState error={profile.error} onRetry={() => void profile.refetch()} />
      )}
      {profile.data && (
        <div className="grid gap-4">
          <DetailsCard profile={profile.data} />
          <ChangePasswordCard />
        </div>
      )}
    </section>
  )
}
