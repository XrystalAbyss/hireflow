import * as React from 'react'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ApiError } from '@/core/api/client'
import { AuthProvider } from '@/core/auth/AuthContext'
import { TooltipProvider } from '@/components/ui/tooltip'
import { LoginPage } from '@/features/auth/LoginPage'
import { StudentLayout } from '@/features/student/StudentLayout'
import { OfficerLayout } from '@/features/officer/OfficerLayout'

// Route-level code splitting for performance and bundle size optimization
const ComponentWorkshopPage = React.lazy(() =>
  import('@/features/workshop/ComponentWorkshopPage').then((m) => ({ default: m.ComponentWorkshopPage }))
)
const StudentDashboardPage = React.lazy(() =>
  import('@/features/student/pages/StudentDashboardPage').then((m) => ({ default: m.StudentDashboardPage }))
)
const StudentDrivesPage = React.lazy(() =>
  import('@/features/student/pages/StudentDrivesPage').then((m) => ({ default: m.StudentDrivesPage }))
)
const StudentDriveDetailPage = React.lazy(() =>
  import('@/features/student/pages/StudentDriveDetailPage').then((m) => ({ default: m.StudentDriveDetailPage }))
)
const StudentApplicationsPage = React.lazy(() =>
  import('@/features/student/pages/StudentApplicationsPage').then((m) => ({ default: m.StudentApplicationsPage }))
)
const StudentApplicationDetailPage = React.lazy(() =>
  import('@/features/student/pages/StudentApplicationDetailPage').then((m) => ({ default: m.StudentApplicationDetailPage }))
)
const StudentInterviewsPage = React.lazy(() =>
  import('@/features/student/pages/StudentInterviewsPage').then((m) => ({ default: m.StudentInterviewsPage }))
)
const StudentInterviewDetailPage = React.lazy(() =>
  import('@/features/student/pages/StudentInterviewDetailPage').then((m) => ({ default: m.StudentInterviewDetailPage }))
)

const OfficerDashboardPage = React.lazy(() =>
  import('@/features/officer/pages/OfficerDashboardPage').then((m) => ({ default: m.OfficerDashboardPage }))
)
const OfficerCompaniesPage = React.lazy(() =>
  import('@/features/officer/pages/OfficerCompaniesPage').then((m) => ({ default: m.OfficerCompaniesPage }))
)
const OfficerCompanyDetailPage = React.lazy(() =>
  import('@/features/officer/pages/OfficerCompanyDetailPage').then((m) => ({ default: m.OfficerCompanyDetailPage }))
)
const OfficerDrivesPage = React.lazy(() =>
  import('@/features/officer/pages/OfficerDrivesPage').then((m) => ({ default: m.OfficerDrivesPage }))
)
const OfficerDriveDetailPage = React.lazy(() =>
  import('@/features/officer/pages/OfficerDriveDetailPage').then((m) => ({ default: m.OfficerDriveDetailPage }))
)
const OfficerCandidateReviewPage = React.lazy(() =>
  import('@/features/officer/pages/OfficerCandidateReviewPage').then((m) => ({ default: m.OfficerCandidateReviewPage }))
)
const OfficerApplicationDetailPage = React.lazy(() =>
  import('@/features/officer/pages/OfficerApplicationDetailPage').then((m) => ({ default: m.OfficerApplicationDetailPage }))
)
const OfficerResourcesPage = React.lazy(() =>
  import('@/features/officer/pages/OfficerResourcesPage').then((m) => ({ default: m.OfficerResourcesPage }))
)
const OfficerSchedulingStudioPage = React.lazy(() =>
  import('@/features/officer/pages/OfficerSchedulingStudioPage').then((m) => ({ default: m.OfficerSchedulingStudioPage }))
)

const SuspenseLoader: React.FC = () => (
  <div className="flex h-64 w-full items-center justify-center">
    <div className="flex flex-col items-center gap-3">
      <div className="h-6 w-6 animate-spin rounded-full border-2 border-academic-teal border-t-transparent" />
      <span className="font-mono text-xs text-muted-foreground">Loading resource module...</span>
    </div>
  </div>
)

const isProduction = import.meta.env.PROD
const defaultHome = '/auth/login'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: (failureCount, error) => failureCount < 1 && !(error instanceof ApiError && error.status >= 400 && error.status < 500),
      refetchOnWindowFocus: true,
      refetchOnReconnect: true,
      staleTime: 20_000,
      gcTime: 5 * 60_000,
    },
  },
})

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <TooltipProvider delayDuration={300}>
          <BrowserRouter>
            <React.Suspense fallback={<SuspenseLoader />}>
              <Routes>
                <Route path="/" element={<Navigate to={defaultHome} replace />} />
                <Route
                  path="/workshop"
                  element={
                    isProduction ? (
                      <Navigate to="/auth/login" replace />
                    ) : (
                      <ComponentWorkshopPage />
                    )
                  }
                />
                <Route path="/auth/login" element={<LoginPage />} />

                {/* Student Opportunity Hub Routes */}
                <Route path="/student" element={<StudentLayout />}>
                  <Route index element={<Navigate to="/student/dashboard" replace />} />
                  <Route path="dashboard" element={<StudentDashboardPage />} />
                  <Route path="drives" element={<StudentDrivesPage />} />
                  <Route path="drives/:id" element={<StudentDriveDetailPage />} />
                  <Route path="applications" element={<StudentApplicationsPage />} />
                  <Route path="applications/:id" element={<StudentApplicationDetailPage />} />
                  <Route path="interviews" element={<StudentInterviewsPage />} />
                  <Route path="interviews/:id" element={<StudentInterviewDetailPage />} />
                </Route>

                {/* Placement Officer Command Center Routes (CAP-8.3) */}
                <Route path="/officer" element={<OfficerLayout />}>
                  <Route index element={<Navigate to="/officer/dashboard" replace />} />
                  <Route path="dashboard" element={<OfficerDashboardPage />} />
                  <Route path="companies" element={<OfficerCompaniesPage />} />
                  <Route path="companies/:id" element={<OfficerCompanyDetailPage />} />
                  <Route path="drives" element={<OfficerDrivesPage />} />
                  <Route path="drives/:id" element={<OfficerDriveDetailPage />} />
                  <Route path="drives/:id/applications" element={<OfficerCandidateReviewPage />} />
                  <Route path="applications" element={<OfficerCandidateReviewPage />} />
                  <Route path="applications/:id" element={<OfficerApplicationDetailPage />} />
                  <Route path="resources" element={<OfficerResourcesPage />} />
                  <Route path="scheduling" element={<OfficerSchedulingStudioPage />} />
                </Route>

                {/* Catch-all */}
                <Route path="*" element={<Navigate to={defaultHome} replace />} />
              </Routes>
            </React.Suspense>
          </BrowserRouter>
        </TooltipProvider>
      </AuthProvider>
    </QueryClientProvider>
  )
}

