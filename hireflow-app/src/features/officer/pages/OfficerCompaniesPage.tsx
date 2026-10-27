import * as React from 'react'
import { useNavigate } from 'react-router-dom'
import { Plus, Search, Edit3, Eye } from 'lucide-react'
import { type ColumnDef } from '@tanstack/react-table'
import { PageHeading } from '@/components/domain/page-heading'
import { DataTable } from '@/components/domain/data-table'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { Pagination } from '@/components/ui/pagination'
import { CompanyDialog } from '../components/CompanyDialog'
import { useOfficerCompanies } from '../api/officerApi'
import type { OfficerCompanyDetailDto } from '@/types/api'

export function OfficerCompaniesPage() {
  const navigate = useNavigate()
  const [page, setPage] = React.useState(0)
  const [searchInput, setSearchInput] = React.useState('')
  const [debouncedSearch, setDebouncedSearch] = React.useState('')
  const [modalOpen, setModalOpen] = React.useState(false)
  const [editingCompany, setEditingCompany] = React.useState<OfficerCompanyDetailDto | null>(null)

  // Debounce search input
  React.useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedSearch(searchInput.trim())
      setPage(0)
    }, 300)
    return () => clearTimeout(timer)
  }, [searchInput])

  const { data: pagedData, isLoading, refetch } = useOfficerCompanies(page, 15, debouncedSearch)
  const companies = pagedData?.content || []
  const totalPages = pagedData?.totalPages || 1
  const totalElements = pagedData?.totalElements || 0

  const handleCreate = () => {
    setEditingCompany(null)
    setModalOpen(true)
  }

  const handleEdit = (company: OfficerCompanyDetailDto) => {
    setEditingCompany(company)
    setModalOpen(true)
  }

  const columns = React.useMemo<ColumnDef<OfficerCompanyDetailDto>[]>(
    () => [
      {
        accessorKey: 'id',
        header: 'Partner ID',
        cell: ({ row }) => (
          <span className="font-mono text-xs font-semibold text-muted-foreground uppercase">
            {row.original.id}
          </span>
        ),
      },
      {
        accessorKey: 'name',
        header: 'Corporate Organization',
        cell: ({ row }) => (
          <div>
            <span className="font-medium text-foreground block">
              {row.original.name}
            </span>
            <span className="text-xs text-muted-foreground sm:hidden">
              {row.original.industry}
            </span>
          </div>
        ),
      },
      {
        accessorKey: 'industry',
        header: 'Industry Sector',
        cell: ({ row }) => (
          <span className="text-xs text-muted-foreground hidden sm:inline-block">
            {row.original.industry}
          </span>
        ),
      },
      {
        accessorKey: 'activeDrivesCount',
        header: 'Active Campaigns',
        cell: ({ row }) => {
          const count = row.original.activeDrivesCount
          return (
            <div className="flex items-center gap-1.5 font-mono">
              <Badge
                variant={count > 0 ? 'default' : 'secondary'}
                className="text-xs px-2 py-0.5"
              >
                {count} Active
              </Badge>
            </div>
          )
        },
      },
      {
        accessorKey: 'totalDrivesCount',
        header: 'Total Drives',
        cell: ({ row }) => (
          <span className="font-mono text-xs text-muted-foreground">
            {row.original.totalDrivesCount}
          </span>
        ),
      },
      {
        id: 'actions',
        header: () => <span className="sr-only">Actions</span>,
        cell: ({ row }) => (
          <div className="flex items-center justify-end gap-1.5 whitespace-nowrap">
            <Button
              variant="outline"
              size="sm"
              onClick={() => navigate(`/officer/companies/${row.original.id}`)}
              className="h-7 text-xs px-2.5 gap-1"
              aria-label={`Dossier for ${row.original.name}`}
            >
              <Eye className="h-3 w-3" />
              <span>Dossier</span>
            </Button>
            <Button
              variant="outline"
              size="sm"
              onClick={() => handleEdit(row.original)}
              className="h-7 text-xs px-2 gap-1 text-muted-foreground hover:text-foreground"
              aria-label={`Edit ${row.original.name}`}
            >
              <Edit3 className="h-3 w-3" />
              <span>Edit</span>
            </Button>
          </div>
        ),
      },
    ],
    [navigate]
  )

  return (
    <div className="space-y-6">
      <PageHeading
        title="Corporate Partner Management"
        description="Directory of accredited campus recruiters, enterprise employers, and placement sponsors."
        breadcrumbs={[
          { label: 'Officer', href: '/officer/dashboard' },
          { label: 'Corporate Partners' },
        ]}
        actions={
          <Button size="sm" onClick={handleCreate} className="text-xs gap-1.5">
            <Plus className="h-3.5 w-3.5" />
            <span>Register Corporate Partner</span>
          </Button>
        }
      />

      {/* Search and Filters Bar */}
      <div className="flex flex-col sm:flex-row items-center justify-between gap-3 p-3.5 rounded-md border border-border bg-card shadow-xs">
        <div className="relative w-full sm:w-80">
          <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
          <Input
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            placeholder="Search by company name, ID, or industry..."
            className="pl-8 text-xs h-9"
          />
        </div>
        <div className="text-xs text-muted-foreground font-mono self-end sm:self-center">
          Showing {companies.length} of {totalElements} registered partners
        </div>
      </div>

      {/* High-Density Companies DataTable */}
      <DataTable
        columns={columns}
        data={companies}
        isLoading={isLoading}
        density="compact"
        emptyTitle="No corporate partners found"
        emptyDescription="No registered companies match your search criteria. Register a new employer to launch recruitment campaigns."
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

      {/* Create / Edit Company Modal */}
      <CompanyDialog
        open={modalOpen}
        onOpenChange={setModalOpen}
        company={editingCompany}
        onSuccess={() => refetch()}
      />
    </div>
  )
}
