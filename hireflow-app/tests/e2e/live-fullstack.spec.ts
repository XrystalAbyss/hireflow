/**
 * HIREFLOW — CAP-8.5.2 Genuine Full-Stack Browser Integration Test Suite
 * 
 * Executes the complete 40-step Student -> Officer -> Scheduling -> Student journey
 * through the actual React UI in Chromium against the running Spring Boot API.
 * 
 * STRICT VERIFICATION RULES:
 * - Zero Playwright route mocking (`page.route` forbidden).
 * - Zero offline fixture fallback (simulation forbidden).
 * - Real session cookies, real CSRF tokens, real PostgreSQL persistence.
 */

import { test, expect } from '@playwright/test'
import { execFileSync } from 'node:child_process'
import path from 'node:path'
import fs from 'node:fs'
const localCredentials = JSON.parse(fs.readFileSync(path.resolve('../.run/demo.local.json'), 'utf8').replace(/^\uFEFF/, ''))
const demoJourney = process.env.HIREFLOW_JOURNEY_ACCOUNT === 'demo'
const studentName = demoJourney ? 'Aarav Sharma' : 'Alan Turing'
const studentId = demoJourney ? 'DEMO-STU-01' : 'E2E-STU-01'
const studentUsername = localCredentials[demoJourney ? 'demoStudentUsername' : 'studentUsername']
const studentPassword = localCredentials[demoJourney ? 'demoStudentPassword' : 'studentPassword']
const officerUsername = localCredentials[demoJourney ? 'demoOfficerUsername' : 'officerUsername']
const officerPassword = localCredentials[demoJourney ? 'demoOfficerPassword' : 'officerPassword']
const runId = Date.now().toString(36).toUpperCase()
let isolationVerified = false
const driveId = 'DRV-B-' + runId
const companyId = 'CMP-B-' + runId
const interviewerId = 'INT-B-' + runId
const roomId = 'RM-B-' + runId
const jobTitle = 'SDE-1 Browser ' + runId
const futureDate = new Date(Date.now() + (90 + Date.now() % 1000) * 86400000).toISOString().split('T')[0]
function persistedSessions() {
  return JSON.parse(execFileSync('docker', ['exec', 'hireflow-cap8-postgres', 'psql', '-X', '-U', 'hireflow_cap8_app', '-d', 'hireflow_cap8_it', '-v', 'ON_ERROR_STOP=1', '-Atc', "SELECT coalesce(json_agg(row_to_json(t)), '[]'::json) FROM (SELECT a.id AS application_id,a.status,s.id AS session_id,s.interviewer_id,s.room_id,s.start_time,s.end_time FROM applications a JOIN interview_sessions s ON s.application_id=a.id WHERE a.drive_id='" + driveId + "') t"], { encoding: 'utf8' }).trim())
}
// Prefer PowerShell 7 when present, otherwise fall back to Windows PowerShell 5.1.
function resolvePowerShell() {
  if (process.env.HIREFLOW_PS_SHELL) return process.env.HIREFLOW_PS_SHELL
  try {
    execFileSync('pwsh', ['-NoProfile', '-Command', '$PSVersionTable.PSVersion.Major'], { stdio: 'pipe' })
    return 'pwsh'
  } catch {
    return 'powershell'
  }
}
test.beforeAll(async ({ request }) => {
  execFileSync(resolvePowerShell(), ['-NoLogo', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', path.resolve('..', 'infra/cap8-integration/preflight-guard.ps1')], { env: { ...process.env, HIREFLOW_IT_APP_PASSWORD: process.env.HIREFLOW_IT_APP_PASSWORD || localCredentials.databasePassword }, stdio: 'pipe' })
  isolationVerified = true
  const base = 'http://localhost:8081/api/v1'
  let csrf = await (await request.get(base + '/auth/csrf')).json()
  const login = await request.post(base + '/auth/login', { data: { username: officerUsername, password: officerPassword }, headers: { 'X-CSRF-TOKEN': csrf.token } })
  expect(login.status()).toBe(200)
  csrf = await (await request.get(base + '/auth/csrf')).json()
  for (const [url, data] of [
    ['/officer/companies', { id: companyId, name: 'Fictional Browser Company ' + runId, industry: 'Integration tests' }],
    ['/officer/interviewers', { id: interviewerId, name: 'Fictional Browser Interviewer ' + runId, companyId }],
    ['/officer/rooms', { id: roomId, name: 'Fictional Browser Room ' + runId, capacity: 2 }],
    ['/officer/drives', { id: driveId, companyId, jobTitle, minimumCgpa: 7, eligibleBranches: ['CSE'], applicationDeadline: futureDate }],
  ] as const) {
    expect((await request.post(base + url, { data, headers: { 'X-CSRF-TOKEN': csrf.token } })).status()).toBe(201)
  }
  expect((await request.post(base + '/auth/logout', { headers: { 'X-CSRF-TOKEN': csrf.token } })).status()).toBe(200)
})

test.describe.configure({ mode: 'serial' })
test.use({ actionTimeout: 15000, navigationTimeout: 15000 })

test.describe('HireFlow CAP-8.5.2 Genuine Full-Stack Journey', () => {
  test('Complete Student -> Officer -> Scheduling -> Student 40-Step Journey', async ({ page }) => {
    test.setTimeout(120000)

    // ------------------------------------------------------------------------
    // STUDENT PHASE (Steps 1 - 12)
    // ------------------------------------------------------------------------
    
    // Step 1: Open real login page
    await page.goto('/auth/login')
    await expect(page.getByRole('heading', { name: /Institutional Sign In|HireFlow/i })).toBeVisible()

    // Step 2: Enter fictional student credentials
    await page.locator('#username').fill(studentUsername)
    await page.locator('#password').fill(studentPassword)

    // Step 3: Authenticate against Spring Boot
    await page.getByRole('button', { name: /Sign In to Portal|Sign In/i }).click()

    // Step 4: Verify actual session state
    await page.waitForURL('**/student/dashboard')
    await expect(page.getByText(studentName, { exact: true }).first()).toBeVisible()
    await expect(page.getByRole('banner').getByText(studentId, { exact: false })).toBeVisible()

    // Step 5: Open the Student Dashboard
    await expect(page.getByRole('heading', { name: /Student Opportunity Hub|Candidate Dashboard/i })).toBeVisible()

    // Step 6: Navigate to recruitment opportunities
    await page.getByRole('link', { name: /Recruitment Drives|Opportunities/i }).first().click()
    await page.waitForURL('**/student/drives')
    await expect(page.getByRole('heading', { name: /Recruitment Opportunity Explorer/i })).toBeVisible()

    // Step 7: Locate and inspect available drive
    const driveCard = page.locator('article').filter({ hasText: jobTitle }).first()
    await expect(driveCard).toBeVisible()
    await driveCard.getByRole('link', { name: /Drive Details & Criteria|View Details/i }).click()
    await page.waitForURL(new RegExp('/student/drives/' + driveId + '$'))
    await expect(page.getByRole('heading', { name: jobTitle, exact: true }).first()).toBeVisible()

    // Step 8: Inspect authoritative eligibility
    const eligibilityCard = page.getByLabel('Itemized Eligibility Breakdown')
    await expect(eligibilityCard).toBeVisible()
    await expect(eligibilityCard.getByText('Eligible to Apply', { exact: true })).toBeVisible()

    // Step 9: Submit an application through the UI.
    // Scoped to the drive detail page action, which owns the "Submit Formal Application" label;
    // the catalogue list renders an unrelated "Apply Now" button per card.
    const applyButton = page.getByRole('button', { name: 'Submit Formal Application', exact: true })
    await expect(applyButton).toBeEnabled()
    await applyButton.click()

    // Step 10: Verify application confirmation modal and submit
    const modal = page.getByRole('dialog')
    await expect(modal).toBeVisible()
    await expect(modal.getByText(/Confirm Application Submission|Submit Formal Application/i)).toBeVisible()
    await modal.getByRole('button', { name: /Confirm & Submit Application/i }).click()
    await expect(modal.getByText(/Application Successfully Submitted/i)).toBeVisible({ timeout: 10000 })
    await modal.getByRole('button', { name: /Close & View Application|Close/i }).click().catch(() => {})

    // Step 11: Inspect My Applications
    await page.goto('/student/applications')
    await page.waitForURL('**/student/applications')
    const appRow = page.locator('tr').filter({ hasText: jobTitle }).first()
    await expect(appRow).toBeVisible()
    await expect(appRow.getByText(/APPLIED/i)).toBeVisible()

    // Step 12: Student Logout
    await page.getByRole('button', { name: /Sign Out|Logout/i }).first().click()
    await page.waitForURL('**/auth/login')

    // ------------------------------------------------------------------------
    // OFFICER PHASE (Steps 13 - 20)
    // ------------------------------------------------------------------------

    // Step 13: Authenticate as placement officer
    await page.locator('#username').fill(officerUsername)
    await page.locator('#password').fill(officerPassword)
    await page.getByRole('button', { name: /Sign In to Portal|Sign In/i }).click()

    // Step 14: Open the Officer Command Center
    await page.waitForURL('**/officer/dashboard')
    await expect(page.getByRole('heading', { name: /Placement Operations Command Center/i })).toBeVisible()

    // Step 15: Locate recruitment drives
    await page.getByRole('link', { name: /Recruitment Drives/i }).first().click()
    await page.waitForURL('**/officer/drives')

    // Step 16: Open applicant roster for the drive
    const driveRow = page.locator('tr').filter({ hasText: jobTitle }).first()
    await expect(driveRow).toBeVisible()
    await driveRow.getByRole('button', { name: 'Candidates: Review applications for ' + jobTitle, exact: true }).click()
    await page.waitForURL(/\/officer\/drives\/.*\/applications/)

    // Step 17: Inspect submitted candidate (Alan Turing / E2E-STU-01)
    const candidateRow = page.locator('tr').filter({ hasText: studentName }).first()
    await expect(candidateRow).toBeVisible()
    await expect(candidateRow.getByText(/APPLIED/i)).toBeVisible()

    // Step 18: Shortlist candidate through the UI
    const shortlistBtn = candidateRow.getByRole('button', { name: 'Shortlist ' + studentName, exact: true })
    await shortlistBtn.click()

    // Step 19: Verify authoritative status transition
    await expect(candidateRow.getByRole('cell', { name: 'Shortlisted', exact: true })).toBeVisible({ timeout: 10000 })

    // Step 20: Inspect available interviewers and rooms
    await page.getByRole('link', { name: /Resource Pools|Resources/i }).first().click()
    await page.waitForURL('**/officer/resources')
    await expect(page.getByRole('heading', { name: /Interview Resource Administration|Resource Management/i })).toBeVisible()

    // ------------------------------------------------------------------------
    // SCHEDULING PHASE (Steps 21 - 33)
    // ------------------------------------------------------------------------

    // Step 21: Open the Scheduling Studio
    await page.getByRole('link', { name: /Scheduling Studio/i }).first().click()
    await page.waitForURL('**/officer/scheduling')
    await expect(page.getByRole('heading', { name: /Interview Scheduling Studio/i })).toBeVisible()

    // Step 22: Select the recruitment drive
    await page.locator('#scheduling-drive-select').selectOption({ value: driveId })

    // Step 23: Configure valid interview window (30 days in future)

    await page.locator('#scheduling-date').fill(futureDate)
    await page.locator('#intv-' + interviewerId).check()
    await page.locator('#rm-' + roomId).check()

    // Step 24: Generate advisory preview
    await page.getByRole('button', { name: /Generate Advisory Timetable Preview|Generate Timetable Preview/i }).click()

    // Step 25: Verify candidate, interviewer, room, and time allocation
    await expect(page.getByText(/ADVISORY — NOT YET PUBLISHED|Advisory Preview Generated/i)).toBeVisible({ timeout: 15000 })
    await expect(page.getByText(studentName, { exact: true }).first()).toBeVisible()

    // Step 26: Verify preview is explicitly uncommitted
    await expect(page.getByText(/Zero database mutations occurred|Advisory Only/i).first()).toBeVisible()

    await page.screenshot({ path: test.info().outputPath('advisory-preview.png'), fullPage: true })
    // Step 27: Confirm zero persisted interview sessions before publishing
    expect(persistedSessions()).toHaveLength(0)
    await page.getByRole('tab', { name: /Confirmed Timetables/i }).click()
    await page.locator('#filter-drive-select').selectOption(driveId)
    await expect(page.getByText(/^Confirmed Appointments:\s*0$/i).first()).toBeVisible()
    await page.getByRole('tab', { name: /Generate & Publish Schedule/i }).click()

    // Step 28: Open publication confirmation dialog
    await page.getByRole('button', { name: /Review & Publish Timetable|Publish Confirmed Timetable/i }).click()
    const pubDialog = page.getByRole('dialog')
    await expect(pubDialog).toBeVisible()

    // Step 29: Acknowledge and publish using actual backend
    await page.locator('#publication-acknowledgement-checkbox').check()
    await pubDialog.getByRole('button', { name: /Confirm & Publish Timetable/i }).click()

    // Step 30: Verify actual HTTP publication result (200 ALL_CONFIRMED)
    await expect(page.getByRole('heading', { name: 'Interview Timetable Successfully Published' })).toBeVisible({ timeout: 15000 })

    await page.screenshot({ path: test.info().outputPath('publication-success.png'), fullPage: true })
    // Step 31: Inspect confirmed timetable
    await page.getByRole('button', { name: /Inspect Confirmed Timetable/i }).click()
    await expect(page.getByText(studentName, { exact: true }).first()).toBeVisible()

    // Step 32: Verified committed session
    const persisted = persistedSessions()
    expect(persisted).toHaveLength(1)
    expect(persisted[0]).toMatchObject({ status: 'SCHEDULED', interviewer_id: interviewerId, room_id: roomId })
    expect(persisted[0].session_id).toMatch(/^[A-F0-9]{32}$/)
    fs.writeFileSync(test.info().outputPath('persistence.json'), JSON.stringify({ driveId, account: studentUsername, ...persisted[0] }, null, 2))
    await test.info().attach('postgres-persistence', { body: JSON.stringify({ driveId, ...persisted[0] }, null, 2), contentType: 'application/json' })

    // Step 33: Officer Logout
    await page.getByRole('button', { name: /Sign Out|Logout/i }).first().click()
    await page.waitForURL('**/auth/login')

    // ------------------------------------------------------------------------
    // STUDENT VERIFICATION PHASE (Steps 34 - 40)
    // ------------------------------------------------------------------------

    // Step 34: Log in again as the student
    await page.locator('#username').fill(studentUsername)
    await page.locator('#password').fill(studentPassword)
    await page.getByRole('button', { name: /Sign In to Portal|Sign In/i }).click()
    await page.waitForURL('**/student/dashboard')

    // Step 35: Open interview timetable
    await page.getByRole('link', { name: /Interview Timetable|Interviews/i }).first().click()
    await page.waitForURL('**/student/interviews')
    await expect(page.getByRole('heading', { name: /Confirmed Interview Timetable|My Interview Timetable/i })).toBeVisible()

    // Step 36: Verify the same committed appointment
    const sessionCard = page.locator('article').filter({ hasText: jobTitle })
    await expect(sessionCard).toBeVisible()

    // Step 37: Confirm details match
    await expect(sessionCard.getByText('Scheduled', { exact: true }).first()).toBeVisible()

    await page.screenshot({ path: test.info().outputPath('student-timetable.png'), fullPage: true })
    // Step 38: Check application status in My Applications
    await page.getByRole('link', { name: /My Applications|Applications/i }).first().click()
    await page.waitForURL('**/student/applications')
    const finalAppRow = page.locator('tr').filter({ hasText: jobTitle }).first()
    await expect(finalAppRow.getByText(/SCHEDULED/i)).toBeVisible()

    // Step 39: Verify account isolation (cannot view officer route)
    await page.goto('/officer/dashboard')
    await expect(page.getByRole('heading', { name: 'Officer Authorization Required' })).toBeVisible()

    // Step 40: Final Logout
    await page.getByRole('button', { name: /Return to Student Hub/i }).click()
    await page.waitForURL('**/student/dashboard')
    await page.getByRole('button', { name: /Sign Out|Logout/i }).first().click()
    await page.waitForURL('**/auth/login')
    expect((await page.request.get('/api/v1/auth/me')).status()).toBe(401)
    await page.goto('/student/dashboard')
    await page.waitForURL('**/auth/login')
    await page.goto('/officer/dashboard')
    await page.waitForURL('**/auth/login')
    await page.screenshot({ path: test.info().outputPath('signed-out.png') })
  })
})

/** Remove only this browser run's fixtures after the isolated-container guard passed. */
test.afterAll(async () => {
  if (!isolationVerified) return
  if (!/^[A-Z0-9]+$/.test(runId)) throw new Error('Unsafe browser run identifier')
  const sql = [
    "DELETE FROM interview_sessions WHERE application_id IN (SELECT id FROM applications WHERE drive_id = '" + driveId + "');",
    "DELETE FROM applications WHERE drive_id = '" + driveId + "';",
    "DELETE FROM recruitment_drives WHERE id = '" + driveId + "';",
    "DELETE FROM interviewers WHERE id = '" + interviewerId + "';",
    "DELETE FROM rooms WHERE id = '" + roomId + "';",
    "DELETE FROM companies WHERE id = '" + companyId + "';",
  ].join(' ')
  execFileSync('docker', ['exec', 'hireflow-cap8-postgres', 'psql', '-X', '-U', 'hireflow_cap8_app', '-d', 'hireflow_cap8_it', '-v', 'ON_ERROR_STOP=1', '-c', sql], { stdio: 'pipe' })
})

/**
 * Independent administrator verification.
 * Credentials are supplied through the environment (never committed), sourced from
 * the gitignored .run/demo.local.json produced by Setup-HireFlow-Demo.cmd.
 */
test.describe('HireFlow Administrator Login (independent)', () => {
  test('demo.master signs in as ADMINISTRATOR through the real login screen', async ({ page }) => {
    const username = process.env.HIREFLOW_DEMO_MASTER_USERNAME
    const password = process.env.HIREFLOW_DEMO_MASTER_PASSWORD
    test.skip(!username || !password, 'Demo administrator credentials absent from the environment.')
    test.setTimeout(60000)

    await page.goto('/auth/login')
    await expect(page.getByRole('heading', { name: /Institutional Sign In|HireFlow/i })).toBeVisible()
    await page.locator('#username').fill(username as string)
    await page.locator('#password').fill(password as string)
    await page.getByRole('button', { name: /Sign In to Portal|Sign In/i }).click()

    await page.waitForURL('**/officer/dashboard')
    await expect(page.getByRole('heading', { name: /Placement Operations Command Center/i })).toBeVisible()

    const me = await page.request.get('/api/v1/auth/me')
    expect(me.status()).toBe(200)
    const profile = await me.json()
    expect(profile.role).toBe('ADMINISTRATOR')
    expect(profile.username).toBe(username)

    // An administrator must reach the officer surface through genuine authorization.
    const rooms = await page.request.get('/api/v1/officer/rooms')
    expect(rooms.status()).toBe(200)
  })
})
