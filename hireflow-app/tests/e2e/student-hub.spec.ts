import { test, expect } from '@playwright/test'
import path from 'path'
import fs from 'fs'

const ARTIFACT_DIR = '../.run/evidence/fixture-artifacts'
const ARTIFACT_SCREENSHOT_DIR = path.join(ARTIFACT_DIR, 'screenshots')
const LOCAL_SCREENSHOT_DIR = path.resolve('../.run/evidence/fixture-local')

if (!fs.existsSync(LOCAL_SCREENSHOT_DIR)) {
  fs.mkdirSync(LOCAL_SCREENSHOT_DIR, { recursive: true })
}
if (!fs.existsSync(ARTIFACT_SCREENSHOT_DIR)) {
  fs.mkdirSync(ARTIFACT_SCREENSHOT_DIR, { recursive: true })
}

const saveScreenshot = async (page: any, filename: string) => {
  const localPath = path.join(LOCAL_SCREENSHOT_DIR, filename)
  const artifactSubPath = path.join(ARTIFACT_SCREENSHOT_DIR, filename)
  const artifactRootPath = path.join(ARTIFACT_DIR, filename)
  await page.screenshot({ path: localPath, fullPage: true })
  fs.copyFileSync(localPath, artifactSubPath)
  fs.copyFileSync(localPath, artifactRootPath)
}

test.describe('HireFlow Student Opportunity Hub Full Experience Suite', () => {
  test.beforeEach(async ({ page }) => {
    // Explicit opt-in to controlled simulation harness for fixture-based visual QA
    await page.addInitScript(() => {
      ;(window as any).__HIREFLOW_SIMULATION_MODE__ = true
    })
  })

  test('1. Student Dashboard desktop and mobile responsiveness', async ({ page }) => {
    // 1440px desktop
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/student/dashboard')
    await page.waitForLoadState('networkidle')

    await expect(page.getByRole('heading', { name: /Student Opportunity Hub/i })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Aarav Sharma' })).toBeVisible()
    await expect(page.getByText('STU-2022-CS0144').first()).toBeVisible()
    await expect(page.getByText('Eligible Drives').first()).toBeVisible()
    await expect(page.getByText('Confirmed Interview Session')).toBeVisible()
    await saveScreenshot(page, 'student_dashboard_desktop.png')

    // 390px mobile
    await page.setViewportSize({ width: 390, height: 844 })
    await page.waitForTimeout(300)
    await saveScreenshot(page, 'student_dashboard_mobile.png')
  })

  test('2. Recruitment Opportunity Explorer desktop and mobile with search and filters', async ({ page }) => {
    // 1440px desktop
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/student/drives')
    await page.waitForLoadState('networkidle')

    await expect(page.getByRole('heading', { name: /Recruitment Opportunity Explorer/i })).toBeVisible()
    await expect(page.getByText('Stratum Quant Labs')).toBeVisible()
    await expect(page.getByText('Eligible to Apply (2)')).toBeVisible()
    await saveScreenshot(page, 'student_opportunities_desktop.png')

    // 390px mobile
    await page.setViewportSize({ width: 390, height: 844 })
    await page.waitForTimeout(300)
    await saveScreenshot(page, 'student_opportunities_mobile.png')

    // Empty state testing: Type search query that has zero matches
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.getByPlaceholder(/Search opportunities/i).fill('NonexistentRecruiterXYZ')
    await expect(page.getByText('No Recruitment Opportunities Found')).toBeVisible()
    await saveScreenshot(page, 'student_empty_state_opportunities.png')
  })

  test('3. Recruitment Drive Details and Authoritative Eligibility Breakdown', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/student/drives/DRV-2026-002')
    await page.waitForLoadState('networkidle')

    await expect(page.getByRole('heading', { name: /Quantitative Systems Analyst/i })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Stratum Quant Labs' })).toBeVisible()
    await saveScreenshot(page, 'student_drive_details_desktop.png')

    // Scrolled to Eligibility Breakdown Card
    const breakdown = page.getByLabel('Itemized Eligibility Breakdown')
    await breakdown.scrollIntoViewIfNeeded()
    await expect(page.getByText('Institutional Eligibility Evaluation')).toBeVisible()
    await expect(page.getByText('Academic Cutoff')).toBeVisible()
    await expect(page.getByText('Department Cutoff')).toBeVisible()
    await saveScreenshot(page, 'student_eligibility_breakdown.png')
  })

  test('4. Application Confirmation, Submission Lifecycle and Conflict Recovery', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/student/drives/DRV-2026-002')
    await page.waitForLoadState('networkidle')

    // Click "Submit Formal Application"
    await page.getByRole('button', { name: /Submit Formal Application/i }).click()

    // Confirmation Modal visible
    await expect(page.getByText('Confirm Application Submission')).toBeVisible()
    await expect(page.getByText(/Institutional Placement Regulation/i)).toBeVisible()
    await expect(page.getByText(/Applications submitted to corporate partners are binding/i)).toBeVisible()
    await saveScreenshot(page, 'student_application_confirmation_modal.png')

    // Confirm submission
    await page.getByRole('button', { name: /Confirm & Submit Application/i }).click()

    // Submission success confirmation visible
    await expect(page.getByText('Application Successfully Submitted')).toBeVisible()
    await expect(page.getByText(/Application Reference:/i)).toBeVisible()
    await saveScreenshot(page, 'student_submission_success_state.png')

    // Close modal
    await page.getByRole('button', { name: /Close & Return to Dashboard/i }).click()
  })

  test('5. Ineligible drive inspection and disqualification explanation', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/student/drives/DRV-2026-005')
    await page.waitForLoadState('networkidle')

    await expect(page.getByRole('heading', { name: /Enterprise Technology Consultant/i })).toBeVisible()
    await expect(page.getByText('Application Blocked — Disqualification Explanations')).toBeVisible()
    await expect(page.getByText(/Candidate cumulative CGPA 9.15 does not satisfy minimum required CGPA of 9.50/i)).toBeVisible()
    await expect(page.getByRole('button', { name: /Application Ineligible/i })).toBeDisabled()
    await saveScreenshot(page, 'student_ineligible_drive_explanation.png')
  })

  test('6. My Applications directory and individual application progression', async ({ page }) => {
    // 1440px desktop
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/student/applications')
    await page.waitForLoadState('networkidle')

    await expect(page.getByRole('heading', { name: /My Applications/i })).toBeVisible()
    await expect(page.locator('text=Apex Innovations').first()).toBeVisible()
    await expect(page.locator('text=Crestline Systems').first()).toBeVisible()
    await saveScreenshot(page, 'student_my_applications_desktop.png')

    // 390px mobile
    await page.setViewportSize({ width: 390, height: 844 })
    await page.waitForTimeout(300)
    await saveScreenshot(page, 'student_my_applications_mobile.png')

    // Application Detail page
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/student/applications/APP-2026-001')
    await page.waitForLoadState('networkidle')

    await expect(page.getByRole('heading', { name: /Application APP-2026-001/i })).toBeVisible()
    await expect(page.getByText('Application Lifecycle & Review Status')).toBeVisible()
    await expect(page.getByText('Verified Audit Milestones')).toBeVisible()
    await saveScreenshot(page, 'student_application_detail_progress.png')
  })

  test('7. Confirmed Interview Timetable desktop, mobile, and session briefing', async ({ page }) => {
    // 1440px desktop
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/student/interviews')
    await page.waitForLoadState('networkidle')

    await expect(page.getByRole('heading', { name: /Confirmed Interview Timetable/i })).toBeVisible()
    await expect(page.getByText('Crestline Systems')).toBeVisible()
    await expect(page.getByText('Placement Suite 302')).toBeVisible()
    await expect(page.getByText('Dr. Michael Chen')).toBeVisible()
    await saveScreenshot(page, 'student_interview_timetable_desktop.png')

    // 390px mobile
    await page.setViewportSize({ width: 390, height: 844 })
    await page.waitForTimeout(300)
    await saveScreenshot(page, 'student_interview_timetable_mobile.png')

    // Interview Detail / Session Briefing
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/student/interviews/INT-2026-801')
    await page.waitForLoadState('networkidle')

    await expect(page.getByRole('heading', { name: /Interview Session: INT-2026-801/i })).toBeVisible()
    await expect(page.getByText('Dr. Michael Chen')).toBeVisible()
    await expect(page.getByText('Placement Suite 302')).toBeVisible()
    await saveScreenshot(page, 'student_interview_detail_briefing.png')
  })
})
