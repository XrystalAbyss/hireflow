import React, { useState, useMemo } from 'react'
import { Link } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { PasswordField } from '@/components/ui/password-field'
import { Textarea } from '@/components/ui/textarea'
import { Checkbox } from '@/components/ui/checkbox'
import { FormField } from '@/components/ui/form-field'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Separator } from '@/components/ui/separator'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog'
import { AlertDialog } from '@/components/ui/alert-dialog'
import { ApplicationStatusBadge } from '@/components/domain/application-status-badge'
import { EligibilityStatusBadge } from '@/components/domain/eligibility-status-badge'
import { PageHeading } from '@/components/domain/page-heading'
import { SectionHeading } from '@/components/domain/section-heading'
import { DataTable } from '@/components/domain/data-table'
import { TableToolbar } from '@/components/domain/table-toolbar'
import { EmptyState } from '@/components/domain/empty-state'
import { ErrorState } from '@/components/domain/error-state'
import { LoadingSkeleton } from '@/components/domain/loading-skeleton'
import { ConfirmationDialog } from '@/components/domain/confirmation-dialog'
import { NoticeBanner } from '@/components/domain/notice-banner'
import { StudentShell } from '@/components/shells/StudentShell'
import { OfficerShell } from '@/components/shells/OfficerShell'
import type { ColumnDef } from '@tanstack/react-table'
import type { ApplicationStatus } from '@/types/api'
import {
  Sparkles,
  CheckCircle2,
  ExternalLink,
} from 'lucide-react'
import { cn } from '@/lib/utils'

// Realistic candidate application fixture record
interface CandidateApplicationFixture {
  applicationId: string
  studentRollNo: string
  studentName: string
  branch: string
  cgpa: number
  driveName: string
  appliedAt: string
  status: ApplicationStatus
}

const INITIAL_APPLICATIONS: CandidateApplicationFixture[] = [
  {
    applicationId: 'app-101',
    studentRollNo: '2022CS0144',
    studentName: 'Aarav Sharma',
    branch: 'Computer Science',
    cgpa: 9.15,
    driveName: 'Google SWE Campus 2026',
    appliedAt: '2026-10-14T09:30:00',
    status: 'SHORTLISTED',
  },
  {
    applicationId: 'app-102',
    studentRollNo: '2022EC0089',
    studentName: 'Ananya Deshmukh',
    branch: 'Electronics & Comm',
    cgpa: 8.84,
    driveName: 'Qualcomm Hardware Intern',
    appliedAt: '2026-10-14T10:15:00',
    status: 'APPLIED',
  },
  {
    applicationId: 'app-103',
    studentRollNo: '2022CS0210',
    studentName: 'Rohan Mehra',
    branch: 'Computer Science',
    cgpa: 7.92,
    driveName: 'Microsoft Cloud Apprentice',
    appliedAt: '2026-10-15T11:45:00',
    status: 'SCHEDULED',
  },
  {
    applicationId: 'app-104',
    studentRollNo: '2022ME0045',
    studentName: 'Pooja Sundaram',
    branch: 'Mechanical Eng',
    cgpa: 8.65,
    driveName: 'Tata Motors R&D Drive',
    appliedAt: '2026-10-15T14:20:00',
    status: 'SELECTED',
  },
  {
    applicationId: 'app-105',
    studentRollNo: '2022IT0112',
    studentName: 'Vikramaditya Rao',
    branch: 'Information Tech',
    cgpa: 6.95,
    driveName: 'Amazon SDE Placement',
    appliedAt: '2026-10-16T08:10:00',
    status: 'REJECTED',
  },
  {
    applicationId: 'app-106',
    studentRollNo: '2022CS0078',
    studentName: 'Sneha Kulkarni',
    branch: 'Computer Science',
    cgpa: 9.42,
    driveName: 'Google SWE Campus 2026',
    appliedAt: '2026-10-16T12:00:00',
    status: 'ELIGIBLE',
  },
  {
    applicationId: 'app-107',
    studentRollNo: '2022EC0156',
    studentName: 'Devansh Verma',
    branch: 'Electronics & Comm',
    cgpa: 8.21,
    driveName: 'Texas Instruments Firmware',
    appliedAt: '2026-10-17T15:30:00',
    status: 'APPLIED',
  },
  {
    applicationId: 'app-108',
    studentRollNo: '2022CS0301',
    studentName: 'Meera Nambiar',
    branch: 'Computer Science',
    cgpa: 8.78,
    driveName: 'Goldman Sachs Analyst',
    appliedAt: '2026-10-18T09:00:00',
    status: 'SHORTLISTED',
  },
]

export const ComponentWorkshopPage: React.FC = () => {
  // Navigation tab state
  const [activeTab, setActiveTab] = useState<'catalog' | 'student-shell' | 'officer-shell'>('catalog')

  // Interactive component states
  const [buttonClickCount, setButtonClickCount] = useState(0)
  const [btnLoading, setBtnLoading] = useState(false)
  const [inputValue, setInputValue] = useState('')
  const [passwordValue, setPasswordValue] = useState('')
  const [selectValue, setSelectValue] = useState('btech')
  const [checkboxValue, setCheckboxValue] = useState(true)
  const [hasFormError, setHasFormError] = useState(false)
  const [toastMessage, setToastMessage] = useState<string | null>(null)
  const [tableSearch, setTableSearch] = useState('')
  const [tableStatusFilter, setTableStatusFilter] = useState('ALL')
  const [applications, setApplications] = useState(INITIAL_APPLICATIONS)
  const [isConfirmOpen, setIsConfirmOpen] = useState(false)
  const [confirmTargetApp, setConfirmTargetApp] = useState<string | null>(null)
  const [isAlertOpen, setIsAlertOpen] = useState(false)

  // Feedback action handler
  const triggerToast = (msg: string) => {
    setToastMessage(msg)
    setTimeout(() => setToastMessage(null), 4000)
  }

  // Handle individual candidate shortlisting
  const handleShortlist = (appId: string) => {
    setApplications((prev) =>
      prev.map((app) => (app.applicationId === appId ? { ...app, status: 'SHORTLISTED' } : app))
    )
    triggerToast(`Candidate application ${appId} successfully shortlisted.`)
  }

  // Filtered applications for TanStack Table
  const filteredData = useMemo(() => {
    return applications.filter((app) => {
      const matchesSearch =
        app.studentName.toLowerCase().includes(tableSearch.toLowerCase()) ||
        app.studentRollNo.toLowerCase().includes(tableSearch.toLowerCase()) ||
        app.driveName.toLowerCase().includes(tableSearch.toLowerCase())
      const matchesStatus = tableStatusFilter === 'ALL' || app.status === tableStatusFilter
      return matchesSearch && matchesStatus
    })
  }, [applications, tableSearch, tableStatusFilter])

  // Table columns definition
  const columns = useMemo<ColumnDef<CandidateApplicationFixture, any>[]>(
    () => [
      {
        accessorKey: 'studentRollNo',
        header: 'Roll Number',
        cell: (info) => (
          <span className="font-mono text-xs font-semibold px-2 py-0.5 rounded bg-muted/80 text-foreground border border-border/60 inline-block">
            {String(info.getValue())}
          </span>
        ),
      },
      {
        accessorKey: 'studentName',
        header: 'Candidate Name',
        cell: (info) => (
          <span className="font-semibold text-sm text-foreground tracking-tight">
            {String(info.getValue())}
          </span>
        ),
      },
      {
        accessorKey: 'branch',
        header: 'Branch',
        cell: (info) => (
          <span className="text-xs font-medium text-muted-foreground">
            {String(info.getValue())}
          </span>
        ),
      },
      {
        accessorKey: 'cgpa',
        header: () => <div className="text-right">CGPA</div>,
        cell: (info) => {
          const val = Number(info.getValue())
          const isHigh = val >= 8.5
          return (
            <div className="text-right">
              <span
                className={cn(
                  "font-mono tabular-nums font-bold text-xs px-2 py-0.5 rounded border inline-block",
                  isHigh
                    ? "bg-academic-teal/10 text-academic-teal border-academic-teal/30"
                    : "bg-muted text-foreground border-border"
                )}
              >
                {val.toFixed(2)}
              </span>
            </div>
          )
        },
      },
      {
        accessorKey: 'driveName',
        header: 'Recruitment Drive',
        cell: (info) => (
          <span className="text-xs font-medium text-foreground">
            {String(info.getValue())}
          </span>
        ),
      },
      {
        accessorKey: 'status',
        header: 'Application Status',
        cell: (info) => <ApplicationStatusBadge status={info.getValue() as ApplicationStatus} />,
      },
      {
        id: 'actions',
        header: () => <div className="text-right">Action</div>,
        cell: ({ row }) => {
          const app = row.original
          const canShortlist = app.status === 'APPLIED' || app.status === 'ELIGIBLE'
          return (
            <div className="flex justify-end">
              {canShortlist ? (
                <Button
                  size="sm"
                  variant="outline"
                  className="h-7 text-xs font-semibold border-academic-teal/40 hover:bg-academic-teal/10 text-academic-teal"
                  onClick={() => {
                    setConfirmTargetApp(app.applicationId)
                    setIsConfirmOpen(true)
                  }}
                >
                  Shortlist
                </Button>
              ) : (
                <span className="text-xs text-muted-foreground font-medium italic">Processed</span>
              )}
            </div>
          )
        },
      },
    ],
    []
  )

  return (
    <div className="min-h-screen bg-background text-foreground flex flex-col">
      {/* Top Workshop Header */}
      <header className="sticky top-0 z-40 bg-midnight-navy text-warm-ivory border-b border-border shadow-md">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-2.5 sm:py-3 flex flex-col md:flex-row md:items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded bg-academic-teal flex items-center justify-center text-warm-ivory font-serif font-bold text-lg shadow-sm shrink-0">
              HF
            </div>
            <div>
              <h1 className="font-serif font-bold text-lg leading-tight tracking-wide flex items-center gap-2">
                <span>HireFlow Component Workshop</span>
                <span className="text-xs font-sans font-semibold tracking-wider uppercase px-2 py-0.5 rounded bg-muted-brass/30 text-muted-brass border border-muted-brass/40 whitespace-nowrap shrink-0">
                  CAP-8.1.1
                </span>
              </h1>
              <p className="text-xs text-warm-ivory/80 font-sans">
                Institutional Design System & Technical Component Audit
              </p>
            </div>
          </div>

          {/* Navigation Controls */}
          <div className="flex flex-wrap items-center gap-2">
            <div className="bg-midnight-navy/60 p-1 rounded-lg border border-warm-ivory/20 flex gap-1 text-xs">
              <button
                type="button"
                onClick={() => setActiveTab('catalog')}
                className={`px-2.5 sm:px-3 py-1.5 rounded font-medium transition-colors cursor-pointer ${
                  activeTab === 'catalog'
                    ? 'bg-academic-teal text-warm-ivory'
                    : 'text-warm-ivory/80 hover:text-warm-ivory'
                }`}
              >
                Component Catalog
              </button>
              <button
                type="button"
                onClick={() => setActiveTab('student-shell')}
                className={`px-2.5 sm:px-3 py-1.5 rounded font-medium transition-colors cursor-pointer ${
                  activeTab === 'student-shell'
                    ? 'bg-academic-teal text-warm-ivory'
                    : 'text-warm-ivory/80 hover:text-warm-ivory'
                }`}
              >
                Student Shell
              </button>
              <button
                type="button"
                onClick={() => setActiveTab('officer-shell')}
                className={`px-2.5 sm:px-3 py-1.5 rounded font-medium transition-colors cursor-pointer ${
                  activeTab === 'officer-shell'
                    ? 'bg-academic-teal text-warm-ivory'
                    : 'text-warm-ivory/80 hover:text-warm-ivory'
                }`}
              >
                Officer Shell
              </button>
            </div>

            <Link to="/auth/login">
              <Button size="sm" variant="outline" className="text-warm-ivory border-warm-ivory/30 hover:bg-warm-ivory/10">
                Sign In View
                <ExternalLink className="ml-1.5 h-3.5 w-3.5" />
              </Button>
            </Link>
          </div>
        </div>
      </header>

      {/* Global Interactive Notification Toast */}
      {toastMessage && (
        <div
          role="status"
          aria-live="polite"
          className="fixed bottom-6 right-6 z-50 max-w-md bg-midnight-navy text-warm-ivory px-4 py-3 rounded-lg shadow-xl border border-academic-teal flex items-center gap-3 animate-in fade-in slide-in-from-bottom-3"
        >
          <CheckCircle2 className="h-5 w-5 text-academic-teal shrink-0" />
          <p className="text-sm font-medium">{toastMessage}</p>
        </div>
      )}

      {/* Confirmation Dialog for Table Shortlist action */}
      <ConfirmationDialog
        open={isConfirmOpen}
        onOpenChange={setIsConfirmOpen}
        title="Confirm Candidate Shortlisting"
        description={`Are you sure you want to advance application ${confirmTargetApp} to SHORTLISTED status? This will make the candidate eligible for interview slot allocation.`}
        consequences="The candidate will be notified of shortlisting and made eligible for timetable slot allocation. This decision is permanently recorded in the placement audit registry."
        confirmText="Confirm Shortlist"
        onConfirm={() => {
          if (confirmTargetApp) {
            handleShortlist(confirmTargetApp)
            setConfirmTargetApp(null)
          }
        }}
      />

      {/* Reusable Alert Dialog */}
      <AlertDialog
        open={isAlertOpen}
        onOpenChange={setIsAlertOpen}
        title="Terminate Recruitment Drive?"
        description="This action will close the recruitment drive. No further student applications will be accepted. Maps to POST /api/v1/officer/drives/{id}/close."
        consequences="Closing this drive will immediately stop candidate submissions and archive pending applications. This action is irreversible once committed."
        confirmLabel="Confirm Closure"
        onConfirm={() => {
          setIsAlertOpen(false)
          triggerToast('Recruitment drive closed successfully.')
        }}
      />

      {/* View Switcher Container */}
      {activeTab === 'student-shell' ? (
        <div className="flex-1">
          <div className="bg-academic-teal/10 border-b border-academic-teal/20 px-6 py-2 flex items-center justify-between text-xs text-academic-teal">
            <span className="font-semibold uppercase tracking-wider">Student Shell Live Preview (Fixture Data)</span>
            <button
              onClick={() => setActiveTab('catalog')}
              className="underline font-medium hover:text-academic-teal/80 cursor-pointer"
            >
              ← Return to Workshop Catalog
            </button>
          </div>
          <StudentShell
            academicProfile={{
              name: 'Aarav Sharma',
              studentId: '2022CS0144',
              branch: 'Computer Science',
              cgpa: 9.15,
            }}
          >
            <div className="space-y-6">
              <PageHeading
                title="Student Opportunity Hub"
                description="Browse active recruitment drives, inspect verified eligibility criteria, and track interview invitations."
                breadcrumbs={[
                  { label: 'Portal Home', href: '/student' },
                  { label: 'Opportunities' },
                ]}
              />

              <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                <div className="rounded-lg border border-border bg-card p-5 space-y-2">
                  <span className="text-xs uppercase font-semibold text-muted-foreground tracking-wider">
                    Eligible Drives
                  </span>
                  <div className="text-3xl font-serif font-bold text-academic-teal">6 Active</div>
                  <p className="text-xs text-muted-foreground">Based on your 9.15 CGPA & CSE branch</p>
                </div>
                <div className="rounded-lg border border-border bg-card p-5 space-y-2">
                  <span className="text-xs uppercase font-semibold text-muted-foreground tracking-wider">
                    Submitted Applications
                  </span>
                  <div className="text-3xl font-serif font-bold text-foreground">3 Drives</div>
                  <p className="text-xs text-muted-foreground">1 Shortlisted, 1 Scheduled, 1 Reviewing</p>
                </div>
                <div className="rounded-lg border border-border bg-card p-5 space-y-2">
                  <span className="text-xs uppercase font-semibold text-muted-foreground tracking-wider">
                    Upcoming Interviews
                  </span>
                  <div className="text-3xl font-serif font-bold text-muted-brass">1 Session</div>
                  <p className="text-xs text-muted-foreground">Microsoft Cloud Apprentice (Tomorrow)</p>
                </div>
              </div>

              <div className="rounded-lg border border-border bg-card p-6">
                <SectionHeading
                  title="Upcoming Recruitment Drives"
                  description="Drives matching your academic qualifications"
                  actions={
                    <Button size="sm" variant="outline" onClick={() => triggerToast('Drives refreshed')}>
                      Refresh Drives
                    </Button>
                  }
                />
                <div className="divide-y divide-border mt-4">
                  <div className="py-4 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                    <div>
                      <h4 className="font-medium text-foreground">Google India — Software Engineer 2026</h4>
                      <p className="text-xs text-muted-foreground">
                        Bengaluru • Min CGPA 8.50 • Deadline Oct 28, 2026
                      </p>
                    </div>
                    <div className="flex items-center gap-3 shrink-0">
                      <EligibilityStatusBadge status="ELIGIBLE" />
                      <Button size="sm" onClick={() => triggerToast('Application modal simulated')}>
                        Apply Now
                      </Button>
                    </div>
                  </div>
                  <div className="py-4 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                    <div>
                      <h4 className="font-medium text-foreground">Qualcomm — Firmware Engineering Intern</h4>
                      <p className="text-xs text-muted-foreground">
                        Hyderabad • Min CGPA 8.00 • Deadline Nov 02, 2026
                      </p>
                    </div>
                    <div className="flex items-center gap-3 shrink-0">
                      <EligibilityStatusBadge status="ELIGIBLE" />
                      <Button size="sm" variant="outline" onClick={() => triggerToast('Application modal simulated')}>
                        View Details
                      </Button>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </StudentShell>
        </div>
      ) : activeTab === 'officer-shell' ? (
        <div className="flex-1">
          <div className="bg-academic-teal/10 border-b border-academic-teal/20 px-6 py-2 flex items-center justify-between text-xs text-academic-teal">
            <span className="font-semibold uppercase tracking-wider">Placement Officer Shell Live Preview (Fixture Data)</span>
            <button
              onClick={() => setActiveTab('catalog')}
              className="underline font-medium hover:text-academic-teal/80 cursor-pointer"
            >
              ← Return to Workshop Catalog
            </button>
          </div>
          <OfficerShell>
            <div className="space-y-6">
              <PageHeading
                title="Placement Command Center"
                description="Campus recruitment operational administration, corporate partner coordination, and interview studio management."
                breadcrumbs={[
                  { label: 'Command Center', href: '/officer' },
                  { label: 'Overview' },
                ]}
              />

              <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
                <div className="rounded-lg border border-border bg-card p-4 space-y-1">
                  <span className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                    Active Drives
                  </span>
                  <div className="text-2xl font-serif font-bold text-foreground">12 Drives</div>
                  <p className="text-xs text-muted-foreground">4 In Shortlisting Phase</p>
                </div>
                <div className="rounded-lg border border-border bg-card p-4 space-y-1">
                  <span className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                    Total Candidates
                  </span>
                  <div className="text-2xl font-serif font-bold text-academic-teal">348 Applicants</div>
                  <p className="text-xs text-muted-foreground">84 Pending Shortlisting</p>
                </div>
                <div className="rounded-lg border border-border bg-card p-4 space-y-1">
                  <span className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                    Interview Rooms
                  </span>
                  <div className="text-2xl font-serif font-bold text-foreground">8 Configured</div>
                  <p className="text-xs text-muted-foreground">Placement Block A & B</p>
                </div>
                <div className="rounded-lg border border-border bg-card p-4 space-y-1">
                  <span className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                    Active Interviewers
                  </span>
                  <div className="text-2xl font-serif font-bold text-muted-brass">14 Registered</div>
                  <p className="text-xs text-muted-foreground">Ready for Studio Scheduling</p>
                </div>
              </div>

              {/* Data Table within Officer View */}
              <div className="rounded-lg border border-border bg-card p-5 space-y-4">
                <SectionHeading
                  title="Candidate Shortlisting Registry"
                  description="Review applicants and advance qualified candidates for timetable scheduling (No client-side bulk loops permitted)"
                />
                <TableToolbar
                  searchQuery={tableSearch}
                  onSearchChange={setTableSearch}
                  searchPlaceholder="Filter by candidate name, roll number, or drive..."
                  filters={
                    <div className="flex items-center gap-2">
                      <select
                        aria-label="Filter by status"
                        value={tableStatusFilter}
                        onChange={(e) => setTableStatusFilter(e.target.value)}
                        className="h-9 px-3 rounded-sm border border-border bg-card text-xs text-foreground focus:ring-1 focus:ring-ring"
                      >
                        <option value="ALL">All Statuses</option>
                        <option value="APPLIED">Applied</option>
                        <option value="SHORTLISTED">Shortlisted</option>
                        <option value="SCHEDULED">Scheduled</option>
                        <option value="SELECTED">Selected</option>
                        <option value="REJECTED">Rejected</option>
                      </select>
                      {(tableSearch || tableStatusFilter !== 'ALL') && (
                        <Button
                          size="sm"
                          variant="ghost"
                          onClick={() => {
                            setTableSearch('')
                            setTableStatusFilter('ALL')
                          }}
                        >
                          Reset
                        </Button>
                      )}
                    </div>
                  }
                />
                <DataTable columns={columns} data={filteredData} />
              </div>
            </div>
          </OfficerShell>
        </div>
      ) : (
        /* Full Component Catalog Mode */
        <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-12">
          {/* Institutional Banner */}
          <div className="p-6 rounded-lg border border-border bg-card space-y-3">
            <div className="flex items-center gap-2 text-academic-teal font-semibold text-sm">
              <Sparkles className="h-4 w-4" />
              <span>Institutional × Contemporary Architecture</span>
            </div>
            <h2 className="font-serif text-2xl md:text-3xl font-bold text-foreground">
              HireFlow Institutional Design System
            </h2>
            <p className="text-sm text-muted-foreground max-w-3xl leading-relaxed">
              Engineered for academic authority, dense administrative workflows, and verified contrast and keyboard accessibility.
              All components strictly consume semantic design tokens mapped from the authoritative palette:
              Midnight Navy (<code className="font-mono text-xs">#17324B</code>), Academic Teal (<code className="font-mono text-xs">#176B69</code>),
              Warm Ivory (<code className="font-mono text-xs">#F6F4EE</code>), and Muted Brass (<code className="font-mono text-xs">#C6A46B</code>).
            </p>
          </div>

          {/* Section 1: Palette & Semantic Colors */}
          <section className="space-y-4">
            <SectionHeading
              title="1. Semantic Color Tokens"
              description="Authoritative, contrast-audited palette without generic SaaS gradients or arbitrary color values"
            />
            <div className="grid grid-cols-2 sm:grid-cols-4 md:grid-cols-6 gap-4">
              <div className="space-y-1.5 p-3 rounded-lg border border-border bg-card">
                <div className="h-16 rounded bg-midnight-navy flex items-end p-2 text-warm-ivory text-xs font-mono">
                  #17324B
                </div>
                <div className="font-semibold text-xs text-foreground">Midnight Navy</div>
                <div className="text-xs text-muted-foreground">Brand Primary</div>
              </div>
              <div className="space-y-1.5 p-3 rounded-lg border border-border bg-card">
                <div className="h-16 rounded bg-academic-teal flex items-end p-2 text-warm-ivory text-xs font-mono">
                  #176B69
                </div>
                <div className="font-semibold text-xs text-foreground">Academic Teal</div>
                <div className="text-xs text-muted-foreground">Scholarly Accent</div>
              </div>
              <div className="space-y-1.5 p-3 rounded-lg border border-border bg-card">
                <div className="h-16 rounded bg-warm-ivory border border-border flex items-end p-2 text-foreground text-xs font-mono">
                  #F6F4EE
                </div>
                <div className="font-semibold text-xs text-foreground">Warm Ivory</div>
                <div className="text-xs text-muted-foreground">Base Canvas</div>
              </div>
              <div className="space-y-1.5 p-3 rounded-lg border border-border bg-card">
                <div className="h-16 rounded bg-muted-brass flex items-end p-2 text-midnight-navy text-xs font-mono font-bold">
                  #C6A46B
                </div>
                <div className="font-semibold text-xs text-foreground">Muted Brass</div>
                <div className="text-xs text-muted-foreground">Highlight / Badge</div>
              </div>
              <div className="space-y-1.5 p-3 rounded-lg border border-border bg-card">
                <div className="h-16 rounded bg-status-confirmed-bg border border-status-confirmed-border flex items-end p-2 text-status-confirmed-text text-xs font-mono font-semibold">
                  Confirmed
                </div>
                <div className="font-semibold text-xs text-foreground">Confirmed / Selected</div>
                <div className="text-xs text-muted-foreground">Semantic Positive</div>
              </div>
              <div className="space-y-1.5 p-3 rounded-lg border border-border bg-card">
                <div className="h-16 rounded bg-status-rejected-bg border border-status-rejected-border flex items-end p-2 text-status-rejected-text text-xs font-mono font-semibold">
                  Rejected
                </div>
                <div className="font-semibold text-xs text-foreground">Conflict / Ineligible</div>
                <div className="text-xs text-muted-foreground">Semantic Danger</div>
              </div>
            </div>
          </section>

          {/* Section 2: Typography Hierarchy */}
          <section className="space-y-4">
            <SectionHeading
              title="2. Typography Hierarchy"
              description="Font pairing: Newsreader (Academic editorial headings) + Inter (Dense UI/Body) + JetBrains Mono (Tabular IDs)"
            />
            <div className="p-6 rounded-lg border border-border bg-card space-y-6">
              <div>
                <span className="text-xs uppercase font-semibold text-muted-foreground tracking-wider">
                  Page Heading — Newsreader Serif (32px Bold)
                </span>
                <h1 className="font-serif text-3xl font-bold text-foreground mt-1">
                  University Placement & Career Timetable
                </h1>
              </div>
              <Separator />
              <div>
                <span className="text-xs uppercase font-semibold text-muted-foreground tracking-wider">
                  Section Heading — Newsreader Serif (20px SemiBold)
                </span>
                <h2 className="font-serif text-xl font-semibold text-foreground mt-1">
                  Greedy Algorithm Timetable Distribution
                </h2>
              </div>
              <Separator />
              <div>
                <span className="text-xs uppercase font-semibold text-muted-foreground tracking-wider">
                  Body & Operational Metadata — Inter Sans (14px - 16px)
                </span>
                <p className="text-base text-foreground leading-relaxed mt-1">
                  Candidate eligibility is computed deterministically using backend validation criteria.
                  Student academic records remain strictly read-only and backend-authoritative.
                </p>
                <p className="text-sm text-muted-foreground leading-relaxed mt-1">
                  Secondary operational description rendered with high-contrast neutral tones (tested for minimum 4.5:1 contrast).
                </p>
              </div>
              <Separator />
              <div>
                <span className="text-xs uppercase font-semibold text-muted-foreground tracking-wider">
                  Tabular Numbers & Identifiers — JetBrains Mono (13px)
                </span>
                <div className="mt-1 font-mono text-sm space-x-4">
                  <span className="px-2 py-0.5 rounded bg-muted text-foreground">ROLL: 2022CS0144</span>
                  <span className="px-2 py-0.5 rounded bg-muted text-foreground">PREV: PREV-2026-X89B</span>
                  <span className="px-2 py-0.5 rounded bg-muted text-foreground">CGPA: 9.15 / 10.00</span>
                </div>
              </div>
            </div>
          </section>

          {/* Section 3: Interactive Buttons & Actions */}
          <section className="space-y-4">
            <SectionHeading
              title="3. Buttons & Interactive Controls"
              description="Accessible focus rings, multiple semantic variants, and real demonstrable click actions"
            />
            <div className="p-6 rounded-lg border border-border bg-card space-y-6">
              <div className="flex flex-wrap items-center gap-3">
                <Button onClick={() => setButtonClickCount((c) => c + 1)}>
                  Default Primary ({buttonClickCount})
                </Button>
                <Button variant="secondary" onClick={() => triggerToast('Secondary action clicked')}>
                  Secondary Variant
                </Button>
                <Button variant="outline" onClick={() => triggerToast('Outline action clicked')}>
                  Academic Outline
                </Button>
                <Button variant="ghost" onClick={() => triggerToast('Ghost action clicked')}>
                  Ghost Action
                </Button>
                <Button variant="destructive" onClick={() => triggerToast('Destructive action clicked')}>
                  Destructive Action
                </Button>
                <Button
                  loading={btnLoading}
                  onClick={() => {
                    setBtnLoading(true)
                    setTimeout(() => {
                      setBtnLoading(false)
                      triggerToast('Async task finished')
                    }, 1200)
                  }}
                >
                  {btnLoading ? 'Processing...' : 'Simulate Async Action'}
                </Button>
              </div>
              <div className="flex flex-wrap items-center gap-3 pt-2">
                <Button size="sm">Small (32px)</Button>
                <Button size="default">Default (36px)</Button>
                <Button size="lg">Large (40px)</Button>
              </div>
            </div>
          </section>

          {/* Section 4: Form Controls & Validation */}
          <section className="space-y-4">
            <SectionHeading
              title="4. Form Inputs & Accessible Validation"
              description="Associated labels, helper messages, error states, and password visibility toggles"
            />
            <div className="p-6 rounded-lg border border-border bg-card">
              <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                <FormField
                  label="Candidate Full Name"
                  id="workshop-name"
                  description="As registered in university records."
                  required
                >
                  <Input
                    id="workshop-name"
                    value={inputValue}
                    onChange={(e) => setInputValue(e.target.value)}
                    placeholder="e.g. Aarav Sharma"
                  />
                </FormField>

                <FormField
                  label="Security Password"
                  id="workshop-password"
                  description="Toggle the eye icon to test accessible visibility toggling."
                  required
                >
                  <PasswordField
                    id="workshop-password"
                    value={passwordValue}
                    onChange={(e) => setPasswordValue(e.target.value)}
                    placeholder="Enter password"
                  />
                </FormField>

                <FormField
                  label="Academic Degree Program"
                  id="workshop-degree"
                  description="Select approved program."
                >
                  <Select value={selectValue} onValueChange={setSelectValue}>
                    <SelectTrigger id="workshop-degree">
                      <SelectValue placeholder="Select program" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="btech">Bachelor of Technology (B.Tech)</SelectItem>
                      <SelectItem value="mtech">Master of Technology (M.Tech)</SelectItem>
                      <SelectItem value="mca">Master of Computer Applications (MCA)</SelectItem>
                      <SelectItem value="phd">Doctor of Philosophy (Ph.D)</SelectItem>
                    </SelectContent>
                  </Select>
                </FormField>

                <FormField
                  label="Simulated Validation Error Field"
                  id="workshop-error"
                  error={hasFormError ? 'The minimum CGPA must be a valid number between 0.00 and 10.00' : undefined}
                >
                  <Input
                    id="workshop-error"
                    defaultValue="12.50"
                    aria-invalid={hasFormError}
                  />
                  <div className="mt-2">
                    <Button
                      size="sm"
                      variant="outline"
                      type="button"
                      onClick={() => setHasFormError((prev) => !prev)}
                    >
                      {hasFormError ? 'Clear Validation Error' : 'Trigger Validation Error State'}
                    </Button>
                  </div>
                </FormField>

                <div className="md:col-span-2">
                  <FormField label="Placement Officer Advisory Notes" id="workshop-notes">
                    <Textarea
                      id="workshop-notes"
                      rows={3}
                      placeholder="Add operational notes or special room equipment requirements..."
                    />
                  </FormField>
                </div>

                <div className="md:col-span-2 flex items-center space-x-2">
                  <Checkbox
                    id="workshop-terms"
                    checked={checkboxValue}
                    onCheckedChange={(checked) => setCheckboxValue(!!checked)}
                  />
                  <label
                    htmlFor="workshop-terms"
                    className="text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70 text-foreground cursor-pointer"
                  >
                    Acknowledge that academic records are backend-authoritative and read-only.
                  </label>
                </div>
              </div>
            </div>
          </section>

          {/* Section 5: Domain Status Badges */}
          <section className="space-y-4">
            <SectionHeading
              title="5. Domain Status Badges"
              description="Color-independent domain badges combining distinct background, border, icon, and label"
            />
            <div className="p-6 rounded-lg border border-border bg-card space-y-6">
              <div>
                <h4 className="text-xs uppercase font-semibold text-muted-foreground tracking-wider mb-3">
                  Application Status Machine
                </h4>
                <div className="flex flex-wrap gap-3">
                  <ApplicationStatusBadge status="APPLIED" />
                  <ApplicationStatusBadge status="ELIGIBLE" />
                  <ApplicationStatusBadge status="SHORTLISTED" />
                  <ApplicationStatusBadge status="SCHEDULED" />
                  <ApplicationStatusBadge status="SELECTED" />
                  <ApplicationStatusBadge status="REJECTED" />
                </div>
              </div>

              <Separator />

              <div>
                <h4 className="text-xs uppercase font-semibold text-muted-foreground tracking-wider mb-3">
                  Eligibility Evaluation
                </h4>
                <div className="flex flex-wrap gap-3">
                  <EligibilityStatusBadge status="ELIGIBLE" />
                  <EligibilityStatusBadge status="INELIGIBLE" />
                </div>
              </div>
            </div>
          </section>

          {/* Section 6: High-Density Data Table */}
          <section className="space-y-4">
            <SectionHeading
              title="6. Data Table Foundation (TanStack Table v8)"
              description="Dense, accessible data table with live column sorting, instant filtering, and row action triggers"
            />
            <div className="p-6 rounded-lg border border-border bg-card space-y-4">
              <TableToolbar
                searchQuery={tableSearch}
                onSearchChange={setTableSearch}
                searchPlaceholder="Filter candidates by name, roll no, or drive..."
                filters={
                  <div className="flex items-center gap-2">
                    <select
                      aria-label="Filter by status"
                      value={tableStatusFilter}
                      onChange={(e) => setTableStatusFilter(e.target.value)}
                      className="h-9 px-3 rounded-sm border border-border bg-card text-xs text-foreground focus:ring-1 focus:ring-ring"
                    >
                      <option value="ALL">All Statuses</option>
                      <option value="APPLIED">Applied</option>
                      <option value="SHORTLISTED">Shortlisted</option>
                      <option value="SCHEDULED">Scheduled</option>
                      <option value="SELECTED">Selected</option>
                      <option value="REJECTED">Rejected</option>
                    </select>
                    {(tableSearch || tableStatusFilter !== 'ALL') && (
                      <Button
                        size="sm"
                        variant="ghost"
                        onClick={() => {
                          setTableSearch('')
                          setTableStatusFilter('ALL')
                        }}
                      >
                        Reset
                      </Button>
                    )}
                  </div>
                }
              />
              <DataTable columns={columns} data={filteredData} />
            </div>
          </section>

          {/* Section 7: Dialogs, Alerts, and Popups */}
          <section className="space-y-4">
            <SectionHeading
              title="7. Modals & Accessible Dialogs"
              description="Radix UI accessible dialogs with automatic focus trapping, Escape dismissal, and return focus"
            />
            <div className="p-6 rounded-lg border border-border bg-card flex flex-wrap gap-4">
              {/* Standard Dialog */}
              <Dialog>
                <DialogTrigger asChild>
                  <Button variant="outline">Open Information Dialog</Button>
                </DialogTrigger>
                <DialogContent>
                  <DialogHeader>
                    <DialogTitle>Placement Policy Directive</DialogTitle>
                    <DialogDescription>
                      Review the official institutional recruitment guidelines for the 2026 academic batch.
                    </DialogDescription>
                  </DialogHeader>
                  <div className="py-3 text-sm text-muted-foreground space-y-2">
                    <p>
                      Each student may hold only one active appointment slot at a given timestamp.
                      Concurrent double-booking is strictly prohibited by PostgreSQL temporal GiST constraints.
                    </p>
                  </div>
                  <DialogFooter>
                    <Button onClick={() => triggerToast('Policy acknowledged')}>
                      Understood
                    </Button>
                  </DialogFooter>
                </DialogContent>
              </Dialog>

              {/* AlertDialog Trigger */}
              <Button variant="destructive" onClick={() => setIsAlertOpen(true)}>
                Open Alert Confirmation
              </Button>
            </div>
          </section>

          {/* Section 8: Feedback Banners & State Screens */}
          <section className="space-y-4">
            <SectionHeading
              title="8. State Screens & Feedback Banners"
              description="Notice banners, empty states, error fallbacks, and skeleton loading indicators"
            />
            <div className="space-y-4">
              <NoticeBanner
                variant="info"
                title="Scheduling Engine Advisory"
              >
                Timetable previews generated by POST /api/v1/officer/scheduling/preview persist for 15 minutes before expiring. Tokens are kept strictly in memory.
              </NoticeBanner>
              <NoticeBanner
                variant="success"
                title="Batch Publication Result (HTTP 200 ALL_CONFIRMED)"
              >
                All 18 candidate interview sessions have been persisted to PostgreSQL without room or interviewer overlap.
              </NoticeBanner>
              <NoticeBanner
                variant="warning"
                title="Partial Success Result (HTTP 207 Multi-Status)"
              >
                14 sessions persisted. 2 sessions encountered interviewer schedule conflicts and remain shortlisted for rescheduling.
              </NoticeBanner>
              <NoticeBanner
                variant="error"
                title="Publication Conflict (HTTP 409 NONE_CONFIRMED)"
              >
                Double-booking detected for Interviewer INT-004. Zero appointments committed. Generate a fresh preview to resolve.
              </NoticeBanner>

              <div className="grid grid-cols-1 md:grid-cols-3 gap-6 pt-4">
                <div className="rounded-lg border border-border bg-card p-6">
                  <span className="text-xs uppercase font-semibold text-muted-foreground block mb-4">
                    Empty State Pattern
                  </span>
                  <EmptyState
                    title="No Applications Found"
                    description="No candidates currently match the applied filter criteria."
                    action={
                      <Button size="sm" variant="outline" onClick={() => triggerToast('Filter reset')}>
                        Reset Filters
                      </Button>
                    }
                  />
                </div>

                <div className="rounded-lg border border-border bg-card p-6">
                  <span className="text-xs uppercase font-semibold text-muted-foreground block mb-4">
                    Error State Pattern
                  </span>
                  <ErrorState
                    title="Service Unavailable"
                    message="Unable to reach the Spring Boot backend at http://localhost:8081."
                    onRetry={() => triggerToast('Retrying backend connection...')}
                  />
                </div>

                <div className="rounded-lg border border-border bg-card p-6">
                  <span className="text-xs uppercase font-semibold text-muted-foreground block mb-4">
                    Loading Skeleton Pattern
                  </span>
                  <LoadingSkeleton rows={4} />
                </div>
              </div>
            </div>
          </section>
        </main>
      )}

      {/* Workshop Footer */}
      <footer className="mt-auto border-t border-border bg-card py-6 text-xs text-muted-foreground text-center">
        <div className="max-w-7xl mx-auto px-4 flex flex-col sm:flex-row items-center justify-between gap-3">
          <p>© 2026 HireFlow University Placement Platform. Design System Foundation (CAP-8.1.1).</p>
          <div className="flex gap-4">
            <span className="text-academic-teal font-medium">CAP-8.1.1 Foundation</span>
            <span>React 19 • TypeScript Strict • Tailwind v4</span>
          </div>
        </div>
      </footer>
    </div>
  )
}
