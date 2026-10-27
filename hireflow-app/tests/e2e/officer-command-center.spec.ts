import { test, expect } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'
import * as path from 'path'
import * as fs from 'fs'

const ARTIFACT_SCREENSHOT_DIR = path.resolve(
  '../.run/evidence/fixture-screenshots'
)

if (!fs.existsSync(ARTIFACT_SCREENSHOT_DIR)) {
  fs.mkdirSync(ARTIFACT_SCREENSHOT_DIR, { recursive: true })
}

test.describe('HireFlow Placement Officer Command Center — Complete Experience Suite', () => {
  test.beforeEach(async ({ page }) => {
    // Explicit opt-in to controlled simulation harness for fixture-based visual & accessibility QA
    await page.addInitScript(() => {
      ;(window as any).__HIREFLOW_SIMULATION_MODE__ = true
    })
    // Navigate with default high-density desktop viewport (1440x900)
    await page.setViewportSize({ width: 1440, height: 900 })
  })

  // ============================================================================
  // 1. Placement Command Center Dashboard
  // ============================================================================
  test('1. Placement Command Center Overview across viewports', async ({ page }) => {
    await page.goto('/officer/dashboard')
    await page.waitForLoadState('networkidle')

    // Verify key institutional sections
    await expect(page.getByRole('heading', { name: /Placement Operations Command Center/i })).toBeVisible()
    await expect(page.getByRole('button', { name: /Active Recruitment Drives/i })).toBeVisible()
    await expect(page.getByRole('button', { name: /Corporate Partners/i })).toBeVisible()
    await expect(page.getByRole('button', { name: /Registered Interviewers/i })).toBeVisible()
    await expect(page.getByRole('button', { name: /Interview Rooms in Pool/i })).toBeVisible()
    await expect(page.getByText(/Candidate Shortlisting Workload/i)).toBeVisible()

    // 1440px Desktop Screenshot
    await page.screenshot({
      path: path.join(ARTIFACT_SCREENSHOT_DIR, 'officer_dashboard_1440_desktop.png'),
      fullPage: true,
    })

    // 1920px Wide Desktop
    await page.setViewportSize({ width: 1920, height: 1080 })
    await page.screenshot({
      path: path.join(ARTIFACT_SCREENSHOT_DIR, 'officer_dashboard_1920_wide.png'),
      fullPage: true,
    })

    // 768px Tablet Viewport
    await page.setViewportSize({ width: 768, height: 1024 })
    await page.screenshot({
      path: path.join(ARTIFACT_SCREENSHOT_DIR, 'officer_dashboard_768_tablet.png'),
      fullPage: true,
    })

    // 390px Mobile Viewport
    await page.setViewportSize({ width: 390, height: 844 })
    await page.screenshot({
      path: path.join(ARTIFACT_SCREENSHOT_DIR, 'officer_dashboard_390_mobile.png'),
      fullPage: true,
    })

    // Reset to desktop & run Axe WCAG 2.1 AA audit
    await page.setViewportSize({ width: 1440, height: 900 })
    const a11y = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa'])
      .disableRules(['color-contrast'])
      .analyze()
    expect(a11y.violations).toEqual([])
  })

  // ============================================================================
  // 2. Corporate Partner Management Directory & Forms
  // ============================================================================
  test('2. Corporate Partner Directory, Search & Registration Dialog', async ({ page }) => {
    await page.goto('/officer/companies')
    await page.waitForLoadState('networkidle')

    await expect(page.getByRole('heading', { name: /Corporate Partner Management/i })).toBeVisible()
    await expect(page.getByText('Google India')).toBeVisible()
    await expect(page.getByText('Microsoft IDC')).toBeVisible()

    // Search filter test
    const searchInput = page.getByPlaceholder(/Search by company name/i)
    await searchInput.fill('Google')
    await page.waitForTimeout(400) // debounce wait
    await expect(page.getByText('Google India')).toBeVisible()
    await expect(page.getByText('Microsoft IDC')).not.toBeVisible()
    await searchInput.clear()
    await page.waitForTimeout(400)

    // Open Register Corporate Partner Modal
    await page.getByRole('button', { name: /Register Corporate Partner/i }).click()
    await expect(page.getByRole('dialog')).toBeVisible()
    await expect(page.getByLabel(/Company Identifier/i)).toBeVisible()
    await expect(page.getByLabel(/Corporate Organization Name/i)).toBeVisible()

    await page.screenshot({
      path: path.join(ARTIFACT_SCREENSHOT_DIR, 'officer_company_creation_dialog.png'),
    })

    // Close modal
    await page.getByRole('button', { name: /Cancel/i }).click()
    await expect(page.getByRole('dialog')).not.toBeVisible()

    // 1024px Laptop Screenshot of Directory
    await page.setViewportSize({ width: 1024, height: 768 })
    await page.screenshot({
      path: path.join(ARTIFACT_SCREENSHOT_DIR, 'officer_companies_1024_laptop.png'),
      fullPage: true,
    })

    // Navigate to Company Dossier
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.getByRole('button', { name: /Dossier for Google India/i }).click()
    await expect(page.getByRole('heading', { name: 'Google India' })).toBeVisible()
    await expect(page.getByText(/Corporate Partner ID: COMP-001/i)).toBeVisible()

    await page.screenshot({
      path: path.join(ARTIFACT_SCREENSHOT_DIR, 'officer_company_detail_1440.png'),
      fullPage: true,
    })
  })

  // ============================================================================
  // 3. Recruitment Drive Directory, Detail & Closure
  // ============================================================================
  test('3. Recruitment Drive Management, Filters & Closure Confirmation', async ({ page }) => {
    await page.goto('/officer/drives')
    await page.waitForLoadState('networkidle')

    await expect(page.getByRole('heading', { name: /Campus Recruitment Drives/i })).toBeVisible()
    await expect(page.getByText('Software Development Engineer I')).toBeVisible()
    await expect(page.getByText('Cloud Solutions Architect Associate')).toBeVisible()

    // Test Active Only filter checkbox
    const activeCheckbox = page.getByLabel(/Active Campaigns Only/i)
    await activeCheckbox.check()
    await expect(page.getByText('CLOSED')).not.toBeVisible()
    await activeCheckbox.uncheck()

    // Open Launch Recruitment Drive Modal
    await page.getByRole('button', { name: /Launch Recruitment Drive/i }).click()
    await expect(page.getByRole('dialog')).toBeVisible()
    await expect(page.getByLabel(/Drive Identifier/i)).toBeVisible()
    await expect(page.getByLabel(/Job Position Title/i)).toBeVisible()
    await expect(page.getByText('Eligible Branches & Programs')).toBeVisible()

    await page.screenshot({
      path: path.join(ARTIFACT_SCREENSHOT_DIR, 'officer_drive_creation_dialog.png'),
    })

    await page.getByRole('button', { name: /Cancel/i }).click()
    await expect(page.getByRole('dialog')).not.toBeVisible()

    // Open Drive Detail view
    await page.getByRole('button', { name: /Inspect drive DRV-2026-001/i }).click()
    await expect(page.getByRole('heading', { name: 'Software Development Engineer I' })).toBeVisible()
    await expect(page.getByText(/Minimum CGPA/i)).toBeVisible()
    await expect(page.getByText(/Eligible Degree Branches/i)).toBeVisible()

    await page.screenshot({
      path: path.join(ARTIFACT_SCREENSHOT_DIR, 'officer_drive_detail_1440.png'),
      fullPage: true,
    })

    // Test Drive Closure Dialog
    await page.getByRole('button', { name: /Close Campaign/i }).click()
    await expect(page.getByRole('dialog')).toBeVisible()
    await expect(page.getByRole('heading', { name: /Close Recruitment Drive/i })).toBeVisible()
    await expect(page.getByText(/Authoritative Closure Consequences/i)).toBeVisible()

    await page.screenshot({
      path: path.join(ARTIFACT_SCREENSHOT_DIR, 'officer_drive_close_dialog.png'),
    })

    await page.getByRole('button', { name: /Cancel/i }).click()
  })

  // ============================================================================
  // 4. Candidate Review & Individual Shortlisting
  // ============================================================================
  test('4. Candidate Review Roster, Status Tabs & Shortlist Mutation', async ({ page }) => {
    await page.goto('/officer/drives/DRV-2026-001/applications')
    await page.waitForLoadState('networkidle')

    await expect(page.getByRole('heading', { name: /Candidate Application Review/i })).toBeVisible()
    await expect(page.getByText('Aarav Sharma')).toBeVisible()
    await expect(page.getByText('Priya Iyer')).toBeVisible()

    // 1440px Candidate Review Table Screenshot
    await page.screenshot({
      path: path.join(ARTIFACT_SCREENSHOT_DIR, 'officer_candidate_roster_1440.png'),
      fullPage: true,
    })

    // Test Status Filter Tabs
    await page.getByRole('button', { name: /Pending Review \(Applied\)/i }).click()
    await expect(page.getByText('Aarav Sharma')).toBeVisible()
    await page.getByRole('button', { name: 'All Candidates' }).click()

    // Inspect Candidate Dossier modal
    await page.getByRole('button', { name: /View dossier for Aarav Sharma/i }).click()
    const dialog = page.getByRole('dialog')
    await expect(dialog).toBeVisible()
    await expect(dialog.getByText('Application Dossier')).toBeVisible()
    await expect(dialog.getByRole('heading', { name: 'Aarav Sharma' })).toBeVisible()
    await expect(dialog.getByText('Honors Candidate')).toBeVisible()

    await page.screenshot({
      path: path.join(ARTIFACT_SCREENSHOT_DIR, 'officer_candidate_dossier_modal.png'),
    })

    // Click Shortlist Candidate inside modal
    await dialog.getByRole('button', { name: /Shortlist Candidate/i }).click()
    await expect(dialog.getByText(/Confirm Candidate Shortlisting/i)).toBeVisible()

    await page.screenshot({
      path: path.join(ARTIFACT_SCREENSHOT_DIR, 'officer_shortlist_confirmation_prompt.png'),
    })

    await dialog.getByRole('button', { name: /Confirm Shortlist/i }).click()
    await expect(page.getByRole('dialog')).not.toBeVisible()
    await expect(page.getByText(/Candidate shortlisted successfully/i)).toBeVisible()

    // 390px Mobile Viewport for Candidate Review
    await page.setViewportSize({ width: 390, height: 844 })
    await page.screenshot({
      path: path.join(ARTIFACT_SCREENSHOT_DIR, 'officer_candidate_roster_390_mobile.png'),
      fullPage: true,
    })
  })

  // ============================================================================
  // 5. Resource Administration (Interviewers & Rooms)
  // ============================================================================
  test('5. Interview Resource Administration — Interviewers and Rooms', async ({ page }) => {
    await page.goto('/officer/resources')
    await page.waitForLoadState('networkidle')

    await expect(page.getByRole('heading', { name: /Interview Resource Administration/i })).toBeVisible()
    await expect(page.getByText('Dr. Sarah Jenkins')).toBeVisible()
    await expect(page.getByText('David Chen')).toBeVisible()

    // 1440px Interviewers Tab
    await page.screenshot({
      path: path.join(ARTIFACT_SCREENSHOT_DIR, 'officer_interviewers_tab_1440.png'),
      fullPage: true,
    })

    // Open Register Interviewer Dialog
    await page.getByRole('button', { name: /Register Interviewer/i }).click()
    await expect(page.getByRole('dialog')).toBeVisible()
    await expect(page.getByLabel(/Interviewer Identifier/i)).toBeVisible()
    await page.getByRole('button', { name: /Cancel/i }).click()

    // Switch to Rooms Tab
    await page.getByRole('button', { name: /Campus Interview Rooms/i }).click()
    await expect(page.getByText('Interview Suite A (Oak Room)')).toBeVisible()
    await expect(page.getByText('Executive Boardroom 1')).toBeVisible()

    // 1440px Rooms Tab
    await page.screenshot({
      path: path.join(ARTIFACT_SCREENSHOT_DIR, 'officer_rooms_tab_1440.png'),
      fullPage: true,
    })

    // Open Register Interview Room Dialog
    await page.getByRole('button', { name: /Register Interview Room/i }).click()
    await expect(page.getByRole('dialog')).toBeVisible()
    await expect(page.getByLabel(/Room Location Name/i)).toBeVisible()
    await expect(page.getByLabel(/Room Seating Capacity/i)).toBeVisible()

    await page.screenshot({
      path: path.join(ARTIFACT_SCREENSHOT_DIR, 'officer_room_creation_dialog.png'),
    })

    await page.getByRole('button', { name: /Cancel/i }).click()

    // Axe a11y audit on Resources view
    const a11y = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa'])
      .disableRules(['color-contrast'])
      .analyze()
    expect(a11y.violations).toEqual([])
  })

  // ============================================================================
  // 6. Interview Scheduling Studio (CAP-8.4 Active Studio)
  // ============================================================================
  test('6. Interview Scheduling Studio Active Experience (CAP-8.4)', async ({ page }) => {
    await page.goto('/officer/scheduling')
    await page.waitForLoadState('networkidle')

    await expect(page.getByRole('heading', { name: /Interview Scheduling Studio/i })).toBeVisible()
    await expect(page.getByText('CAP-8.4 ACTIVE STUDIO')).toBeVisible()
    await expect(page.getByRole('tab', { name: /Generate & Publish Schedule/i })).toBeVisible()
    await expect(page.getByRole('tab', { name: /Confirmed Timetables & Sessions/i })).toBeVisible()

    await page.screenshot({
      path: path.join(ARTIFACT_SCREENSHOT_DIR, 'officer_scheduling_studio_1440.png'),
      fullPage: true,
    })
  })

  // ============================================================================
  // 7. Mobile Navigation Drawer & Touch Usability
  // ============================================================================
  test('7. Mobile officer hamburger drawer and touch targets', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await page.goto('/officer/dashboard')
    await page.waitForLoadState('networkidle')

    // Open hamburger drawer
    const menuBtn = page.getByRole('button', { name: /Open menu/i })
    await menuBtn.click()
    await expect(page.getByRole('link', { name: /Command Center/i })).toBeVisible()
    await expect(page.getByRole('link', { name: /Corporate Partners/i })).toBeVisible()
    await expect(page.getByRole('link', { name: /Recruitment Drives/i })).toBeVisible()
    await expect(page.getByRole('link', { name: /Resource Pools/i })).toBeVisible()

    await page.screenshot({
      path: path.join(ARTIFACT_SCREENSHOT_DIR, 'officer_mobile_drawer_390.png'),
    })
  })
})

