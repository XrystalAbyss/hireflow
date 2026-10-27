import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StudentShell } from '@/components/shells/StudentShell'

vi.mock('@/core/auth/AuthContext', () => ({
  useAuth: () => ({
    user: { username: 'student', studentId: 'DEMO-STU-LOGIN', displayName: 'Student', role: 'STUDENT' },
    logout: vi.fn(),
  }),
}))

describe('Student shell verified-profile boundary', () => {
  it('keeps the authenticated identity but never invents branch or GPA if profile loading fails', () => {
    const queryClient = new QueryClient()
    render(<QueryClientProvider client={queryClient}><MemoryRouter><StudentShell academicProfile={undefined}><div>Student work</div></StudentShell></MemoryRouter></QueryClientProvider>)
    expect(screen.getByText('Student work')).toBeInTheDocument()
    expect(screen.getByText('Verified profile unavailable')).toBeInTheDocument()
    expect(screen.getByText('—')).toBeInTheDocument()
    expect(screen.getByText('Student', { exact: true })).toBeInTheDocument()
    expect(screen.queryByText('9.15')).not.toBeInTheDocument()
    expect(screen.queryByText('9.85')).not.toBeInTheDocument()
  })
})
