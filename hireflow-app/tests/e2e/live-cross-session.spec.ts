import { test, expect } from '@playwright/test'
import { execFileSync } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'

const credentials = JSON.parse(fs.readFileSync(path.resolve('../.run/demo.local.json'), 'utf8').replace(/^\uFEFF/, ''))
const baseURL = 'http://127.0.0.1:5174'

async function signIn(page: import('@playwright/test').Page, username: string, password: string, destination: string) {
  await page.goto(`${baseURL}/auth/login`)
  await page.locator('#username').fill(username)
  await page.locator('#password').fill(password)
  await page.getByRole('button', { name: 'Sign In to Portal' }).click()
  await page.waitForURL(`${baseURL}${destination}`)
}

test('student and officer sessions receive live cross-role changes without reload', async ({ browser }) => {
  test.setTimeout(150_000)
  const guard = path.resolve('../infra/cap8-integration/preflight-guard.ps1')
  execFileSync('powershell.exe', ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', guard], {
    env: { ...process.env, HIREFLOW_IT_APP_PASSWORD: credentials.databasePassword },
    stdio: 'pipe',
  })

  const id = Date.now().toString(36).toUpperCase()
  if (!/^[A-Z0-9]+$/.test(id)) throw new Error('Unsafe cross-session run identifier')
  const companyId = `CMP-X-${id}`
  const driveId = `DRV-X-${id}`
  const studentContext = await browser.newContext({ baseURL })
  const officerContext = await browser.newContext({ baseURL })
  try {
    const student = await studentContext.newPage()
    const officer = await officerContext.newPage()
    await signIn(student, credentials.demoStudentUsername, credentials.demoStudentPassword, '/student/dashboard')
    await signIn(officer, credentials.demoOfficerUsername, credentials.demoOfficerPassword, '/officer/dashboard')

    const jobTitle = `Cross-session engineer ${id}`
    const csrf = await (await officerContext.request.get('/api/v1/auth/csrf')).json()
    const headers = { 'X-CSRF-TOKEN': csrf.token }
    expect((await officerContext.request.post('/api/v1/officer/companies', {
      headers, data: { id: companyId, name: `Fictional Cross-session Partner ${id}`, industry: 'Integration tests' },
    })).status()).toBe(201)

    await student.goto(`${baseURL}/student/drives`)
    await student.getByRole('searchbox', { name: 'Search recruitment opportunities' }).fill(jobTitle)
    await expect(student.locator('article').filter({ hasText: jobTitle })).toHaveCount(0)

    const deadline = new Date(Date.now() + 45 * 86400000).toISOString().slice(0, 10)
    expect((await officerContext.request.post('/api/v1/officer/drives', {
      headers, data: { id: driveId, companyId, jobTitle, minimumCgpa: 7, eligibleBranches: ['CSE'], applicationDeadline: deadline },
    })).status()).toBe(201)

    // The student sees a genuine officer write while remaining signed in on another browser session.
    await expect(student.locator('article').filter({ hasText: jobTitle })).toBeVisible({ timeout: 45_000 })
    await officer.goto(`${baseURL}/officer/drives/${driveId}/applications`)
    await expect(officer.getByRole('heading', { name: /Candidate/i }).first()).toBeVisible()

    await student.goto(`${baseURL}/student/drives/${driveId}`)
    await student.getByRole('button', { name: 'Submit Formal Application', exact: true }).click()
    await student.getByRole('dialog').getByRole('button', { name: /Confirm & Submit Application/i }).click()
    await expect(student.getByRole('dialog').getByText(/Application Successfully Submitted/i)).toBeVisible()

    // Officer roster receives the student's real submission without navigation or reload.
    const candidateRow = officer.locator('tr').filter({ hasText: 'Aarav Sharma' }).first()
    await expect(candidateRow).toBeVisible({ timeout: 45_000 })
    await student.goto(`${baseURL}/student/applications`)
    const applicationRow = student.locator('tr').filter({ hasText: jobTitle }).first()
    await expect(applicationRow).toBeVisible()
    await expect(applicationRow.getByText(/APPLIED/i)).toBeVisible()

    await candidateRow.getByRole('button', { name: 'Shortlist Aarav Sharma', exact: true }).click()
    await expect(candidateRow.getByRole('cell', { name: 'Shortlisted', exact: true })).toBeVisible()
    // The student's open application list receives the officer's genuine status transition.
    await expect(applicationRow.getByText(/SHORTLISTED/i)).toBeVisible({ timeout: 45_000 })
    const persisted = execFileSync('docker', ['exec', 'hireflow-cap8-postgres', 'psql', '-X', '-U', 'hireflow_cap8_app', '-d', 'hireflow_cap8_it', '-Atc', "SELECT status FROM applications WHERE drive_id='" + driveId + "'"], { encoding: 'utf8' }).trim()
    expect(persisted).toBe('SHORTLISTED')
  } finally {
    await Promise.allSettled([studentContext.close(), officerContext.close()])
    const sql = [
      "DELETE FROM applications WHERE drive_id = '" + driveId + "';",
      "DELETE FROM recruitment_drives WHERE id = '" + driveId + "';",
      "DELETE FROM companies WHERE id = '" + companyId + "';",
    ].join(' ')
    execFileSync('docker', ['exec', 'hireflow-cap8-postgres', 'psql', '-X', '-U', 'hireflow_cap8_app', '-d', 'hireflow_cap8_it', '-v', 'ON_ERROR_STOP=1', '-c', sql], { stdio: 'pipe' })
  }
})


