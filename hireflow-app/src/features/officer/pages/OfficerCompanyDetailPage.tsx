import * as React from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import {
  Briefcase,
  Users,
  Edit3,
  Plus,
  ArrowLeft,
} from 'lucide-react'
import { PageHeading } from '@/components/domain/page-heading'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { CompanyDialog } from '../components/CompanyDialog'
import { DriveDialog } from '../components/DriveDialog'
import { InterviewerDialog } from '../components/InterviewerDialog'
import {
  useOfficerCompany,
  useOfficerDrives,
  useOfficerInterviewers,
} from '../api/officerApi'

export function OfficerCompanyDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()

  const [editCompanyOpen, setEditCompanyOpen] = React.useState(false)
  const [createDriveOpen, setCreateDriveOpen] = React.useState(false)
  const [createInterviewerOpen, setCreateInterviewerOpen] = React.useState(false)

  const { data: company, isLoading, refetch } = useOfficerCompany(id || '')
  const { data: drivesData, refetch: refetchDrives } = useOfficerDrives(0, 50, id)
  const { data: interviewers = [], refetch: refetchInterviewers } = useOfficerInterviewers(id)

  const drives = drivesData?.content || []

  if (isLoading) {
    return (
      <div className="p-8 text-center text-xs text-muted-foreground animate-pulse">
        Loading corporate partner dossier...
      </div>
    )
  }

  if (!company) {
    return (
      <div className="p-8 rounded-md border border-border bg-card text-center space-y-3">
        <p className="text-sm font-semibold text-foreground">Corporate Partner Not Found</p>
        <p className="text-xs text-muted-foreground">
          The requested organization profile does not exist in the institutional registry.
        </p>
        <Button size="sm" variant="outline" onClick={() => navigate('/officer/companies')}>
          Back to Directory
        </Button>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <PageHeading
        title={company.name}
        description={`Corporate Partner ID: ${company.id} • Industry: ${company.industry}`}
        breadcrumbs={[
          { label: 'Officer', href: '/officer/dashboard' },
          { label: 'Corporate Partners', href: '/officer/companies' },
          { label: company.id },
        ]}
        actions={
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={() => navigate('/officer/companies')}
              className="text-xs gap-1.5"
            >
              <ArrowLeft className="h-3.5 w-3.5" />
              <span>Back</span>
            </Button>
            <Button
              variant="outline"
              size="sm"
              onClick={() => setEditCompanyOpen(true)}
              className="text-xs gap-1.5"
            >
              <Edit3 className="h-3.5 w-3.5" />
              <span>Edit Partner</span>
            </Button>
            <Button
              size="sm"
              onClick={() => setCreateDriveOpen(true)}
              className="text-xs gap-1.5"
            >
              <Plus className="h-3.5 w-3.5" />
              <span>Launch Drive</span>
            </Button>
          </div>
        }
      />

      {/* Snapshot Information Card */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div className="p-4 rounded-md border border-border bg-card shadow-xs">
          <span className="text-xs font-mono uppercase text-muted-foreground">Industry Sector</span>
          <p className="text-sm font-semibold text-foreground mt-1">{company.industry}</p>
        </div>
        <div className="p-4 rounded-md border border-border bg-card shadow-xs">
          <span className="text-xs font-mono uppercase text-muted-foreground">Active Campaigns</span>
          <p className="text-2xl font-serif font-bold text-accent mt-1">{company.activeDrivesCount}</p>
        </div>
        <div className="p-4 rounded-md border border-border bg-card shadow-xs">
          <span className="text-xs font-mono uppercase text-muted-foreground">Total Registered Drives</span>
          <p className="text-2xl font-serif font-bold text-foreground mt-1">{company.totalDrivesCount}</p>
        </div>
      </div>

      {/* Associated Recruitment Drives */}
      <div className="rounded-md border border-border bg-card shadow-xs overflow-hidden">
        <div className="p-4 border-b border-border flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Briefcase className="h-4 w-4 text-accent" />
            <h2 className="font-serif text-base font-semibold text-foreground">
              Recruitment Campaigns ({drives.length})
            </h2>
          </div>
        </div>

        {drives.length === 0 ? (
          <div className="p-8 text-center text-xs text-muted-foreground">
            No recruitment drives registered for this partner yet.
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs border-collapse">
              <thead className="bg-muted/60 border-b border-border text-muted-foreground font-mono uppercase tracking-wider">
                <tr>
                  <th className="py-2.5 px-3.5">Drive ID</th>
                  <th className="py-2.5 px-3.5">Job Title</th>
                  <th className="py-2.5 px-3.5">Min CGPA</th>
                  <th className="py-2.5 px-3.5">Deadline</th>
                  <th className="py-2.5 px-3.5">Status</th>
                  <th className="py-2.5 px-3.5 text-center">Applications</th>
                  <th className="py-2.5 px-3.5 text-center">Shortlisted</th>
                  <th className="py-2.5 px-3.5 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {drives.map((d) => (
                  <tr key={d.id} className="hover:bg-muted/40 transition-colors">
                    <td className="py-2.5 px-3.5 font-mono text-muted-foreground">{d.id}</td>
                    <td className="py-2.5 px-3.5 font-medium text-foreground">{d.jobTitle}</td>
                    <td className="py-2.5 px-3.5 font-mono">{d.minimumCgpa.toFixed(2)}</td>
                    <td className="py-2.5 px-3.5 font-mono text-muted-foreground">{d.applicationDeadline}</td>
                    <td className="py-2.5 px-3.5">
                      <Badge variant={d.closed ? 'secondary' : 'default'} className="text-xs">
                        {d.closed ? 'Closed' : 'Active'}
                      </Badge>
                    </td>
                    <td className="py-2.5 px-3.5 text-center font-mono">{d.totalApplications}</td>
                    <td className="py-2.5 px-3.5 text-center font-mono text-accent font-semibold">
                      {d.shortlistedApplications}
                    </td>
                    <td className="py-2.5 px-3.5 text-right whitespace-nowrap">
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => navigate(`/officer/drives/${d.id}`)}
                        className="h-7 text-xs px-2"
                      >
                        Inspect
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Associated Corporate Interviewers */}
      <div className="rounded-md border border-border bg-card shadow-xs overflow-hidden">
        <div className="p-4 border-b border-border flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Users className="h-4 w-4 text-brass-600" />
            <h2 className="font-serif text-base font-semibold text-foreground">
              Corporate Interviewers ({interviewers.length})
            </h2>
          </div>
          <Button
            size="sm"
            variant="outline"
            onClick={() => setCreateInterviewerOpen(true)}
            className="text-xs gap-1"
          >
            <Plus className="h-3 w-3" />
            <span>Add Interviewer</span>
          </Button>
        </div>

        {interviewers.length === 0 ? (
          <div className="p-8 text-center text-xs text-muted-foreground">
            No corporate interviewers registered for this company yet.
          </div>
        ) : (
          <div className="p-4 grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-3">
            {interviewers.map((i) => (
              <div
                key={i.id}
                className="p-3 rounded-sm border border-border bg-muted/20 flex flex-col justify-between"
              >
                <div>
                  <span className="font-mono text-xs text-muted-foreground">{i.id}</span>
                  <p className="font-medium text-foreground text-sm mt-0.5">{i.name}</p>
                </div>
                <span className="text-xs text-muted-foreground mt-2 block">
                  Employer: {i.companyName}
                </span>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Dialog Modals */}
      <CompanyDialog
        open={editCompanyOpen}
        onOpenChange={setEditCompanyOpen}
        company={company}
        onSuccess={() => refetch()}
      />
      <DriveDialog
        open={createDriveOpen}
        onOpenChange={setCreateDriveOpen}
        preselectedCompanyId={company.id}
        onSuccess={() => {
          refetch()
          refetchDrives()
        }}
      />
      <InterviewerDialog
        open={createInterviewerOpen}
        onOpenChange={setCreateInterviewerOpen}
        preselectedCompanyId={company.id}
        onSuccess={() => refetchInterviewers()}
      />
    </div>
  )
}
