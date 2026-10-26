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

test.describe('HireFlow Placement Officer Interview Scheduling Studio — CAP-8.4 Flagship E2E & Visual QA Suite', () => {
  test.beforeEach(async ({ page }) => {
    // Explicit opt-in to controlled simulation harness for fixture-based visual & accessibility QA
    await page.addInitScript(() => {
      ;(window as any).__HIREFLOW_SIMULATION_MODE__ = true
    })
    // Standard desktop viewport (1440x900)
    await page.setViewportSize({ width: 1440, height: 900 })
  })

  // ============================================================================
  // Test 1: Configuration Form across Viewports (390px, 768px, 1024px, 1440px, 1920px)
  // ============================================================================
  test('1. Scheduling Configuration across all 5 responsive viewports', async ({ page }) => {
    await page.goto('/officer/scheduling')
    await page.waitForLoadState('networkidle')

    // Verify institutional page heading and breadcrumbs
    await expect(page.getByRole('heading', { name: /Interview Scheduling Studio/i })).toBeVisible()
    await expect(page.getByText('CAP-8.4 ACTIVE STUDIO')).toBeVisible()

    // Verify 3-column configuration sections
    await expect(page.getByText('1. Recruitment Drive Context')).toBeVisible()
    await expect(page.getByText('2. Slot Durations & Intervals')).toBeVisible()
    await expect(page.getByText('3. Room Resource Allocation')).toBeVisible()

    // 1440px Desktop Screenshot
    await page.screenshot({
      path: path.join(ARTIFACT_SCREENSHOT_DIR, '01_scheduling_config_desktop_1440.png'),
      fullPage: true,
    })

    // 1920px Full HD Wide Desktop
    await page.setViewportSize({ width: 1920, height: 1080 })
    await page.screenshot({
      path: path.join(ARTIFACT_SCREENSHOT_DIR, '02_scheduling_config_wide_1920.png'),
      fullPage: true,
    })

    // 1024px Small Desktop / iPad Pro
    await page.setViewportSize({ width: 1024, height: 768 })
    await page.screenshot({
      path: path.join(ARTIFACT_SCREENSHOT_DIR, '03_scheduling_config_laptop_1024.png'),
      fullPage: true,
    })

    // 768px Tablet Viewport
    await page.setViewportSize({ width: 768, height: 1024 })
    await page.screenshot({
      path: path.join(ARTIFACT_SCREENSHOT_DIR, '04_scheduling_config_tablet_768.png'),
      fullPage: true,
    })

    // 390px Mobile Viewport
    await page.setViewportSize({ width: 390, height: 844 })
    await page.screenshot({
      path: path.join(ARTIFACT_SCREENSHOT_DIR, '05_scheduling_config_mobile_390.png'),
      fullPage: true,
    })

    // Reset to 1440px and perform Axe WCAG 2.1 AA audit
    await page.setViewportSize({ width: 1440, height: 900 })
    const a11y = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa'])
      .disableRules(['color-contrast'])
      .analyze()
    expect(a11y.violations).toEqual([])
  })

  // ============================================================================
  // Test 2: Advisory Preview Generation, Time Grid, Tabular View, Unscheduled Candidates
  // ============================================================================
  test('2. Advisory Timetable Preview (Visual Time Grid & Accessible Table)', async ({ page }) => {
    await page.goto('/officer/scheduling')
    await page.waitForLoadState('networkidle')

    // Enable Dev Simulation Mode for deterministic in-browser greedy allocation
    const simCheckbox = page.locator('#dev-simulation-toggle')
    if (await simCheckbox.isVisible()) {
      await simCheckbox.check()
    }

    // Set constrained window (09:00 to 09:45) to demonstrate both scheduled appointments and unscheduled candidates
    await page.locator('#scheduling-end-time').fill('09:45')

    // Submit configuration form to generate advisory preview
    const generateBtn = page.getByRole('button', { name: /Generate Advisory Timetable Preview/i })
    await expect(generateBtn).toBeVisible()
    await generateBtn.click()

    // Verify Advisory banner
    await expect(page.getByText(/ADVISORY — NOT YET PUBLISHED/i)).toBeVisible()
    await expect(page.getByText('VOLATILE IN-MEMORY PROPOSAL')).toBeVisible()
    await expect(page.getByText(/Token Ref:/i)).toBeVisible()

    // Verify TTL countdown is running
    await expect(page.getByText(/TTL:/i)).toBeVisible()

    // Verify Visual Time-Grid View (default)
    await expect(page.getByText('Advisory Appointment')).toBeVisible()
    await expect(page.getByText(/room lane\(s\)/i)).toBeVisible()
    await page.screenshot({
      path: path.join(ARTIFACT_SCREENSHOT_DIR, '06_advisory_preview_timegrid_desktop.png'),
      fullPage: true,
    })

    // Switch to Accessible Tabular View
    const tableBtn = page.getByRole('button', { name: /Accessible Tabular View/i })
    await tableBtn.click()

    // Verify Table view rendered
    await expect(page.getByPlaceholder(/Search candidate/i)).toBeVisible()
    await page.screenshot({
      path: path.join(ARTIFACT_SCREENSHOT_DIR, '07_advisory_preview_tabular_desktop.png'),
      fullPage: true,
    })

    // Inspect Unscheduled Candidates Section
    await expect(page.getByText(/Unscheduled Shortlisted Candidates/i)).toBeVisible()
    await page.screenshot({
      path: path.join(ARTIFACT_SCREENSHOT_DIR, '08_unscheduled_candidates_section.png'),
      fullPage: false,
    })

    // Run Axe Accessibility check on Advisory Preview
    const a11y = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa'])
      .disableRules(['color-contrast'])
      .analyze()
    expect(a11y.violations).toEqual([])
  })

  // ============================================================================
  // Test 3: Publication Confirmation Dialog & Consequential Review
  // ============================================================================
  test('3. Publication Confirmation Dialog with Non-Atomic Warning', async ({ page }) => {
    await page.goto('/officer/scheduling')
    await page.waitForLoadState('networkidle')

    // Enable Dev Simulation Mode
    const simCheckbox = page.locator('#dev-simulation-toggle')
    if (await simCheckbox.isVisible()) {
      await simCheckbox.check()
    }

    // Generate Preview
    await page.getByRole('button', { name: /Generate Advisory Timetable Preview/i }).click()
    await expect(page.getByText(/ADVISORY — NOT YET PUBLISHED/i)).toBeVisible()

    // Click Review & Publish Timetable
    const reviewBtn = page.getByRole('button', { name: /Review & Publish Timetable/i })
    await reviewBtn.click()

    // Verify modal elements
    await expect(page.getByRole('heading', { name: 'Confirm Timetable Publication' })).toBeVisible()
    await expect(page.getByText(/Non-Atomic Per-Session Persistence/i)).toBeVisible()
    await expect(page.getByText(/Single-Use Token Consumption/i)).toBeVisible()

    // Publish button must be disabled prior to acknowledgement
    const publishSubmitBtn = page.getByRole('button', { name: /Confirm & Publish Timetable/i })
    await expect(publishSubmitBtn).toBeDisabled()

    // Capture screenshot of Confirmation Dialog
    await page.screenshot({
      path: path.join(ARTIFACT_SCREENSHOT_DIR, '09_publication_confirmation_dialog.png'),
    })

    // Check acknowledgement checkbox
    const ackCheckbox = page.locator('#publication-acknowledgement-checkbox')
    await ackCheckbox.click()
    await expect(publishSubmitBtn).toBeEnabled()
  })

  // ============================================================================
  // Test 4: Outcome ALL_CONFIRMED (HTTP 200)
  // ============================================================================
  test('4. Publication Outcome: ALL_CONFIRMED (HTTP 200 Full Persistence)', async ({ page }) => {
    await page.goto('/officer/scheduling')
    await page.waitForLoadState('networkidle')

    const simCheckbox = page.locator('#dev-simulation-toggle')
    if (await simCheckbox.isVisible()) {
      await simCheckbox.check()
    }

    // Select ALL_CONFIRMED scenario
    await page.getByRole('button', { name: /HTTP 200 \(Full Success\)/i }).click()

    // Generate & Publish
    await page.getByRole('button', { name: /Generate Advisory Timetable Preview/i }).click()
    await expect(page.getByText(/ADVISORY — NOT YET PUBLISHED/i)).toBeVisible()

    await page.getByRole('button', { name: /Review & Publish Timetable/i }).click()
    await page.locator('#publication-acknowledgement-checkbox').click()
    await page.getByRole('button', { name: /Confirm & Publish Timetable/i }).click()

    // Verify ALL_CONFIRMED view
    await expect(page.getByRole('heading', { name: /Interview Timetable Successfully Published/i })).toBeVisible()
    await expect(page.getByText('HTTP 200 OK')).toBeVisible()
    await expect(page.getByText('Newly Confirmed')).toBeVisible()

    await page.screenshot({
      path: path.join(ARTIFACT_SCREENSHOT_DIR, '10_publication_outcome_all_confirmed_200.png'),
      fullPage: true,
    })
  })

  // ============================================================================
  // Test 5: Outcome PARTIAL_SUCCESS (HTTP 207 Multi-Status)
  // ============================================================================
  test('5. Publication Outcome: PARTIAL_SUCCESS (HTTP 207 Multi-Status with Recovery Instructions)', async ({ page }) => {
    await page.goto('/officer/scheduling')
    await page.waitForLoadState('networkidle')

    const simCheckbox = page.locator('#dev-simulation-toggle')
    if (await simCheckbox.isVisible()) {
      await simCheckbox.check()
    }

    // Select PARTIAL_SUCCESS scenario
    await page.getByRole('button', { name: /HTTP 207 \(Partial Conflict\)/i }).click()

    // Generate & Publish
    await page.getByRole('button', { name: /Generate Advisory Timetable Preview/i }).click()
    await expect(page.getByText(/ADVISORY — NOT YET PUBLISHED/i)).toBeVisible()

    await page.getByRole('button', { name: /Review & Publish Timetable/i }).click()
    await page.locator('#publication-acknowledgement-checkbox').click()
    await page.getByRole('button', { name: /Confirm & Publish Timetable/i }).click()

    // Verify PARTIAL_SUCCESS view
    await expect(page.getByRole('heading', { name: /Partial Publication Committed — Multi-Status/i })).toBeVisible()
    await expect(page.getByText('HTTP 207 MULTI_STATUS')).toBeVisible()
    await expect(page.getByText(/Backend Reconciliation Guidance:/i)).toBeVisible()
    await expect(page.getByText(/Concurrent Conflicts Encountered/i)).toBeVisible()

    await page.screenshot({
      path: path.join(ARTIFACT_SCREENSHOT_DIR, '11_publication_outcome_partial_success_207.png'),
      fullPage: true,
    })
  })

  // ============================================================================
  // Test 6: Outcome NONE_CONFIRMED (HTTP 409 Conflict)
  // ============================================================================
  test('6. Publication Outcome: NONE_CONFIRMED (HTTP 409 Zero Persistent Sessions)', async ({ page }) => {
    await page.goto('/officer/scheduling')
    await page.waitForLoadState('networkidle')

    const simCheckbox = page.locator('#dev-simulation-toggle')
    if (await simCheckbox.isVisible()) {
      await simCheckbox.check()
    }

    // Select NONE_CONFIRMED scenario
    await page.getByRole('button', { name: /HTTP 409 \(All Conflicted\)/i }).click()

    // Generate & Publish
    await page.getByRole('button', { name: /Generate Advisory Timetable Preview/i }).click()
    await expect(page.getByText(/ADVISORY — NOT YET PUBLISHED/i)).toBeVisible()

    await page.getByRole('button', { name: /Review & Publish Timetable/i }).click()
    await page.locator('#publication-acknowledgement-checkbox').click()
    await page.getByRole('button', { name: /Confirm & Publish Timetable/i }).click()

    // Verify NONE_CONFIRMED view
    await expect(page.getByRole('heading', { name: /Timetable Publication Blocked — Zero Appointments Confirmed/i })).toBeVisible()
    await expect(page.getByText('HTTP 409 CONFLICT')).toBeVisible()
    await expect(page.getByText(/Adjust Parameters & Generate Fresh Preview/i)).toBeVisible()

    await page.screenshot({
      path: path.join(ARTIFACT_SCREENSHOT_DIR, '12_publication_outcome_none_confirmed_409.png'),
      fullPage: true,
    })
  })

  // ============================================================================
  // Test 7: Outcome EXPIRED (HTTP 410) & CONSUMED_REPLAY (HTTP 409)
  // ============================================================================
  test('7. Token Hygiene: Expired Preview (HTTP 410) & Replay Prevention (HTTP 409)', async ({ page }) => {
    await page.goto('/officer/scheduling')
    await page.waitForLoadState('networkidle')

    const simCheckbox = page.locator('#dev-simulation-toggle')
    if (await simCheckbox.isVisible()) {
      await simCheckbox.check()
    }

    // A. EXPIRED Token (HTTP 410)
    await page.getByRole('button', { name: /HTTP 410 \(Token Expired\)/i }).click()
    await page.getByRole('button', { name: /Generate Advisory Timetable Preview/i }).click()
    await page.getByRole('button', { name: /Review & Publish Timetable/i }).click()
    await page.locator('#publication-acknowledgement-checkbox').click()
    await page.getByRole('button', { name: /Confirm & Publish Timetable/i }).click()

    await expect(page.getByRole('heading', { name: /Scheduling Preview Token Expired/i })).toBeVisible()
    await expect(page.getByText('HTTP 410 GONE')).toBeVisible()

    await page.screenshot({
      path: path.join(ARTIFACT_SCREENSHOT_DIR, '13_publication_outcome_expired_410.png'),
      fullPage: true,
    })

    // Reset for B. CONSUMED_REPLAY (HTTP 409)
    await page.getByRole('button', { name: /Generate Fresh Preview/i }).click()
    await page.getByRole('button', { name: /HTTP 409 \(Token Replay\)/i }).click()
    await page.getByRole('button', { name: /Generate Advisory Timetable Preview/i }).click()
    await page.getByRole('button', { name: /Review & Publish Timetable/i }).click()
    await page.locator('#publication-acknowledgement-checkbox').click()
    await page.getByRole('button', { name: /Confirm & Publish Timetable/i }).click()

    await expect(page.getByRole('heading', { name: /Preview Token Already Consumed/i })).toBeVisible()
    await expect(page.getByText('HTTP 409 REPLAY')).toBeVisible()

    await page.screenshot({
      path: path.join(ARTIFACT_SCREENSHOT_DIR, '14_publication_outcome_consumed_replay_409.png'),
      fullPage: true,
    })
  })

  // ============================================================================
  // Test 8: Outcome SERVER_ERROR_UNCERTAIN (HTTP 5xx Outage)
  // ============================================================================
  test('8. Infrastructure Failure: Server Error Uncertain Outcome (HTTP 500)', async ({ page }) => {
    await page.goto('/officer/scheduling')
    await page.waitForLoadState('networkidle')

    const simCheckbox = page.locator('#dev-simulation-toggle')
    if (await simCheckbox.isVisible()) {
      await simCheckbox.check()
    }

    // Select DATABASE_ERROR
    await page.getByRole('button', { name: /HTTP 500 \(PostgreSQL Outage\)/i }).click()
    await page.getByRole('button', { name: /Generate Advisory Timetable Preview/i }).click()
    await page.getByRole('button', { name: /Review & Publish Timetable/i }).click()
    await page.locator('#publication-acknowledgement-checkbox').click()
    await page.getByRole('button', { name: /Confirm & Publish Timetable/i }).click()

    // Verify Uncertain Outcome State
    await expect(page.getByRole('heading', { name: /Publication State Uncertain/i })).toBeVisible()
    await expect(page.getByText('HTTP 5xx OUTAGE')).toBeVisible()
    await expect(page.getByText(/DO NOT assume zero sessions were committed/i)).toBeVisible()
    await expect(page.getByText(/Inspect Confirmed Timetable for Reconciliation/i)).toBeVisible()

    await page.screenshot({
      path: path.join(ARTIFACT_SCREENSHOT_DIR, '15_publication_outcome_server_error_uncertain_500.png'),
      fullPage: true,
    })
  })

  // ============================================================================
  // Test 9: Confirmed Timetables Tab & Session Detail Modal
  // ============================================================================
  test('9. Confirmed Timetable View across Desktop & Mobile with Session Briefing', async ({ page }) => {
    await page.goto('/officer/scheduling')
    await page.waitForLoadState('networkidle')

    // Enable simulation so simulated confirmed sessions are returned
    const simCheckbox = page.locator('#dev-simulation-toggle')
    if (await simCheckbox.isVisible()) {
      await simCheckbox.check()
    }

    // Switch to Confirmed tab
    await page.getByRole('tab', { name: /Confirmed Timetables & Sessions/i }).click()

    // Verify Confirmed Timetable Header & Filter Toolbar
    await expect(page.getByRole('heading', { name: /Filter Confirmed Timetable/i })).toBeVisible()
    await expect(page.getByText(/Confirmed Appointments:/i)).toBeVisible()

    // 1440px Desktop Screenshot
    await page.screenshot({
      path: path.join(ARTIFACT_SCREENSHOT_DIR, '16_confirmed_timetable_desktop_1440.png'),
      fullPage: true,
    })

    // 390px Mobile Viewport with stacked appointment cards
    await page.setViewportSize({ width: 390, height: 844 })
    await page.screenshot({
      path: path.join(ARTIFACT_SCREENSHOT_DIR, '17_confirmed_timetable_mobile_390.png'),
      fullPage: true,
    })

    // Return to desktop and open a session detail dialog
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.waitForTimeout(300)

    const briefingBtn = page.getByRole('button', { name: /Briefing/i }).first()
    if (await briefingBtn.isVisible()) {
      await briefingBtn.click()
      await expect(page.getByRole('heading', { name: 'Confirmed Interview Session' })).toBeVisible()
      await expect(page.getByText(/Confirmed Campus Wall-Clock Time:/i)).toBeVisible()

      await page.screenshot({
        path: path.join(ARTIFACT_SCREENSHOT_DIR, '18_confirmed_session_detail_dialog.png'),
      })
    }
  })
})
