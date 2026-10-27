import type { QueryClient } from '@tanstack/react-query'
import * as studentApi from '@/features/student/api/studentApi'

// Warm only the destination the person is about to open. Query data stays in
// memory, follows the normal freshness policy, and is cleared on account change.
export async function warmRoute(path: string, queryClient: QueryClient): Promise<void> {
  try {
    if (path === '/student/dashboard') {
      const api = studentApi
      const page = import('@/features/student/pages/StudentDashboardPage')
      await Promise.all([
        page,
        queryClient.prefetchQuery({ queryKey: api.studentQueryKeys.dashboard(), queryFn: api.fetchStudentDashboard }),
        queryClient.prefetchQuery({ queryKey: api.studentQueryKeys.drives(), queryFn: api.fetchStudentDrives }),
        queryClient.prefetchQuery({ queryKey: api.studentQueryKeys.interviews(), queryFn: api.fetchStudentInterviews }),
      ])
    } else if (path === '/student/drives') {
      const api = studentApi
      await Promise.all([
        import('@/features/student/pages/StudentDrivesPage'),
        queryClient.prefetchQuery({ queryKey: api.studentQueryKeys.drives(), queryFn: api.fetchStudentDrives }),
      ])
    } else if (path === '/student/applications') {
      const api = studentApi
      await Promise.all([
        import('@/features/student/pages/StudentApplicationsPage'),
        queryClient.prefetchQuery({
        queryKey: api.studentQueryKeys.applications(0, 10, 'appliedAt,desc'),
        queryFn: () => api.fetchStudentApplications(0, 10, 'appliedAt,desc'),
        }),
      ])
    } else if (path === '/student/interviews') {
      const api = studentApi
      await Promise.all([
        import('@/features/student/pages/StudentInterviewsPage'),
        queryClient.prefetchQuery({ queryKey: api.studentQueryKeys.interviews(), queryFn: api.fetchStudentInterviews }),
      ])
    } else if (path === '/officer/dashboard') {
      await import('@/features/officer/pages/OfficerDashboardPage')
    } else if (path === '/officer/companies') {
      const api = await import('@/features/officer/api/officerApi')
      await Promise.all([
        import('@/features/officer/pages/OfficerCompaniesPage'),
        queryClient.prefetchQuery({
        queryKey: api.officerQueryKeys.companies({ page: 0, size: 15, search: '' }),
        queryFn: () => api.fetchOfficerCompanies(0, 15, ''),
        }),
      ])
    } else if (path === '/officer/drives') {
      const api = await import('@/features/officer/api/officerApi')
      await Promise.all([
        import('@/features/officer/pages/OfficerDrivesPage'),
        queryClient.prefetchQuery({
          queryKey: api.officerQueryKeys.drives({ page: 0, size: 15, companyId: undefined, activeOnly: false }),
          queryFn: () => api.fetchOfficerDrives(0, 15, undefined, false),
        }),
        queryClient.prefetchQuery({
          queryKey: api.officerQueryKeys.companies({ page: 0, size: 100, search: undefined }),
          queryFn: () => api.fetchOfficerCompanies(0, 100),
        }),
      ])
    } else if (path === '/officer/applications') {
      const api = await import('@/features/officer/api/officerApi')
      await Promise.all([
        import('@/features/officer/pages/OfficerCandidateReviewPage'),
        queryClient.prefetchQuery({
        queryKey: api.officerQueryKeys.drives({ page: 0, size: 100, companyId: undefined, activeOnly: false }),
        queryFn: () => api.fetchOfficerDrives(0, 100),
        }),
      ])
    } else if (path === '/officer/resources') {
      const api = await import('@/features/officer/api/officerApi')
      await Promise.all([
        import('@/features/officer/pages/OfficerResourcesPage'),
        queryClient.prefetchQuery({ queryKey: api.officerQueryKeys.interviewers(undefined), queryFn: () => api.fetchOfficerInterviewers() }),
        queryClient.prefetchQuery({ queryKey: api.officerQueryKeys.rooms(), queryFn: api.fetchOfficerRooms }),
      ])
    } else if (path === '/officer/scheduling') {
      await import('@/features/officer/pages/OfficerSchedulingStudioPage')
    }
  } catch {
    // Prefetch is only an optimization. The destination owns its error state.
  }
}
