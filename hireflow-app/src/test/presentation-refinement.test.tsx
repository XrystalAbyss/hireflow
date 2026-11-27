import { describe, expect, it, vi } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import {
  formatStudentDisplayName,
  formatCompanyDisplayName,
  formatInterviewerDisplayName,
  formatContactEmail,
  formatEligibilityExplanation,
} from '@/lib/presentationAdapter'
import { CandidateDetailDialog } from '@/features/officer/components/CandidateDetailDialog'
import type { OfficerApplicationReviewDto, OfficerDriveDetailDto } from '@/types/api'

const mockMutateAsync = vi.fn()

vi.mock('@/features/officer/api/officerApi', () => ({
  useShortlistApplicationMutation: () => ({
    mutateAsync: mockMutateAsync,
    isPending: false,
  }),
}))

function renderWithQuery(ui: React.ReactElement) {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>)
}

const baseIneligibleApp: OfficerApplicationReviewDto = {
  id: 'APP-NBA261128-165',
  studentId: 'STU-NBA261128-077',
  studentName: 'Sana Nair [Demo]',
  studentEmail: 'nba261128.student077@example.test',
  branch: 'EEE',
  cgpa: 6.2,
  driveId: 'DRV-NBA261128-01',
  companyName: 'Aster Cloudworks [Synthetic]',
  jobTitle: 'Cloud Platform Engineer',
  status: 'INELIGIBLE',
  appliedAt: '2026-11-20T10:00:00',
  remarks: '[NBA261128] Synthetic negative eligibility case: Inactive student profile.',
}

const sampleDrive: OfficerDriveDetailDto = {
  id: 'DRV-NBA261128-01',
  companyId: 'CMP-NBA261128-01',
  companyName: 'Aster Cloudworks [Synthetic]',
  jobTitle: 'Cloud Platform Engineer',
  minimumCgpa: 7.0,
  eligibleBranches: ['CSE', 'IT', 'AI/ML'],
  applicationDeadline: '2026-12-02',
  closed: false,
  deadlinePassed: false,
  totalApplications: 17,
  shortlistedApplications: 4,
}

describe('Phase H — NBA Presentation Refinement Regression Suite', () => {
  it('1. removes exact known [Demo] suffix from student names', () => {
    expect(formatStudentDisplayName('Sana Nair [Demo]')).toBe('Sana Nair')
    expect(formatStudentDisplayName('Aarav Sharma')).toBe('Aarav Sharma')
  })

  it('2. removes exact known [Synthetic] suffix from company and interviewer names', () => {
    expect(formatCompanyDisplayName('Aster Cloudworks [Synthetic]')).toBe('Aster Cloudworks')
    expect(formatInterviewerDisplayName('Nisha Rao [Synthetic Panel]')).toBe('Nisha Rao')
  })

  it('3. does not mutate source API objects', () => {
    const frozen = Object.freeze({ ...baseIneligibleApp })
    expect(formatStudentDisplayName(frozen.studentName)).toBe('Sana Nair')
    expect(formatCompanyDisplayName(frozen.companyName)).toBe('Aster Cloudworks')
    expect(frozen.studentName).toBe('Sana Nair [Demo]')
    expect(frozen.companyName).toBe('Aster Cloudworks [Synthetic]')
    expect(frozen.id).toBe('APP-NBA261128-165')
  })

  it('4. preserves legitimate names containing the word "demo" or "synthetic"', () => {
    expect(formatStudentDisplayName('Demosthenes Rao')).toBe('Demosthenes Rao')
    expect(formatStudentDisplayName('Aarav Demo')).toBe('Aarav Demo')
    expect(formatCompanyDisplayName('Synthetic Biology Labs')).toBe('Synthetic Biology Labs')
  })

  it('5–9. renders Recorded CGPA, preserves INELIGIBLE status, readable inactive explanation, neutral fallback, and secondary technical IDs', async () => {
    const user = userEvent.setup()
    renderWithQuery(
      <CandidateDetailDialog
        open={true}
        onOpenChange={vi.fn()}
        application={baseIneligibleApp}
        drive={sampleDrive}
      />
    )

    // Clean display names & Recorded CGPA (never Verified CGPA)
    expect(screen.getByText('Sana Nair')).toBeInTheDocument()
    expect(screen.getByText('Aster Cloudworks')).toBeInTheDocument()
    expect(screen.getByText('Recorded CGPA')).toBeInTheDocument()
    expect(screen.queryByText(/Verified CGPA/i)).not.toBeInTheDocument()

    // No raw [Demo], [Synthetic], or [NBA261128] in primary surfaces before expanding details
    expect(screen.queryByText(/\[Demo\]/)).not.toBeInTheDocument()
    expect(screen.queryByText(/\[Synthetic\]/)).not.toBeInTheDocument()
    expect(screen.queryByText(/\[NBA261128\]/)).not.toBeInTheDocument()

    // Ineligible status preserved & readable inactive explanation shown
    expect(screen.getAllByText('Ineligible').length).toBeGreaterThanOrEqual(1)
    expect(
      screen.getByText("The student's profile is currently inactive.")
    ).toBeInTheDocument()

    // Protected action button (Shortlist Candidate) not offered for INELIGIBLE
    expect(screen.queryByRole('button', { name: /Shortlist Candidate/i })).not.toBeInTheDocument()
    expect(screen.getByText('Not eligible for shortlisting')).toBeInTheDocument()

    // @example.test contact is not rendered as a functional mailto link or operational email
    expect(screen.queryByText(/example\.test/i)).not.toBeInTheDocument()
    expect(screen.queryByRole('link')).not.toBeInTheDocument()
    expect(screen.getByText('Institutional contact unavailable')).toBeInTheDocument()

    // Technical IDs are hidden until expandable Secondary Details is opened
    expect(screen.queryByText('STU-NBA261128-077')).not.toBeInTheDocument()
    const toggleBtn = screen.getByRole('button', { name: /Technical & Audit Details/i })
    expect(toggleBtn).toHaveAttribute('aria-expanded', 'false')
    await user.click(toggleBtn)
    expect(toggleBtn).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByText('STU-NBA261128-077')).toBeInTheDocument()
    expect(screen.getByText('APP-NBA261128-165')).toBeInTheDocument()
  })

  it('8. does not fabricate specific causes for unknown seeded remarks', () => {
    const neutral = formatEligibilityExplanation({
      status: 'APPLIED',
      remarks: '[NBA261128] Synthetic internal note with unknown text.',
    })
    expect(neutral).toBe('Application submitted and awaiting screening review.')
    expect(neutral).not.toContain('NBA261128')
    expect(neutral).not.toContain('Synthetic')
  })

  it('10 & 12. allows shortlisting for APPLIED/ELIGIBLE and supports keyboard close/Escape', async () => {
    const user = userEvent.setup()
    const onOpenChange = vi.fn()
    const appliedApp: OfficerApplicationReviewDto = {
      ...baseIneligibleApp,
      id: 'APP-NBA261128-111',
      status: 'APPLIED',
      cgpa: 8.5,
      branch: 'CSE',
      remarks: '[NBA261128] Synthetic NBA evaluation workflow sample.',
    }

    renderWithQuery(
      <CandidateDetailDialog
        open={true}
        onOpenChange={onOpenChange}
        application={appliedApp}
        drive={sampleDrive}
      />
    )

    const shortlistBtn = screen.getByRole('button', { name: /Shortlist Candidate/i })
    expect(shortlistBtn).toBeEnabled()

    const dialog = screen.getByRole('dialog')
    expect(within(dialog).getAllByRole('button', { name: 'Close', exact: true }).length).toBeGreaterThanOrEqual(1)

    await user.keyboard('{Escape}')
    expect(onOpenChange).toHaveBeenCalledWith(false)
  })

  it('14. treats @example.test emails as non-contactable placeholders', () => {
    const info = formatContactEmail('nba261128.student001@example.test')
    expect(info.isPlaceholder).toBe(true)
    expect(info.canContact).toBe(false)
    expect(info.display).toBe('Institutional contact unavailable')

    const real = formatContactEmail('student@university.edu')
    expect(real.isPlaceholder).toBe(false)
    expect(real.canContact).toBe(true)
    expect(real.display).toBe('student@university.edu')
  })
})
