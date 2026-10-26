import { Outlet, Navigate, useLocation } from 'react-router-dom'
import { StudentShell } from '@/components/shells/StudentShell'
import { useStudentProfile } from './api/studentApi'
import { useAuth } from '@/core/auth/AuthContext'

export const StudentLayout: React.FC = () => {
  const { user, isLoading } = useAuth()
  const location = useLocation()
  const simulationEnabled = !import.meta.env.PROD && import.meta.env.VITE_HIREFLOW_MODE !== 'live' && (window as Window & { __HIREFLOW_SIMULATION_MODE__?: boolean }).__HIREFLOW_SIMULATION_MODE__ === true
  const { data: profile } = useStudentProfile(user?.role === 'STUDENT' || simulationEnabled)

  const academicProfile = profile ? {
    name: profile.name,
    studentId: profile.id,
    branch: profile.branch,
    cgpa: profile.cgpa,
  } : undefined
  if (isLoading) return <div role="status">Checking session...</div>
  if (!user && !simulationEnabled) return <Navigate to="/auth/login" replace />
  if (user && user.role !== 'STUDENT') return <Navigate to="/officer/dashboard" replace />

  // If navigating directly to /student, redirect to /student/dashboard
  if (location.pathname === '/student' || location.pathname === '/student/') {
    return <Navigate to="/student/dashboard" replace />
  }

  return (
    <StudentShell academicProfile={academicProfile}>
      <div key={location.pathname} className="hireflow-page-enter">
        <Outlet />
      </div>
    </StudentShell>
  )
}
