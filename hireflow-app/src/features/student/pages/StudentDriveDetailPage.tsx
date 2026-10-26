import * as React from 'react'
import { useParams, Link } from 'react-router-dom'
import {
  useDriveEligibility,
  useStudentDrives,
  useStudentProfile,
} from '../api/studentApi'
import { EligibilityBreakdownCard } from '../components/EligibilityBreakdownCard'
import { ApplicationConfirmationModal } from '../components/ApplicationConfirmationModal'
import { PageHeading } from '@/components/domain/page-heading'
import { Button } from '@/components/ui/button'
import { LoadingSkeleton } from '@/components/domain/loading-skeleton'
import { ErrorState } from '@/components/domain/error-state'
import {
  Building2,
  Calendar,
  ArrowLeft,
  FileCheck2,
  CheckCircle2,
  Lock,
} from 'lucide-react'

export const StudentDriveDetailPage: React.FC = () => {
  const { id } = useParams<{ id: string }>()

  const { data: drives, isLoading: isDrivesLoading } = useStudentDrives()
  const { data: profile } = useStudentProfile()
  const {
    data: eligibility,
    isLoading: isEligibilityLoading,
    error: eligibilityError,
    refetch: refetchEligibility,
  } = useDriveEligibility(id)

  const [isApplyModalOpen, setIsApplyModalOpen] = React.useState(false)

  const drive = React.useMemo(() => {
    if (!drives || !id) return null
    return drives.find((d) => d.id.toUpperCase() === id.toUpperCase()) || null
  }, [drives, id])

  if (isDrivesLoading || isEligibilityLoading) {
    return (
      <div className="space-y-6">
        <PageHeading
          title="Recruitment Drive Evaluation"
          description="Retrieving drive parameters and evaluating student academic eligibility..."
        />
        <LoadingSkeleton rows={3} />
      </div>
    )
  }

  if (eligibilityError || !eligibility) {
    return (
      <div className="space-y-6">
        <PageHeading title="Drive Criteria" description="University Placement Portal" />
        <ErrorState
          title="Drive Eligibility Could Not Be Evaluated"
          message={
            eligibilityError instanceof Error
              ? eligibilityError.message
              : 'Failed to retrieve authoritative eligibility verdict for this drive.'
          }
          onRetry={refetchEligibility}
        />
      </div>
    )
  }

  const companyName = drive?.companyName || 'Corporate Recruiter'
  const jobTitle = drive?.jobTitle || 'Recruitment Opportunity'
  const isCgpaMet = eligibility.studentCgpa >= eligibility.minimumCgpa

  return (
    <div className="space-y-6">
      {/* Navigation Breadcrumbs & Back Button */}
      <div className="flex items-center gap-3">
        <Link
          to="/student/drives"
          className="inline-flex items-center gap-1.5 text-xs font-medium text-muted-foreground hover:text-foreground transition-colors p-1 rounded"
        >
          <ArrowLeft className="h-3.5 w-3.5" aria-hidden="true" />
          <span>Back to Opportunities Catalog</span>
        </Link>
      </div>

      <PageHeading
        title={jobTitle}
        description={`Official campus recruitment drive conducted by ${companyName}. Reference code: ${eligibility.driveId}`}
        breadcrumbs={[
          { label: 'Portal Home', href: '/student/dashboard' },
          { label: 'Recruitment Drives', href: '/student/drives' },
          { label: jobTitle },
        ]}
      />

      {/* Main Drive Overview Card */}
      <div className="rounded-lg border border-border bg-card p-6 shadow-xs space-y-6">
        {/* Recruiter & Primary Status Bar */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-border/80">
          <div className="flex items-center gap-3">
            <div className="h-12 w-12 rounded-md bg-muted flex items-center justify-center text-primary font-bold">
              <Building2 className="h-6 w-6" aria-hidden="true" />
            </div>
            <div>
              <span className="text-xs uppercase tracking-wider text-muted-foreground block font-sans">
                Corporate Partner
              </span>
              <h2 className="font-serif font-bold text-xl text-foreground">
                {companyName}
              </h2>
            </div>
          </div>

          <div className="flex items-center gap-3">
            {eligibility.alreadyApplied ? (
              <Link to={`/student/applications/${eligibility.existingApplicationId || ''}`}>
                <Button variant="outline" size="sm" className="text-xs gap-1.5 font-medium">
                  <CheckCircle2 className="h-4 w-4 text-teal-800" aria-hidden="true" />
                  <span>View Submitted Application</span>
                </Button>
              </Link>
            ) : eligibility.canApply ? (
              <Button
                variant="default"
                size="sm"
                onClick={() => setIsApplyModalOpen(true)}
                className="text-xs gap-1.5 font-medium"
              >
                <FileCheck2 className="h-4 w-4" aria-hidden="true" />
                <span>Submit Formal Application</span>
              </Button>
            ) : (
              <Button
                variant="secondary"
                size="sm"
                disabled
                className="text-xs gap-1.5 font-medium cursor-not-allowed text-muted-foreground"
              >
                <Lock className="h-3.5 w-3.5" aria-hidden="true" />
                <span>Application Ineligible</span>
              </Button>
            )}
          </div>
        </div>

        {/* Real DTO Metadata Field Summary */}
        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-4 text-xs">
          <div className="p-3 rounded-md bg-muted/40 border border-border/60 space-y-1">
            <span className="text-[11px] uppercase tracking-wider text-muted-foreground block">
              Drive Reference
            </span>
            <span className="font-mono font-bold text-foreground block text-sm">
              {eligibility.driveId}
            </span>
          </div>

          <div className="p-3 rounded-md bg-muted/40 border border-border/60 space-y-1">
            <span className="text-[11px] uppercase tracking-wider text-muted-foreground block">
              Minimum Academic CGPA
            </span>
            <span
              className={`font-mono font-bold block text-sm ${
                isCgpaMet ? 'text-teal-800' : 'text-destructive'
              }`}
            >
              {eligibility.minimumCgpa.toFixed(2)} CGPA
            </span>
          </div>

          <div className="p-3 rounded-md bg-muted/40 border border-border/60 space-y-1">
            <span className="text-[11px] uppercase tracking-wider text-muted-foreground block">
              Application Deadline
            </span>
            <span className="font-mono font-bold text-foreground block text-sm flex items-center gap-1">
              <Calendar className="h-3.5 w-3.5 text-brass-600" aria-hidden="true" />
              {eligibility.applicationDeadline}
            </span>
          </div>

          <div className="p-3 rounded-md bg-muted/40 border border-border/60 space-y-1">
            <span className="text-[11px] uppercase tracking-wider text-muted-foreground block">
              Portal State
            </span>
            <span className="font-medium text-foreground block text-sm">
              {eligibility.closed
                ? 'Concluded / Closed'
                : eligibility.deadlinePassed
                ? 'Deadline Expired'
                : 'Accepting Submissions'}
            </span>
          </div>
        </div>

        {/* Department Eligibility List */}
        <div className="space-y-2 pt-2 border-t border-border/60">
          <span className="text-xs uppercase tracking-wider font-semibold text-muted-foreground block">
            Approved Academic Departments for Recruitment
          </span>
          <div className="flex flex-wrap gap-2">
            {Array.from(eligibility.eligibleBranches).map((branch) => {
              const isStudentBranch =
                branch.trim().toLowerCase() === eligibility.studentBranch.trim().toLowerCase()
              return (
                <span
                  key={branch}
                  className={`text-xs px-2.5 py-1 rounded-sm border font-medium ${
                    isStudentBranch
                      ? 'bg-teal-900/10 border-teal-700/30 text-teal-800 font-bold'
                      : 'bg-muted/60 border-border text-foreground'
                  }`}
                >
                  {branch}
                  {isStudentBranch && ' (Your Branch)'}
                </span>
              )
            })}
          </div>
        </div>
      </div>

      {/* Authoritative Itemized Eligibility Breakdown Card */}
      <EligibilityBreakdownCard eligibility={eligibility} />

      {/* Application Confirmation Modal */}
      {drive && profile && (
        <ApplicationConfirmationModal
          isOpen={isApplyModalOpen}
          onClose={() => setIsApplyModalOpen(false)}
          drive={drive}
          studentProfile={{
            studentId: profile.id,
            name: profile.name,
            branch: profile.branch,
            cgpa: profile.cgpa,
          }}
          onSuccess={() => {
            refetchEligibility()
          }}
        />
      )}
    </div>
  )
}
