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
import { NoticeBanner } from '@/components/domain/notice-banner'
import { useSubmitApplication } from '../api/studentApi'
import { ApiError } from '@/core/api/client'
import {
  AlertTriangle,
  CheckCircle2,
  Building2,
  Briefcase,
  Calendar,
  GraduationCap,
  Loader2,
  FileCheck2,
} from 'lucide-react'
import type { StudentDriveItemDto, StudentApplicationDetailDto } from '@/types/api'
import { formatLocalDateTime } from '@/lib/dateUtils'

export interface ApplicationConfirmationModalProps {
  isOpen: boolean
  onClose: () => void
  drive: StudentDriveItemDto | null
  studentProfile: {
    studentId: string
    name: string
    branch: string
    cgpa: number
  }
  onSuccess?: (app: StudentApplicationDetailDto) => void
}

export const ApplicationConfirmationModal: React.FC<ApplicationConfirmationModalProps> = ({
  isOpen,
  onClose,
  drive,
  studentProfile,
  onSuccess,
}) => {
  const submitMutation = useSubmitApplication()
  const [submissionError, setSubmissionError] = React.useState<string | null>(null)
  const [confirmedApp, setConfirmedApp] = React.useState<StudentApplicationDetailDto | null>(null)

  const handleClose = () => {
    setSubmissionError(null)
    setConfirmedApp(null)
    onClose()
  }

  if (!drive) return null

  const handleConfirmSubmit = async () => {
    setSubmissionError(null)
    try {
      const result = await submitMutation.mutateAsync(drive.id)
      setConfirmedApp(result)
      if (onSuccess) {
        onSuccess(result)
      }
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        if (err.status === 409) {
          setSubmissionError(
            `Submission Conflict: An application for this recruitment drive has already been registered (${err.message}).`
          )
        } else if (err.status === 400) {
          setSubmissionError(`Eligibility Invalidation: ${err.message}`)
        } else {
          setSubmissionError(err.message || 'Server error occurred during application processing.')
        }
      } else if (err instanceof Error) {
        setSubmissionError(err.message)
      } else {
        setSubmissionError('An unexpected network error occurred while submitting your application.')
      }
    }
  }

  return (
    <Dialog open={isOpen} onOpenChange={(open) => !submitMutation.isPending && !open && handleClose()}>
      <DialogContent className="sm:max-w-lg bg-card border-border">
        {confirmedApp ? (
          /* Success View */
          <div className="space-y-5 py-2">
            <DialogHeader className="text-left space-y-2">
              <div className="h-10 w-10 rounded-full bg-teal-900/10 flex items-center justify-center text-teal-800 border border-teal-700/20">
                <CheckCircle2 className="h-6 w-6" aria-hidden="true" />
              </div>
              <DialogTitle className="font-serif font-bold text-xl text-foreground">
                Application Successfully Submitted
              </DialogTitle>
              <DialogDescription className="text-xs text-muted-foreground">
                Your application has been authoritatively recorded by the university recruitment system.
              </DialogDescription>
            </DialogHeader>

            <div className="rounded-md border border-teal-700/20 bg-teal-900/5 p-4 space-y-2 text-xs">
              <div className="flex justify-between items-center pb-2 border-b border-teal-700/20">
                <span className="text-muted-foreground">Application Reference:</span>
                <span className="font-mono font-bold text-primary text-sm">{confirmedApp.id}</span>
              </div>
              <div className="flex justify-between items-center">
                <span className="text-muted-foreground">Recruiter:</span>
                <span className="font-semibold text-foreground">{confirmedApp.companyName}</span>
              </div>
              <div className="flex justify-between items-center">
                <span className="text-muted-foreground">Position:</span>
                <span className="font-medium text-foreground">{confirmedApp.jobTitle}</span>
              </div>
              <div className="flex justify-between items-center">
                <span className="text-muted-foreground">Status:</span>
                <span className="font-mono font-bold text-teal-800">{confirmedApp.status}</span>
              </div>
              <div className="flex justify-between items-center pt-1 border-t border-teal-700/20 text-muted-foreground">
                <span>Submitted At:</span>
                <span className="font-mono text-xs">{formatLocalDateTime(confirmedApp.appliedAt)}</span>
              </div>
            </div>

            <DialogFooter className="sm:justify-end gap-2 pt-2">
              <Button
                type="button"
                variant="default"
                onClick={handleClose}
                className="w-full sm:w-auto text-xs"
              >
                Close & Return to Dashboard
              </Button>
            </DialogFooter>
          </div>
        ) : (
          /* Confirmation Form View */
          <div className="space-y-4">
            <DialogHeader className="text-left space-y-1.5">
              <DialogTitle className="font-serif font-bold text-xl text-foreground flex items-center gap-2">
                <FileCheck2 className="h-5 w-5 text-accent" aria-hidden="true" />
                Confirm Application Submission
              </DialogTitle>
              <DialogDescription className="text-xs text-muted-foreground">
                Please review drive details and official academic credentials prior to formal submission.
              </DialogDescription>
            </DialogHeader>

            {submissionError && (
              <NoticeBanner variant="error" title="Submission Denied">
                {submissionError}
              </NoticeBanner>
            )}

            {/* Target Opportunity Summary */}
            <div className="rounded-md border border-border bg-muted/40 p-3.5 space-y-2 text-xs">
              <div className="flex items-center gap-2 font-semibold text-foreground">
                <Building2 className="h-4 w-4 text-accent" aria-hidden="true" />
                <span>{drive.companyName}</span>
              </div>
              <div className="flex items-center gap-2 text-muted-foreground pl-6">
                <Briefcase className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                <span className="text-foreground font-medium">{drive.jobTitle}</span>
              </div>
              <div className="flex items-center gap-2 text-muted-foreground pl-6">
                <Calendar className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                <span>Deadline: {drive.applicationDeadline}</span>
              </div>
            </div>

            {/* Submitting Student Dossier Verification */}
            <div className="rounded-md border border-border/80 bg-card p-3 space-y-1.5 text-xs">
              <div className="flex items-center gap-2 font-semibold text-primary">
                <GraduationCap className="h-4 w-4 text-accent" aria-hidden="true" />
                <span>Verified Candidate Credentials</span>
              </div>
              <div className="grid grid-cols-2 gap-2 pt-1 text-muted-foreground">
                <div>
                  <span>Roll No:</span>{' '}
                  <strong className="font-mono text-foreground">{studentProfile.studentId}</strong>
                </div>
                <div>
                  <span>Candidate:</span>{' '}
                  <strong className="text-foreground">{studentProfile.name}</strong>
                </div>
                <div>
                  <span>Department:</span>{' '}
                  <strong className="text-foreground">{studentProfile.branch}</strong>
                </div>
                <div>
                  <span>Verified CGPA:</span>{' '}
                  <strong className="font-mono text-teal-800 font-bold">
                    {studentProfile.cgpa.toFixed(2)}
                  </strong>
                </div>
              </div>
            </div>

            {/* Policy & Irreversibility Warning */}
            <div className="p-3 rounded-md bg-brass-500/10 border border-brass-600/30 text-xs space-y-1">
              <div className="flex items-center gap-1.5 font-semibold text-brass-700">
                <AlertTriangle className="h-4 w-4 shrink-0" aria-hidden="true" />
                <span>Institutional Placement Regulation</span>
              </div>
              <p className="text-muted-foreground text-xs leading-relaxed">
                Applications submitted to corporate partners are binding and cannot be retracted or modified through the portal. Your official academic transcript will be shared with the recruiting committee.
              </p>
            </div>

            <DialogFooter className="gap-2 sm:gap-0 pt-2 border-t border-border/80">
              <Button
                type="button"
                variant="outline"
                onClick={handleClose}
                disabled={submitMutation.isPending}
                className="text-xs"
              >
                Cancel
              </Button>
              <Button
                type="button"
                variant="default"
                onClick={handleConfirmSubmit}
                disabled={submitMutation.isPending || !drive.canApply}
                className="gap-2 text-xs"
              >
                {submitMutation.isPending ? (
                  <>
                    <Loader2 className="h-3.5 w-3.5 animate-spin" aria-hidden="true" />
                    Transacting with Backend...
                  </>
                ) : (
                  'Confirm & Submit Application'
                )}
              </Button>
            </DialogFooter>
          </div>
        )}
      </DialogContent>
    </Dialog>
  )
}
