import * as React from 'react'
import { Link } from 'react-router-dom'
import { useStudentApplications } from '../api/studentApi'
import { ApplicationStatusBadge } from '@/components/domain/application-status-badge'
import { PageHeading } from '@/components/domain/page-heading'
import { Button } from '@/components/ui/button'
import { LoadingSkeleton } from '@/components/domain/loading-skeleton'
import { ErrorState } from '@/components/domain/error-state'
import { EmptyState } from '@/components/domain/empty-state'
import {
  FileCheck2,
  Briefcase,
  ArrowRight,
  ChevronLeft,
  ChevronRight,
  Search,
} from 'lucide-react'
import { formatLocalDateTime, formatLocalDateOnly } from '@/lib/dateUtils'

export const StudentApplicationsPage: React.FC = () => {
  const [page, setPage] = React.useState(0)
  const size = 10
  const [statusFilter, setStatusFilter] = React.useState<string>('ALL')
  const [searchQuery, setSearchQuery] = React.useState('')

  const { data: pagedResponse, isLoading, error, refetch } = useStudentApplications(page, size, 'appliedAt,desc')

  const applications = React.useMemo(() => pagedResponse?.content || [], [pagedResponse])

  // Filter the loaded page by status and text search.
  const filteredApplications = React.useMemo(() => {
    return applications.filter((app) => {
      if (statusFilter !== 'ALL' && app.status.toUpperCase() !== statusFilter) {
        return false
      }
      if (searchQuery.trim()) {
        const query = searchQuery.toLowerCase()
        const matches =
          app.companyName.toLowerCase().includes(query) ||
          app.jobTitle.toLowerCase().includes(query) ||
          app.id.toLowerCase().includes(query)
        if (!matches) return false
      }
      return true
    })
  }, [applications, statusFilter, searchQuery])

  if (isLoading) {
    return (
      <div className="space-y-6">
        <PageHeading
          title="My Applications"
          description="Retrieving formal candidate placement records and recruitment statuses..."
        />
        <LoadingSkeleton rows={4} />
      </div>
    )
  }

  if (error || !pagedResponse) {
    return (
      <div className="space-y-6">
        <PageHeading title="My Applications" description="Application Tracking" />
        <ErrorState
          title="Unable to Retrieve Applications"
          message={error instanceof Error ? error.message : 'Connection to the placement server failed.'}
          onRetry={refetch}
        />
      </div>
    )
  }

  return (
    <div className="space-y-6">
      {/* Page Title & Breadcrumbs */}
      <PageHeading
        title="My Applications"
        description="Comprehensive audit directory of your submitted recruitment applications, departmental evaluations, and interview invitations."
        breadcrumbs={[
          { label: 'Portal Home', href: '/student/dashboard' },
          { label: 'My Applications' },
        ]}
        actions={
          <Link to="/student/drives">
            <Button variant="outline" size="sm" className="text-xs gap-1.5 h-8">
              <Briefcase className="h-3.5 w-3.5 text-accent" aria-hidden="true" />
              <span>Explore Active Drives</span>
            </Button>
          </Link>
        }
      />

      {/* Filter Toolbar */}
      <div className="rounded-lg border border-border bg-card p-4 shadow-xs space-y-3">
        <div className="flex flex-col sm:flex-row gap-3">
          <div className="relative flex-1">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" aria-hidden="true" />
            <input
              type="search"
              placeholder="Filter this page by company, role, or ID..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full pl-9 pr-3 py-1.5 rounded-md border border-input bg-background text-xs sm:text-sm text-foreground focus:outline-none focus:ring-1 focus:ring-ring"
              aria-label="Filter applications"
            />
          </div>

          <div className="flex items-center gap-2">
            <label htmlFor="status-filter" className="text-xs font-medium text-muted-foreground whitespace-nowrap">
              Status:
            </label>
            <select
              id="status-filter"
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              className="h-9 px-3 rounded-md border border-input bg-background text-xs font-medium text-foreground focus:outline-none focus:ring-1 focus:ring-ring"
              aria-label="Filter by application status"
            >
              <option value="ALL">All Statuses</option>
              <option value="APPLIED">Applied</option>
              <option value="SHORTLISTED">Shortlisted</option>
              <option value="SCHEDULED">Scheduled</option>
              <option value="REJECTED">Rejected</option>
              <option value="SELECTED">Selected</option>
            </select>
          </div>
        </div>
      </div>

      <p className="text-xs text-muted-foreground">
        Filters apply to this page of {pagedResponse.totalElements} applications.
      </p>

      {/* Applications Directory View */}
      {filteredApplications.length > 0 ? (
        <div className="space-y-3">
          {/* Desktop Table View */}
          <div className="hidden md:block rounded-lg border border-border bg-card overflow-hidden shadow-xs">
            <table className="w-full text-left text-xs" aria-label="Submitted Applications Table">
              <thead className="bg-muted/60 border-b border-border text-muted-foreground uppercase tracking-wider font-semibold">
                <tr>
                  <th scope="col" className="px-4 py-3">Reference ID</th>
                  <th scope="col" className="px-4 py-3">Corporate Recruiter</th>
                  <th scope="col" className="px-4 py-3">Job Role</th>
                  <th scope="col" className="px-4 py-3">Submission Timestamp</th>
                  <th scope="col" className="px-4 py-3">Status</th>
                  <th scope="col" className="px-4 py-3 text-right">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border/60">
                {filteredApplications.map((app) => (
                  <tr key={app.id} className="hover:bg-muted/30 transition-colors">
                    <td className="px-4 py-3.5 font-mono font-medium text-primary">
                      {app.id}
                    </td>
                    <td className="px-4 py-3.5 font-semibold text-foreground">
                      {app.companyName}
                    </td>
                    <td className="px-4 py-3.5 text-muted-foreground">
                      {app.jobTitle}
                    </td>
                    <td className="px-4 py-3.5 font-mono text-muted-foreground text-[11px]">
                      {formatLocalDateTime(app.appliedAt)}
                    </td>
                    <td className="px-4 py-3.5">
                      <ApplicationStatusBadge status={app.status} />
                    </td>
                    <td className="px-4 py-3.5 text-right">
                      <Link to={`/student/applications/${app.id}`}>
                        <Button variant="ghost" size="sm" className="h-7 text-xs gap-1 text-primary hover:text-primary">
                          <span>Inspection</span>
                          <ArrowRight className="h-3 w-3" aria-hidden="true" />
                        </Button>
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Mobile Card View (390px responsive) */}
          <div className="md:hidden space-y-3">
            {filteredApplications.map((app) => (
              <article
                key={app.id}
                className="rounded-lg border border-border bg-card p-4 space-y-3 shadow-xs"
                aria-labelledby={`app-card-${app.id}`}
              >
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <span className="font-mono text-xs font-bold text-primary block">
                      {app.id}
                    </span>
                    <h3 id={`app-card-${app.id}`} className="font-serif font-bold text-base text-foreground mt-0.5">
                      {app.companyName}
                    </h3>
                    <p className="text-xs text-muted-foreground">{app.jobTitle}</p>
                  </div>
                  <ApplicationStatusBadge status={app.status} />
                </div>

                <div className="text-[11px] font-mono text-muted-foreground pt-1 border-t border-border/60 flex items-center justify-between">
                  <span>Applied:</span>
                  <span>{formatLocalDateOnly(app.appliedAt)}</span>
                </div>

                <div className="pt-2 border-t border-border/60">
                  <Link to={`/student/applications/${app.id}`}>
                    <Button variant="outline" size="sm" className="w-full text-xs justify-center gap-1.5 h-8">
                      <span>View Application Status</span>
                      <ArrowRight className="h-3.5 w-3.5" aria-hidden="true" />
                    </Button>
                  </Link>
                </div>
              </article>
            ))}
          </div>

        </div>
      ) : (
        <EmptyState
          icon={<FileCheck2 className="h-5 w-5" aria-hidden="true" />}
          title={statusFilter !== 'ALL' || searchQuery ? 'No Matches on This Page' : 'No Applications Recorded'}
          description={
            statusFilter !== 'ALL' || searchQuery
              ? 'No applications on this page match your filters. Try another page or clear the filters.'
              : 'You have not submitted any placement applications yet. Explore active recruitment drives to submit your candidacy.'
          }
          action={statusFilter !== 'ALL' || searchQuery ? undefined : (
            <Link to="/student/drives">
              <Button variant="default" size="sm" className="text-xs">
                Explore Active Drives
              </Button>
            </Link>
          )}
        />
      )}
      {pagedResponse.totalPages > 1 && (
        <div className="flex items-center justify-between pt-3 text-xs text-muted-foreground border-t border-border/60">
          <span className="font-mono">
            Page {pagedResponse.page + 1} of {pagedResponse.totalPages} ({pagedResponse.totalElements} records)
          </span>
          <div className="flex items-center gap-2">
            <Button variant="outline" size="sm" onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={pagedResponse.page === 0} className="h-8 text-xs gap-1">
              <ChevronLeft className="h-3.5 w-3.5" aria-hidden="true" />
              <span>Previous</span>
            </Button>
            <Button variant="outline" size="sm" onClick={() => setPage((p) => p + 1)} disabled={pagedResponse.last} className="h-8 text-xs gap-1">
              <span>Next</span>
              <ChevronRight className="h-3.5 w-3.5" aria-hidden="true" />
            </Button>
          </div>
        </div>
      )}
    </div>
  )
}
