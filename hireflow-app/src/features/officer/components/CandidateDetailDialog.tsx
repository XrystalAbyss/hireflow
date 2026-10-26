import * as React from 'react'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { ApplicationStatusBadge } from '@/components/domain/application-status-badge'
import { formatLocalDateTime } from '@/lib/dateUtils'
import type { OfficerApplicationReviewDto } from '@/types/api'
import { useShortlistApplicationMutation } from '../api/officerApi'
import { Award, UserCheck, Mail, Building, Briefcase, Calendar } from 'lucide-react'

export interface CandidateDetailDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  application: OfficerApplicationReviewDto | null
  onSuccess?: () => void
}

export function CandidateDetailDialog({
  open,
  onOpenChange,
  application,
  onSuccess,
}: CandidateDetailDialogProps) {
  const [errorMsg, setErrorMsg] = React.useState<string | null>(null)
  const [isConfirming, setIsConfirming] = React.useState(false)
  const shortlistMutation = useShortlistApplicationMutation()

  React.useEffect(() => {
    setErrorMsg(null)
    setIsConfirming(false)
  }, [open, application])

  if (!application) return null

  const isHighCgpa = application.cgpa >= 8.5
  const canShortlist = application.status === 'APPLIED' || application.status === 'ELIGIBLE'

  const handleShortlist = async () => {
    setErrorMsg(null)
    try {
      await shortlistMutation.mutateAsync(application.id)
      setIsConfirming(false)
      onSuccess?.()
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to shortlist candidate.'
      setErrorMsg(msg)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <div className="flex items-center justify-between gap-3">
            <span className="font-mono text-xs text-muted-foreground uppercase tracking-wider">
              Application Dossier
            </span>
            <ApplicationStatusBadge status={application.status} />
          </div>
          <DialogTitle className="text-xl">{application.studentName}</DialogTitle>
          <DialogDescription className="font-mono text-xs">
            Student ID: {application.studentId} • Application: {application.id}
          </DialogDescription>
        </DialogHeader>

        {errorMsg && (
          <div
            className="p-3 text-xs rounded-sm bg-destructive/10 text-destructive border border-destructive/20 font-medium"
            role="alert"
          >
            {errorMsg}
          </div>
        )}

        <div className="space-y-4 text-xs">
          {/* Academic Snapshot Card */}
          <div className="rounded-md border border-border bg-muted/30 p-3.5 space-y-2.5">
            <div className="flex items-center justify-between">
              <span className="font-semibold text-foreground uppercase tracking-wider">
                Academic Profile
              </span>
              {isHighCgpa && (
                <span className="inline-flex items-center gap-1 text-brass-700 dark:text-brass-400 font-medium">
                  <Award className="h-3.5 w-3.5" />
                  Honors Candidate
                </span>
              )}
            </div>

            <div className="grid grid-cols-2 gap-3 pt-1">
              <div>
                <span className="text-muted-foreground block">Verified CGPA</span>
                <span className={`font-mono text-lg font-bold ${isHighCgpa ? 'text-brass-700 dark:text-brass-400' : 'text-accent'}`}>
                  {application.cgpa.toFixed(2)}
                </span>
              </div>
              <div>
                <span className="text-muted-foreground block">Degree Program & Branch</span>
                <span className="font-medium text-foreground block truncate">
                  {application.branch}
                </span>
              </div>
            </div>

            <div className="pt-1 border-t border-border/60 flex items-center gap-1.5 text-muted-foreground">
              <Mail className="h-3.5 w-3.5" />
              <span>{application.studentEmail}</span>
            </div>
          </div>

          {/* Recruitment Drive Context */}
          <div className="rounded-md border border-border bg-card p-3.5 space-y-2">
            <span className="font-semibold text-foreground uppercase tracking-wider block">
              Recruitment Campaign
            </span>
            <div className="space-y-1.5 text-foreground">
              <div className="flex items-center gap-2">
                <Building className="h-3.5 w-3.5 text-muted-foreground shrink-0" />
                <span className="font-medium">{application.companyName}</span>
              </div>
              <div className="flex items-center gap-2">
                <Briefcase className="h-3.5 w-3.5 text-muted-foreground shrink-0" />
                <span>{application.jobTitle}</span>
              </div>
              <div className="flex items-center gap-2">
                <Calendar className="h-3.5 w-3.5 text-muted-foreground shrink-0" />
                <span className="text-muted-foreground">
                  Applied: {formatLocalDateTime(application.appliedAt)}
                </span>
              </div>
            </div>
          </div>

          {/* Remarks Section */}
          {application.remarks && (
            <div className="rounded-md border border-border/80 bg-muted/20 p-3 text-muted-foreground">
              <span className="font-medium text-foreground block mb-0.5">Application Remarks</span>
              <p>{application.remarks}</p>
            </div>
          )}

          {/* Inline Shortlist Confirmation Prompt */}
          {isConfirming && (
            <div className="rounded-md border border-accent/40 bg-accent/10 p-3.5 space-y-2.5">
              <p className="font-semibold text-accent">
                Confirm Candidate Shortlisting
              </p>
              <p className="text-muted-foreground leading-relaxed">
                Shortlisting transitions this application to{' '}
                <strong className="text-foreground">SHORTLISTED</strong>. The student will become eligible for interview room timetable scheduling.
              </p>
              <div className="flex items-center gap-2 pt-1">
                <Button
                  size="sm"
                  onClick={handleShortlist}
                  disabled={shortlistMutation.isPending}
                >
                  <UserCheck className="h-3.5 w-3.5 mr-1" />
                  {shortlistMutation.isPending ? 'Shortlisting...' : 'Confirm Shortlist'}
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setIsConfirming(false)}
                  disabled={shortlistMutation.isPending}
                >
                  Cancel
                </Button>
              </div>
            </div>
          )}
        </div>

        <DialogFooter className="pt-2">
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => onOpenChange(false)}
          >
            Close Dossier
          </Button>

          {!isConfirming && canShortlist && (
            <Button
              type="button"
              size="sm"
              onClick={() => setIsConfirming(true)}
              disabled={shortlistMutation.isPending}
            >
              <UserCheck className="h-3.5 w-3.5 mr-1" />
              Shortlist Candidate
            </Button>
          )}

          {!canShortlist && (
            <span className="text-xs font-mono text-muted-foreground self-center">
              Candidate already {application.status.toLowerCase()}
            </span>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
