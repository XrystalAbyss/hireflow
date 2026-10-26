import * as React from 'react'
import type { StudentDriveEligibilityDto } from '@/types/api'
import { EligibilityStatusBadge } from '@/components/domain/eligibility-status-badge'
import { Check, X, AlertTriangle, HelpCircle, ShieldCheck } from 'lucide-react'

export interface EligibilityBreakdownCardProps {
  eligibility: StudentDriveEligibilityDto
  className?: string
}

export const EligibilityBreakdownCard: React.FC<EligibilityBreakdownCardProps> = ({
  eligibility,
  className = '',
}) => {
  const cgpaMet = eligibility.studentCgpa >= eligibility.minimumCgpa
  const branchMet = eligibility.eligibleBranches.some(
    (b) => b.trim().toLowerCase() === eligibility.studentBranch.trim().toLowerCase()
  )
  const deadlineMet = !eligibility.deadlinePassed && !eligibility.closed

  return (
    <div
      className={`rounded-lg border border-border bg-card p-5 sm:p-6 shadow-xs space-y-5 ${className}`}
      aria-label="Itemized Eligibility Breakdown"
    >
      {/* Header with Title and Overall Determination Badge */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-border/80 pb-4">
        <div>
          <div className="flex items-center gap-2">
            <ShieldCheck className="h-4 w-4 text-accent" aria-hidden="true" />
            <h3 className="font-serif font-bold text-base text-foreground">
              Institutional Eligibility Evaluation
            </h3>
          </div>
          <p className="text-xs text-muted-foreground mt-0.5">
            Authoritative criteria evaluated against your official academic dossier.
          </p>
        </div>
        <div className="shrink-0">
          <EligibilityStatusBadge
            canApply={eligibility.canApply}
            hasApplied={eligibility.alreadyApplied}
            closed={eligibility.closed}
            deadlinePassed={eligibility.deadlinePassed}
            academicallyEligible={eligibility.academicallyEligible}
          />
        </div>
      </div>

      {/* Criteria Comparison Grid */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-3.5">
        {/* CGPA Criterion */}
        <div
          className={`p-3.5 rounded-md border text-xs flex flex-col justify-between ${
            cgpaMet
              ? 'bg-teal-900/5 border-teal-700/20 text-foreground'
              : 'bg-destructive/5 border-destructive/20 text-foreground'
          }`}
        >
          <div className="flex items-center justify-between font-semibold mb-1.5">
            <span className="uppercase tracking-wider text-muted-foreground text-xs">Academic Cutoff</span>
            {cgpaMet ? (
              <span className="flex items-center gap-1 text-teal-800 font-bold text-xs">
                <Check className="h-3.5 w-3.5" aria-hidden="true" /> Met
              </span>
            ) : (
              <span className="flex items-center gap-1 text-destructive font-bold text-xs">
                <X className="h-3.5 w-3.5" aria-hidden="true" /> Unmet
              </span>
            )}
          </div>
          <div className="space-y-1">
            <div className="flex justify-between items-center text-xs">
              <span className="text-muted-foreground">Required:</span>
              <span className="font-mono font-medium">{eligibility.minimumCgpa.toFixed(2)} CGPA</span>
            </div>
            <div className="flex justify-between items-center text-xs">
              <span className="text-muted-foreground">Your Record:</span>
              <span className="font-mono font-bold text-primary">{eligibility.studentCgpa.toFixed(2)} CGPA</span>
            </div>
          </div>
        </div>

        {/* Branch Criterion */}
        <div
          className={`p-3.5 rounded-md border text-xs flex flex-col justify-between ${
            branchMet
              ? 'bg-teal-900/5 border-teal-700/20 text-foreground'
              : 'bg-destructive/5 border-destructive/20 text-foreground'
          }`}
        >
          <div className="flex items-center justify-between font-semibold mb-1.5">
            <span className="uppercase tracking-wider text-muted-foreground text-xs">Department Cutoff</span>
            {branchMet ? (
              <span className="flex items-center gap-1 text-teal-800 font-bold text-xs">
                <Check className="h-3.5 w-3.5" aria-hidden="true" /> Met
              </span>
            ) : (
              <span className="flex items-center gap-1 text-destructive font-bold text-xs">
                <X className="h-3.5 w-3.5" aria-hidden="true" /> Unmet
              </span>
            )}
          </div>
          <div className="space-y-1">
            <div className="flex justify-between items-center text-xs">
              <span className="text-muted-foreground">Candidate Branch:</span>
              <span className="font-medium text-primary truncate max-w-[140px]" title={eligibility.studentBranch}>
                {eligibility.studentBranch}
              </span>
            </div>
            <div className="text-muted-foreground text-xs line-clamp-1" title={Array.from(eligibility.eligibleBranches).join(', ')}>
              Allowed: {Array.from(eligibility.eligibleBranches).join(', ')}
            </div>
          </div>
        </div>

        {/* Deadline & Drive Status */}
        <div
          className={`p-3.5 rounded-md border text-xs flex flex-col justify-between ${
            deadlineMet
              ? 'bg-teal-900/5 border-teal-700/20 text-foreground'
              : 'bg-destructive/5 border-destructive/20 text-foreground'
          }`}
        >
          <div className="flex items-center justify-between font-semibold mb-1.5">
            <span className="uppercase tracking-wider text-muted-foreground text-xs">Drive Window</span>
            {deadlineMet ? (
              <span className="flex items-center gap-1 text-teal-800 font-bold text-xs">
                <Check className="h-3.5 w-3.5" aria-hidden="true" /> Open
              </span>
            ) : (
              <span className="flex items-center gap-1 text-destructive font-bold text-xs">
                <X className="h-3.5 w-3.5" aria-hidden="true" /> Closed
              </span>
            )}
          </div>
          <div className="space-y-1">
            <div className="flex justify-between items-center text-xs">
              <span className="text-muted-foreground">Deadline:</span>
              <span className="font-mono font-medium">{eligibility.applicationDeadline}</span>
            </div>
            <div className="flex justify-between items-center text-xs">
              <span className="text-muted-foreground">Status:</span>
              <span className="font-medium">
                {eligibility.closed ? 'Drive Terminated' : eligibility.deadlinePassed ? 'Deadline Expired' : 'Accepting Submissions'}
              </span>
            </div>
          </div>
        </div>
      </div>

      {/* Disqualification / Notice Reasons List */}
      {!eligibility.canApply && eligibility.reasons && eligibility.reasons.length > 0 && (
        <div
          className="p-3.5 rounded-md bg-destructive/5 border border-destructive/20 space-y-2 text-xs"
          role="alert"
        >
          <div className="flex items-center gap-2 font-semibold text-destructive">
            <AlertTriangle className="h-4 w-4 shrink-0" aria-hidden="true" />
            <span>Application Blocked — Disqualification Explanations</span>
          </div>
          <ul className="list-disc list-inside space-y-1 text-muted-foreground">
            {eligibility.reasons.map((reason, idx) => (
              <li key={idx} className="leading-relaxed">
                {reason}
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* Existing Application Notice */}
      {eligibility.alreadyApplied && (
        <div className="p-3.5 rounded-md bg-primary/5 border border-primary/20 flex items-center justify-between text-xs">
          <div className="space-y-0.5">
            <span className="font-semibold text-primary block">
              Application on Record
            </span>
            <span className="text-muted-foreground">
              Reference ID:{' '}
              <span className="font-mono font-bold text-foreground">
                {eligibility.existingApplicationId || 'Registered'}
              </span>
            </span>
          </div>
          <span className="font-mono text-xs px-2.5 py-1 rounded bg-secondary text-primary font-semibold border border-border">
            Status: {eligibility.existingApplicationStatus || 'APPLIED'}
          </span>
        </div>
      )}

      {/* Institutional Explanatory Footnote */}
      <div className="flex items-start gap-2 pt-1 text-xs text-muted-foreground border-t border-border/60">
        <HelpCircle className="h-3.5 w-3.5 shrink-0 text-muted-foreground mt-0.5" aria-hidden="true" />
        <p className="leading-relaxed">
          Eligibility evaluations are executed synchronously by the HireFlow backend rules engine against official university registrar records. If your CGPA or department affiliation requires correction, contact the Central Placement Office.
        </p>
      </div>
    </div>
  )
}
