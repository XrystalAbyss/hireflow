/**
 * Secure HTTP Client for HireFlow (CAP-7 Backend Baseline)
 * Enforces session-cookie credentials, in-memory CSRF token management,
 * and sanitized ApiErrorResponse transformation.
 */

import type {
  ApiErrorResponse,
  CsrfTokenDto,
  LoginRequestDto,
  LogoutResponseDto,
  UserProfileDto,
} from '@/types/api'

export class ApiError extends Error {
  public readonly status: number
  public readonly error: string
  public readonly path: string
  public readonly timestamp: string

  constructor(payload: ApiErrorResponse) {
    super(payload.message || 'An error occurred while processing the request.')
    this.name = 'ApiError'
    this.status = payload.status
    this.error = payload.error
    this.path = payload.path
    this.timestamp = payload.timestamp
  }
}

class ApiClient {
  private csrfToken: string | null = null
  private csrfHeaderName: string = 'X-CSRF-TOKEN'
  private onUnauthorizedCallback: (() => void) | null = null

  public setOnUnauthorized(callback: () => void) {
    this.onUnauthorizedCallback = callback
  }

  /**
   * Refreshes the session-bound CSRF token via GET /api/v1/auth/csrf.
   * Keeps tokens strictly in memory, never persisting to localStorage or logs.
   */
  public async fetchCsrfToken(): Promise<string> {
    try {
      const response = await fetch('/api/v1/auth/csrf', {
        method: 'GET',
        headers: {
          Accept: 'application/json',
        },
        credentials: 'include',
      })

      if (!response.ok) {
        throw new Error(`Failed to retrieve CSRF token: status ${response.status}`)
      }

      const data = (await response.json()) as CsrfTokenDto
      this.csrfToken = data.token
      this.csrfHeaderName = data.headerName || 'X-CSRF-TOKEN'
      return this.csrfToken
    } catch (err) {
      this.csrfToken = null
      throw err
    }
  }

  /**
   * Core request wrapper guaranteeing:
   * - withCredentials: true
   * - X-CSRF-TOKEN injection on state-changing methods
   * - ApiErrorResponse extraction on non-2xx
   * - Safe error handling without blind retries
   */
  public async request<T>(path: string, options: RequestInit = {}): Promise<T> {
    const method = (options.method || 'GET').toUpperCase()
    const headers = new Headers(options.headers || {})

    headers.set('Accept', 'application/json')

    const isMutation = ['POST', 'PUT', 'DELETE', 'PATCH'].includes(method)

    if (isMutation) {
      if (!this.csrfToken) {
        await this.fetchCsrfToken()
      }
      if (this.csrfToken) {
        headers.set(this.csrfHeaderName, this.csrfToken)
      }
      if (options.body && !(options.body instanceof FormData)) {
        headers.set('Content-Type', 'application/json')
      }
    }

    const response = await fetch(path, {
      ...options,
      method,
      headers,
      credentials: 'include',
    })

    if (response.status === 401) {
      if (this.onUnauthorizedCallback) {
        this.onUnauthorizedCallback()
      }
    }

    if (!response.ok) {
      let errorPayload: ApiErrorResponse
      try {
        errorPayload = (await response.json()) as ApiErrorResponse
      } catch {
        errorPayload = {
          status: response.status,
          error: response.statusText || 'Error',
          message: `Request failed with HTTP status ${response.status}`,
          path,
          timestamp: new Date().toISOString(),
        }
      }
      throw new ApiError(errorPayload)
    }

    // Handle 204 No Content
    if (response.status === 204) {
      return {} as T
    }

    return (await response.json()) as T
  }


  // ============================================================================
  // Authentication Specific Endpoints
  // ============================================================================

  /**
   * Authenticates against POST /api/v1/auth/login.
   * Following successful login, immediately re-fetches CSRF token to update
   * the session-rotated token state.
   */
  public async login(credentials: LoginRequestDto): Promise<UserProfileDto> {
    // 1. Ensure initial CSRF token exists before login POST
    if (!this.csrfToken) {
      await this.fetchCsrfToken()
    }

    // 2. Perform authentication
    const profile = await this.request<UserProfileDto>('/api/v1/auth/login', {
      method: 'POST',
      body: JSON.stringify(credentials),
    })

    // 3. Spring Security rotates the session and issues a new CSRF token on login
    // Refresh cached token immediately
    try {
      await this.fetchCsrfToken()
    } catch {
      // In dev or mocked environments, non-blocking fallback
    }

    return profile
  }

  /**
   * Retrieves currently authenticated principal via GET /api/v1/auth/me.
   */
  public async getCurrentUser(): Promise<UserProfileDto> {
    return this.request<UserProfileDto>('/api/v1/auth/me', {
      method: 'GET',
    })
  }

  /**
   * Terminates session via POST /api/v1/auth/logout.
   */
  public async logout(): Promise<LogoutResponseDto> {
    try {
      const res = await this.request<LogoutResponseDto>('/api/v1/auth/logout', {
        method: 'POST',
      })
      this.csrfToken = null
      return res
    } finally {
      this.csrfToken = null
    }
  }
}

export const apiClient = new ApiClient()


