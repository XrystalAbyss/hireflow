import * as React from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import {
  Building2,
  UserCheck,
  Eye,
  Award,
  ArrowLeft,
  CheckCircle2,
} from 'lucide-react'
import { type ColumnDef } from '@tanstack/react-table'
import { PageHeading } from '@/components/domain/page-heading'
import { DataTable } from '@/components/domain/data-table'
import { ApplicationStatusBadge } from '@/components/domain/application-status-badge'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Pagination } from '@/components/ui/pagination'
import { CandidateDetailDialog } from '../components/CandidateDetailDialog'
import { formatLocalDateTime } from '@/lib/dateUtils'
import {
  useOfficerDriveApplications,
  useOfficerDrive,
  useOfficerDrives,
  useShortlistApplicationMutation,
} from '../api/officerApi'
import type { OfficerApplicationReviewDto } from '@/types/api'

const STATUS_FILTERS: { label: string; value: string }[] = [
  { label: 'All Candidates', value: 'ALL' },
  { label: 'Pending Review (Applied)', value: 'APPLIED' },
  { label: 'Shortlisted', value: 'SHORTLISTED' },
  { label: 'Scheduled', value: 'SCHEDULED' },
  { label: 'Selected', value: 'SELECTED' },
  { label: 'Rejected', value: 'REJECTED' },
]

export function OfficerCandidateReviewPage() {
  const { id: driveIdParam } = useParams<{ id: string }>()
  const navigate = useNavigate()

  // Drives for switcher dropdown if driveIdParam not in route or user wants to switch
  const { data: allDrivesData } = useOfficerDrives(0, 100)
  const allDrives = allDrivesData?.content || []

  const activeDriveId = driveIdParam || allDrives[0]?.id || ''

  const [page, setPage] = React.useState(0)
  const [statusFilter, setStatusFilter] = React.useState('ALL')
  const [selectedApp, setSelectedApp] = React.useState<OfficerApplicationReviewDto | null>(null)
  const [shortlistSuccessMsg, setShortlistSuccessMsg] = React.useState<string | null>(null)
  const [shortlistErrorMsg, setShortlistErrorMsg] = React.useState<string | null>(null)

  const { data: drive } = useOfficerDrive(activeDriveId)
  const { data: pagedApps, isLoading, refetch } = useOfficerDriveApplications(
    activeDriveId,
    page,
    15,
    statusFilter
  )

  const shortlistMutation = useShortlistApplicationMutation()

  const applications = pagedApps?.content || []
  const totalPages = pagedApps?.totalPages || 1
  const totalElements = pagedApps?.totalElements || 0

  const handleShortlist = React.useCallback(
    async (appId: string) => {
      setShortlistSuccessMsg(null)
      setShortlistErrorMsg(null)
      try {
        const updated = await shortlistMutation.mutateAsync(appId)
        setShortlistSuccessMsg(
          `Candidate '${updated.studentName}' (${updated.studentId}) successfully shortlisted.`
        )
        refetch()
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : 'Failed to shortlist candidate.'
        setShortlistErrorMsg(msg)
      }
    },
    [shortlistMutation, refetch]
  )

  const columns = React.useMemo<ColumnDef<OfficerApplicationReviewDto>[]>(
    () => [
      {
        accessorKey: 'studentId',
        header: 'Roll Number',
        cell: ({ row }) => (
          <span className="font-mono text-xs font-semibold text-foreground uppercase">
            {row.original.studentId}
          </span>
        ),
      },
      {
        accessorKey: 'studentName',
        header: 'Candidate Name',
        cell: ({ row }) => (
          <div>
            <span className="font-medium text-foreground block">
              {row.original.studentName}
            </span>
            <span className="text-xs text-muted-foreground block truncate max-w-[200px]">
              {row.original.studentEmail}
            </span>
          </div>
        ),
      },
      {
        accessorKey: 'branch',
        header: 'Degree Branch',
        cell: ({ row }) => (
          <span className="text-xs text-foreground block truncate max-w-[180px]">
            {row.original.branch}
          </span>
        ),
      },
      {
        accessorKey: 'cgpa',
        header: 'Verified CGPA',
        cell: ({ row }) => {
          const cgpa = row.original.cgpa
          const isHigh = cgpa >= 8.5
          return (
            <div className="flex items-center gap-1.5 font-mono">
              <span
                className={`text-xs font-bold ${
                  isHigh ? 'text-brass-800 dark:text-brass-400' : 'text-foreground'
                }`}
              >
                {cgpa.toFixed(2)}
              </span>
              {isHigh && (
                <span title="Honors Candidate (CGPA >= 8.5)" className="inline-flex">
                  <Award className="h-3.5 w-3.5 text-brass-600 shrink-0" />
                </span>
              )}
            </div>
          )
        },
      },
      {
        accessorKey: 'appliedAt',
        header: 'Application Date',
        cell: ({ row }) => (
          <span className="font-mono text-xs text-muted-foreground whitespace-nowrap">
            {formatLocalDateTime(row.original.appliedAt)}
          </span>
        ),
      },
      {
        accessorKey: 'status',
        header: 'Status',
        cell: ({ row }) => <ApplicationStatusBadge status={row.original.status} />,
      },
      {
        id: 'actions',
        header: () => <span className="sr-only">Actions</span>,
        cell: ({ row }) => {
          const app = row.original
          const canShortlist = app.status === 'APPLIED' || app.status === 'ELIGIBLE'
          const isRowShortlisting =
            shortlistMutation.isPending &&
            shortlistMutation.variables === app.id

          return (
            <div className="flex items-center justify-end gap-1.5 whitespace-nowrap">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setSelectedApp(app)}
                className="h-7 text-xs px-2"
                aria-label={`View dossier for ${app.studentName}`}
              >
                <Eye className="h-3 w-3" />
              </Button>

              {canShortlist ? (
                <Button
                  size="sm"
                  onClick={() => handleShortlist(app.id)}
                  disabled={isRowShortlisting}
                  className="h-7 text-xs px-2.5 gap-1 bg-accent hover:bg-accent/90 text-accent-foreground font-medium"
                  aria-label={`Shortlist ${app.studentName}`}
                >
                  <UserCheck className="h-3 w-3" />
                  <span>{isRowShortlisting ? 'Saving...' : 'Shortlist'}</span>
                </Button>
              ) : app.status === 'SHORTLISTED' ? (
                <span className="inline-flex items-center gap-1 text-[11px] font-mono font-medium text-accent px-2 py-0.5 rounded-xs bg-accent/10 border border-accent/20">
                  <CheckCircle2 className="h-3 w-3" />
                  Shortlisted
                </span>
              ) : null}
            </div>
          )
        },
      },
    ],
    [shortlistMutation.isPending, shortlistMutation.variables, handleShortlist]
  )

  return (
    <div className="space-y-6">
      <PageHeading
        title="Candidate Application Review"
        description={
          drive
            ? `Candidate roster for ${drive.jobTitle} at ${drive.companyName}. Individual candidate evaluation and shortlisting.`
            : 'Candidate roster review and qualification shortlisting.'
        }
        breadcrumbs={[
          { label: 'Officer', href: '/officer/dashboard' },
          { label: 'Recruitment Drives', href: '/officer/drives' },
          { label: activeDriveId, href: `/officer/drives/${activeDriveId}` },
          { label: 'Candidate Review' },
        ]}
        actions={
          <div className="flex min-w-0 max-w-full flex-wrap items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={() => navigate('/officer/drives')}
              className="text-xs gap-1.5"
            >
              <ArrowLeft className="h-3.5 w-3.5" />
              <span>Back to Drives</span>
            </Button>
            {/* Drive Switcher */}
            <select
              value={activeDriveId}
              onChange={(e) => {
                navigate(`/officer/drives/${e.target.value}/applications`)
                setPage(0)
              }}
              className="h-8 w-full sm:w-72 max-w-full min-w-0 rounded-sm border border-input bg-card px-2.5 text-xs text-foreground font-mono focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
              aria-label="Switch recruitment drive"
            >
              {allDrives.map((d) => (
                <option key={d.id} value={d.id}>
                  {d.id} - {d.jobTitle} ({d.companyName})
                </option>
              ))}
            </select>
          </div>
        }
      />

      {/* Success & Error Banners */}
      {shortlistSuccessMsg && (
        <div
          className="p-3 text-xs rounded-sm bg-accent/10 text-accent border border-accent/20 flex items-center justify-between"
          role="status"
        >
          <div className="flex items-center gap-2 font-medium">
            <CheckCircle2 className="h-4 w-4 shrink-0" />
            <span>{shortlistSuccessMsg}</span>
          </div>
          <button
            type="button"
            onClick={() => setShortlistSuccessMsg(null)}
            className="text-accent hover:opacity-80 font-bold ml-2 cursor-pointer"
          >
            ×
          </button>
        </div>
      )}

      {shortlistErrorMsg && (
        <div
          className="p-3 text-xs rounded-sm bg-destructive/10 text-destructive border border-destructive/20 font-medium"
          role="alert"
        >
          {shortlistErrorMsg}
        </div>
      )}

      {/* Drive Context Summary Header */}
      {drive && (
        <div className="rounded-md border border-border bg-card p-4 shadow-xs flex flex-wrap items-center justify-between gap-4 text-xs">
          <div className="flex items-center gap-3">
            <div className="h-10 w-10 rounded-sm bg-primary/10 flex items-center justify-center text-primary">
              <Building2 className="h-5 w-5" />
            </div>
            <div>
              <span className="font-semibold text-foreground text-sm block">
                {drive.companyName} • {drive.jobTitle}
              </span>
              <span className="font-mono text-muted-foreground">
                Cutoff: CGPA {drive.minimumCgpa.toFixed(2)} • Allowed: {drive.eligibleBranches.join(', ')}
              </span>
            </div>
          </div>

          <div className="flex items-center gap-4 font-mono">
            <div>
              <span className="text-muted-foreground block text-[11px] uppercase">Total Applications</span>
              <span className="text-sm font-bold text-foreground">{drive.totalApplications}</span>
            </div>
            <div className="border-l border-border pl-4">
              <span className="text-muted-foreground block text-[11px] uppercase">Shortlisted</span>
              <span className="text-sm font-bold text-accent">{drive.shortlistedApplications}</span>
            </div>
            <div className="border-l border-border pl-4">
              <span className="text-muted-foreground block text-[11px] uppercase">Status</span>
              <Badge variant={drive.closed ? 'secondary' : 'default'} className="text-[11px]">
                {drive.closed ? 'CLOSED' : 'ACTIVE'}
              </Badge>
            </div>
          </div>
        </div>
      )}

      {/* Status Filter Tabs */}
      <div className="flex items-center gap-1.5 overflow-x-auto pb-1 border-b border-border/60">
        {STATUS_FILTERS.map((tab) => (
          <button
            key={tab.value}
            type="button"
            onClick={() => {
              setStatusFilter(tab.value)
              setPage(0)
            }}
            className={`px-3 py-1.5 text-xs font-medium rounded-sm whitespace-nowrap transition-colors cursor-pointer select-none ${
              statusFilter === tab.value
                ? 'bg-primary text-primary-foreground font-semibold shadow-xs'
                : 'text-muted-foreground hover:text-foreground hover:bg-muted/70'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* High-Density Candidates DataTable */}
      <DataTable
        columns={columns}
        data={applications}
        isLoading={isLoading}
        density="compact"
        emptyTitle="No candidates match filter criteria"
        emptyDescription="There are no student applications matching this status filter for the selected recruitment drive."
      />

      {/* Pagination Bar */}
      {totalPages > 1 && (
        <Pagination
          page={page}
          totalPages={totalPages}
          onPageChange={setPage}
          pageSize={15}
          totalElements={totalElements}
        />
      )}

      {/* Candidate Dossier Inspection & Shortlisting Dialog */}
      <CandidateDetailDialog
        open={Boolean(selectedApp)}
        onOpenChange={(open) => !open && setSelectedApp(null)}
        application={selectedApp}
        onSuccess={() => {
          refetch()
          setSelectedApp(null)
          setShortlistSuccessMsg('Candidate shortlisted successfully.')
        }}
      />
    </div>
  )
}
