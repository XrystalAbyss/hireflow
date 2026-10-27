import * as React from 'react'
import { Link } from 'react-router-dom'
import { useStudentInterviews } from '../api/studentApi'
import { InterviewSessionCard } from '../components/InterviewSessionCard'
import { PageHeading } from '@/components/domain/page-heading'
import { LoadingSkeleton } from '@/components/domain/loading-skeleton'
import { ErrorState } from '@/components/domain/error-state'
import { EmptyState } from '@/components/domain/empty-state'
import { Button } from '@/components/ui/button'
import {
  CalendarDays,
  Briefcase,
} from 'lucide-react'

export const StudentInterviewsPage: React.FC = () => {
  const { data: interviews, isLoading, error, refetch } = useStudentInterviews()

  if (isLoading) {
    return (
      <div className="space-y-6">
        <PageHeading
          title="Interview Timetable"
          description="Retrieving confirmed interview sessions and room assignments..."
        />
        <LoadingSkeleton rows={3} />
      </div>
    )
  }

  if (error || !interviews) {
    return (
      <div className="space-y-6">
        <PageHeading title="Interview Timetable" description="University Placement Portal" />
        <ErrorState
          title="Unable to Retrieve Interview Timetable"
          message={error instanceof Error ? error.message : 'Connection to interview scheduling service failed.'}
          onRetry={refetch}
        />
      </div>
    )
  }

  return (
    <div className="space-y-6">
      {/* Page Title & Breadcrumbs */}
      <PageHeading
        title="Confirmed Interview Timetable"
        description="Official schedule of published candidate interviews. All sessions are confirmed by the Central Placement Office and assigned corporate evaluation panels."
        breadcrumbs={[
          { label: 'Portal Home', href: '/student/dashboard' },
          { label: 'Interview Timetable' },
        ]}
        actions={
          <Link to="/student/applications">
            <Button variant="outline" size="sm" className="text-xs gap-1.5 h-8">
              <Briefcase className="h-3.5 w-3.5 text-accent" aria-hidden="true" />
              <span>Review Applications</span>
            </Button>
          </Link>
        }
      />

      {/* General Candidate Advisory Notice */}
      <div className="rounded-lg border border-border bg-card p-4 sm:p-5 shadow-xs flex items-start gap-3.5 text-xs">
        <div className="h-8 w-8 rounded-sm bg-primary/10 text-primary flex items-center justify-center shrink-0 mt-0.5">
          <CalendarDays className="h-4 w-4 text-accent" aria-hidden="true" />
        </div>
        <div className="space-y-1">
          <h2 className="font-semibold text-xs sm:text-sm text-foreground">
            General Candidate Advisory (Non-Binding Guidance)
          </h2>
          <p className="text-muted-foreground leading-relaxed">
            Interview timetables, room assignments, and designated evaluation panels below reflect authoritative records confirmed by the Placement Office. Candidates are generally advised to arrive at assigned placement rooms early. For official reschedule petitions or departmental accommodation inquiries, contact Career Services.
          </p>
        </div>
      </div>

      {/* Interview Sessions List */}
      {interviews.length > 0 ? (
        <div className="space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-1 text-xs text-muted-foreground font-mono">
            <span>
              Total Confirmed Sessions: <strong className="text-foreground">{interviews.length}</strong>
            </span>
            <span>Sorted Chronologically</span>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4 sm:gap-5">
            {interviews.map((session) => (
              <InterviewSessionCard key={session.sessionId} session={session} />
            ))}
          </div>
        </div>
      ) : (
        <EmptyState
          icon={<CalendarDays className="h-5 w-5" aria-hidden="true" />}
          title="No Interviews Scheduled"
          description="You currently have no confirmed interview sessions on your timetable. When applications are shortlisted and interview slots are published, they will appear here."
          action={
            <Link to="/student/applications">
              <Button variant="default" size="sm" className="text-xs">
                Inspect My Applications
              </Button>
            </Link>
          }
        />
      )}
    </div>
  )
}

