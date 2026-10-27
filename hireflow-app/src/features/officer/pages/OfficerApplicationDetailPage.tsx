import * as React from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import {
  UserCheck,
  ArrowLeft,
  Award,
  Mail,
  Calendar,
  CheckCircle2,
} from 'lucide-react'
import { PageHeading } from '@/components/domain/page-heading'
import { ApplicationStatusBadge } from '@/components/domain/application-status-badge'
import { Button } from '@/components/ui/button'
import { formatLocalDateTime } from '@/lib/dateUtils'
import { useOfficerApplication, useShortlistApplicationMutation } from '../api/officerApi'

export function OfficerApplicationDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()

  const [successMsg, setSuccessMsg] = React.useState<string | null>(null)
  const [errorMsg, setErrorMsg] = React.useState<string | null>(null)

  const { data: application, isLoading, refetch } = useOfficerApplication(id || '')
  const shortlistMutation = useShortlistApplicationMutation()

  if (isLoading) {
    return (
      <div className="p-8 text-center text-xs text-muted-foreground animate-pulse">
        Loading candidate application dossier...
      </div>
    )
  }

  if (!application) {
    return (
      <div className="p-8 rounded-md border border-border bg-card text-center space-y-3">
        <p className="text-sm font-semibold text-foreground">Application Not Found</p>
        <p className="text-xs text-muted-foreground">
          Application '{id}' does not exist or has been removed.
        </p>
        <Button size="sm" variant="outline" onClick={() => navigate('/officer/drives')}>
          Back to Drives
        </Button>
      </div>
    )
  }

  const isHighCgpa = application.cgpa >= 8.5
  const canShortlist = application.status === 'APPLIED' || application.status === 'ELIGIBLE'

  const handleShortlist = async () => {
    setSuccessMsg(null)
    setErrorMsg(null)
    try {
      await shortlistMutation.mutateAsync(application.id)
      setSuccessMsg('Candidate application transitioned to SHORTLISTED.')
      refetch()
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to shortlist candidate.'
      setErrorMsg(msg)
    }
  }

  return (
    <div className="space-y-6">
      <PageHeading
        title={application.studentName}
        description={`Application ID: ${application.id} • Candidate Roll Number: ${application.studentId}`}
        breadcrumbs={[
          { label: 'Officer', href: '/officer/dashboard' },
          { label: 'Recruitment Drives', href: '/officer/drives' },
          { label: application.driveId, href: `/officer/drives/${application.driveId}/applications` },
          { label: application.id },
        ]}
        badge={<ApplicationStatusBadge status={application.status} />}
        actions={
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={() => navigate(`/officer/drives/${application.driveId}/applications`)}
              className="text-xs gap-1.5"
            >
              <ArrowLeft className="h-3.5 w-3.5" />
              <span>Back to Roster</span>
            </Button>
            {canShortlist && (
              <Button
                size="sm"
                onClick={handleShortlist}
                disabled={shortlistMutation.isPending}
                className="text-xs gap-1.5 bg-accent hover:bg-accent/90"
              >
                <UserCheck className="h-3.5 w-3.5" />
                <span>{shortlistMutation.isPending ? 'Shortlisting...' : 'Shortlist Candidate'}</span>
              </Button>
            )}
          </div>
        }
      />

      {successMsg && (
        <div
          className="p-3 text-xs rounded-sm bg-accent/10 text-accent border border-accent/20 flex items-center gap-2 font-medium"
          role="status"
        >
          <CheckCircle2 className="h-4 w-4 shrink-0" />
          <span>{successMsg}</span>
        </div>
      )}

      {errorMsg && (
        <div
          className="p-3 text-xs rounded-sm bg-destructive/10 text-destructive border border-destructive/20 font-medium"
          role="alert"
        >
          {errorMsg}
        </div>
      )}

      {/* Main Candidate Dossier Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {/* Academic Record */}
        <div className="rounded-md border border-border bg-card p-5 space-y-4 shadow-xs">
          <div className="flex items-center justify-between border-b border-border pb-3">
            <h2 className="font-serif text-base font-semibold text-foreground">
              Academic Credentials
            </h2>
            {isHighCgpa && (
              <span className="inline-flex items-center gap-1 text-brass-700 dark:text-brass-400 font-medium text-xs">
                <Award className="h-3.5 w-3.5" />
                Honors Student
              </span>
            )}
          </div>

          <div className="grid grid-cols-2 gap-4 text-xs">
            <div>
              <span className="text-muted-foreground block uppercase font-mono">Roll Number</span>
              <span className="font-mono text-sm font-semibold text-foreground block mt-0.5">
                {application.studentId}
              </span>
            </div>
            <div>
              <span className="text-muted-foreground block uppercase font-mono">Verified CGPA</span>
              <span className={`font-mono text-xl font-bold block mt-0.5 ${isHighCgpa ? 'text-brass-700 dark:text-brass-400' : 'text-accent'}`}>
                {application.cgpa.toFixed(2)}
              </span>
            </div>
          </div>

          <div className="space-y-2 text-xs pt-2 border-t border-border">
            <div>
              <span className="text-muted-foreground block uppercase font-mono">Degree & Department</span>
              <span className="font-medium text-foreground">{application.branch}</span>
            </div>
            <div className="flex items-center gap-1.5 text-muted-foreground">
              <Mail className="h-3.5 w-3.5" />
              <span>{application.studentEmail}</span>
            </div>
          </div>
        </div>

        {/* Campaign & Application Information */}
        <div className="rounded-md border border-border bg-card p-5 space-y-4 shadow-xs">
          <div className="border-b border-border pb-3">
            <h2 className="font-serif text-base font-semibold text-foreground">
              Application Details
            </h2>
          </div>

          <div className="space-y-3 text-xs">
            <div>
              <span className="text-muted-foreground block uppercase font-mono">Recruitment Campaign</span>
              <span className="font-medium text-foreground text-sm block mt-0.5">
                {application.jobTitle}
              </span>
              <span className="text-muted-foreground">{application.companyName}</span>
            </div>

            <div className="flex items-center gap-1.5 text-muted-foreground">
              <Calendar className="h-3.5 w-3.5" />
              <span>Submission Timestamp: {formatLocalDateTime(application.appliedAt)}</span>
            </div>

            {application.remarks && (
              <div className="pt-2 border-t border-border">
                <span className="text-muted-foreground block uppercase font-mono mb-1">
                  Candidate Remarks
                </span>
                <p className="p-2.5 rounded-sm bg-muted/30 text-foreground border border-border/80">
                  {application.remarks}
                </p>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
