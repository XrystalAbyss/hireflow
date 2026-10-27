import '@testing-library/jest-dom/vitest'
import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StudentDossierBanner } from '@/features/student/components/StudentDossierBanner'
import { EligibilityBreakdownCard } from '@/features/student/components/EligibilityBreakdownCard'
import { ApplicationConfirmationModal } from '@/features/student/components/ApplicationConfirmationModal'
import { DriveCard } from '@/features/student/components/DriveCard'
import { InterviewSessionCard } from '@/features/student/components/InterviewSessionCard'
import type {
  StudentProfileDto,
  StudentDriveEligibilityDto,
  StudentDriveItemDto,
  StudentInterviewDto,
} from '@/types/api'

function createTestQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })
}

const mockProfile: StudentProfileDto = {
  id: 'STU-2022-CS0144',
  name: 'Aarav Sharma',
  email: 'aarav.sharma@university.edu',
  branch: 'Computer Science',
  cgpa: 9.15,
  graduationYear: 2026,
  active: true,
}

const mockEligibleBreakdown: StudentDriveEligibilityDto = {
  driveId: 'DRV-2026-002',
  studentId: 'STU-2022-CS0144',
  academicallyEligible: true,
  acceptingApplications: true,
  alreadyApplied: false,
  canApply: true,
  existingApplicationId: null,
  existingApplicationStatus: null,
  minimumCgpa: 8.5,
  studentCgpa: 9.15,
  eligibleBranches: ['Computer Science', 'Mathematics & Computing'],
  studentBranch: 'Computer Science',
  applicationDeadline: '2026-11-20',
  closed: false,
  deadlinePassed: false,
  reasons: [],
}

const mockIneligibleBreakdown: StudentDriveEligibilityDto = {
  driveId: 'DRV-2026-005',
  studentId: 'STU-2022-CS0144',
  academicallyEligible: false,
  acceptingApplications: true,
  alreadyApplied: false,
  canApply: false,
  existingApplicationId: null,
  existingApplicationStatus: null,
  minimumCgpa: 9.5,
  studentCgpa: 9.15,
  eligibleBranches: ['Computer Science', 'Information Technology'],
  studentBranch: 'Computer Science',
  applicationDeadline: '2026-11-05',
  closed: false,
  deadlinePassed: false,
  reasons: ['Candidate cumulative CGPA 9.15 does not satisfy minimum required CGPA of 9.50.'],
}

const mockAlreadyAppliedBreakdown: StudentDriveEligibilityDto = {
  driveId: 'DRV-2026-001',
  studentId: 'STU-2022-CS0144',
  academicallyEligible: true,
  acceptingApplications: true,
  alreadyApplied: true,
  canApply: false,
  existingApplicationId: 'APP-2026-001',
  existingApplicationStatus: 'SHORTLISTED',
  minimumCgpa: 8.0,
  studentCgpa: 9.15,
  eligibleBranches: ['Computer Science'],
  studentBranch: 'Computer Science',
  applicationDeadline: '2026-11-15',
  closed: false,
  deadlinePassed: false,
  reasons: ['You have already submitted an application for this drive (APP-2026-001).'],
}

const mockDriveItem: StudentDriveItemDto = {
  id: 'DRV-2026-002',
  companyId: 'CMP-002',
  companyName: 'Stratum Quant Labs',
  jobTitle: 'Quantitative Systems Analyst',
  minimumCgpa: 8.5,
  eligibleBranches: ['Computer Science'],
  applicationDeadline: '2026-11-20',
  closed: false,
  deadlinePassed: false,
  hasApplied: false,
  existingApplicationId: null,
  existingApplicationStatus: null,
  academicallyEligible: true,
  acceptingApplications: true,
  canApply: true,
  eligibilityReasons: [],
}

const mockInterviewSession: StudentInterviewDto = {
  sessionId: 'INT-2026-801',
  applicationId: 'APP-2026-007',
  studentId: 'STU-2022-CS0144',
  studentName: 'Aarav Sharma',
  studentBranch: 'Computer Science',
  driveId: 'DRV-2026-007',
  jobTitle: 'Full-Stack Developer Intern',
  companyName: 'Crestline Systems',
  interviewerId: 'USR-INT-01',
  interviewerName: 'Dr. Michael Chen',
  roomId: 'RM-302',
  roomName: 'Placement Suite 302',
  startTime: '2026-10-24T10:00:00',
  endTime: '2026-10-24T10:45:00',
  status: 'SCHEDULED',
}

describe('HireFlow Student Opportunity Hub Tests', () => {
  describe('StudentDossierBanner', () => {
    it('renders candidate identity, academic metrics, and verified badge', () => {
      render(<StudentDossierBanner profile={mockProfile} />)

      expect(screen.getByText('Aarav Sharma')).toBeInTheDocument()
      expect(screen.getByText(/Roll No: STU-2022-CS0144/i)).toBeInTheDocument()
      expect(screen.getByText('9.15')).toBeInTheDocument()
      expect(screen.getByText('Computer Science')).toBeInTheDocument()
      expect(screen.getByText('2026')).toBeInTheDocument()
      expect(screen.getByText('Verified Student')).toBeInTheDocument()
    })

    it('enforces read-only institutional record without edit triggers', () => {
      render(<StudentDossierBanner profile={mockProfile} />)
      expect(screen.queryByRole('button', { name: /edit/i })).not.toBeInTheDocument()
      expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
      expect(screen.getByText(/contact the University Placement Cell/i)).toBeInTheDocument()
    })
  })

  describe('EligibilityBreakdownCard', () => {
    it('renders eligible state when student meets all criteria', () => {
      render(<EligibilityBreakdownCard eligibility={mockEligibleBreakdown} />)

      expect(screen.getByText('Institutional Eligibility Evaluation')).toBeInTheDocument()
      expect(screen.getByText('Eligible to Apply')).toBeInTheDocument()
      expect(screen.getByText('8.50 CGPA')).toBeInTheDocument()
      expect(screen.getByText('9.15 CGPA')).toBeInTheDocument()
      expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    })

    it('renders itemized failure reasons when student is disqualified', () => {
      render(<EligibilityBreakdownCard eligibility={mockIneligibleBreakdown} />)

      expect(screen.getByText('Criteria Unmet')).toBeInTheDocument()
      expect(screen.getByRole('alert')).toBeInTheDocument()
      expect(
        screen.getByText('Candidate cumulative CGPA 9.15 does not satisfy minimum required CGPA of 9.50.')
      ).toBeInTheDocument()
    })

    it('renders application reference and status pill when already applied', () => {
      render(<EligibilityBreakdownCard eligibility={mockAlreadyAppliedBreakdown} />)

      expect(screen.getByText('Application on Record')).toBeInTheDocument()
      expect(screen.getByText('APP-2026-001')).toBeInTheDocument()
      expect(screen.getByText('Status: SHORTLISTED')).toBeInTheDocument()
    })
  })

  describe('DriveCard', () => {
    it('renders primary details and Apply Now button when canApply is true', () => {
      const handleApply = vi.fn()
      render(
        <MemoryRouter>
          <DriveCard drive={mockDriveItem} onApplyClick={handleApply} studentCgpa={9.15} />
        </MemoryRouter>
      )

      expect(screen.getByText('Stratum Quant Labs')).toBeInTheDocument()
      expect(screen.getByText('Quantitative Systems Analyst')).toBeInTheDocument()
      expect(screen.getByText('8.50')).toBeInTheDocument()
      expect(screen.getByText('2026-11-20')).toBeInTheDocument()

      const applyBtn = screen.getByRole('button', { name: /Apply Now/i })
      expect(applyBtn).toBeInTheDocument()
      fireEvent.click(applyBtn)
      expect(handleApply).toHaveBeenCalledWith(mockDriveItem)
    })

    it('renders View Application button when already applied', () => {
      const appliedDrive = {
        ...mockDriveItem,
        hasApplied: true,
        canApply: false,
        existingApplicationId: 'APP-2026-001',
      }
      render(
        <MemoryRouter>
          <DriveCard drive={appliedDrive} studentCgpa={9.15} />
        </MemoryRouter>
      )

      expect(screen.getByRole('button', { name: /View Application/i })).toBeInTheDocument()
    })
  })

  describe('InterviewSessionCard', () => {
    it('renders confirmed appointment badge, room, interviewer, and time range', () => {
      render(
        <MemoryRouter>
          <InterviewSessionCard session={mockInterviewSession} />
        </MemoryRouter>
      )

      expect(screen.getByText('Crestline Systems')).toBeInTheDocument()
      expect(screen.getByText('Full-Stack Developer Intern')).toBeInTheDocument()
      expect(screen.getByText('Placement Suite 302')).toBeInTheDocument()
      expect(screen.getByText('Dr. Michael Chen')).toBeInTheDocument()
      expect(screen.getByText('Confirmed Appointment')).toBeInTheDocument()
      expect(screen.getByText('INT-2026-801')).toBeInTheDocument()
    })
  })

  describe('ApplicationConfirmationModal', () => {
    it('renders explicit confirmation dialogue informing student that applications cannot be retracted', () => {
      const queryClient = createTestQueryClient()
      const handleClose = vi.fn()

      render(
        <QueryClientProvider client={queryClient}>
          <ApplicationConfirmationModal
            isOpen={true}
            onClose={handleClose}
            drive={mockDriveItem}
            studentProfile={{
              studentId: mockProfile.id,
              name: mockProfile.name,
              branch: mockProfile.branch,
              cgpa: mockProfile.cgpa,
            }}
          />
        </QueryClientProvider>
      )

      expect(screen.getByText('Confirm Application Submission')).toBeInTheDocument()
      expect(screen.getByText('Stratum Quant Labs')).toBeInTheDocument()
      expect(screen.getByText('Quantitative Systems Analyst')).toBeInTheDocument()
      expect(screen.getByText('Institutional Placement Regulation')).toBeInTheDocument()
      expect(
        screen.getByText(/Applications submitted to corporate partners are binding and cannot be retracted/i)
      ).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /Confirm & Submit Application/i })).toBeInTheDocument()
    })
  })
})
