import * as React from 'react'
import { useParams, Link } from 'react-router-dom'
import { useInterviewDetail } from '../api/studentApi'
import { PageHeading } from '@/components/domain/page-heading'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { ApplicationStatusBadge } from '@/components/domain/application-status-badge'
import { formatLocalDateTime } from '@/lib/dateUtils'
import { LoadingSkeleton } from '@/components/domain/loading-skeleton'
import { ErrorState } from '@/components/domain/error-state'
import {
  CalendarDays,
  Clock,
  MapPin,
  UserCheck,
  ArrowLeft,
  FileCheck2,
  CheckCircle2,
  AlertTriangle,
} from 'lucide-react'

export const StudentInterviewDetailPage: React.FC = () => {
  const { id } = useParams<{ id: string }>()
  const { data: session, isLoading, error, refetch } = useInterviewDetail(id)

  if (isLoading) {
    return (
      <div className="space-y-6">
        <PageHeading
          title="Interview Session Briefing"
          description="Retrieving confirmed interview details and venue assignment..."
        />
        <LoadingSkeleton rows={3} />
      </div>
    )
  }

  if (error || !session) {
    return (
      <div className="space-y-6">
        <div className="flex items-center gap-3">
          <Link
            to="/student/interviews"
            className="inline-flex items-center gap-1.5 text-xs font-medium text-muted-foreground hover:text-foreground transition-colors p-1 rounded"
          >
            <ArrowLeft className="h-3.5 w-3.5" aria-hidden="true" />
            <span>Back to Timetable</span>
          </Link>
        </div>
        <ErrorState
          title="Interview Session Not Found"
          message={
            error instanceof Error
              ? error.message
              : 'The requested interview appointment was not found or belongs to another candidate.'
          }
          onRetry={refetch}
        />
      </div>
    )
  }

  const formatDateTime = (startStr: string, endStr: string) => {
    try {
      const dateStr = formatLocalDateTime(startStr, {
        weekday: 'long',
        month: 'long',
        day: 'numeric',
        year: 'numeric',
      })

      const startTime = formatLocalDateTime(startStr, {
        hour: 'numeric',
        minute: '2-digit',
        hour12: true,
      })

      const endTime = formatLocalDateTime(endStr, {
        hour: 'numeric',
        minute: '2-digit',
        hour12: true,
      })

      return { dateStr, timeRange: `${startTime} – ${endTime}` }
    } catch {
      return { dateStr: session.startTime, timeRange: `${session.startTime} to ${session.endTime}` }
    }
  }

  const { dateStr, timeRange } = formatDateTime(session.startTime, session.endTime)

  return (
    <div className="space-y-6">
      {/* Navigation Breadcrumbs */}
      <div className="flex items-center gap-3">
        <Link
          to="/student/interviews"
          className="inline-flex items-center gap-1.5 text-xs font-medium text-muted-foreground hover:text-foreground transition-colors p-1 rounded"
        >
          <ArrowLeft className="h-3.5 w-3.5" aria-hidden="true" />
          <span>Back to Confirmed Timetable</span>
        </Link>
      </div>

      <PageHeading
        title={`Interview Session: ${session.sessionId}`}
        description={`Formal recruitment evaluation for ${session.jobTitle} with ${session.companyName}.`}
        breadcrumbs={[
          { label: 'Portal Home', href: '/student/dashboard' },
          { label: 'Interview Timetable', href: '/student/interviews' },
          { label: session.sessionId },
        ]}
        actions={
          <div className="flex items-center gap-2">
            <Link to={`/student/applications/${session.applicationId}`}>
              <Button variant="outline" size="sm" className="text-xs gap-1.5 h-8">
                <FileCheck2 className="h-3.5 w-3.5 text-accent" aria-hidden="true" />
                <span>View Application {session.applicationId}</span>
              </Button>
            </Link>
          </div>
        }
      />

      {/* Primary Session Card */}
      <div className="rounded-lg border border-border bg-card p-6 shadow-xs space-y-6">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-border/80">
          <div>
            <span className="text-xs uppercase tracking-wider text-muted-foreground block font-sans">
              Corporate Evaluation Session
            </span>
            <h2 className="font-serif font-bold text-xl text-foreground mt-0.5">
              {session.companyName}
            </h2>
            <p className="text-sm font-semibold text-primary mt-0.5">
              {session.jobTitle}
            </p>
          </div>

          <div className="flex items-center gap-2 self-start sm:self-auto">
            <Badge variant="confirmed" className="text-xs px-3 py-1">
              <CheckCircle2 className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
              <span>Confirmed Appointment</span>
            </Badge>
            {session.status && (
              <ApplicationStatusBadge status={session.status} className="text-xs px-3 py-1" />
            )}
          </div>
        </div>

        {/* Schedule & Location Highlighting Card */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div className="p-4 rounded-md bg-teal-900/5 border border-teal-700/20 space-y-2">
            <div className="flex items-center gap-2 text-teal-800 font-semibold text-xs uppercase tracking-wider">
              <CalendarDays className="h-4 w-4" aria-hidden="true" />
              <span>Session Schedule</span>
            </div>
            <p className="text-sm font-serif font-bold text-foreground">{dateStr}</p>
            <div className="flex items-center gap-2 font-mono text-xs text-primary font-semibold">
              <Clock className="h-3.5 w-3.5 text-brass-600" aria-hidden="true" />
              <span>{timeRange}</span>
            </div>
          </div>

          <div className="p-4 rounded-md bg-muted/40 border border-border/60 space-y-2">
            <div className="flex items-center gap-2 text-primary font-semibold text-xs uppercase tracking-wider">
              <MapPin className="h-4 w-4 text-accent" aria-hidden="true" />
              <span>Assigned Venue</span>
            </div>
            <p className="text-sm font-serif font-bold text-foreground">{session.roomName}</p>
            <p className="font-mono text-xs text-muted-foreground">Room ID: {session.roomId}</p>
          </div>
        </div>

        {/* Evaluation Panel Details */}
        <div className="p-4 rounded-md border border-border/80 bg-card space-y-3">
          <span className="text-xs uppercase tracking-wider font-semibold text-muted-foreground block">
            Assigned Interview Panel Member
          </span>
          <div className="flex items-center gap-3">
            <div className="h-10 w-10 rounded-full bg-primary/10 text-primary flex items-center justify-center">
              <UserCheck className="h-5 w-5 text-accent" aria-hidden="true" />
            </div>
            <div>
              <p className="font-semibold text-sm text-foreground">{session.interviewerName}</p>
              <p className="font-mono text-xs text-muted-foreground">Interviewer ID: {session.interviewerId}</p>
            </div>
          </div>
        </div>

        {/* DTO Traceability Information */}
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-xs pt-2 border-t border-border/60">
          <div>
            <span className="text-[11px] uppercase tracking-wider text-muted-foreground block">
              Candidate Roll No
            </span>
            <span className="font-mono font-bold text-foreground">{session.studentId}</span>
          </div>
          <div>
            <span className="text-[11px] uppercase tracking-wider text-muted-foreground block">
              Application Reference
            </span>
            <span className="font-mono font-bold text-primary">{session.applicationId}</span>
          </div>
          <div>
            <span className="text-[11px] uppercase tracking-wider text-muted-foreground block">
              Recruitment Drive ID
            </span>
            <span className="font-mono font-bold text-foreground">{session.driveId}</span>
          </div>
          <div>
            <span className="text-[11px] uppercase tracking-wider text-muted-foreground block">
              Application Status
            </span>
            <span className="font-mono font-bold text-foreground">{session.status}</span>
          </div>
        </div>
      </div>

      {/* General Candidate Advisory Notice */}
      <div className="p-4 rounded-md bg-muted/40 border border-border/60 text-xs flex items-start gap-3">
        <AlertTriangle className="h-4 w-4 text-muted-foreground shrink-0 mt-0.5" aria-hidden="true" />
        <p className="text-muted-foreground leading-relaxed">
          General Advisory: The interview session schedule, room assignment, and evaluation panel member above represent authoritative records confirmed by the Central Placement Office. Candidates experiencing emergency scheduling conflicts should consult the Placement Office coordinator.
        </p>
      </div>
    </div>
  )
}
