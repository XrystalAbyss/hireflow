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
import {
  Clock,
  User,
  DoorOpen,
  Briefcase,
  ShieldCheck,
} from 'lucide-react'
import type { OfficerInterviewDetailDto, ApplicationStatus } from '@/types/api'
import { parseCampusLocalDateTime } from '../../utils/schedulingUtils'
import { ApplicationStatusBadge } from '@/components/domain/application-status-badge'

export interface ConfirmedSessionDetailDialogProps {
  session: OfficerInterviewDetailDto | null
  open: boolean
  onOpenChange: (open: boolean) => void
}

export const ConfirmedSessionDetailDialog: React.FC<ConfirmedSessionDetailDialogProps> = ({
  session,
  open,
  onOpenChange,
}) => {
  if (!session) return null

  const start = parseCampusLocalDateTime(session.startTime)
  const end = parseCampusLocalDateTime(session.endTime)

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-lg max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <div className="flex items-center gap-2">
            <div className="h-8 w-8 rounded-sm bg-academic-teal/10 text-academic-teal flex items-center justify-center shrink-0">
              <ShieldCheck className="h-4 w-4" />
            </div>
            <div>
              <DialogTitle className="font-serif text-base font-bold text-foreground">
                Confirmed Interview Session
              </DialogTitle>
              <DialogDescription className="text-xs text-muted-foreground font-mono">
                {session.sessionId} • Committed in PostgreSQL
              </DialogDescription>
            </div>
          </div>
        </DialogHeader>

        <div className="space-y-4 py-2 text-xs">
          {/* Candidate & Status Card */}
          <div className="rounded-sm border border-border bg-muted/20 p-3.5 space-y-3">
            <div className="flex justify-between items-start">
              <div>
                <span className="font-semibold text-sm text-foreground block">
                  {session.studentName}
                </span>
                <span className="font-mono text-xs text-muted-foreground">
                  Student ID: {session.studentId}
                </span>
              </div>
              <ApplicationStatusBadge status={session.status as ApplicationStatus} />
            </div>

            <div className="grid grid-cols-2 gap-2 text-muted-foreground pt-2 border-t border-border/60">
              <div>
                <span>Academic Branch:</span>
                <strong className="text-foreground block font-medium">{session.studentBranch}</strong>
              </div>
              <div>
                <span>Application Reference:</span>
                <strong className="text-foreground block font-mono text-[11px]">{session.applicationId}</strong>
              </div>
            </div>
          </div>

          {/* Appointment Timing */}
          <div className="rounded-sm border border-academic-teal/30 bg-academic-teal/5 p-3.5 space-y-2">
            <span className="font-semibold text-academic-teal flex items-center gap-1.5">
              <Clock className="h-3.5 w-3.5" />
              <span>Confirmed Campus Wall-Clock Time:</span>
            </span>
            <div className="font-mono text-sm font-bold text-foreground">
              {start.formattedTime} – {end.formattedTime}
            </div>
            <div className="text-muted-foreground text-[11px]">
              {start.formattedDate} ({start.datePart})
            </div>
          </div>

          {/* Drive Context */}
          <div className="rounded-sm border border-border p-3.5 space-y-2">
            <span className="font-semibold text-foreground flex items-center gap-1.5">
              <Briefcase className="h-3.5 w-3.5 text-academic-teal" />
              <span>Recruitment Drive & Corporate Partner:</span>
            </span>
            <div className="space-y-1 text-muted-foreground">
              <div className="flex justify-between">
                <span>Job Role:</span>
                <strong className="text-foreground">{session.jobTitle}</strong>
              </div>
              <div className="flex justify-between">
                <span>Corporate Partner:</span>
                <span className="font-medium text-foreground">{session.companyName}</span>
              </div>
              <div className="flex justify-between">
                <span>Drive Identifier:</span>
                <span className="font-mono text-[11px] text-muted-foreground">{session.driveId}</span>
              </div>
            </div>
          </div>

          {/* Resource Allocations */}
          <div className="grid grid-cols-2 gap-3">
            <div className="rounded-sm border border-border p-3 space-y-1">
              <span className="text-muted-foreground flex items-center gap-1 text-[11px]">
                <User className="h-3 w-3 text-academic-teal" />
                <span>Interviewer</span>
              </span>
              <strong className="text-foreground block font-medium truncate">
                {session.interviewerName}
              </strong>
              <span className="font-mono text-[10px] text-muted-foreground block">
                {session.interviewerId}
              </span>
            </div>

            <div className="rounded-sm border border-border p-3 space-y-1">
              <span className="text-muted-foreground flex items-center gap-1 text-[11px]">
                <DoorOpen className="h-3 w-3 text-academic-teal" />
                <span>Interview Room</span>
              </span>
              <strong className="text-foreground block font-medium truncate">
                {session.roomName}
              </strong>
              <span className="font-mono text-[10px] text-muted-foreground block">
                {session.roomId}
              </span>
            </div>
          </div>
        </div>

        <DialogFooter className="pt-2 border-t border-border/80">
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => onOpenChange(false)}
            className="text-xs"
          >
            Close Session Briefing
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
