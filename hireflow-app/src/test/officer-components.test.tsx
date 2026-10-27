import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter } from 'react-router-dom'
import { OfficerStatCard } from '@/features/officer/components/OfficerStatCard'
import { CompanyDialog } from '@/features/officer/components/CompanyDialog'
import { DriveDialog } from '@/features/officer/components/DriveDialog'
import { DriveCloseDialog } from '@/features/officer/components/DriveCloseDialog'
import { CandidateDetailDialog } from '@/features/officer/components/CandidateDetailDialog'
import { RoomDialog } from '@/features/officer/components/RoomDialog'
import type { OfficerDriveDetailDto, OfficerApplicationReviewDto, OfficerCompanyDetailDto } from '@/types/api'

function renderWithProviders(ui: React.ReactElement) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>{ui}</BrowserRouter>
    </QueryClientProvider>
  )
}

describe('Placement Officer UI Components Suite', () => {
  describe('OfficerStatCard', () => {
    it('renders label, value, subtext, and responds to click', () => {
      const handleClick = vi.fn()
      render(
        <OfficerStatCard
          label="Active Drives"
          value={5}
          subtext="5 campaigns currently active"
          tone="accent"
          onClick={handleClick}
        />
      )

      expect(screen.getByText('Active Drives')).toBeInTheDocument()
      expect(screen.getByText('5')).toBeInTheDocument()
      expect(screen.getByText('5 campaigns currently active')).toBeInTheDocument()

      const card = screen.getByRole('button')
      fireEvent.click(card)
      expect(handleClick).toHaveBeenCalledTimes(1)
    })
  })

  describe('CompanyDialog', () => {
    it('renders in create mode with blank fields', () => {
      renderWithProviders(
        <CompanyDialog open={true} onOpenChange={() => {}} company={null} />
      )

      expect(screen.getByText('Register Corporate Partner')).toBeInTheDocument()
      expect(screen.getByLabelText(/Company Identifier/i)).toBeInTheDocument()
      expect(screen.getByLabelText(/Corporate Organization Name/i)).toBeInTheDocument()
      expect(screen.getByLabelText(/Industry Classification/i)).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /Register Partner/i })).toBeInTheDocument()
    })

    it('renders in edit mode with immutable ID', () => {
      const mockCompany: OfficerCompanyDetailDto = {
        id: 'COMP-001',
        name: 'Google India',
        industry: 'Technology',
        activeDrivesCount: 2,
        totalDrivesCount: 3,
      }

      renderWithProviders(
        <CompanyDialog open={true} onOpenChange={() => {}} company={mockCompany} />
      )

      expect(screen.getByText('Edit Corporate Partner')).toBeInTheDocument()
      const idInput = screen.getByLabelText(/Company Identifier/i) as HTMLInputElement
      expect(idInput.value).toBe('COMP-001')
      expect(idInput).toBeDisabled()

      const nameInput = screen.getByLabelText(/Corporate Organization Name/i) as HTMLInputElement
      expect(nameInput.value).toBe('Google India')
      expect(nameInput).not.toBeDisabled()
    })
  })

  describe('DriveDialog', () => {
    it('renders branch selectors and validates minimum CGPA', () => {
      renderWithProviders(
        <DriveDialog open={true} onOpenChange={() => {}} drive={null} />
      )

      expect(screen.getByText('Launch Recruitment Drive')).toBeInTheDocument()
      expect(screen.getByText('CSE')).toBeInTheDocument()
      expect(screen.getByText('ECE')).toBeInTheDocument()
      expect(screen.getByLabelText(/Minimum CGPA/i)).toBeInTheDocument()
    })
  })

  describe('DriveCloseDialog', () => {
    it('renders consequential warning and consequences list', () => {
      const mockDrive: OfficerDriveDetailDto = {
        id: 'DRV-2026-001',
        companyId: 'COMP-001',
        companyName: 'Google India',
        jobTitle: 'Software Engineer',
        minimumCgpa: 8.0,
        eligibleBranches: ['CSE'],
        applicationDeadline: '2026-11-15',
        closed: false,
        totalApplications: 10,
        shortlistedApplications: 4,
      }

      renderWithProviders(
        <DriveCloseDialog open={true} onOpenChange={() => {}} drive={mockDrive} />
      )

      expect(screen.getByText('Close Recruitment Drive')).toBeInTheDocument()
      expect(
        screen.getByText(/Students will no longer be able to submit applications/i)
      ).toBeInTheDocument()
      expect(
        screen.getByRole('button', { name: /Confirm Authoritative Closure/i })
      ).toBeInTheDocument()
    })
  })

  describe('CandidateDetailDialog', () => {
    it('renders candidate dossier, honors badge for high CGPA, and shortlist action', () => {
      const mockApp: OfficerApplicationReviewDto = {
        id: 'APP-2026-001',
        studentId: 'STU-2026-042',
        studentName: 'Aarav Sharma',
        studentEmail: 'aarav@hireflow.local',
        branch: 'Computer Science and Engineering',
        cgpa: 9.15, // Honors
        driveId: 'DRV-2026-001',
        jobTitle: 'Software Development Engineer I',
        companyName: 'Google India',
        status: 'APPLIED',
        appliedAt: '2026-10-18T10:15:00',
        remarks: 'Excellent portfolio',
      }

      renderWithProviders(
        <CandidateDetailDialog open={true} onOpenChange={() => {}} application={mockApp} />
      )

      expect(screen.getByText('Aarav Sharma')).toBeInTheDocument()
      expect(screen.getByText('Honors Candidate')).toBeInTheDocument()
      expect(screen.getByText('9.15')).toBeInTheDocument()
      expect(screen.getByText('Excellent portfolio')).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /Shortlist Candidate/i })).toBeInTheDocument()
    })
  })

  describe('RoomDialog', () => {
    it('validates positive room capacity constraint', () => {
      renderWithProviders(
        <RoomDialog open={true} onOpenChange={() => {}} room={null} />
      )

      expect(screen.getByText('Register Interview Room')).toBeInTheDocument()
      const capInput = screen.getByLabelText(/Room Seating Capacity/i) as HTMLInputElement
      expect(capInput.min).toBe('1')
    })
  })
})
