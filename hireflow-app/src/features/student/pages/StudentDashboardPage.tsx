import * as React from 'react'
import { Link } from 'react-router-dom'
import { useStudentDashboard, useStudentDrives, useStudentInterviews } from '../api/studentApi'
import { StudentDossierBanner } from '../components/StudentDossierBanner'
import { DriveCard } from '../components/DriveCard'
import { ApplicationConfirmationModal } from '../components/ApplicationConfirmationModal'
import { ApplicationStatusBadge } from '@/components/domain/application-status-badge'
import { PageHeading } from '@/components/domain/page-heading'
import { Button } from '@/components/ui/button'
import { LoadingSkeleton } from '@/components/domain/loading-skeleton'
import { ErrorState } from '@/components/domain/error-state'
import type { StudentDriveItemDto } from '@/types/api'
import {
  Briefcase,
  Sparkles,
  CalendarDays,
  ArrowRight,
} from 'lucide-react'
import { formatLocalDateTime } from '@/lib/dateUtils'

export const StudentDashboardPage: React.FC = () => {
  const { data: dashboard, isLoading: isDashboardLoading, error: dashboardError, refetch: refetchDashboard } = useStudentDashboard()
  const { data: drives, error: drivesError, refetch: refetchDrives } = useStudentDrives()
  const { data: interviews, error: interviewsError, refetch: refetchInterviews } = useStudentInterviews()

  const [selectedDriveForApply, setSelectedDriveForApply] = React.useState<StudentDriveItemDto | null>(null)
  const [isApplyModalOpen, setIsApplyModalOpen] = React.useState(false)

  if (isDashboardLoading) {
    return (
      <div className="space-y-6">
        <PageHeading
          title="Candidate Dashboard"
          description="Retrieving verified academic dossier and real-time recruitment updates..."
        />
        <LoadingSkeleton rows={3} />
      </div>
    )
  }

  if (dashboardError || !dashboard) {
    return (
      <div className="space-y-6">
        <PageHeading title="Candidate Dashboard" description="University Placement Portal" />
        <ErrorState
          title="Failed to Load Academic Dashboard"
          message={dashboardError instanceof Error ? dashboardError.message : 'Unable to connect to placement server.'}
          onRetry={refetchDashboard}
        />
      </div>
    )
  }

  const { profile, totalEligibleDrives, totalApplications, shortlistedCount, recentApplications } = dashboard
  const confirmedInterviewsCount = interviews ? interviews.length : '—'
  const upcomingInterview = interviews && interviews.length > 0 ? interviews[0] : null

  // Eligible drives ready for immediate application
  const actionableDrives = (drives || []).filter((d) => d.canApply).slice(0, 3)

  const handleApplyClick = (drive: StudentDriveItemDto) => {
    setSelectedDriveForApply(drive)
    setIsApplyModalOpen(true)
  }

  return (
    <div className="space-y-6">
      {/* Page Title & Breadcrumbs */}
      <PageHeading
        title="Student Opportunity Hub"
        description="Official recruitment portal for verified university candidates. Review eligibility, submit applications, and track confirmed interviews."
        breadcrumbs={[{ label: 'Portal Home', href: '/student/dashboard' }, { label: 'Dashboard' }]}
        actions={
          <div className="flex flex-wrap items-center gap-2">
            <Button variant="outline" size="sm" onClick={() => { void refetchDashboard(); void refetchDrives(); void refetchInterviews() }}>Refresh live data</Button>
          </div>
        }
      />

      <p className="text-xs text-muted-foreground">Live updates refresh every 30 seconds while this page is open, and when you return to the tab.</p>

      {(drivesError || interviewsError) && (
        <div role="alert" className="rounded-md border border-amber-300 bg-amber-50 p-3 text-sm text-amber-950">
          Some live information could not be loaded. Counts marked with a dash are unavailable. Use Refresh live data to try again.
        </div>
      )}

      {/* Verified Academic Profile Banner */}
      <StudentDossierBanner profile={profile} />

      {/* One divided overview keeps real counts together without decorative cards. */}
      <section aria-label="Placement Overview Metrics" className="grid grid-cols-2 overflow-hidden rounded-lg border border-border bg-card lg:grid-cols-4">
        {[
          { label: 'Eligible drives', value: totalEligibleDrives, detail: 'Matching your branch and CGPA' },
          { label: 'Applications', value: totalApplications, detail: 'Submitted applications' },
          { label: 'Shortlisted', value: shortlistedCount, detail: 'Selected for interview rounds' },
          { label: 'Interviews', value: confirmedInterviewsCount, detail: 'Confirmed appointments' },
        ].map((metric, index) => (
          <div key={metric.label} className={`min-w-0 p-4 sm:p-5 ${index % 2 === 1 ? 'border-l border-border' : ''} ${index >= 2 ? 'border-t border-border lg:border-t-0' : ''} ${index === 2 ? 'lg:border-l' : ''}`}>
            <span className="block text-xs font-medium text-muted-foreground">{metric.label}</span>
            <span className="mt-2 block text-3xl font-medium tabular-nums text-foreground">{metric.value}</span>
            <span className="mt-1 block text-xs leading-snug text-muted-foreground">{metric.detail}</span>
          </div>
        ))}
      </section>

      {/* Actionable Next Step Alert Banner */}
      {upcomingInterview ? (
        <div className="rounded-lg border border-teal-700/20 bg-teal-900/5 p-4 sm:p-5 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div className="flex items-start sm:items-center gap-3">
            <div className="h-9 w-9 rounded-sm bg-teal-900/10 text-teal-800 flex items-center justify-center shrink-0">
              <CalendarDays className="h-5 w-5" aria-hidden="true" />
            </div>
            <div>
              <div className="flex flex-wrap items-center gap-x-2 gap-y-0.5">
                <span className="text-xs uppercase font-bold tracking-wider text-teal-800">
                  Confirmed Interview Session
                </span>
                <span className="font-mono text-[11px] text-muted-foreground whitespace-nowrap shrink-0">
                  ({upcomingInterview.sessionId})
                </span>
              </div>
              <p className="text-sm font-semibold text-foreground mt-0.5">
                {upcomingInterview.companyName} — {upcomingInterview.jobTitle}
              </p>
              <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-muted-foreground mt-1">
                <span>Room: <strong className="text-foreground">{upcomingInterview.roomName}</strong></span>
                <span>Interviewer: <strong className="text-foreground">{upcomingInterview.interviewerName}</strong></span>
                <span className="font-mono text-teal-800 font-medium">
                  {new Date(upcomingInterview.startTime).toLocaleString('en-US', {
                    month: 'short',
                    day: 'numeric',
                    hour: 'numeric',
                    minute: '2-digit',
                  })}
                </span>
              </div>
            </div>
          </div>
          <Link to={`/student/interviews/${upcomingInterview.sessionId}`} className="shrink-0">
            <Button variant="default" size="sm" className="text-xs gap-1.5 h-8">
              <span>View Appointment</span>
              <ArrowRight className="h-3.5 w-3.5" aria-hidden="true" />
            </Button>
          </Link>
        </div>
      ) : actionableDrives.length > 0 ? (
        <div className="rounded-lg border border-border bg-card p-4 sm:p-5 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div className="flex items-start sm:items-center gap-3">
            <div className="h-9 w-9 rounded-sm bg-primary/10 text-primary flex items-center justify-center shrink-0">
              <Sparkles className="h-5 w-5 text-accent" aria-hidden="true" />
            </div>
            <div>
              <span className="text-xs uppercase font-bold tracking-wider text-primary">
                Action Required
              </span>
              <p className="text-sm font-semibold text-foreground mt-0.5">
                {actionableDrives.length} recruitment drive{actionableDrives.length > 1 ? 's' : ''} currently open matching your academic criteria.
              </p>
              <p className="text-xs text-muted-foreground mt-0.5">
                Review verified requirements and submit before application windows close.
              </p>
            </div>
          </div>
          <Link to="/student/drives" className="shrink-0">
            <Button variant="default" size="sm" className="text-xs gap-1.5 h-8">
              <span>Explore Opportunities</span>
              <ArrowRight className="h-3.5 w-3.5" aria-hidden="true" />
            </Button>
          </Link>
        </div>
      ) : null}

      {/* Main Content Grid: Actionable Opportunities & Recent Applications */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Left Column (2 Cols): Open Opportunities Preview */}
        <section aria-labelledby="heading-open-drives" className="lg:col-span-2 space-y-4">
          <div className="flex items-center justify-between">
            <div>
              <h2 id="heading-open-drives" className="font-serif font-bold text-lg text-foreground">
                Actionable Recruitment Opportunities
              </h2>
              <p className="text-xs text-muted-foreground">
                Positions where your academic profile fulfills all departmental and CGPA criteria.
              </p>
            </div>
            <Link
              to="/student/drives"
              className="text-xs font-semibold text-primary hover:text-primary/80 inline-flex items-center gap-1"
            >
              <span>View All ({drives?.length || 0})</span>
              <ArrowRight className="h-3 w-3" aria-hidden="true" />
            </Link>
          </div>

          {actionableDrives.length > 0 ? (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {actionableDrives.map((drive) => (
                <DriveCard
                  key={drive.id}
                  drive={drive}
                  studentCgpa={profile.cgpa}
                  onApplyClick={handleApplyClick}
                />
              ))}
            </div>
          ) : (
            <div className="rounded-lg border border-border bg-card p-6 text-center space-y-2">
              <Briefcase className="h-6 w-6 text-muted-foreground mx-auto" aria-hidden="true" />
              <p className="text-sm font-semibold text-foreground">No Pending Actionable Drives</p>
              <p className="text-xs text-muted-foreground max-w-sm mx-auto">
                {drivesError ? 'Opportunities could not be loaded right now. Refresh live data to try again.' : 'You have applied to all currently open eligible drives or new drives are being scheduled by the Placement Cell.'}
              </p>
              <div className="pt-2">
                <Link to="/student/drives">
                  <Button variant="outline" size="sm" className="text-xs">
                    Browse All Catalog Drives
                  </Button>
                </Link>
              </div>
            </div>
          )}
        </section>

        {/* Right Column (1 Col): Recent Applications Activity */}
        <section aria-labelledby="heading-recent-apps" className="space-y-4">
          <div className="flex items-center justify-between">
            <div>
              <h2 id="heading-recent-apps" className="font-serif font-bold text-lg text-foreground">
                Recent Applications
              </h2>
              <p className="text-xs text-muted-foreground">
                Submission status and recruitment outcomes.
              </p>
            </div>
            <Link
              to="/student/applications"
              className="text-xs font-semibold text-primary hover:text-primary/80 inline-flex items-center gap-1"
            >
              <span>History</span>
              <ArrowRight className="h-3 w-3" aria-hidden="true" />
            </Link>
          </div>

          <div className="rounded-lg border border-border bg-card divide-y divide-border/60 shadow-xs">
            {recentApplications.length > 0 ? (
              recentApplications.slice(0, 5).map((app) => (
                <Link
                  key={app.id}
                  to={`/student/applications/${app.id}`}
                  className="p-3.5 flex flex-col gap-2 hover:bg-muted/40 transition-colors block group"
                >
                  <div className="flex items-start justify-between gap-2">
                    <div>
                      <span className="font-semibold text-xs text-foreground group-hover:text-primary transition-colors block">
                        {app.companyName}
                      </span>
                      <span className="text-[11px] text-muted-foreground line-clamp-1">
                        {app.jobTitle}
                      </span>
                    </div>
                    <ApplicationStatusBadge status={app.status} />
                  </div>
                  <div className="flex items-center justify-between text-[11px] text-muted-foreground font-mono pt-1">
                    <span>{app.id}</span>
                    <span>
                      {formatLocalDateTime(app.appliedAt, {
                        month: 'short',
                        day: 'numeric',
                      })}
                    </span>
                  </div>
                </Link>
              ))
            ) : (
              <div className="p-6 text-center text-xs text-muted-foreground">
                No applications submitted yet. Browse active recruitment drives to get started.
              </div>
            )}
          </div>
        </section>
      </div>

      {/* Application Confirmation Modal */}
      <ApplicationConfirmationModal
        isOpen={isApplyModalOpen}
        onClose={() => setIsApplyModalOpen(false)}
        drive={selectedDriveForApply}
        studentProfile={{
          studentId: profile.id,
          name: profile.name,
          branch: profile.branch,
          cgpa: profile.cgpa,
        }}
        onSuccess={() => {
          refetchDashboard()
        }}
      />
    </div>
  )
}
