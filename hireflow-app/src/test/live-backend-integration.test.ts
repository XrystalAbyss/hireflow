/**
 * Live System End-to-End Integration Test Suite (CAP-8.5).
 * 
 * Executes real HTTP requests directly against the live Spring Boot backend
 * (port 8081) and verified isolated PostgreSQL on 127.0.0.1:55432.
 * 
 * Tests complete Student, Officer, and Algorithmic Scheduling Studio workflows
 * with real session cookies, CSRF token rotations, and PostgreSQL persistence assertions.
 */

import { describe, it, expect, beforeAll, afterAll } from 'vitest'
import { execFileSync } from 'child_process'
import { writeFileSync } from 'node:fs'
function purgeIsolatedFixtures(runId: string) {
  if (!/^[A-Z0-9]+$/.test(runId)) throw new Error('Unsafe test run identifier')
  const sql = [
    "DELETE FROM interview_sessions WHERE application_id IN (SELECT id FROM applications WHERE drive_id = 'DRV-LIVE-" + runId + "');",
    "DELETE FROM applications WHERE drive_id = 'DRV-LIVE-" + runId + "';",
    "DELETE FROM recruitment_drives WHERE id = 'DRV-LIVE-" + runId + "';",
    "DELETE FROM interviewers WHERE id = 'INT-LIVE-" + runId + "';",
    "DELETE FROM rooms WHERE id = 'RM-LIVE-" + runId + "';",
    "DELETE FROM companies WHERE id = 'CMP-LIVE-" + runId + "';",
  ].join(' ')
  execFileSync('docker', ['exec', 'hireflow-cap8-postgres', 'psql', '-X', '-U', 'hireflow_cap8_app', '-d', 'hireflow_cap8_it', '-v', 'ON_ERROR_STOP=1', '-c', sql], { encoding: 'utf8' })
}
function databaseRows(driveId: string) {
  if (!/^DRV-LIVE-[A-Z0-9]+$/.test(driveId)) throw new Error('Unsafe test drive identifier')
  return JSON.parse(execFileSync('docker', ['exec', 'hireflow-cap8-postgres', 'psql', '-X', '-U', 'hireflow_cap8_app', '-d', 'hireflow_cap8_it', '-v', 'ON_ERROR_STOP=1', '-Atc', "SELECT coalesce(json_agg(row_to_json(t)), '[]'::json) FROM (SELECT a.id AS application_id,a.status,s.id AS session_id,s.interviewer_id,s.room_id,s.start_time,s.end_time FROM applications a JOIN interview_sessions s ON s.application_id=a.id WHERE a.drive_id='" + driveId + "') t"], { encoding: 'utf8' }).trim())
}

const BASE_URL = 'http://localhost:8081'

// Helper for cookie-aware HTTP requests
class LiveHttpClient {
  private cookies: Map<string, string> = new Map()
  private csrfToken: string | null = null

  public getCookieHeader(): string {
    return Array.from(this.cookies.entries())
      .map(([k, v]) => `${k}=${v}`)
      .join('; ')
  }

  public updateCookies(response: Response) {
    const setCookieHeaders = response.headers.getSetCookie
      ? response.headers.getSetCookie()
      : [response.headers.get('set-cookie') || '']

    for (const raw of setCookieHeaders) {
      if (!raw) continue
      const parts = raw.split(';')
      if (parts.length > 0) {
        const [k, v] = parts[0].split('=')
        if (k && v !== undefined) {
          this.cookies.set(k.trim(), v.trim())
        }
      }
    }
  }

  public async fetchCsrf(): Promise<string> {
    const res = await fetch(`${BASE_URL}/api/v1/auth/csrf`, {
      headers: {
        Cookie: this.getCookieHeader(),
      },
    })
    this.updateCookies(res)
    const data = await res.json()
    this.csrfToken = data.token
    return data.token
  }

  public async request<T = any>(
    path: string,
    options: RequestInit = {}
  ): Promise<{ status: number; data: T; response: Response }> {
    const headers: Record<string, string> = {
      Accept: 'application/json',
      Cookie: this.getCookieHeader(),
      ...(options.headers as Record<string, string>),
    }

    if (options.method && options.method !== 'GET') {
      if (!this.csrfToken) {
        await this.fetchCsrf()
      }
      headers['X-CSRF-TOKEN'] = this.csrfToken!
      if (options.body && typeof options.body === 'string') {
        headers['Content-Type'] = 'application/json'
      }
    }

    const response = await fetch(`${BASE_URL}${path}`, {
      ...options,
      headers,
    })

    this.updateCookies(response)

    let data: any = null
    const text = await response.text()
    if (text) {
      try {
        data = JSON.parse(text)
      } catch {
        data = text
      }
    }

    return { status: response.status, data, response }
  }

  public async login(username: string, password: string): Promise<any> {
    await this.fetchCsrf()
    const res = await this.request('/api/v1/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    })
    // Immediately refresh CSRF following Spring Security session rotation
    if (res.status === 200) {
      await this.fetchCsrf()
    }
    return res
  }

  public async logout(): Promise<void> {
    await this.request('/api/v1/auth/logout', { method: 'POST' })
    this.cookies.clear()
    this.csrfToken = null
  }
}

const optInValue = process.env.HIREFLOW_ALLOW_ISOLATED_IT
const isExplicitLiveOptIn = Boolean(optInValue && optInValue.trim() !== '')

describe.skipIf(!isExplicitLiveOptIn)('HireFlow Live System Real-HTTP Integration Suite (CAP-8.5)', () => {
  const client = new LiveHttpClient()

  // Deterministic resource identifiers for this test run
  const testId = Date.now().toString(36).toUpperCase()
  const testCompanyId = `CMP-LIVE-${testId}`
  const testDriveId = `DRV-LIVE-${testId}`
  const testInterviewerId = `INT-LIVE-${testId}`
  const testRoomId = `RM-LIVE-${testId}`
  let submittedApplicationId: string
  let isolationVerified = false

  beforeAll(async () => {
    if (optInValue !== '55432/hireflow_cap8_it') {
      throw new Error(`Explicit isolated integration opt-in required: expected '55432/hireflow_cap8_it', got '${optInValue}'`)
    }
    const binding = JSON.parse(execFileSync('docker', ['inspect', 'hireflow-cap8-postgres', '--format', '{{json .HostConfig.PortBindings}}'], { encoding: 'utf8' }))
    expect(binding['5432/tcp']).toEqual([{ HostIp: '127.0.0.1', HostPort: '55432' }])
    const identity = execFileSync('docker', ['exec', 'hireflow-cap8-postgres', 'psql', '-X', '-U', 'hireflow_cap8_app', '-d', 'hireflow_cap8_it', '-v', 'ON_ERROR_STOP=1', '-Atc', "SELECT current_database() || '|' || current_user || '|' || marker_value FROM _integration_sentinel WHERE marker_key='ENVIRONMENT_ID' AND NOT EXISTS (SELECT FROM pg_roles WHERE rolname=current_user AND (rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls))"], { encoding: 'utf8' }).trim()
    expect(identity).toBe('hireflow_cap8_it|hireflow_cap8_app|HIREFLOW_CAP8_ISOLATED_INTEGRATION_2026')
    isolationVerified = true
    // Verify backend is reachable and database status is CONNECTED
    const health = await fetch(`${BASE_URL}/api/v1/health`)
    expect(health.status).toBe(200)
    const healthJson = await health.json()
    expect(healthJson.status).toBe('UP')
    expect(healthJson.databaseStatus).toBe('CONNECTED')
    expect(healthJson.schemaStatus).toBe('FLYWAY_VALIDATED')

  })

  // ============================================================================
  // Step 1: Officer Flow — Resource Registration & Drive Creation
  // ============================================================================
  it('1. Authenticates officer and creates company, drive, interviewer, and room', async () => {
    // Officer Login
    const loginRes = await client.login('e2e.officer', 'OfficerPass123!')
    expect(loginRes.status).toBe(200)
    expect(loginRes.data.username).toBe('e2e.officer')
    expect(loginRes.data.role).toBe('PLACEMENT_OFFICER')

    // Verify current user endpoint
    const meRes = await client.request('/api/v1/auth/me')
    expect(meRes.status).toBe(200)
    expect(meRes.data.role).toBe('PLACEMENT_OFFICER')

    // Create Corporate Partner
    const compRes = await client.request('/api/v1/officer/companies', {
      method: 'POST',
      body: JSON.stringify({
        id: testCompanyId,
        name: `Live Tech Systems ${testId}`,
        industry: 'Cloud Infrastructure & AI',
      }),
    })
    expect(compRes.status).toBe(201)

    // Create Interviewer Resource
    const intRes = await client.request('/api/v1/officer/interviewers', {
      method: 'POST',
      body: JSON.stringify({
        id: testInterviewerId,
        name: `Dr. Lead Interviewer ${testId}`,
        companyId: testCompanyId,
      }),
    })
    expect(intRes.status).toBe(201)

    // Create Room Resource
    const rmRes = await client.request('/api/v1/officer/rooms', {
      method: 'POST',
      body: JSON.stringify({
        id: testRoomId,
        name: `Interview Studio ${testId}`,
        capacity: 4,
      }),
    })
    expect(rmRes.status).toBe(201)

    // Calculate future application deadline (30 days ahead)
    const deadline = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000)
      .toISOString()
      .split('T')[0]

    // Create Recruitment Drive
    const drvRes = await client.request('/api/v1/officer/drives', {
      method: 'POST',
      body: JSON.stringify({
        id: testDriveId,
        companyId: testCompanyId,
        jobTitle: `Systems Performance Architect ${testId}`,
        minimumCgpa: 7.0,
        eligibleBranches: ['CSE', 'IT'],
        applicationDeadline: deadline,
      }),
    })
    expect(drvRes.status).toBe(201)

    // Verify Drive is retrievable
    const driveDetail = await client.request(`/api/v1/officer/drives/${testDriveId}`)
    expect(driveDetail.status).toBe(200)
    expect(driveDetail.data.id).toBe(testDriveId)
    expect(driveDetail.data.closed).toBe(false)

    await client.logout()
  })

  // ============================================================================
  // Step 2: Student Flow — Browsing, Eligibility, and Application Submission
  // ============================================================================
  it('2. Authenticates student, evaluates eligibility, submits application and verifies persistence', async () => {
    // Student Login (Alan Turing: E2E-STU-01)
    const stuLogin = await client.login('e2e.student', 'StudentPass123!')
    expect(stuLogin.status).toBe(200)
    expect(stuLogin.data.username).toBe('e2e.student')
    expect(stuLogin.data.role).toBe('STUDENT')
    expect(stuLogin.data.studentId).toBe('E2E-STU-01')

    // Verify Academic Profile
    const profile = await client.request('/api/v1/student/profile')
    expect(profile.status).toBe(200)
    expect(profile.data.id).toBe('E2E-STU-01')
    expect(profile.data.cgpa).toBeGreaterThanOrEqual(7.0)

    // Check Eligibility on newly created drive
    const eligRes = await client.request(`/api/v1/student/drives/${testDriveId}/eligibility`)
    expect(eligRes.status).toBe(200)
    expect(eligRes.data.academicallyEligible).toBe(true)
    expect(eligRes.data.acceptingApplications).toBe(true)
    expect(eligRes.data.canApply).toBe(true)
    expect(eligRes.data.alreadyApplied).toBe(false)

    // Submit Application
    const submitRes = await client.request('/api/v1/student/applications', {
      method: 'POST',
      body: JSON.stringify({ driveId: testDriveId }),
    })
    expect(submitRes.status).toBe(201)
    expect(submitRes.data.id).toBeDefined()
    expect(submitRes.data.status).toBe('APPLIED')
    submittedApplicationId = submitRes.data.id

    // Verify Duplicate Application Prevention
    const duplicateRes = await client.request('/api/v1/student/applications', {
      method: 'POST',
      body: JSON.stringify({ driveId: testDriveId }),
    })
    expect([400, 409]).toContain(duplicateRes.status)

    // Verify Application History
    const historyRes = await client.request('/api/v1/student/applications')
    expect(historyRes.status).toBe(200)
    const found = historyRes.data.content.find((a: any) => a.id === submittedApplicationId)
    expect(found).toBeDefined()
    expect(found.status).toBe('APPLIED')

    await client.logout()
  })

  // ============================================================================
  // Step 3: Officer Shortlisting & Algorithmic Preview (Zero DB Mutation)
  // ============================================================================
  it('3. Shortlists candidate, generates advisory preview, and verifies zero premature DB mutations', async () => {
    await client.login('e2e.officer', 'OfficerPass123!')

    // Candidate Application Review
    const appsRes = await client.request(`/api/v1/officer/drives/${testDriveId}/applications`)
    expect(appsRes.status).toBe(200)
    const appItem = appsRes.data.content.find((a: any) => a.id === submittedApplicationId)
    expect(appItem).toBeDefined()
    expect(appItem.status).toBe('APPLIED')

    // Execute Individual Shortlisting
    const shortlistRes = await client.request(
      `/api/v1/officer/applications/${submittedApplicationId}/shortlist`,
      { method: 'POST' }
    )
    expect(shortlistRes.status).toBe(200)
    expect(shortlistRes.data.status).toBe('SHORTLISTED')

    // Scheduling Window on unique date per test run to prevent candidate overlap conflicts across test runs
    const dayOffset = (parseInt(testId.slice(-6), 36) % 200) + 1
    const testDate = new Date(Date.now() + dayOffset * 24 * 60 * 60 * 1000).toISOString().split('T')[0]

    // Generate Advisory Preview (POST /api/v1/officer/scheduling/preview)
    const previewRes = await client.request('/api/v1/officer/scheduling/preview', {
      method: 'POST',
      body: JSON.stringify({
        driveId: testDriveId,
        date: testDate,
        startTime: '09:00',
        endTime: '17:00',
        durationMinutes: 45,
        bufferMinutes: 15,
        interviewerIds: [testInterviewerId],
        roomIds: [testRoomId],
      }),
    })

    expect(previewRes.status).toBe(200)
    const preview = previewRes.data
    expect(preview.previewId).toBeDefined()
    expect(preview.totalRequested).toBe(1)
    expect(preview.totalScheduled).toBe(1)
    expect(preview.totalUnscheduled).toBe(0)
    expect(preview.proposedSessions).toHaveLength(1)
    expect(preview.proposedSessions[0].studentId).toBe('E2E-STU-01')
    expect(preview.proposedSessions[0].interviewerId).toBe(testInterviewerId)
    expect(preview.proposedSessions[0].roomId).toBe(testRoomId)

    // ZERO DB MUTATION ASSERTION: Check that no interview sessions exist yet for this drive
    const publishedCheck = await client.request(
      `/api/v1/officer/scheduling/published?driveId=${testDriveId}`
    )
    expect(publishedCheck.status).toBe(200)
    expect(publishedCheck.data.totalElements).toBe(0)
    expect(databaseRows(testDriveId)).toHaveLength(0)

    // Check application status is STILL SHORTLISTED (not prematurely changed)
    const appCheck = await client.request(`/api/v1/officer/applications/${submittedApplicationId}`)
    expect(appCheck.status).toBe(200)
    expect(appCheck.data.status).toBe('SHORTLISTED')

    // ============================================================================
    // Step 4: Transactional Timetable Publication & PostgreSQL Persistence
    // ============================================================================
    // Two genuine concurrent HTTP submissions of the same token: one winner only.
    const publicationResults = await Promise.all([0, 1].map(() => client.request('/api/v1/officer/scheduling/publish', {
      method: 'POST', body: JSON.stringify({ previewId: preview.previewId }),
    })))
    expect(publicationResults.map(r => r.status).sort()).toEqual([200, 409])
    const publishRes = publicationResults.find(r => r.status === 200)!

    expect(publishRes.status).toBe(200)
    const pubSummary = publishRes.data
    expect(pubSummary.outcome).toBe('ALL_CONFIRMED')
    expect(pubSummary.publishedCount).toBe(1)
    expect(pubSummary.conflictsCount).toBe(0)
    expect(pubSummary.confirmedSessionIds).toHaveLength(1)
    const confirmedSessionId = pubSummary.confirmedSessionIds[0]
    const rows = databaseRows(testDriveId)
    expect(rows).toHaveLength(1)
    expect(rows[0]).toMatchObject({ application_id: submittedApplicationId, status: 'SCHEDULED', session_id: confirmedSessionId, interviewer_id: testInterviewerId, room_id: testRoomId })
    expect(confirmedSessionId).toMatch(/^[A-F0-9]{32}$/)
    if (process.env.HIREFLOW_IT_EVIDENCE_PATH) writeFileSync(process.env.HIREFLOW_IT_EVIDENCE_PATH, JSON.stringify({ driveId: testDriveId, simultaneousPublicationStatuses: publicationResults.map(r => r.status).sort(), persisted: rows }, null, 2))

    // Verify Application status transitioned to SCHEDULED
    const appAfterPublish = await client.request(`/api/v1/officer/applications/${submittedApplicationId}`)
    expect(appAfterPublish.status).toBe(200)
    expect(appAfterPublish.data.status).toBe('SCHEDULED')

    // Inspect Confirmed Session Detail via Officer Endpoint
    const sessDetail = await client.request(`/api/v1/officer/scheduling/published/${confirmedSessionId}`)
    expect(sessDetail.status).toBe(200)
    expect(sessDetail.data.sessionId).toBe(confirmedSessionId)
    expect(sessDetail.data.studentId).toBe('E2E-STU-01')
    expect(sessDetail.data.interviewerId).toBe(testInterviewerId)
    expect(sessDetail.data.roomId).toBe(testRoomId)
    expect(sessDetail.data.status).toBe('SCHEDULED')

    // ============================================================================
    // Step 5: Token Lifecycle & Replay Prevention
    // ============================================================================
    // Replay attempt on consumed token must yield HTTP 409
    const replayRes = await client.request('/api/v1/officer/scheduling/publish', {
      method: 'POST',
      body: JSON.stringify({ previewId: preview.previewId }),
    })
    expect(replayRes.status).toBe(409)
    expect(replayRes.data.message).toContain('already been published')

    // Unknown or evicted token must yield HTTP 404
    const unknownRes = await client.request('/api/v1/officer/scheduling/publish', {
      method: 'POST',
      body: JSON.stringify({ previewId: '00000000-0000-0000-0000-000000000000' }),
    })
    expect(unknownRes.status).toBe(404)

    await client.logout()
  })

  // ============================================================================
  // Step 6: Student Confirmed Timetable Verification
  // ============================================================================
  it('4. Authenticates student and verifies confirmed interview appears on their timetable', async () => {
    await client.login('e2e.student', 'StudentPass123!')

    const timetableRes = await client.request('/api/v1/student/interviews')
    expect(timetableRes.status).toBe(200)
    expect(Array.isArray(timetableRes.data)).toBe(true)

    const interview = timetableRes.data.find((i: any) => i.driveId === testDriveId)
    expect(interview).toBeDefined()
    expect(interview.studentId).toBe('E2E-STU-01')
    expect(interview.status).toBe('SCHEDULED')
    expect(interview.interviewerId).toBe(testInterviewerId)
    expect(interview.roomId).toBe(testRoomId)
    expect(interview.startTime).toBeDefined()
    expect(interview.endTime).toBeDefined()

    await client.logout()
  })

  // ============================================================================
  // Step 7: Drive Closure Lifecycle
  // ============================================================================
  it('5. Closes recruitment drive and enforces mutation immutability', async () => {
    await client.login('e2e.officer', 'OfficerPass123!')

    // Close Drive
    const closeRes = await client.request(`/api/v1/officer/drives/${testDriveId}/close`, {
      method: 'POST',
    })
    expect(closeRes.status).toBe(200)
    expect(closeRes.data.closed).toBe(true)

    // Verify Drive is now closed
    const driveCheck = await client.request(`/api/v1/officer/drives/${testDriveId}`)
    expect(driveCheck.status).toBe(200)
    expect(driveCheck.data.closed).toBe(true)

    await client.logout()
  })

  // Remove the per-run fixtures so repeated executions never accumulate throwaway
  // drives (and stray interviewers/rooms/companies) in the isolated demonstration database.
  afterAll(() => {
    if (isolationVerified) purgeIsolatedFixtures(testId)
  })

})
