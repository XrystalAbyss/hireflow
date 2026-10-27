import * as React from 'react'
import { useParams, Link } from 'react-router-dom'
import { useApplicationDetail } from '../api/studentApi'
import { ApplicationStatusBadge } from '@/components/domain/application-status-badge'
import { PageHeading } from '@/components/domain/page-heading'
import { Button } from '@/components/ui/button'
import { LoadingSkeleton } from '@/components/domain/loading-skeleton'
import { ErrorState } from '@/components/domain/error-state'
import {
  Briefcase,
  ArrowLeft,
} from 'lucide-react'
import { formatLocalDateTime } from '@/lib/dateUtils'

export const StudentApplicationDetailPage: React.FC = () => {
  const { id } = useParams<{ id: string }>()
  const { data: app, isLoading, error, refetch } = useApplicationDetail(id)

  if (isLoading) {
    return (
      <div className="space-y-6">
        <PageHeading
          title="Application Record"
          description="Retrieving authoritative candidate application data..."
        />
        <LoadingSkeleton rows={3} />
      </div>
    )
  }

  if (error || !app) {
    return (
      <div className="space-y-6">
        <div className="flex items-center gap-3">
          <Link
            to="/student/applications"
            className="inline-flex items-center gap-1.5 text-xs font-medium text-muted-foreground hover:text-foreground transition-colors p-1 rounded"
          >
            <ArrowLeft className="h-3.5 w-3.5" aria-hidden="true" />
            <span>Back to Applications</span>
          </Link>
        </div>
        <ErrorState
          title="Application Record Not Found"
          message={
            error instanceof Error
              ? error.message
              : 'The requested application could not be retrieved. Enforced ownership checks prevent access to unauthorized records.'
          }
          onRetry={refetch}
        />
      </div>
    )
  }

  // Authoritative status description for all 7 ApplicationStatus values
  const getStatusDescriptor = (status: string) => {
    switch (status.toUpperCase()) {
      case 'APPLIED':
        return {
          title: 'Application Formally Received & Queued for Screening',
          description:
            'Your candidate record has been authoritatively received by the Central Placement Office. Departmental coordinators and corporate recruiters review submissions in scheduled evaluation cycles.',
          badgeColor: 'text-primary',
          bgColor: 'border-primary/20 bg-primary/5',
        }
      case 'ELIGIBLE':
        return {
          title: 'Academic Eligibility Verified',
          description:
            'Your departmental CGPA and degree branch criteria have been validated against corporate drive prerequisites. The application is eligible for candidate shortlisting.',
          badgeColor: 'text-teal-800',
          bgColor: 'border-teal-700/20 bg-teal-900/5',
        }
      case 'INELIGIBLE':
        return {
          title: 'Prerequisite Criteria Unmet',
          description:
            'The academic prerequisites (minimum CGPA or eligible department) were not met for this specific recruitment drive at the time of evaluation.',
          badgeColor: 'text-destructive',
          bgColor: 'border-destructive/20 bg-destructive/5',
        }
      case 'SHORTLISTED':
        return {
          title: 'Candidate Profile Shortlisted for Technical Rounds',
          description:
            'The recruiting committee has reviewed your credentials and shortlisted your profile for subsequent evaluation stages.',
          badgeColor: 'text-teal-800',
          bgColor: 'border-teal-700/20 bg-teal-900/5',
        }
      case 'SCHEDULED':
        return {
          title: 'Interview Appointment Confirmed & Published',
          description:
            'An official interview session has been published by the Placement Officer. Venue room assignments, designated interviewers, and scheduled time windows are confirmed on your timetable.',
          badgeColor: 'text-teal-800',
          bgColor: 'border-teal-700/20 bg-teal-900/5',
        }
      case 'REJECTED':
        return {
          title: 'Recruitment Evaluation Concluded',
          description:
            'Corporate recruiters have completed the evaluation round for this placement drive. While this candidacy will not proceed further, your verified dossier remains active for upcoming opportunities.',
          badgeColor: 'text-destructive',
          bgColor: 'border-destructive/20 bg-destructive/5',
        }
      case 'SELECTED':
        return {
          title: 'Candidate Selected / Placement Offer Extended',
          description:
            'Congratulations! Official corporate selection has been recorded. Formal placement offer documentation and joining prerequisites will be coordinated through Career Services.',
          badgeColor: 'text-teal-800',
          bgColor: 'border-teal-700/20 bg-teal-900/5',
        }
      default:
        return {
          title: `Status: ${status}`,
          description: 'Official application record is maintained by the university placement system.',
          badgeColor: 'text-foreground',
          bgColor: 'border-border bg-muted/20',
        }
    }
  }

  const descriptor = getStatusDescriptor(app.status)

  return (
    <div className="space-y-6">
      {/* Navigation Breadcrumbs */}
      <div className="flex items-center gap-3">
        <Link
          to="/student/applications"
          className="inline-flex items-center gap-1.5 text-xs font-medium text-muted-foreground hover:text-foreground transition-colors p-1 rounded"
        >
          <ArrowLeft className="h-3.5 w-3.5" aria-hidden="true" />
          <span>Back to Applications Directory</span>
        </Link>
      </div>

      <PageHeading
        title={`Application ${app.id}`}
        description={`Formal placement record for ${app.jobTitle} at ${app.companyName}.`}
        breadcrumbs={[
          { label: 'Portal Home', href: '/student/dashboard' },
          { label: 'My Applications', href: '/student/applications' },
          { label: app.id },
        ]}
        actions={
          <div className="flex items-center gap-2">
            <Link to={`/student/drives/${app.driveId}`}>
              <Button variant="outline" size="sm" className="text-xs gap-1.5 h-8">
                <Briefcase className="h-3.5 w-3.5 text-accent" aria-hidden="true" />
                <span>View Drive Requirements</span>
              </Button>
            </Link>
          </div>
        }
      />

      {/* Primary Detail Card */}
      <div className="rounded-lg border border-border bg-card p-6 shadow-xs space-y-6">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-border/80">
          <div>
            <span className="text-xs uppercase tracking-wider text-muted-foreground block font-sans">
              Recruiter & Role
            </span>
            <h2 className="font-serif font-bold text-xl text-foreground mt-0.5">
              {app.jobTitle}
            </h2>
            <p className="text-sm font-semibold text-primary mt-0.5">
              {app.companyName}
            </p>
          </div>

          <div className="flex items-center gap-3">
            <ApplicationStatusBadge status={app.status} className="text-xs px-3 py-1" />
          </div>
        </div>

        {/* Real DTO Metadata Grid */}
        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-4 text-xs">
          <div className="p-3.5 rounded-md bg-muted/40 border border-border/60 space-y-1">
            <span className="text-[11px] uppercase tracking-wider text-muted-foreground block">
              Application ID
            </span>
            <span className="font-mono font-bold text-foreground block text-sm">
              {app.id}
            </span>
          </div>

          <div className="p-3.5 rounded-md bg-muted/40 border border-border/60 space-y-1">
            <span className="text-[11px] uppercase tracking-wider text-muted-foreground block">
              Drive Reference
            </span>
            <span className="font-mono font-bold text-primary block text-sm">
              {app.driveId}
            </span>
          </div>

          <div className="p-3.5 rounded-md bg-muted/40 border border-border/60 space-y-1">
            <span className="text-[11px] uppercase tracking-wider text-muted-foreground block">
              Candidate Roll No
            </span>
            <span className="font-mono font-bold text-foreground block text-sm">
              {app.studentId}
            </span>
          </div>

          <div className="p-3.5 rounded-md bg-muted/40 border border-border/60 space-y-1">
            <span className="text-[11px] uppercase tracking-wider text-muted-foreground block">
              Submitted Timestamp
            </span>
            <span className="font-mono text-foreground block text-xs">
              {formatLocalDateTime(app.appliedAt)}
            </span>
          </div>
        </div>

        {/* Official Placement Office Remarks */}
        {app.remarks && (
          <div className="p-4 rounded-md bg-muted/30 border border-border space-y-1 text-xs">
            <span className="text-[11px] uppercase tracking-wider font-semibold text-muted-foreground block">
              Placement Cell Operational Notes
            </span>
            <p className="text-foreground leading-relaxed font-sans">{app.remarks}</p>
          </div>
        )}
      </div>

      {/* Authoritative Application Lifecycle & Status Overview */}
      <div className="rounded-lg border border-border bg-card p-6 shadow-xs space-y-5">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-border/80 pb-3">
          <div>
            <h3 className="font-serif font-bold text-base text-foreground">
              Application Lifecycle & Review Status
            </h3>
            <p className="text-xs text-muted-foreground mt-0.5">
              Deterministic evaluation record synchronized with Spring Boot transactional database state.
            </p>
          </div>
          <ApplicationStatusBadge status={app.status} className="self-start sm:self-auto" />
        </div>

        {/* Current Status Explanation Banner */}
        <div className={`p-4 rounded-md border text-xs space-y-1.5 ${descriptor.bgColor}`}>
          <div className="flex items-center gap-2">
            <span className="font-semibold text-xs sm:text-sm text-foreground">
              {descriptor.title}
            </span>
          </div>
          <p className="text-muted-foreground leading-relaxed">
            {descriptor.description}
          </p>
        </div>

        {/* Recorded Evaluation Milestones */}
        <div className="space-y-3 pt-2">
          <span className="text-xs uppercase tracking-wider font-semibold text-muted-foreground block">
            Verified Audit Milestones
          </span>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-3.5">
            <div className="p-3.5 rounded-md border border-border/80 bg-muted/20 text-xs space-y-1">
              <span className="text-[11px] uppercase tracking-wider text-muted-foreground block">
                01. Registration Record
              </span>
              <p className="font-semibold text-foreground">Application Formally Submitted</p>
              <p className="font-mono text-[11px] text-muted-foreground">
                Timestamp: {formatLocalDateTime(app.appliedAt)}
              </p>
            </div>

            <div className="p-3.5 rounded-md border border-border/80 bg-muted/20 text-xs space-y-1">
              <span className="text-[11px] uppercase tracking-wider text-muted-foreground block">
                02. Academic Evaluation
              </span>
              <p className="font-semibold text-foreground">Departmental & CGPA Prerequisite Check</p>
              <p className="text-[11px] text-muted-foreground">
                Evaluated against drive criteria at submission time
              </p>
            </div>

            <div className="p-3.5 rounded-md border border-border/80 bg-muted/20 text-xs space-y-1">
              <span className="text-[11px] uppercase tracking-wider text-muted-foreground block">
                03. Placement Office Decision
              </span>
              <p className="font-semibold text-foreground">
                Current Status: <strong className="font-mono text-primary">{app.status}</strong>
              </p>
              <p className="text-[11px] text-muted-foreground line-clamp-2">
                {app.remarks || 'Standard placement workflow determination'}
              </p>
            </div>
          </div>
        </div>

        {/* Institutional Non-Linear Workflow Notice */}
        <p className="text-[11px] text-muted-foreground border-t border-border/60 pt-3 leading-relaxed">
          Institutional Governance Notice: Recruitment evaluation cycles are determined authoritatively by individual corporate partner committees and Placement Cell operational guidelines. Candidates do not transition through an assumed uniform linear pipeline.
        </p>
      </div>
    </div>
  )
}
