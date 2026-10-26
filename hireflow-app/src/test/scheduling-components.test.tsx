import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter } from 'react-router-dom'
import { TooltipProvider } from '@/components/ui/tooltip'
import { SchedulingConfigPanel } from '@/features/officer/components/scheduling/SchedulingConfigPanel'
import { AdvisoryPreviewView } from '@/features/officer/components/scheduling/AdvisoryPreviewView'
import { PublicationConfirmDialog } from '@/features/officer/components/scheduling/PublicationConfirmDialog'
import { PublicationOutcomeView } from '@/features/officer/components/scheduling/PublicationOutcomeView'
import { ConfirmedTimetableView } from '@/features/officer/components/scheduling/ConfirmedTimetableView'
import { ConfirmedSessionDetailDialog } from '@/features/officer/components/scheduling/ConfirmedSessionDetailDialog'
import type {
  OfficerDriveDetailDto,
  OfficerSchedulingPreviewResponseDto,
  OfficerPublicationSummaryDto,
  OfficerInterviewDetailDto,
} from '@/types/api'

function renderWithProviders(ui: React.ReactElement) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <TooltipProvider>
        <BrowserRouter>{ui}</BrowserRouter>
      </TooltipProvider>
    </QueryClientProvider>
  )
}

const mockDrives: OfficerDriveDetailDto[] = [
  {
    id: 'DRIVE-001',
    companyId: 'COMP-001',
    companyName: 'Google India',
    jobTitle: 'Software Development Engineer',
    minimumCgpa: 7.5,
    eligibleBranches: ['Computer Science', 'Information Technology'],
    applicationDeadline: '2026-10-30',
    closed: false,
    totalApplications: 12,
    shortlistedApplications: 8,
  },
  {
    id: 'DRIVE-002',
    companyId: 'COMP-002',
    companyName: 'Microsoft IDC',
    jobTitle: 'Cloud Solutions Architect',
    minimumCgpa: 8.0,
    eligibleBranches: ['Computer Science'],
    applicationDeadline: '2026-11-05',
    closed: false,
    totalApplications: 15,
    shortlistedApplications: 5,
  },
]

const mockInterviewers = [
  {
    id: 'INT-001',
    name: 'Dr. Ramesh Kumar',
    email: 'ramesh.kumar@google.com',
    companyId: 'COMP-001',
    companyName: 'Google India',
    assignedInterviewsCount: 2,
  },
  {
    id: 'INT-002',
    name: 'Sneha Verma',
    email: 'sneha.verma@google.com',
    companyId: 'COMP-001',
    companyName: 'Google India',
    assignedInterviewsCount: 1,
  },
]

const mockRooms = [
  {
    id: 'ROOM-101',
    name: 'Lab 101 - Placement Center',
    capacity: 4,
    location: 'Academic Block A, First Floor',
    assignedInterviewsCount: 3,
  },
  {
    id: 'ROOM-102',
    name: 'Conference Room B',
    capacity: 6,
    location: 'Main Administrative Wing',
    assignedInterviewsCount: 2,
  },
]

const mockPreview: OfficerSchedulingPreviewResponseDto = {
  previewId: '3fa85f64-5717-4562-b3fc-2c963f66afa6',
  driveId: 'DRIVE-001',
  jobTitle: 'Software Development Engineer',
  companyName: 'Google India',
  date: '2026-10-22',
  startTime: '09:00',
  endTime: '17:00',
  durationMinutes: 45,
  bufferMinutes: 15,
  expiresAt: new Date(Date.now() + 15 * 60 * 1000).toISOString(),
  totalRequested: 3,
  totalScheduled: 2,
  totalUnscheduled: 1,
  proposedSessions: [
    {
      sessionId: 'PROP-001',
      applicationId: 'APP-001',
      studentId: 'STU-001',
      studentName: 'Aarav Patel',
      studentBranch: 'Computer Science',
      interviewerId: 'INT-001',
      interviewerName: 'Dr. Ramesh Kumar',
      roomId: 'ROOM-101',
      roomName: 'Lab 101 - Placement Center',
      startTime: '2026-10-22T09:00:00',
      endTime: '2026-10-22T09:45:00',
    },
    {
      sessionId: 'PROP-002',
      applicationId: 'APP-002',
      studentId: 'STU-002',
      studentName: 'Priya Sharma',
      studentBranch: 'Information Technology',
      interviewerId: 'INT-002',
      interviewerName: 'Sneha Verma',
      roomId: 'ROOM-102',
      roomName: 'Conference Room B',
      startTime: '2026-10-22T09:00:00',
      endTime: '2026-10-22T09:45:00',
    },
  ],
  unscheduledCandidates: [
    {
      applicationId: 'APP-003',
      studentId: 'STU-003',
      studentName: 'Rohan Gupta',
      studentBranch: 'Electrical Engineering',
      reason: 'Slot capacity reached: all interviewer and room slots occupied during the requested operational window.',
    },
  ],
}

describe('Placement Officer Scheduling Studio UI Components', () => {
  // ============================================================================
  // Component 1: SchedulingConfigPanel
  // ============================================================================
  describe('SchedulingConfigPanel', () => {
    it('renders with drive selection and backend defaults (45m duration, 15m buffer)', () => {
      renderWithProviders(
        <SchedulingConfigPanel
          drives={mockDrives}
          interviewers={mockInterviewers}
          rooms={mockRooms}
          isLoadingDrives={false}
          onSubmit={vi.fn()}
          isGenerating={false}
        />
      )

      expect(screen.getByText('1. Recruitment Drive Context')).toBeInTheDocument()
      expect(screen.getByText('2. Slot Durations & Intervals')).toBeInTheDocument()
      expect(screen.getByText('3. Room Resource Allocation')).toBeInTheDocument()

      const durationInput = screen.getByLabelText(/Duration \(mins\)/i) as HTMLInputElement
      const bufferInput = screen.getByLabelText(/Buffer \(mins\)/i) as HTMLInputElement
      expect(durationInput.value).toBe('45')
      expect(bufferInput.value).toBe('15')
    })

    it('submits form with valid parameters', () => {
      const handleSubmit = vi.fn()
      renderWithProviders(
        <SchedulingConfigPanel
          drives={mockDrives}
          interviewers={mockInterviewers}
          rooms={mockRooms}
          isLoadingDrives={false}
          onSubmit={handleSubmit}
          isGenerating={false}
        />
      )

      const generateBtn = screen.getByRole('button', { name: /Generate Advisory Timetable Preview/i })
      fireEvent.click(generateBtn)

      expect(handleSubmit).toHaveBeenCalledTimes(1)
      expect(handleSubmit).toHaveBeenCalledWith(
        expect.objectContaining({
          driveId: 'DRIVE-001',
          durationMinutes: 45,
          bufferMinutes: 15,
        })
      )
    })
  })

  // ============================================================================
  // Component 2: AdvisoryPreviewView
  // ============================================================================
  describe('AdvisoryPreviewView', () => {
    it('renders prominent advisory banner and proposal totals', () => {
      renderWithProviders(
        <AdvisoryPreviewView
          preview={mockPreview}
          onProceedToPublish={vi.fn()}
          onAdjustConfig={vi.fn()}
          onRefreshPreview={vi.fn()}
          isPublishing={false}
        />
      )

      expect(screen.getByText(/ADVISORY — NOT YET PUBLISHED/i)).toBeInTheDocument()
      expect(screen.getByText('Software Development Engineer')).toBeInTheDocument()
      expect(screen.getByText('Google India')).toBeInTheDocument()
      expect(screen.getByText('Aarav Patel')).toBeInTheDocument()
      expect(screen.getByText('Rohan Gupta')).toBeInTheDocument()
      expect(screen.getByText(/Slot capacity reached/i)).toBeInTheDocument()
    })

    it('switches between visual time grid and accessible data table views', () => {
      renderWithProviders(
        <AdvisoryPreviewView
          preview={mockPreview}
          onProceedToPublish={vi.fn()}
          onAdjustConfig={vi.fn()}
          onRefreshPreview={vi.fn()}
          isPublishing={false}
        />
      )

      const tabularBtn = screen.getByRole('button', { name: /Accessible Tabular View/i })
      fireEvent.click(tabularBtn)

      expect(screen.getByPlaceholderText(/Search candidate/i)).toBeInTheDocument()
    })
  })

  // ============================================================================
  // Component 3: PublicationConfirmDialog
  // ============================================================================
  describe('PublicationConfirmDialog', () => {
    it('requires explicit acknowledgement checkbox before enabling publication', () => {
      const handleConfirm = vi.fn()
      renderWithProviders(
        <PublicationConfirmDialog
          open={true}
          onOpenChange={vi.fn()}
          preview={mockPreview}
          onConfirmPublish={handleConfirm}
          isPublishing={false}
        />
      )

      expect(screen.getByText('Confirm Timetable Publication')).toBeInTheDocument()
      expect(screen.getByText(/Non-Atomic Per-Session Persistence/i)).toBeInTheDocument()

      const publishBtn = screen.getByRole('button', { name: /Confirm & Publish Timetable/i })
      expect(publishBtn).toBeDisabled()

      const checkbox = screen.getByRole('checkbox')
      fireEvent.click(checkbox)

      expect(publishBtn).not.toBeDisabled()
      fireEvent.click(publishBtn)
      expect(handleConfirm).toHaveBeenCalledTimes(1)
    })
  })

  // ============================================================================
  // Component 4: PublicationOutcomeView
  // ============================================================================
  describe('PublicationOutcomeView', () => {
    it('renders HTTP 200 ALL_CONFIRMED outcome with session counts', () => {
      const fullSummary: OfficerPublicationSummaryDto = {
        attemptId: 'ATTEMPT-001',
        previewId: '3fa85f64-5717-4562-b3fc-2c963f66afa6',
        previewReference: 'prev-3fa8...afa6',
        driveId: 'DRIVE-001',
        jobTitle: 'Software Development Engineer',
        companyName: 'Google India',
        outcome: 'ALL_CONFIRMED',
        recoveryInstructions: 'All sessions successfully persisted.',
        publishedAt: '2026-10-19T18:00:00Z',
        totalProposals: 2,
        publishedCount: 2,
        skippedDuplicatesCount: 0,
        conflictsCount: 0,
        errorsCount: 0,
        unattemptedCount: 0,
        confirmedSessionIds: ['SESS-001', 'SESS-002'],
        publishedSessions: [],
        skippedDuplicateApplicationIds: [],
        conflictRejections: [],
        infrastructureErrors: [],
      }

      renderWithProviders(
        <PublicationOutcomeView
          outcomeState={{
            type: 'ALL_CONFIRMED',
            summary: fullSummary,
          }}
          onNavigateToConfirmedTimetable={vi.fn()}
          onGenerateNewPreview={vi.fn()}
        />
      )

      expect(screen.getByText(/Interview Timetable Successfully Published/i)).toBeInTheDocument()
      expect(screen.getByText('HTTP 200 OK')).toBeInTheDocument()
    })

    it('renders HTTP 207 PARTIAL_SUCCESS outcome with recovery instructions and conflict list', () => {
      const partialSummary: OfficerPublicationSummaryDto = {
        attemptId: 'ATTEMPT-002',
        previewId: '3fa85f64-5717-4562-b3fc-2c963f66afa6',
        previewReference: 'prev-3fa8...afa6',
        driveId: 'DRIVE-001',
        jobTitle: 'Software Development Engineer',
        companyName: 'Google India',
        outcome: 'PARTIAL_SUCCESS',
        recoveryInstructions: '1 session confirmed. 1 conflict occurred.',
        publishedAt: '2026-10-19T18:00:00Z',
        totalProposals: 2,
        publishedCount: 1,
        skippedDuplicatesCount: 0,
        conflictsCount: 1,
        errorsCount: 0,
        unattemptedCount: 0,
        confirmedSessionIds: ['SESS-001'],
        publishedSessions: [],
        skippedDuplicateApplicationIds: [],
        conflictRejections: [
          {
            applicationId: 'APP-002',
            message: 'Concurrent booking conflict on Room ROOM-102.',
            category: 'RESOURCE_COLLISION',
          },
        ],
        infrastructureErrors: [],
      }

      renderWithProviders(
        <PublicationOutcomeView
          outcomeState={{
            type: 'PARTIAL_SUCCESS',
            summary: partialSummary,
          }}
          onNavigateToConfirmedTimetable={vi.fn()}
          onGenerateNewPreview={vi.fn()}
        />
      )

      expect(screen.getByText(/Partial Publication Committed — Multi-Status/i)).toBeInTheDocument()
      expect(screen.getByText('HTTP 207 MULTI_STATUS')).toBeInTheDocument()
      expect(screen.getByText(/Concurrent booking conflict/i)).toBeInTheDocument()
    })

    it('renders HTTP 5xx SERVER_ERROR_UNCERTAIN with explicit uncertain-outcome warning', () => {
      renderWithProviders(
        <PublicationOutcomeView
          outcomeState={{
            type: 'SERVER_ERROR_UNCERTAIN',
            message: 'Database connection failed during publication transaction.',
            driveId: 'DRIVE-001',
          }}
          onNavigateToConfirmedTimetable={vi.fn()}
          onGenerateNewPreview={vi.fn()}
        />
      )

      expect(screen.getByText(/Publication State Uncertain/i)).toBeInTheDocument()
      expect(screen.getByText(/HTTP 5xx OUTAGE/i)).toBeInTheDocument()
      expect(screen.getByText(/Inspect Confirmed Timetable for Reconciliation/i)).toBeInTheDocument()
    })

    it('renders HTTP 404 NOT_FOUND_EVICTED state when preview is unknown or backend restarted', () => {
      renderWithProviders(
        <PublicationOutcomeView
          outcomeState={{
            type: 'NOT_FOUND_EVICTED',
            message: "Scheduling preview 'PREV-3fa8****' was not found or has expired.",
            driveId: 'DRIVE-001',
          }}
          onNavigateToConfirmedTimetable={vi.fn()}
          onGenerateNewPreview={vi.fn()}
        />
      )

      expect(screen.getByRole('heading', { name: /Scheduling Preview Not Found or Evicted/i })).toBeInTheDocument()
      expect(screen.getByText(/HTTP 404 NOT FOUND/i)).toBeInTheDocument()
      expect(screen.getByText(/Advisory previews are stored in instance memory and do not survive a server restart/i)).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /Generate Fresh Preview/i })).toBeInTheDocument()
    })
  })

  // ============================================================================
  // Component 5: ConfirmedTimetableView & Detail Dialog
  // ============================================================================
  describe('ConfirmedTimetableView & ConfirmedSessionDetailDialog', () => {
    const mockConfirmedSessions: OfficerInterviewDetailDto[] = [
      {
        sessionId: 'SESS-001',
        applicationId: 'APP-001',
        studentId: 'STU-001',
        studentName: 'Aarav Patel',
        studentBranch: 'Computer Science',
        driveId: 'DRIVE-001',
        jobTitle: 'Software Development Engineer',
        companyId: 'COMP-001',
        companyName: 'Google India',
        interviewerId: 'INT-001',
        interviewerName: 'Dr. Ramesh Kumar',
        roomId: 'ROOM-101',
        roomName: 'Lab 101 - Placement Center',
        startTime: '2026-10-22T09:00:00',
        endTime: '2026-10-22T09:45:00',
        status: 'SHORTLISTED',
      },
    ]

    it('renders confirmed session list with candidate, interviewer, room, and status', () => {
      renderWithProviders(
        <ConfirmedTimetableView
          sessions={mockConfirmedSessions}
          totalElements={1}
          totalPages={1}
          page={0}
          size={20}
          onPageChange={vi.fn()}
          onPageSizeChange={vi.fn()}
          drives={mockDrives}
          companies={[]}
          onRefetch={vi.fn()}
          onDriveChange={vi.fn()}
          onCompanyChange={vi.fn()}
          onDateChange={vi.fn()}
          isLoading={false}
        />
      )

      expect(screen.getAllByText('Aarav Patel')[0]).toBeInTheDocument()
      expect(screen.getAllByText('Dr. Ramesh Kumar')[0]).toBeInTheDocument()
      expect(screen.getAllByText('Lab 101 - Placement Center')[0]).toBeInTheDocument()
      expect(screen.getAllByText('Shortlisted')[0]).toBeInTheDocument()
    })

    it('renders session detail dialog with complete briefing', () => {
      renderWithProviders(
        <ConfirmedSessionDetailDialog
          open={true}
          onOpenChange={vi.fn()}
          session={mockConfirmedSessions[0]}
        />
      )

      expect(screen.getByText('Confirmed Interview Session')).toBeInTheDocument()
      expect(screen.getByText('Aarav Patel')).toBeInTheDocument()
      expect(screen.getByText('Computer Science')).toBeInTheDocument()
    })
  })
})
