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
import {
  formatStudentDisplayName,
  formatCompanyDisplayName,
  formatContactEmail,
  formatEligibilityExplanation,
} from '@/lib/presentationAdapter'
import type { OfficerApplicationReviewDto, OfficerDriveDetailDto } from '@/types/api'
import { useShortlistApplicationMutation } from '../api/officerApi'
import {
  Award,
  UserCheck,
  Mail,
  Building,
  Briefcase,
  Calendar,
  ChevronDown,
  ChevronUp,
  Info,
} from 'lucide-react'

export interface CandidateDetailDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  application: OfficerApplicationReviewDto | null
  drive?: OfficerDriveDetailDto | null
  onSuccess?: () => void
}

function getActionStatusNote(status: OfficerApplicationReviewDto['status']): string {
  switch (status) {
    case 'INELIGIBLE':
      return 'Not eligible for shortlisting'
    case 'SHORTLISTED':
      return 'Already shortlisted'
    case 'SCHEDULED':
      return 'Interview scheduled'
    case 'SELECTED':
      return 'Candidate selected'
    case 'REJECTED':
      return 'Application closed'
    default:
      return `Status: ${status.toLowerCase()}`
  }
}

export function CandidateDetailDialog({
  open,
  onOpenChange,
  application,
  drive,
  onSuccess,
}: CandidateDetailDialogProps) {
  const [errorMsg, setErrorMsg] = React.useState<string | null>(null)
  const [isConfirming, setIsConfirming] = React.useState(false)
  const [showTechnicalDetails, setShowTechnicalDetails] = React.useState(false)
  const shortlistMutation = useShortlistApplicationMutation()

  React.useEffect(() => {
    setErrorMsg(null)
    setIsConfirming(false)
    setShowTechnicalDetails(false)
  }, [open, application])

  if (!application) return null

  const studentDisplayName = formatStudentDisplayName(application.studentName)
  const companyDisplayName = formatCompanyDisplayName(application.companyName)
  const contactInfo = formatContactEmail(application.studentEmail)
  const eligibilityExplanation = formatEligibilityExplanation({
    status: application.status,
    remarks: application.remarks,
    cgpa: application.cgpa,
    minimumCgpa: drive?.minimumCgpa,
    branch: application.branch,
    eligibleBranches: drive?.eligibleBranches,
    driveClosed: drive?.closed,
  })

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
      <DialogContent className="max-w-lg max-h-[90vh] overflow-y-auto">
        {/* 1. Header */}
        <DialogHeader className="pr-8 space-y-2">
          <div className="flex flex-wrap items-center gap-2">
            <span className="font-mono text-[11px] text-muted-foreground uppercase tracking-wider">
              Candidate Application
            </span>
          </div>
          <div className="flex flex-wrap items-center justify-between gap-2">
            <DialogTitle className="text-xl leading-snug">
              {studentDisplayName}
            </DialogTitle>
            <ApplicationStatusBadge status={application.status} />
          </div>
          <DialogDescription className="text-xs text-muted-foreground">
            {application.branch}
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

        <div className="space-y-3.5 text-xs">
          {/* 2. Academic Information */}
          <section
            aria-label="Academic Information"
            className="rounded-md border border-border bg-muted/30 p-3.5 space-y-2.5"
          >
            <div className="flex items-center justify-between gap-2">
              <span className="font-semibold text-foreground uppercase tracking-wider">
                Academic Information
              </span>
              {isHighCgpa && (
                <span className="inline-flex items-center gap-1 text-brass-700 dark:text-brass-400 font-medium">
                  <Award className="h-3.5 w-3.5" aria-hidden="true" />
                  Honors Candidate
                </span>
              )}
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-0.5">
              <div>
                <span className="text-muted-foreground block">Recorded CGPA</span>
                <span
                  className={`font-mono text-lg font-bold ${
                    isHighCgpa ? 'text-brass-700 dark:text-brass-400' : 'text-accent'
                  }`}
                >
                  {application.cgpa.toFixed(2)}
                </span>
              </div>
              <div>
                <span className="text-muted-foreground block">Branch / Program</span>
                <span className="font-medium text-foreground block break-words">
                  {application.branch}
                </span>
              </div>
            </div>

            <div className="pt-2 border-t border-border/60 flex items-center gap-1.5 text-muted-foreground">
              <Mail className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
              <span className="truncate">{contactInfo.display}</span>
            </div>
          </section>

          {/* 3. Recruitment Application */}
          <section
            aria-label="Recruitment Application"
            className="rounded-md border border-border bg-card p-3.5 space-y-2"
          >
            <span className="font-semibold text-foreground uppercase tracking-wider block">
              Recruitment Application
            </span>
            <div className="space-y-1.5 text-foreground">
              <div className="flex items-center gap-2">
                <Building className="h-3.5 w-3.5 text-muted-foreground shrink-0" aria-hidden="true" />
                <span className="font-medium">{companyDisplayName}</span>
              </div>
              <div className="flex items-center gap-2">
                <Briefcase className="h-3.5 w-3.5 text-muted-foreground shrink-0" aria-hidden="true" />
                <span>{application.jobTitle}</span>
              </div>
              <div className="flex items-center gap-2">
                <Calendar className="h-3.5 w-3.5 text-muted-foreground shrink-0" aria-hidden="true" />
                <span className="text-muted-foreground">
                  Applied {formatLocalDateTime(application.appliedAt)}
                </span>
              </div>
              {drive && (
                <div className="pt-1.5 mt-1.5 border-t border-border/60 flex flex-wrap gap-x-4 gap-y-1 text-muted-foreground">
                  <span>
                    Min CGPA:{' '}
                    <strong className="font-mono text-foreground">
                      {drive.minimumCgpa.toFixed(2)}
                    </strong>
                  </span>
                  <span>
                    Eligible Branches:{' '}
                    <strong className="text-foreground">
                      {drive.eligibleBranches.join(', ')}
                    </strong>
                  </span>
                </div>
              )}
            </div>
          </section>

          {/* 4. Eligibility Assessment */}
          <section
            aria-label="Eligibility Assessment"
            className="rounded-md border border-border/80 bg-muted/20 p-3.5 space-y-2"
          >
            <div className="flex items-center justify-between gap-2">
              <span className="font-semibold text-foreground uppercase tracking-wider">
                Eligibility Assessment
              </span>
              <ApplicationStatusBadge status={application.status} />
            </div>
            <p className="text-muted-foreground leading-relaxed">
              {eligibilityExplanation}
            </p>
          </section>

          {/* Inline Shortlist Confirmation Prompt */}
          {isConfirming && (
            <div className="rounded-md border border-accent/40 bg-accent/10 p-3.5 space-y-2.5">
              <p className="font-semibold text-accent">Confirm Candidate Shortlisting</p>
              <p className="text-muted-foreground leading-relaxed">
                Shortlisting transitions this application to{' '}
                <strong className="text-foreground">SHORTLISTED</strong> so the candidate can be included in interview timetable scheduling.
              </p>
              <div className="flex flex-wrap items-center gap-2 pt-1">
                <Button
                  size="sm"
                  onClick={handleShortlist}
                  disabled={shortlistMutation.isPending}
                >
                  <UserCheck className="h-3.5 w-3.5 mr-1" aria-hidden="true" />
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

          {/* 6. Secondary Details (Expandable Audit & Reference IDs) */}
          <div className="rounded-md border border-border/70 bg-background">
            <button
              type="button"
              onClick={() => setShowTechnicalDetails((prev) => !prev)}
              aria-expanded={showTechnicalDetails}
              className="w-full flex items-center justify-between px-3.5 py-2 text-left text-xs font-medium text-muted-foreground hover:text-foreground transition-colors"
            >
              <span className="inline-flex items-center gap-1.5">
                <Info className="h-3.5 w-3.5" aria-hidden="true" />
                <span>Technical &amp; Audit Details</span>
              </span>
              {showTechnicalDetails ? (
                <ChevronUp className="h-3.5 w-3.5" aria-hidden="true" />
              ) : (
                <ChevronDown className="h-3.5 w-3.5" aria-hidden="true" />
              )}
            </button>

            {showTechnicalDetails && (
              <div className="px-3.5 pb-3 pt-1 border-t border-border/60 space-y-1.5 font-mono text-[11px] text-muted-foreground">
                <div className="flex flex-wrap justify-between gap-2">
                  <span>Student ID:</span>
                  <span className="text-foreground">{application.studentId}</span>
                </div>
                <div className="flex flex-wrap justify-between gap-2">
                  <span>Application ID:</span>
                  <span className="text-foreground">{application.id}</span>
                </div>
                <div className="flex flex-wrap justify-between gap-2">
                  <span>Drive ID:</span>
                  <span className="text-foreground">{application.driveId}</span>
                </div>
                {application.remarks && (
                  <div className="pt-1.5 border-t border-border/50">
                    <span className="block mb-0.5">Recorded Metadata:</span>
                    <p className="font-sans text-xs text-muted-foreground break-words">
                      {application.remarks}
                    </p>
                  </div>
                )}
              </div>
            )}
          </div>
        </div>

        {/* 5. Actions */}
        <DialogFooter className="pt-2 flex-col-reverse sm:flex-row sm:justify-between sm:items-center gap-2">
          {!canShortlist ? (
            <span className="text-xs text-muted-foreground">
              {getActionStatusNote(application.status)}
            </span>
          ) : (
            <span />
          )}

          <div className="flex flex-wrap items-center justify-end gap-2">
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => onOpenChange(false)}
            >
              Close
            </Button>

            {!isConfirming && canShortlist && (
              <Button
                type="button"
                size="sm"
                onClick={() => setIsConfirming(true)}
                disabled={shortlistMutation.isPending}
              >
                <UserCheck className="h-3.5 w-3.5 mr-1" aria-hidden="true" />
                Shortlist Candidate
              </Button>
            )}
          </div>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
