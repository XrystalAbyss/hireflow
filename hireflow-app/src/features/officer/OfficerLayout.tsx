import * as React from 'react'
import { Outlet, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { OfficerShell } from '@/components/shells/OfficerShell'
import { useAuth } from '@/core/auth/AuthContext'
import { ShieldAlert, ArrowLeft } from 'lucide-react'
import { Button } from '@/components/ui/button'

export const OfficerLayout: React.FC = () => {
  const { user, isLoading } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()

  const simulationEnabled = !import.meta.env.PROD && import.meta.env.VITE_HIREFLOW_MODE !== 'live' && (window as Window & { __HIREFLOW_SIMULATION_MODE__?: boolean }).__HIREFLOW_SIMULATION_MODE__ === true
  if (isLoading) return <div role="status">Checking session...</div>
  if (!user && !simulationEnabled) return <Navigate to="/auth/login" replace />
  

  // If navigating directly to /officer, redirect to /officer/dashboard
  if (location.pathname === '/officer' || location.pathname === '/officer/') {
    return <Navigate to="/officer/dashboard" replace />
  }

  // Authorization check: If a student role user attempts to access officer operations, display institutional access denial
  if (user && user.role === 'STUDENT') {
    return (
      <div className="min-h-screen bg-background flex items-center justify-center p-4">
        <div className="max-w-md w-full rounded-md border border-destructive/30 bg-card p-6 space-y-4 shadow-sm text-center">
          <div className="mx-auto h-12 w-12 rounded-full bg-destructive/10 flex items-center justify-center text-destructive">
            <ShieldAlert className="h-6 w-6" />
          </div>
          <div className="space-y-1">
            <h1 className="font-serif text-lg font-bold text-foreground">
              Officer Authorization Required
            </h1>
            <p className="text-xs text-muted-foreground leading-relaxed">
              Your account ({user.username}) is registered with student permissions. The Placement Command Center and administrative operations are restricted to accredited Placement Officers and Administrators.
            </p>
          </div>
          <div className="pt-2 flex justify-center gap-3">
            <Button
              size="sm"
              variant="outline"
              onClick={() => navigate('/student/dashboard')}
              className="text-xs gap-1.5"
            >
              <ArrowLeft className="h-3.5 w-3.5" />
              <span>Return to Student Hub</span>
            </Button>
            <Button
              size="sm"
              onClick={() => navigate('/auth/login')}
              className="text-xs"
            >
              Switch Account
            </Button>
          </div>
        </div>
      </div>
    )
  }

  return (
    <OfficerShell>
      <div key={location.pathname} className="hireflow-page-enter">
        <Outlet />
      </div>
    </OfficerShell>
  )
}
