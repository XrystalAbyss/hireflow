import * as React from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import {
  DoorOpen,
  Plus,
  ArrowRight,
  CalendarClock,
} from 'lucide-react'
import { PageHeading } from '@/components/domain/page-heading'
import { ErrorState } from '@/components/domain/error-state'
import { Button } from '@/components/ui/button'
import { OfficerStatCard } from '../components/OfficerStatCard'
import { CompanyDialog } from '../components/CompanyDialog'
import { DriveDialog } from '../components/DriveDialog'
import { RoomDialog } from '../components/RoomDialog'
import {
  useOfficerCompanies,
  useOfficerDrives,
  useOfficerInterviewers,
  useOfficerRooms,
} from '../api/officerApi'

export function OfficerDashboardPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const [companyModalOpen, setCompanyModalOpen] = React.useState(false)
  const [driveModalOpen, setDriveModalOpen] = React.useState(false)
  const [roomModalOpen, setRoomModalOpen] = React.useState(false)

  // Fetch operational summaries from authoritative endpoints
  const { data: companiesData, isLoading: loadingCompanies, error: companiesError } = useOfficerCompanies(0, 20)
  const { data: drivesData, error: drivesError } = useOfficerDrives(0, 20, undefined, false)
  const { data: activeDrivesData, isLoading: loadingActiveDrives, error: activeDrivesError } = useOfficerDrives(0, 100, undefined, true)
  const { data: interviewers = [], isLoading: loadingInterviewers, error: interviewersError } = useOfficerInterviewers()
  const { data: rooms = [], isLoading: loadingRooms, error: roomsError } = useOfficerRooms()
  const summaryError = companiesError || drivesError || activeDrivesError || interviewersError || roomsError

  const totalCompanies = companiesData?.totalElements ?? 0
  const totalDrives = drivesData?.totalElements ?? 0
  const activeDrivesCount = activeDrivesData?.totalElements ?? 0
  const totalInterviewers = interviewers.length
  const totalRooms = rooms.length

  // Calculate pending shortlisting workload across loaded active drives
  const activeDrives = activeDrivesData?.content || []
  const totalApplications = activeDrives.reduce((acc, d) => acc + d.totalApplications, 0)
  const totalShortlisted = activeDrives.reduce((acc, d) => acc + d.shortlistedApplications, 0)
  const displayedDriveCount = activeDrives.length

  if (summaryError) {
    return <ErrorState title="Unable to refresh placement operations" message="The live operational summary could not be loaded. Check the connection and try again." onRetry={() => queryClient.invalidateQueries({ queryKey: ['officer'] })} />
  }

  return (
    <div className="space-y-6">
      <PageHeading
        title="Placement Operations Command Center"
        description="Operational administration for corporate partnerships, campus recruitment campaigns, candidate pool review, and venue resource allocation."
        breadcrumbs={[
          { label: 'Officer', href: '/officer/dashboard' },
          { label: 'Command Center' },
        ]}
        actions={
          <div className="flex flex-wrap items-center gap-2">
            <Button variant="outline" size="sm" onClick={() => queryClient.invalidateQueries({ queryKey: ['officer'] })}>Refresh live data</Button>
            <Button
              size="sm"
              variant="outline"
              onClick={() => setCompanyModalOpen(true)}
              className="text-xs gap-1.5"
            >
              <Plus className="h-3.5 w-3.5 text-accent" />
              <span>Register Partner</span>
            </Button>
            <Button
              size="sm"
              onClick={() => setDriveModalOpen(true)}
              className="text-xs gap-1.5"
            >
              <Plus className="h-3.5 w-3.5" />
              <span>Launch Drive</span>
            </Button>
          </div>
        }
      />

      <p className="text-xs text-muted-foreground">Drive data refreshes about every 30 seconds while this page is visible. Use Refresh live data for all summaries.</p>

      <section aria-label="Placement Operational Metrics" className="overflow-hidden rounded-lg border border-border bg-card">
        <div className="grid grid-cols-2 lg:grid-cols-4">
          <OfficerStatCard
            label="Active Recruitment Drives"
            value={loadingActiveDrives ? '...' : activeDrivesCount}
            subtext={`${totalDrives} lifetime campaigns registered`}
            className="border-r border-b border-border lg:border-b-0"
            onClick={() => navigate('/officer/drives')}
          />
          <OfficerStatCard
            label="Corporate Partners"
            value={loadingCompanies ? '...' : totalCompanies}
            subtext="Authorized recruiting organizations"
            className="border-b border-border lg:border-r lg:border-b-0"
            onClick={() => navigate('/officer/companies')}
          />
          <OfficerStatCard
            label="Registered Interviewers"
            value={loadingInterviewers ? '...' : totalInterviewers}
            subtext="Corporate assessment leads in roster"
            className="border-r border-border"
            onClick={() => navigate('/officer/resources')}
          />
          <OfficerStatCard
            label="Interview Rooms in Pool"
            value={loadingRooms ? '...' : totalRooms}
            subtext="Campus facilities for interview slots"
            onClick={() => navigate('/officer/resources')}
          />
        </div>
      </section>

      {/* Quick Launchpad & Operational Workload */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Active Recruitment Drives Table (2 cols) */}
        <div className="lg:col-span-2 rounded-md border border-border bg-card shadow-xs overflow-hidden">
          <div className="p-4 border-b border-border flex items-center justify-between">
            <div>
              <h2 className="font-serif text-base font-semibold text-foreground">
                Active Recruitment Campaigns
              </h2>
              <p className="text-xs text-muted-foreground">
                Open recruitment drives currently accepting applications
              </p>
            </div>
            <Button
              variant="outline"
              size="sm"
              onClick={() => navigate('/officer/drives')}
              className="text-xs gap-1"
            >
              <span>View All</span>
              <ArrowRight className="h-3 w-3" />
            </Button>
          </div>

          {activeDrives.length === 0 ? (
            <div className="p-8 text-center text-xs text-muted-foreground">
              No active recruitment drives currently in progress.
            </div>
          ) : (
            <>
              <div className="divide-y divide-border md:hidden">
                {activeDrives.slice(0, 5).map((drive) => (
                  <div key={drive.id} className="space-y-3 p-4">
                    <div className="min-w-0">
                      <p className="font-medium text-foreground">{drive.jobTitle}</p>
                      <p className="text-xs text-muted-foreground">{drive.companyName} · {drive.id}</p>
                    </div>
                    <div className="grid grid-cols-2 gap-2 text-xs text-muted-foreground">
                      <span>Applications <strong className="tabular-nums text-foreground">{drive.totalApplications}</strong></span>
                      <span>Shortlisted <strong className="tabular-nums text-foreground">{drive.shortlistedApplications}</strong></span>
                      <span>Min CGPA <strong className="tabular-nums text-foreground">{drive.minimumCgpa.toFixed(2)}</strong></span>
                      <span>Deadline <strong className="tabular-nums text-foreground">{drive.applicationDeadline}</strong></span>
                    </div>
                    <Button variant="outline" size="sm" onClick={() => navigate(`/officer/drives/${drive.id}/applications`)} className="min-h-10 w-full">Review Candidates</Button>
                  </div>
                ))}
              </div>
              <div className="hidden overflow-x-auto md:block">
                <table className="w-full text-left text-xs border-collapse">
                <thead className="bg-muted/60 border-b border-border text-muted-foreground font-mono uppercase tracking-wider">
                  <tr>
                    <th className="py-2.5 px-3.5">Drive ID</th>
                    <th className="py-2.5 px-3.5">Job Title & Partner</th>
                    <th className="py-2.5 px-3.5">Min CGPA</th>
                    <th className="py-2.5 px-3.5">Deadline</th>
                    <th className="py-2.5 px-3.5 text-center">Applications</th>
                    <th className="py-2.5 px-3.5 text-center">Shortlisted</th>
                    <th className="py-2.5 px-3.5 text-right">Action</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {activeDrives.slice(0, 5).map((drive) => (
                    <tr
                      key={drive.id}
                      className="hover:bg-muted/40 transition-colors"
                    >
                      <td className="py-2.5 px-3.5 font-mono text-muted-foreground">
                        {drive.id}
                      </td>
                      <td className="py-2.5 px-3.5">
                        <span className="font-medium text-foreground block">
                          {drive.jobTitle}
                        </span>
                        <span className="text-muted-foreground">{drive.companyName}</span>
                      </td>
                      <td className="py-2.5 px-3.5 font-mono">
                        {drive.minimumCgpa.toFixed(2)}
                      </td>
                      <td className="py-2.5 px-3.5 font-mono text-muted-foreground whitespace-nowrap">
                        {drive.applicationDeadline}
                      </td>
                      <td className="py-2.5 px-3.5 text-center font-mono font-medium">
                        {drive.totalApplications}
                      </td>
                      <td className="py-2.5 px-3.5 text-center font-mono text-accent font-semibold">
                        {drive.shortlistedApplications}
                      </td>
                      <td className="py-2.5 px-3.5 text-right whitespace-nowrap">
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => navigate(`/officer/drives/${drive.id}/applications`)}
                          className="h-7 text-xs px-2"
                        >
                          Review Candidates
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
                </table>
              </div>
            </>
          )}
        </div>

        {/* Operational Attention & Quick Tools (1 col) */}
        <div className="space-y-6">
          {/* Workload Summary Box */}
          <div className="rounded-md border border-border bg-card p-4 space-y-3.5 shadow-xs">
            <h3 className="font-serif text-sm font-semibold text-foreground">Application status snapshot</h3>
            <p className="text-xs text-muted-foreground leading-relaxed">
              Counts below cover {displayedDriveCount} of {activeDrivesCount} active drives. Review candidate statuses within each drive before scheduling.
            </p>

            <div className="rounded-sm bg-muted/50 p-3 space-y-2 border border-border/80">
              <div className="flex justify-between text-xs">
                <span className="text-muted-foreground">Applications in view:</span>
                <span className="font-mono font-bold text-foreground">{totalApplications}</span>
              </div>
              <div className="flex justify-between text-xs">
                <span className="text-muted-foreground">Shortlisted in view:</span>
                <span className="font-mono font-bold text-accent">{totalShortlisted}</span>
              </div>
              <div className="flex justify-between text-xs">
                <span className="text-muted-foreground">Other statuses in view:</span>
                <span className="font-mono font-bold text-amber-800 dark:text-amber-300">
                  {Math.max(0, totalApplications - totalShortlisted)}
                </span>
              </div>
            </div>

          </div>

          {/* Quick Operations Launchpad */}
          <div className="rounded-md border border-border bg-card p-4 space-y-2.5 shadow-xs">
            <h3 className="font-serif text-sm font-semibold text-foreground">
              Administrative Actions
            </h3>
            <div className="grid grid-cols-1 gap-2">
              <button
                type="button"
                onClick={() => setRoomModalOpen(true)}
                className="w-full flex items-center justify-between p-2.5 text-xs text-left rounded-sm border border-border hover:bg-muted/60 transition-colors"
              >
                <span className="flex items-center gap-2 font-medium text-foreground">
                  <DoorOpen className="h-3.5 w-3.5 text-accent" />
                  Register Interview Room
                </span>
                <Plus className="h-3.5 w-3.5 text-muted-foreground" />
              </button>

              <button
                type="button"
                onClick={() => navigate('/officer/scheduling')}
                className="w-full flex items-center justify-between p-2.5 text-xs text-left rounded-sm border border-border hover:bg-muted/60 transition-colors"
              >
                <span className="flex items-center gap-2 font-medium text-foreground">
                  <CalendarClock className="h-3.5 w-3.5 text-brass-600" />
                  Interview Scheduling Studio
                </span>
                <ArrowRight className="h-3.5 w-3.5 text-muted-foreground" />
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* Modals for Quick Actions */}
      <CompanyDialog
        open={companyModalOpen}
        onOpenChange={setCompanyModalOpen}
      />
      <DriveDialog
        open={driveModalOpen}
        onOpenChange={setDriveModalOpen}
      />
      <RoomDialog
        open={roomModalOpen}
        onOpenChange={setRoomModalOpen}
      />
    </div>
  )
}


