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
import { Badge } from '@/components/ui/badge'
import { Checkbox } from '@/components/ui/checkbox'
import {
  ShieldAlert,
  Send,
  AlertTriangle,
} from 'lucide-react'
import type { OfficerSchedulingPreviewResponseDto } from '@/types/api'
import {
  parseUtcInstant,
  maskPreviewToken,
} from '../../utils/schedulingUtils'

export interface PublicationConfirmDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  preview: OfficerSchedulingPreviewResponseDto
  onConfirmPublish: () => void
  isPublishing: boolean
}

export const PublicationConfirmDialog: React.FC<PublicationConfirmDialogProps> = ({
  open,
  onOpenChange,
  preview,
  onConfirmPublish,
  isPublishing,
}) => {
  const [acknowledged, setAcknowledged] = React.useState(false)
  const [now, setNow] = React.useState(() => Date.now())

  React.useEffect(() => {
    if (open) {
      setNow(Date.now())
    }
  }, [open])

  const expires = parseUtcInstant(preview.expiresAt)
  const isExpired = expires.getTime() <= now

  // Distinct interviewers & rooms allocated
  const distinctInterviewersCount = React.useMemo(() => {
    return new Set(preview.proposedSessions.map((s) => s.interviewerId)).size
  }, [preview.proposedSessions])

  const distinctRoomsCount = React.useMemo(() => {
    return new Set(preview.proposedSessions.map((s) => s.roomId)).size
  }, [preview.proposedSessions])

  const handleOpenChange = (nextOpen: boolean) => {
    if (!nextOpen) {
      setAcknowledged(false)
    }
    onOpenChange(nextOpen)
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="max-w-xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <div className="flex items-center gap-2">
            <div className="h-8 w-8 rounded-sm bg-academic-teal/10 text-academic-teal flex items-center justify-center shrink-0">
              <Send className="h-4 w-4" />
            </div>
            <div>
              <DialogTitle className="font-serif text-base font-bold text-foreground">
                Confirm Timetable Publication
              </DialogTitle>
              <DialogDescription className="text-xs text-muted-foreground">
                Authorize transactional PostgreSQL persistence for advisory timetable proposals.
              </DialogDescription>
            </div>
          </div>
        </DialogHeader>

        <div className="space-y-4 py-2 text-xs">
          {/* Structured Review Summary */}
          <div className="rounded-sm border border-border bg-muted/20 p-3.5 space-y-2.5">
            <div className="flex justify-between items-center text-muted-foreground pb-2 border-b border-border/60">
              <span>Recruitment Drive:</span>
              <strong className="text-foreground">{preview.jobTitle} ({preview.driveId})</strong>
            </div>
            <div className="flex justify-between items-center text-muted-foreground">
              <span>Corporate Partner:</span>
              <span className="font-medium text-foreground">{preview.companyName}</span>
            </div>
            <div className="flex justify-between items-center text-muted-foreground">
              <span>Scheduled Date & Window:</span>
              <span className="font-mono text-foreground">{preview.date} ({preview.startTime} – {preview.endTime})</span>
            </div>
            <div className="flex justify-between items-center text-muted-foreground">
              <span>Proposals to Confirm:</span>
              <Badge className="font-mono text-xs bg-academic-teal text-warm-ivory">
                {preview.totalScheduled} appointments
              </Badge>
            </div>
            <div className="flex justify-between items-center text-muted-foreground">
              <span>Resources Allocated:</span>
              <span className="text-foreground">
                {distinctInterviewersCount} interviewer(s) across {distinctRoomsCount} room(s)
              </span>
            </div>
            <div className="flex justify-between items-center text-muted-foreground">
              <span>Unscheduled Candidates:</span>
              <span className="font-mono text-brass-800 dark:text-brass-400">
                {preview.totalUnscheduled} candidate(s) remain shortlisted
              </span>
            </div>
            <div className="flex justify-between items-center text-muted-foreground pt-1 border-t border-border/60">
              <span>Preview Token:</span>
              <span className="font-mono text-[11px] text-muted-foreground">
                {maskPreviewToken(preview.previewId)}
              </span>
            </div>
          </div>

          {/* Operational Consequences Notice */}
          <div className="rounded-sm border border-brass-600/30 bg-brass-600/5 p-3.5 space-y-2">
            <div className="flex items-center gap-1.5 font-semibold text-brass-800 dark:text-brass-300">
              <ShieldAlert className="h-4 w-4 text-brass-600" />
              <span>Consequences of Publication:</span>
            </div>
            <ul className="list-disc list-inside space-y-1 text-muted-foreground text-[11px] leading-relaxed">
              <li>
                <strong className="text-foreground">Non-Atomic Per-Session Persistence:</strong> Individual appointments are validated and committed independently in PostgreSQL. If concurrent conflicts arise, partial success (HTTP 207) will commit valid sessions.
              </li>
              <li>
                <strong className="text-foreground">Application Status Transition:</strong> Successfully committed candidates immediately advance from <code className="font-mono text-[10px] bg-muted px-1 py-0.5 rounded">SHORTLISTED</code> to <code className="font-mono text-[10px] bg-muted px-1 py-0.5 rounded">SCHEDULED</code>.
              </li>
              <li>
                <strong className="text-foreground">Candidate Visibility:</strong> Confirmed interview slots will instantly appear on candidate Opportunity Hubs and Interview Timetables.
              </li>
              <li>
                <strong className="text-foreground">Single-Use Token Consumption:</strong> This preview token will be atomically consumed on the server and cannot be reused or replayed.
              </li>
            </ul>
          </div>

          {/* Expiration Guard */}
          {isExpired && (
            <div className="p-3 rounded-sm border border-destructive/40 bg-destructive/10 text-destructive text-xs flex items-center gap-2">
              <AlertTriangle className="h-4 w-4 shrink-0" />
              <span>
                This preview has expired on the server. Please close this dialog and generate a fresh preview before publishing.
              </span>
            </div>
          )}

          {/* Required Confirmation Checkbox */}
          <label className="flex items-start gap-2.5 p-3 rounded-sm border border-border bg-card cursor-pointer hover:bg-muted/30 transition-colors">
            <Checkbox
              id="publication-acknowledgement-checkbox"
              checked={acknowledged}
              onCheckedChange={(checked) => setAcknowledged(Boolean(checked))}
              disabled={isPublishing || isExpired}
              className="mt-0.5"
            />
            <span className="text-xs text-foreground font-medium leading-normal select-none">
              I understand that individual appointments are persisted independently in PostgreSQL and that confirmed sessions cannot be rolled back automatically once published.
            </span>
          </label>
        </div>

        <DialogFooter className="gap-2 sm:gap-0 pt-2 border-t border-border/80">
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => onOpenChange(false)}
            disabled={isPublishing}
            className="text-xs"
          >
            Cancel
          </Button>

          <Button
            type="button"
            size="sm"
            onClick={onConfirmPublish}
            disabled={!acknowledged || isPublishing || isExpired}
            className="text-xs gap-1.5 bg-academic-teal hover:bg-academic-teal/90 text-warm-ivory shadow-xs"
          >
            {isPublishing ? (
              <>
                <span className="h-3.5 w-3.5 rounded-full border-2 border-warm-ivory/30 border-t-warm-ivory animate-spin" />
                <span>Committing to PostgreSQL...</span>
              </>
            ) : (
              <>
                <Send className="h-3.5 w-3.5" />
                <span>Confirm & Publish Timetable</span>
              </>
            )}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
