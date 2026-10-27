import * as React from 'react'
import { useStudentDrives, useStudentProfile } from '../api/studentApi'
import { DriveCard } from '../components/DriveCard'
import { ApplicationConfirmationModal } from '../components/ApplicationConfirmationModal'
import { PageHeading } from '@/components/domain/page-heading'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { LoadingSkeleton } from '@/components/domain/loading-skeleton'
import { ErrorState } from '@/components/domain/error-state'
import { EmptyState } from '@/components/domain/empty-state'
import type { StudentDriveItemDto } from '@/types/api'
import {
  Search,
  CheckCircle2,
  XCircle,
  Briefcase,
} from 'lucide-react'

type EligibilityFilter = 'ALL' | 'ELIGIBLE' | 'APPLIED' | 'INELIGIBLE'

export const StudentDrivesPage: React.FC = () => {
  const { data: drives, isLoading: isDrivesLoading, error: drivesError, refetch: refetchDrives } = useStudentDrives()
  const { data: profile } = useStudentProfile()

  const [searchQuery, setSearchQuery] = React.useState('')
  const [eligibilityFilter, setEligibilityFilter] = React.useState<EligibilityFilter>('ALL')
  const [selectedBranchFilter, setSelectedBranchFilter] = React.useState<string>('ALL')

  const [selectedDriveForApply, setSelectedDriveForApply] = React.useState<StudentDriveItemDto | null>(null)
  const [isApplyModalOpen, setIsApplyModalOpen] = React.useState(false)

  // Extract distinct branches from drives for the filter dropdown
  const distinctBranches = React.useMemo(() => {
    if (!drives) return []
    const branches = new Set<string>()
    drives.forEach((d) => d.eligibleBranches.forEach((b) => branches.add(b)))
    return Array.from(branches).sort()
  }, [drives])

  // Filtered and sorted drives
  const filteredDrives = React.useMemo(() => {
    if (!drives) return []

    return drives.filter((drive) => {
      // 1. Text Search Filter (company name or job title)
      const matchesSearch =
        drive.companyName.toLowerCase().includes(searchQuery.toLowerCase()) ||
        drive.jobTitle.toLowerCase().includes(searchQuery.toLowerCase()) ||
        drive.id.toLowerCase().includes(searchQuery.toLowerCase())

      if (!matchesSearch) return false

      // 2. Eligibility Filter
      if (eligibilityFilter === 'ELIGIBLE' && !drive.canApply) return false
      if (eligibilityFilter === 'APPLIED' && !drive.hasApplied) return false
      if (eligibilityFilter === 'INELIGIBLE' && (drive.canApply || drive.hasApplied)) return false

      // 3. Branch Filter
      if (selectedBranchFilter !== 'ALL') {
        const matchesBranch = drive.eligibleBranches.some(
          (b) => b.trim().toLowerCase() === selectedBranchFilter.trim().toLowerCase()
        )
        if (!matchesBranch) return false
      }

      return true
    })
  }, [drives, searchQuery, eligibilityFilter, selectedBranchFilter])

  // Counts for filter pills
  const counts = React.useMemo(() => {
    if (!drives) return { all: 0, eligible: 0, applied: 0, ineligible: 0 }
    return {
      all: drives.length,
      eligible: drives.filter((d) => d.canApply).length,
      applied: drives.filter((d) => d.hasApplied).length,
      ineligible: drives.filter((d) => !d.canApply && !d.hasApplied).length,
    }
  }, [drives])

  const handleApplyClick = (drive: StudentDriveItemDto) => {
    setSelectedDriveForApply(drive)
    setIsApplyModalOpen(true)
  }

  if (isDrivesLoading) {
    return (
      <div className="space-y-6">
        <PageHeading
          title="Recruitment Opportunity Explorer"
          description="Retrieving campus recruitment drives and real-time eligibility determinations..."
        />
        <LoadingSkeleton rows={4} />
      </div>
    )
  }

  if (drivesError || !drives) {
    return (
      <div className="space-y-6">
        <PageHeading title="Recruitment Opportunity Explorer" description="Opportunity Catalog" />
        <ErrorState
          title="Unable to Retrieve Recruitment Drives"
          message={drivesError instanceof Error ? drivesError.message : 'Connection to the recruitment catalog failed.'}
          onRetry={refetchDrives}
        />
      </div>
    )
  }

  return (
    <div className="space-y-6">
      {/* Page Title & Breadcrumbs */}
      <PageHeading
        title="Recruitment Opportunity Explorer"
        description="Browse corporate recruitment drives, inspect verified academic eligibility, and submit authoritative placement applications."
        breadcrumbs={[
          { label: 'Portal Home', href: '/student/dashboard' },
          { label: 'Recruitment Drives' },
        ]}
      />

      {/* Filter and Search Bar */}
      <div className="rounded-lg border border-border bg-card p-4 shadow-xs space-y-4">
        {/* Top Controls: Search Input & Branch Select */}
        <div className="flex flex-col sm:flex-row gap-3">
          <div className="relative flex-1">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" aria-hidden="true" />
            <Input
              type="search"
              placeholder="Search opportunities by company, job title, or drive ID..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="pl-9 text-xs sm:text-sm h-9"
              aria-label="Search recruitment opportunities"
            />
          </div>

          <div className="flex items-center gap-2">
            <label htmlFor="branch-filter" className="text-xs font-medium text-muted-foreground whitespace-nowrap">
              Department:
            </label>
            <select
              id="branch-filter"
              value={selectedBranchFilter}
              onChange={(e) => setSelectedBranchFilter(e.target.value)}
              className="h-9 px-3 rounded-md border border-input bg-background text-xs font-medium text-foreground focus:outline-none focus:ring-1 focus:ring-ring"
              aria-label="Filter by academic department"
            >
              <option value="ALL">All Departments</option>
              {distinctBranches.map((branch) => (
                <option key={branch} value={branch}>
                  {branch}
                </option>
              ))}
            </select>
          </div>
        </div>

        {/* Filter Tabs / Quick Select Pills */}
        <div className="flex flex-wrap items-center justify-between gap-2 pt-2 border-t border-border/60">
          <div className="flex flex-wrap items-center gap-1.5" role="tablist" aria-label="Eligibility Filter">
            <button
              type="button"
              role="tab"
              aria-selected={eligibilityFilter === 'ALL'}
              onClick={() => setEligibilityFilter('ALL')}
              className={`px-3 py-1.5 rounded-sm text-xs font-medium transition-colors ${
                eligibilityFilter === 'ALL'
                  ? 'bg-primary text-primary-foreground shadow-xs'
                  : 'bg-muted/60 text-muted-foreground hover:text-foreground hover:bg-muted'
              }`}
            >
              All Opportunities ({counts.all})
            </button>

            <button
              type="button"
              role="tab"
              aria-selected={eligibilityFilter === 'ELIGIBLE'}
              onClick={() => setEligibilityFilter('ELIGIBLE')}
              className={`px-3 py-1.5 rounded-sm text-xs font-medium transition-colors inline-flex items-center gap-1.5 ${
                eligibilityFilter === 'ELIGIBLE'
                  ? 'bg-teal-800 text-white shadow-xs'
                  : 'bg-muted/60 text-muted-foreground hover:text-foreground hover:bg-muted'
              }`}
            >
              <span className="h-1.5 w-1.5 rounded-full bg-teal-800" aria-hidden="true" />
              Eligible to Apply ({counts.eligible})
            </button>

            <button
              type="button"
              role="tab"
              aria-selected={eligibilityFilter === 'APPLIED'}
              onClick={() => setEligibilityFilter('APPLIED')}
              className={`px-3 py-1.5 rounded-sm text-xs font-medium transition-colors inline-flex items-center gap-1.5 ${
                eligibilityFilter === 'APPLIED'
                  ? 'bg-primary text-primary-foreground shadow-xs'
                  : 'bg-muted/60 text-muted-foreground hover:text-foreground hover:bg-muted'
              }`}
            >
              <CheckCircle2 className="h-3.5 w-3.5" aria-hidden="true" />
              Applied ({counts.applied})
            </button>

            <button
              type="button"
              role="tab"
              aria-selected={eligibilityFilter === 'INELIGIBLE'}
              onClick={() => setEligibilityFilter('INELIGIBLE')}
              className={`px-3 py-1.5 rounded-sm text-xs font-medium transition-colors inline-flex items-center gap-1.5 ${
                eligibilityFilter === 'INELIGIBLE'
                  ? 'bg-destructive text-destructive-foreground shadow-xs'
                  : 'bg-muted/60 text-muted-foreground hover:text-foreground hover:bg-muted'
              }`}
            >
              <XCircle className="h-3.5 w-3.5" aria-hidden="true" />
              Criteria Unmet ({counts.ineligible})
            </button>
          </div>

          <div className="text-xs text-muted-foreground font-mono">
            Showing <strong className="text-foreground">{filteredDrives.length}</strong> of {drives.length} drives
          </div>
        </div>
      </div>

      {/* Opportunities Directory Grid */}
      {filteredDrives.length > 0 ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 sm:gap-5">
          {filteredDrives.map((drive) => (
            <DriveCard
              key={drive.id}
              drive={drive}
              studentCgpa={profile?.cgpa}
              onApplyClick={handleApplyClick}
            />
          ))}
        </div>
      ) : (
        <EmptyState
          icon={<Briefcase className="h-5 w-5" aria-hidden="true" />}
          title="No Recruitment Opportunities Found"
          description={
            searchQuery || eligibilityFilter !== 'ALL' || selectedBranchFilter !== 'ALL'
              ? 'No campus recruitment drives match the specified filters. Try adjusting your search query or reset filter settings.'
              : 'There are currently no active recruitment drives scheduled in the catalog.'
          }
          action={
            (searchQuery || eligibilityFilter !== 'ALL' || selectedBranchFilter !== 'ALL') ? (
              <Button
                variant="outline"
                size="sm"
                onClick={() => {
                  setSearchQuery('')
                  setEligibilityFilter('ALL')
                  setSelectedBranchFilter('ALL')
                }}
                className="text-xs"
              >
                Reset Filters
              </Button>
            ) : undefined
          }
        />
      )}

      {/* Application Confirmation Modal */}
      {profile && (
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
            refetchDrives()
          }}
        />
      )}
    </div>
  )
}
