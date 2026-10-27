import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { StudentLayout } from '@/features/student/StudentLayout'
import { OfficerLayout } from '@/features/officer/OfficerLayout'

const state = vi.hoisted(() => ({ user: null as null | { role: string, displayName: string, studentId: string, username: string }, isLoading: false }))
vi.mock('@/core/auth/AuthContext', () => ({ useAuth: () => state }))
vi.mock('@/features/student/api/studentApi', () => ({ useStudentProfile: () => ({ data: undefined }) }))
vi.mock('@/components/shells/StudentShell', () => ({ StudentShell: () => <div>Protected student content</div> }))
vi.mock('@/components/shells/OfficerShell', () => ({ OfficerShell: () => <div>Protected officer content</div> }))
function tree(entry: string) {
  return <MemoryRouter initialEntries={[entry]}><Routes>
    <Route path="/auth/login" element={<div>Institutional sign in</div>} />
    <Route path="/student/*" element={<StudentLayout />} />
    <Route path="/officer/*" element={<OfficerLayout />} />
  </Routes></MemoryRouter>
}
beforeEach(() => {
  state.user = null
  state.isLoading = false
  ;(window as any).__HIREFLOW_SIMULATION_MODE__ = false
})
describe('Protected layout session boundaries', () => {
  it.each(['/student/dashboard', '/officer/dashboard'])('redirects signed-out visits to %s', (route) => {
    render(tree(route))
    expect(screen.getByText('Institutional sign in')).toBeInTheDocument()
    expect(screen.queryByText(/Protected .* content/)).not.toBeInTheDocument()
  })
  it('unmounts protected content when logout clears the principal', () => {
    state.user = { role: 'STUDENT', displayName: 'Fictional student', studentId: 'S-1', username: 'student' }
    const view = render(tree('/student/dashboard'))
    expect(screen.getByText('Protected student content')).toBeInTheDocument()
    state.user = null
    view.rerender(tree('/student/dashboard'))
    expect(screen.getByText('Institutional sign in')).toBeInTheDocument()
    expect(screen.queryByText('Protected student content')).not.toBeInTheDocument()
  })
  it('waits for session resolution without exposing protected content', () => {
    state.isLoading = true
    render(tree('/student/dashboard'))
    expect(screen.getByRole('status')).toHaveTextContent('Checking session')
    expect(screen.queryByText('Protected student content')).not.toBeInTheDocument()
  })
})
