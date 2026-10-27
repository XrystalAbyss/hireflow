import * as React from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Plus,
  Users,
  Edit3,
  Eye,
  Lock,
} from 'lucide-react'
import { type ColumnDef } from '@tanstack/react-table'
import { PageHeading } from '@/components/domain/page-heading'
import { DataTable } from '@/components/domain/data-table'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Pagination } from '@/components/ui/pagination'
import { DriveDialog } from '../components/DriveDialog'
import { DriveCloseDialog } from '../components/DriveCloseDialog'
import { useOfficerDrives, useOfficerCompanies } from '../api/officerApi'
import type { OfficerDriveDetailDto, OfficerCompanyDetailDto } from '@/types/api'

export function OfficerDrivesPage() {
  const navigate = useNavigate()
  const [page, setPage] = React.useState(0)
  const [activeOnly, setActiveOnly] = React.useState(false)
  const [selectedCompanyId, setSelectedCompanyId] = React.useState('')
  const [createModalOpen, setCreateModalOpen] = React.useState(false)
  const [editingDrive, setEditingDrive] = React.useState<OfficerDriveDetailDto | null>(null)
  const [closingDrive, setClosingDrive] = React.useState<OfficerDriveDetailDto | null>(null)

  const { data: companiesData } = useOfficerCompanies(0, 100)
  const companies: OfficerCompanyDetailDto[] = companiesData?.content || []

  const { data: pagedData, isLoading, refetch } = useOfficerDrives(
    page,
    15,
    selectedCompanyId || undefined,
    activeOnly
  )

  const drives = pagedData?.content || []
  const totalPages = pagedData?.totalPages || 1
  const totalElements = pagedData?.totalElements || 0

  const handleCreate = () => {
    setEditingDrive(null)
    setCreateModalOpen(true)
  }

  const handleEdit = (drive: OfficerDriveDetailDto) => {
    setEditingDrive(drive)
    setCreateModalOpen(true)
  }

  const handleClose = (drive: OfficerDriveDetailDto) => {
    setClosingDrive(drive)
  }

  const columns = React.useMemo<ColumnDef<OfficerDriveDetailDto>[]>(
    () => [
      {
        accessorKey: 'id',
        header: 'Drive ID',
        cell: ({ row }) => (
          <span className="font-mono text-xs font-semibold text-muted-foreground uppercase">
            {row.original.id}
          </span>
        ),
      },
      {
        accessorKey: 'jobTitle',
        header: 'Position & Partner',
        cell: ({ row }) => (
          <div>
            <span className="font-medium text-foreground block">
              {row.original.jobTitle}
            </span>
            <span className="text-xs text-muted-foreground">
              {row.original.companyName}
            </span>
          </div>
        ),
      },
      {
        accessorKey: 'minimumCgpa',
        header: 'Min CGPA',
        cell: ({ row }) => (
          <span className="font-mono text-xs font-medium text-foreground">
            {row.original.minimumCgpa.toFixed(2)}
          </span>
        ),
      },
      {
        accessorKey: 'eligibleBranches',
        header: 'Eligible Branches',
        cell: ({ row }) => (
          <div className="flex flex-wrap gap-1 max-w-[180px]">
            {row.original.eligibleBranches.slice(0, 3).map((b) => (
              <span
                key={b}
                className="px-1.5 py-0.5 rounded-xs bg-muted text-muted-foreground font-mono text-[11px]"
              >
                {b}
              </span>
            ))}
            {row.original.eligibleBranches.length > 3 && (
              <span className="text-[11px] font-mono text-muted-foreground self-center">
                +{row.original.eligibleBranches.length - 3}
              </span>
            )}
          </div>
        ),
      },
      {
        accessorKey: 'applicationDeadline',
        header: 'Deadline',
        cell: ({ row }) => (
          <span className="font-mono text-xs text-muted-foreground whitespace-nowrap">
            {row.original.applicationDeadline}
          </span>
        ),
      },
      {
        accessorKey: 'closed',
        header: 'Status',
        cell: ({ row }) => (
          <Badge
            variant={row.original.closed ? 'secondary' : 'default'}
            className="text-xs font-mono"
          >
            {row.original.closed ? 'CLOSED' : 'ACTIVE'}
          </Badge>
        ),
      },
      {
        accessorKey: 'totalApplications',
        header: 'Applications',
        cell: ({ row }) => (
          <div className="flex items-center gap-1.5 font-mono text-xs">
            <span className="font-semibold text-foreground">
              {row.original.totalApplications}
            </span>
            <span className="text-muted-foreground">
              ({row.original.shortlistedApplications} shortlisted)
            </span>
          </div>
        ),
      },
      {
        id: 'actions',
        header: () => <span className="sr-only">Actions</span>,
        cell: ({ row }) => {
          const drive = row.original
          return (
            <div className="flex items-center justify-end gap-1.5 whitespace-nowrap">
              <Button
                variant="outline"
                size="sm"
                onClick={() => navigate(`/officer/drives/${drive.id}/applications`)}
                className="h-7 text-xs px-2.5 gap-1 bg-accent/5 hover:bg-accent/15 border-accent/30 text-accent font-medium"
                aria-label={`Candidates: Review applications for ${drive.jobTitle}`}
              >
                <Users className="h-3 w-3" />
                <span>Candidates</span>
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={() => navigate(`/officer/drives/${drive.id}`)}
                className="h-7 text-xs px-2"
                aria-label={`Inspect drive ${drive.id}`}
              >
                <Eye className="h-3 w-3" />
              </Button>
              {!drive.closed && (
                <>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => handleEdit(drive)}
                    className="h-7 text-xs px-2 text-muted-foreground hover:text-foreground"
                    aria-label={`Edit drive ${drive.id}`}
                  >
                    <Edit3 className="h-3 w-3" />
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => handleClose(drive)}
                    className="h-7 text-xs px-2 text-destructive hover:bg-destructive/10 border-destructive/20"
                    aria-label={`Close drive ${drive.id}`}
                  >
                    <Lock className="h-3 w-3" />
                  </Button>
                </>
              )}
            </div>
          )
        },
      },
    ],
    [navigate]
  )

  return (
    <div className="space-y-6">
      <PageHeading
        title="Campus Recruitment Drives"
        description="Oversee company recruitment campaigns, academic qualification requirements, submission deadlines, and closure lifecycle."
        breadcrumbs={[
          { label: 'Officer', href: '/officer/dashboard' },
          { label: 'Recruitment Drives' },
        ]}
        actions={
          <Button size="sm" onClick={handleCreate} className="text-xs gap-1.5">
            <Plus className="h-3.5 w-3.5" />
            <span>Launch Recruitment Drive</span>
          </Button>
        }
      />

      {/* Filter and Criteria Toolbar */}
      <div className="flex flex-col sm:flex-row items-center justify-between gap-3 p-3.5 rounded-md border border-border bg-card shadow-xs">
        <div className="flex flex-wrap items-center gap-3 w-full sm:w-auto">
          {/* Company Filter Dropdown */}
          <div className="w-full sm:w-56">
            <select
              value={selectedCompanyId}
              onChange={(e) => {
                setSelectedCompanyId(e.target.value)
                setPage(0)
              }}
              className="w-full h-9 rounded-sm border border-input bg-card px-2.5 text-xs text-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
              aria-label="Filter drives by corporate partner"
            >
              <option value="">All Corporate Partners</option>
              {companies.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name} ({c.id})
                </option>
              ))}
            </select>
          </div>

          {/* Active Only Filter */}
          <label className="flex items-center gap-2 text-xs font-medium text-foreground cursor-pointer select-none">
            <input
              type="checkbox"
              checked={activeOnly}
              onChange={(e) => {
                setActiveOnly(e.target.checked)
                setPage(0)
              }}
              className="rounded-xs border-input h-4 w-4 text-primary focus:ring-accent"
            />
            <span>Active Campaigns Only</span>
          </label>
        </div>

        <div className="text-xs text-muted-foreground font-mono self-end sm:self-center">
          Showing {drives.length} of {totalElements} recruitment drives
        </div>
      </div>

      {/* High-Density Drives DataTable */}
      <DataTable
        columns={columns}
        data={drives}
        isLoading={isLoading}
        density="compact"
        emptyTitle="No recruitment drives found"
        emptyDescription="No drives match your current filter settings. Launch a new drive to begin accepting student applications."
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

      {/* Drive Create/Edit Modal */}
      <DriveDialog
        open={createModalOpen}
        onOpenChange={setCreateModalOpen}
        drive={editingDrive}
        onSuccess={() => refetch()}
      />

      {/* Consequential Drive Close Dialog */}
      <DriveCloseDialog
        open={Boolean(closingDrive)}
        onOpenChange={(open) => !open && setClosingDrive(null)}
        drive={closingDrive}
        onSuccess={() => refetch()}
      />
    </div>
  )
}
