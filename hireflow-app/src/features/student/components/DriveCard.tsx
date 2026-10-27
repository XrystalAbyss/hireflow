import * as React from 'react'
import { Link } from 'react-router-dom'
import type { StudentDriveItemDto } from '@/types/api'
import { EligibilityStatusBadge } from '@/components/domain/eligibility-status-badge'
import { Button } from '@/components/ui/button'
import {
  Building2,
  Calendar,
  GraduationCap,
  ArrowRight,
  CheckCircle2,
  FileCheck2,
} from 'lucide-react'

export interface DriveCardProps {
  drive: StudentDriveItemDto
  onApplyClick?: (drive: StudentDriveItemDto) => void
  studentCgpa?: number
}

export const DriveCard: React.FC<DriveCardProps> = ({
  drive,
  onApplyClick,
  studentCgpa,
}) => {
  const isCgpaMet = studentCgpa !== undefined ? studentCgpa >= drive.minimumCgpa : drive.academicallyEligible

  return (
    <article
      className="rounded-lg border border-border bg-card p-5 sm:p-6 shadow-xs hover:border-accent/40 transition-all flex flex-col justify-between space-y-4 group"
      aria-labelledby={`drive-title-${drive.id}`}
    >
      <div className="space-y-3">
        {/* Header: Company & Eligibility Badge */}
        <div className="flex items-start justify-between gap-2.5">
          <div className="flex items-start gap-2 min-w-0 flex-1">
            <div className="h-8 w-8 rounded-sm bg-muted flex items-center justify-center text-muted-foreground group-hover:bg-primary/10 group-hover:text-primary transition-colors shrink-0 mt-0.5">
              <Building2 className="h-4 w-4" aria-hidden="true" />
            </div>
            <div className="min-w-0 flex-1">
              <span className="font-semibold text-xs text-foreground uppercase tracking-wider block leading-tight line-clamp-1" title={drive.companyName}>
                {drive.companyName}
              </span>
              <span className="font-mono text-[11px] text-muted-foreground block mt-0.5">
                {drive.id}
              </span>
            </div>
          </div>
          <div className="shrink-0">
            <EligibilityStatusBadge
              canApply={drive.canApply}
              hasApplied={drive.hasApplied}
              closed={drive.closed}
              deadlinePassed={drive.deadlinePassed}
              academicallyEligible={drive.academicallyEligible}
            />
          </div>
        </div>

        {/* Position Title */}
        <div>
          <h3
            id={`drive-title-${drive.id}`}
            className="font-serif font-bold text-lg text-foreground group-hover:text-primary transition-colors leading-snug"
          >
            {drive.jobTitle}
          </h3>
        </div>

        {/* Requirements & Criteria */}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 text-xs pt-1 border-t border-border/60">
          <div className="flex items-center gap-1.5 text-muted-foreground">
            <GraduationCap className="h-3.5 w-3.5 shrink-0 text-accent" aria-hidden="true" />
            <span>Min CGPA:</span>
            <span
              className={`font-mono font-bold ${
                isCgpaMet ? 'text-teal-800' : 'text-destructive'
              }`}
            >
              {drive.minimumCgpa.toFixed(2)}
            </span>
          </div>

          <div className="flex items-center gap-1.5 text-muted-foreground">
            <Calendar className="h-3.5 w-3.5 shrink-0 text-brass-600" aria-hidden="true" />
            <span>Deadline:</span>
            <span
              className={`font-mono ${
                drive.deadlinePassed ? 'text-destructive font-semibold' : 'text-foreground'
              }`}
            >
              {drive.applicationDeadline}
            </span>
          </div>
        </div>

        {/* Eligible Branches */}
        <div className="text-xs text-muted-foreground space-y-1">
          <span className="text-[11px] uppercase tracking-wider text-muted-foreground block">
            Eligible Departments
          </span>
          <p className="line-clamp-1 font-medium text-foreground text-xs" title={Array.from(drive.eligibleBranches).join(', ')}>
            {Array.from(drive.eligibleBranches).join(', ')}
          </p>
        </div>
      </div>

      {/* Footer / Actions */}
      <div className="pt-3 border-t border-border flex flex-wrap items-center justify-between gap-2">
        <Link
          to={`/student/drives/${drive.id}`}
          className="text-xs font-semibold text-primary hover:text-primary/80 inline-flex items-center gap-1 py-1 focus:outline-none focus:ring-1 focus:ring-ring rounded"
        >
          <span>Drive Details & Criteria</span>
          <ArrowRight className="h-3.5 w-3.5" aria-hidden="true" />
        </Link>

        <div>
          {drive.hasApplied ? (
            <Link to={`/student/applications/${drive.existingApplicationId || ''}`}>
              <Button
                type="button"
                variant="outline"
                size="sm"
                className="text-xs gap-1.5 h-8 font-medium"
              >
                <CheckCircle2 className="h-3.5 w-3.5 text-teal-800" aria-hidden="true" />
                <span>View Application</span>
              </Button>
            </Link>
          ) : drive.canApply ? (
            <Button
              type="button"
              variant="default"
              size="sm"
              onClick={() => onApplyClick && onApplyClick(drive)}
              className="text-xs gap-1.5 h-8 font-medium"
            >
              <FileCheck2 className="h-3.5 w-3.5" aria-hidden="true" />
              <span>Apply Now</span>
            </Button>
          ) : (
            <Link to={`/student/drives/${drive.id}`}>
              <Button
                type="button"
                variant="secondary"
                size="sm"
                className="text-xs gap-1.5 h-8 text-muted-foreground hover:text-foreground font-medium"
              >
                <span>Inspect Eligibility</span>
              </Button>
            </Link>
          )}
        </div>
      </div>
    </article>
  )
}
