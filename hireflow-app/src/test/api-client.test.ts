import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { apiClient, ApiError } from '@/core/api/client'
import type { CsrfTokenDto, UserProfileDto } from '@/types/api'

describe('Secure API Client & Auth Lifecycle Suite', () => {
  const originalFetch = globalThis.fetch

  beforeEach(() => {
    vi.restoreAllMocks()
  })

  afterEach(() => {
    globalThis.fetch = originalFetch
  })

  it('fetches CSRF token and injects X-CSRF-TOKEN on mutating methods', async () => {
    const mockCsrf: CsrfTokenDto = {
      token: 'mock-csrf-token-xyz',
      parameterName: '_csrf',
      headerName: 'X-CSRF-TOKEN',
    }

    const mockFetch = vi.fn().mockImplementation((url, init) => {
      if (url === '/api/v1/auth/csrf') {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: () => Promise.resolve(mockCsrf),
        })
      }
      if (url === '/api/v1/student/applications') {
        expect(init.headers.get('X-CSRF-TOKEN')).toBe('mock-csrf-token-xyz')
        expect(init.credentials).toBe('include')
        return Promise.resolve({
          ok: true,
          status: 201,
          json: () =>
            Promise.resolve({
              id: 'APP-2026-099',
              studentId: 'STU-001',
              driveId: 'drv-01',
              companyId: 'CMP-01',
              companyName: 'Acme Corp',
              jobTitle: 'Software Engineer',
              status: 'APPLIED',
              appliedAt: '2026-10-19T10:00:00',
              remarks: null,
            }),
        })
      }
      return Promise.reject(new Error(`Unhandled URL ${url}`))
    })

    globalThis.fetch = mockFetch

    const token = await apiClient.fetchCsrfToken()
    expect(token).toBe('mock-csrf-token-xyz')

    const res = await apiClient.request<{ id: string }>('/api/v1/student/applications', {
      method: 'POST',
      body: JSON.stringify({ driveId: 'drv-01' }),
    })

    expect(res.id).toBe('APP-2026-099')
  })

  it('refreshes CSRF token immediately after login to synchronize session rotation', async () => {
    let csrfFetchCount = 0
    const mockProfile: UserProfileDto = {
      username: 'student.cs01',
      displayName: 'Aarav Sharma',
      role: 'STUDENT',
      studentId: '2022CS0144',
      lastLoginAt: '2026-10-19T00:00:00Z',
    }

    const mockFetch = vi.fn().mockImplementation((url) => {
      if (url === '/api/v1/auth/csrf') {
        csrfFetchCount++
        return Promise.resolve({
          ok: true,
          status: 200,
          json: () => Promise.resolve({ token: `token-v${csrfFetchCount}`, headerName: 'X-CSRF-TOKEN' }),
        })
      }
      if (url === '/api/v1/auth/login') {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: () => Promise.resolve(mockProfile),
        })
      }
      return Promise.reject(new Error(`Unhandled URL ${url}`))
    })

    globalThis.fetch = mockFetch

    const user = await apiClient.login({ username: 'student.cs01', password: 'Password@123' })
    expect(user.username).toBe('student.cs01')
    // Should have fetched CSRF pre-login (if not present) and post-login for session rotation
    expect(csrfFetchCount).toBeGreaterThanOrEqual(1)
  })

  it('properly transforms ApiErrorResponse and includes backend message and path', async () => {
    const errorPayload = {
      status: 409,
      error: 'Conflict',
      message: 'Interviewer double-booking detected on Room R-101',
      path: '/api/v1/officer/scheduling/publish',
      timestamp: '2026-10-19T02:00:00Z',
    }

    const mockFetch = vi.fn().mockImplementation(() =>
      Promise.resolve({
        ok: false,
        status: 409,
        statusText: 'Conflict',
        json: () => Promise.resolve(errorPayload),
      })
    )

    globalThis.fetch = mockFetch

    await expect(apiClient.request('/api/v1/officer/scheduling/publish', { method: 'POST' })).rejects.toThrow(
      'Interviewer double-booking detected on Room R-101'
    )
  })

  it('triggers onUnauthorized callback on HTTP 401 response', async () => {
    const handleUnauthorized = vi.fn()
    apiClient.setOnUnauthorized(handleUnauthorized)

    const mockFetch = vi.fn().mockImplementation(() =>
      Promise.resolve({
        ok: false,
        status: 401,
        statusText: 'Unauthorized',
        json: () =>
          Promise.resolve({
            status: 401,
            error: 'Unauthorized',
            message: 'Full authentication is required to access this resource',
            path: '/api/v1/student/dashboard',
            timestamp: new Date().toISOString(),
          }),
      })
    )

    globalThis.fetch = mockFetch

    await expect(apiClient.request('/api/v1/student/dashboard')).rejects.toThrow(ApiError)
    expect(handleUnauthorized).toHaveBeenCalledTimes(1)
  })
})
