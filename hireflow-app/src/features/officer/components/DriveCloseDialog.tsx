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
import { AlertTriangle } from 'lucide-react'
import type { OfficerDriveDetailDto } from '@/types/api'
import { useCloseDriveMutation } from '../api/officerApi'

export interface DriveCloseDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  drive: OfficerDriveDetailDto | null
  onSuccess?: () => void
}

export function DriveCloseDialog({
  open,
  onOpenChange,
  drive,
  onSuccess,
}: DriveCloseDialogProps) {
  const [errorMsg, setErrorMsg] = React.useState<string | null>(null)
  const closeMutation = useCloseDriveMutation()

  React.useEffect(() => {
    setErrorMsg(null)
  }, [open, drive])

  if (!drive) return null

  const handleClose = async () => {
    setErrorMsg(null)
    try {
      await closeMutation.mutateAsync(drive.id)
      onOpenChange(false)
      onSuccess?.()
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to close recruitment drive.'
      setErrorMsg(msg)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <div className="flex items-center gap-2.5 text-status-rejected-text mb-1">
            <AlertTriangle className="h-5 w-5 shrink-0" aria-hidden="true" />
            <DialogTitle>Close Recruitment Drive</DialogTitle>
          </div>
          <DialogDescription>
            You are closing recruitment drive{' '}
            <strong className="font-mono text-foreground">{drive.id}</strong> ({drive.jobTitle} at {drive.companyName}).
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

        <div className="rounded-md border border-amber-300/60 bg-amber-50/50 dark:bg-amber-950/20 p-3.5 space-y-2 text-xs text-amber-900 dark:text-amber-200">
          <p className="font-semibold">Authoritative Closure Consequences:</p>
          <ul className="list-disc list-inside space-y-1 text-muted-foreground">
            <li>Students will no longer be able to submit applications.</li>
            <li>
              All existing <strong className="text-foreground">{drive.totalApplications}</strong>{' '}
              applications remain archived and accessible for review.
            </li>
            <li>
              Existing shortlisted candidates ({drive.shortlistedApplications}) remain eligible for interview scheduling.
            </li>
            <li>Drive closure is recorded in PostgreSQL and cannot be retracted.</li>
          </ul>
        </div>

        <DialogFooter className="pt-2">
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => onOpenChange(false)}
            disabled={closeMutation.isPending}
          >
            Cancel
          </Button>
          <Button
            type="button"
            variant="destructive"
            size="sm"
            onClick={handleClose}
            disabled={closeMutation.isPending}
          >
            {closeMutation.isPending ? 'Closing Drive...' : 'Confirm Authoritative Closure'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
