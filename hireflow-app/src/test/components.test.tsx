import '@testing-library/jest-dom/vitest'
import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { Button } from '@/components/ui/button'
import { FormField } from '@/components/ui/form-field'
import { Input } from '@/components/ui/input'
import { PasswordField } from '@/components/ui/password-field'
import { ApplicationStatusBadge } from '@/components/domain/application-status-badge'
import { EligibilityStatusBadge } from '@/components/domain/eligibility-status-badge'
import { NoticeBanner } from '@/components/domain/notice-banner'
import { ConfirmationDialog } from '@/components/domain/confirmation-dialog'

describe('Institutional UI Component Suite', () => {
  describe('Button Component', () => {
    it('renders with default primary styling and responds to clicks', () => {
      const handleClick = vi.fn()
      render(<Button onClick={handleClick}>Submit Roster</Button>)

      const btn = screen.getByRole('button', { name: /Submit Roster/i })
      expect(btn).toBeInTheDocument()
      fireEvent.click(btn)
      expect(handleClick).toHaveBeenCalledTimes(1)
    })

    it('displays accessible loading spinner and disables interaction when loading', () => {
      const handleClick = vi.fn()
      render(<Button loading onClick={handleClick}>Generating Timetable</Button>)

      const btn = screen.getByRole('button', { name: /Generating Timetable/i })
      expect(btn).toBeDisabled()
      expect(btn).toHaveAttribute('aria-busy', 'true')
      fireEvent.click(btn)
      expect(handleClick).not.toHaveBeenCalled()
    })
  })

  describe('FormField & PasswordField Components', () => {
    it('renders label with required indicator and connects with input', () => {
      render(
        <FormField label="Candidate Roll No." id="roll-no" required helperText="University issued ID">
          <Input id="roll-no" defaultValue="2022CS0144" />
        </FormField>
      )

      expect(screen.getByText('Candidate Roll No.')).toBeInTheDocument()
      expect(screen.getByText('*')).toBeInTheDocument()
      expect(screen.getByText('University issued ID')).toBeInTheDocument()
      expect(screen.getByDisplayValue('2022CS0144')).toBeInTheDocument()
    })

    it('renders accessible error message with role="alert"', () => {
      render(
        <FormField label="CGPA" id="cgpa" error="CGPA must be between 0.00 and 10.00">
          <Input id="cgpa" defaultValue="11.5" />
        </FormField>
      )

      const alert = screen.getByRole('alert')
      expect(alert).toHaveTextContent('CGPA must be between 0.00 and 10.00')
    })

    it('toggles password visibility when the visibility action is clicked', () => {
      render(<PasswordField id="pwd" placeholder="Enter password" defaultValue="SecretPass123!" />)

      const input = screen.getByPlaceholderText('Enter password') as HTMLInputElement
      expect(input.type).toBe('password')

      const toggleBtn = screen.getByRole('button', { name: /Show password/i })
      fireEvent.click(toggleBtn)
      expect(input.type).toBe('text')

      const hideBtn = screen.getByRole('button', { name: /Hide password/i })
      fireEvent.click(hideBtn)
      expect(input.type).toBe('password')
    })
  })

  describe('Domain Status Badges', () => {
    it('renders correct labels and semantic styles for application statuses', () => {
      const { rerender } = render(<ApplicationStatusBadge status="APPLIED" />)
      expect(screen.getByText('Applied')).toBeInTheDocument()

      rerender(<ApplicationStatusBadge status="SHORTLISTED" />)
      expect(screen.getByText('Shortlisted')).toBeInTheDocument()

      rerender(<ApplicationStatusBadge status="SCHEDULED" />)
      expect(screen.getByText('Scheduled')).toBeInTheDocument()

      rerender(<ApplicationStatusBadge status="SELECTED" />)
      expect(screen.getByText('Selected')).toBeInTheDocument()

      rerender(<ApplicationStatusBadge status="REJECTED" />)
      expect(screen.getByText('Rejected')).toBeInTheDocument()
    })

    it('renders eligibility badge correctly for both boolean and shorthand values', () => {
      const { rerender } = render(<EligibilityStatusBadge status="ELIGIBLE" />)
      expect(screen.getByText('Eligible to Apply')).toBeInTheDocument()

      rerender(<EligibilityStatusBadge status="INELIGIBLE" />)
      expect(screen.getByText('Criteria Unmet')).toBeInTheDocument()

      rerender(
        <EligibilityStatusBadge
          canApply={false}
          hasApplied={true}
          closed={false}
          deadlinePassed={false}
          academicallyEligible={true}
        />
      )
      expect(screen.getByText('Application Submitted')).toBeInTheDocument()
    })
  })

  describe('NoticeBanner Component', () => {
    it('renders accessible role="status" and dismisses when onDismiss is invoked', () => {
      const handleDismiss = vi.fn()
      render(
        <NoticeBanner variant="warning" title="Advisory Notice" onDismiss={handleDismiss}>
          Timetable slot conflict occurred.
        </NoticeBanner>
      )

      expect(screen.getByRole('status')).toBeInTheDocument()
      expect(screen.getByText('Advisory Notice')).toBeInTheDocument()
      expect(screen.getByText('Timetable slot conflict occurred.')).toBeInTheDocument()

      const dismissBtn = screen.getByRole('button', { name: /Dismiss notice/i })
      fireEvent.click(dismissBtn)
      expect(handleDismiss).toHaveBeenCalledTimes(1)
    })
  })

  describe('ConfirmationDialog Component', () => {
    it('executes onConfirm callback when confirmed', async () => {
      const handleConfirm = vi.fn()
      const handleOpenChange = vi.fn()

      render(
        <ConfirmationDialog
          open={true}
          onOpenChange={handleOpenChange}
          title="Confirm Shortlisting"
          description="Are you sure you want to shortlist this candidate?"
          confirmText="Yes, Shortlist"
          onConfirm={handleConfirm}
        />
      )

      expect(screen.getByText('Confirm Shortlisting')).toBeInTheDocument()
      expect(screen.getByText('Are you sure you want to shortlist this candidate?')).toBeInTheDocument()

      const confirmBtn = screen.getByRole('button', { name: /Yes, Shortlist/i })
      fireEvent.click(confirmBtn)
      expect(handleConfirm).toHaveBeenCalledTimes(1)
    })
  })
})
