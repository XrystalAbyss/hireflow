import * as React from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import {
  Building2,
  Users,
  Edit3,
  Lock,
  ArrowLeft,
  Calendar,
  GraduationCap,
} from 'lucide-react'
import { PageHeading } from '@/components/domain/page-heading'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { DriveDialog } from '../components/DriveDialog'
import { DriveCloseDialog } from '../components/DriveCloseDialog'
import { useOfficerDrive } from '../api/officerApi'

export function OfficerDriveDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()

  const [editModalOpen, setEditModalOpen] = React.useState(false)
  const [closeModalOpen, setCloseModalOpen] = React.useState(false)

  const { data: drive, isLoading, refetch } = useOfficerDrive(id || '')

  if (isLoading) {
    return (
      <div className="p-8 text-center text-xs text-muted-foreground animate-pulse">
        Loading recruitment drive dossier...
      </div>
    )
  }

  if (!drive) {
    return (
      <div className="p-8 rounded-md border border-border bg-card text-center space-y-3">
        <p className="text-sm font-semibold text-foreground">Drive Not Found</p>
        <p className="text-xs text-muted-foreground">
          Recruitment drive '{id}' does not exist or has been removed.
        </p>
        <Button size="sm" variant="outline" onClick={() => navigate('/officer/drives')}>
          Back to Drives Directory
        </Button>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <PageHeading
        title={drive.jobTitle}
        description={`Recruitment Campaign ID: ${drive.id} • Partner: ${drive.companyName}`}
        breadcrumbs={[
          { label: 'Officer', href: '/officer/dashboard' },
          { label: 'Recruitment Drives', href: '/officer/drives' },
          { label: drive.id },
        ]}
        badge={
          <Badge
            variant={drive.closed ? 'secondary' : 'default'}
            className="text-xs font-mono"
          >
            {drive.closed ? 'CLOSED' : 'ACTIVE'}
          </Badge>
        }
        actions={
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={() => navigate('/officer/drives')}
              className="text-xs gap-1.5"
            >
              <ArrowLeft className="h-3.5 w-3.5" />
              <span>Back</span>
            </Button>
            {!drive.closed && (
              <>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setEditModalOpen(true)}
                  className="text-xs gap-1.5"
                >
                  <Edit3 className="h-3.5 w-3.5" />
                  <span>Edit Specifications</span>
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setCloseModalOpen(true)}
                  className="text-xs gap-1.5 text-destructive hover:bg-destructive/10 border-destructive/20"
                >
                  <Lock className="h-3.5 w-3.5" />
                  <span>Close Campaign</span>
                </Button>
              </>
            )}
            <Button
              size="sm"
              onClick={() => navigate(`/officer/drives/${drive.id}/applications`)}
              className="text-xs gap-1.5"
            >
              <Users className="h-3.5 w-3.5" />
              <span>Review Candidates ({drive.totalApplications})</span>
            </Button>
          </div>
        }
      />

      {/* Snapshot Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="p-4 rounded-md border border-border bg-card shadow-xs">
          <div className="flex items-center gap-1.5 text-muted-foreground text-xs font-mono uppercase">
            <Building2 className="h-3.5 w-3.5" />
            <span>Sponsoring Partner</span>
          </div>
          <p className="text-sm font-semibold text-foreground mt-1.5">
            {drive.companyName}
          </p>
          <span className="font-mono text-xs text-muted-foreground">{drive.companyId}</span>
        </div>

        <div className="p-4 rounded-md border border-border bg-card shadow-xs">
          <div className="flex items-center gap-1.5 text-muted-foreground text-xs font-mono uppercase">
            <GraduationCap className="h-3.5 w-3.5" />
            <span>Minimum CGPA</span>
          </div>
          <p className="text-2xl font-serif font-bold text-accent mt-1">
            {drive.minimumCgpa.toFixed(2)}
          </p>
          <span className="text-xs text-muted-foreground">Mandatory academic cutoff</span>
        </div>

        <div className="p-4 rounded-md border border-border bg-card shadow-xs">
          <div className="flex items-center gap-1.5 text-muted-foreground text-xs font-mono uppercase">
            <Calendar className="h-3.5 w-3.5" />
            <span>Application Deadline</span>
          </div>
          <p className="font-mono text-lg font-semibold text-foreground mt-1">
            {drive.applicationDeadline}
          </p>
          <span className="text-xs text-muted-foreground">
            {drive.closed ? 'Campaign closed' : 'Submissions open'}
          </span>
        </div>

        <div className="p-4 rounded-md border border-border bg-card shadow-xs">
          <div className="flex items-center gap-1.5 text-muted-foreground text-xs font-mono uppercase">
            <Users className="h-3.5 w-3.5" />
            <span>Application Volume</span>
          </div>
          <p className="text-2xl font-serif font-bold text-foreground mt-1">
            {drive.totalApplications}
          </p>
          <span className="text-xs text-accent font-mono font-medium">
            {drive.shortlistedApplications} shortlisted
          </span>
        </div>
      </div>

      {/* Academic Qualifications Dossier */}
      <div className="rounded-md border border-border bg-card p-5 space-y-4 shadow-xs">
        <h2 className="font-serif text-base font-semibold text-foreground">
          Eligibility & Academic Rules
        </h2>
        <div className="space-y-3 text-xs">
          <div>
            <span className="text-muted-foreground block font-medium uppercase tracking-wider mb-1.5">
              Eligible Degree Branches & Programs
            </span>
            <div className="flex flex-wrap gap-1.5">
              {drive.eligibleBranches.map((b) => (
                <Badge key={b} variant="secondary" className="font-mono text-xs px-2.5 py-1">
                  {b}
                </Badge>
              ))}
            </div>
          </div>

          <div className="pt-3 border-t border-border flex flex-col sm:flex-row sm:items-center justify-between gap-4">
            <div className="text-xs text-muted-foreground">
              Candidate applications are evaluated against these criteria during submission and shortlisting review.
            </div>
            <Button
              size="sm"
              onClick={() => navigate(`/officer/drives/${drive.id}/applications`)}
              className="text-xs gap-1.5 shrink-0"
            >
              <Users className="h-3.5 w-3.5" />
              <span>Open Candidate Shortlisting Roster</span>
            </Button>
          </div>
        </div>
      </div>

      {/* Dialog Modals */}
      <DriveDialog
        open={editModalOpen}
        onOpenChange={setEditModalOpen}
        drive={drive}
        onSuccess={() => refetch()}
      />
      <DriveCloseDialog
        open={closeModalOpen}
        onOpenChange={setCloseModalOpen}
        drive={drive}
        onSuccess={() => refetch()}
      />
    </div>
  )
}
