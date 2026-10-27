import * as React from 'react'
import { Link } from 'react-router-dom'
import type { StudentInterviewDto } from '@/types/api'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { ApplicationStatusBadge } from '@/components/domain/application-status-badge'
import { formatLocalDateTime } from '@/lib/dateUtils'
import {
  CalendarDays,
  Clock,
  MapPin,
  UserCheck,
  Building2,
  ArrowRight,
} from 'lucide-react'

export interface InterviewSessionCardProps {
  session: StudentInterviewDto
  className?: string
}

export const InterviewSessionCard: React.FC<InterviewSessionCardProps> = ({
  session,
  className = '',
}) => {
  // Deterministic formatting for offset-free LocalDateTime ranges
  const formatInterviewDateTime = (startStr: string, endStr: string) => {
    try {
      const dateFormatted = formatLocalDateTime(startStr, {
        weekday: 'short',
        month: 'short',
        day: 'numeric',
        year: 'numeric',
      })

      const startTimeFormatted = formatLocalDateTime(startStr, {
        hour: 'numeric',
        minute: '2-digit',
        hour12: true,
      })

      const endTimeFormatted = formatLocalDateTime(endStr, {
        hour: 'numeric',
        minute: '2-digit',
        hour12: true,
      })

      return {
        date: dateFormatted,
        timeRange: `${startTimeFormatted} – ${endTimeFormatted}`,
      }
    } catch {
      return {
        date: session.startTime,
        timeRange: `${session.startTime} to ${session.endTime}`,
      }
    }
  }

  const { date, timeRange } = formatInterviewDateTime(session.startTime, session.endTime)

  return (
    <article
      className={`rounded-lg border border-border bg-card p-5 sm:p-6 shadow-xs hover:border-accent/40 transition-all flex flex-col justify-between space-y-4 ${className}`}
      aria-labelledby={`session-title-${session.sessionId}`}
    >
      <div className="space-y-3.5">
        {/* Header: Date, Time, Confirmed Badge & Application Status */}
        <div className="flex flex-wrap items-start justify-between gap-2.5 pb-3 border-b border-border/80">
          <div className="space-y-1">
            <div className="flex items-center gap-2 text-primary font-semibold text-xs sm:text-sm">
              <CalendarDays className="h-4 w-4 text-accent" aria-hidden="true" />
              <span>{date}</span>
            </div>
            <div className="flex items-center gap-2 text-muted-foreground text-xs font-mono">
              <Clock className="h-3.5 w-3.5 text-brass-600" aria-hidden="true" />
              <span>{timeRange}</span>
            </div>
          </div>

          <div className="flex items-center gap-1.5 shrink-0">
            <Badge variant="confirmed" className="shrink-0">
              <span className="h-1.5 w-1.5 rounded-full bg-teal-800" aria-hidden="true" />
              <span>Confirmed Appointment</span>
            </Badge>
            {session.status && (
              <ApplicationStatusBadge status={session.status} className="shrink-0 text-[11px]" />
            )}
          </div>
        </div>

        {/* Company & Role */}
        <div className="space-y-1">
          <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
            <Building2 className="h-3.5 w-3.5" aria-hidden="true" />
            <span>{session.companyName}</span>
          </div>
          <h3
            id={`session-title-${session.sessionId}`}
            className="font-serif font-bold text-lg text-foreground leading-snug"
          >
            {session.jobTitle}
          </h3>
        </div>

        {/* Operational Context: Room & Interviewer */}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 text-xs pt-2 border-t border-border/60">
          <div className="flex items-center gap-1.5 text-muted-foreground">
            <MapPin className="h-3.5 w-3.5 shrink-0 text-accent" aria-hidden="true" />
            <span>Venue:</span>
            <span className="font-semibold text-foreground truncate max-w-[150px]" title={session.roomName}>
              {session.roomName}
            </span>
          </div>

          <div className="flex items-center gap-1.5 text-muted-foreground">
            <UserCheck className="h-3.5 w-3.5 shrink-0 text-brass-600" aria-hidden="true" />
            <span>Panel:</span>
            <span className="font-medium text-foreground truncate max-w-[150px]" title={session.interviewerName}>
              {session.interviewerName}
            </span>
          </div>
        </div>
      </div>

      {/* Footer / Detail Link */}
      <div className="pt-3 border-t border-border flex flex-wrap items-center justify-between gap-2">
        <span className="min-w-0 font-mono text-xs text-muted-foreground break-all">
          Session ID: <strong className="text-foreground">{session.sessionId}</strong>
        </span>

        <Link to={`/student/interviews/${session.sessionId}`}>
          <Button
            type="button"
            variant="outline"
            size="sm"
            className="text-xs gap-1.5 h-8 font-medium"
          >
            <span>Session Briefing</span>
            <ArrowRight className="h-3.5 w-3.5" aria-hidden="true" />
          </Button>
        </Link>
      </div>
    </article>
  )
}

