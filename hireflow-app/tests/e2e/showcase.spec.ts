import { test, expect } from '@playwright/test'
import path from 'path'
import fs from 'fs'

const ARTIFACT_DIR = '../.run/evidence/fixture-screenshots'
const LOCAL_SCREENSHOT_DIR = path.resolve('../.run/evidence/fixture-local')

// Ensure screenshot directories exist
if (!fs.existsSync(LOCAL_SCREENSHOT_DIR)) {
  fs.mkdirSync(LOCAL_SCREENSHOT_DIR, { recursive: true })
}
if (!fs.existsSync(ARTIFACT_DIR)) {
  fs.mkdirSync(ARTIFACT_DIR, { recursive: true })
}

const saveScreenshot = async (page: any, filename: string) => {
  const localPath = path.join(LOCAL_SCREENSHOT_DIR, filename)
  const artifactPath = path.join(ARTIFACT_DIR, filename)
  await page.screenshot({ path: localPath, fullPage: true })
  fs.copyFileSync(localPath, artifactPath)
}

test.describe('HireFlow Institutional UI & Showcase Suite', () => {
  test.beforeEach(async ({ page }) => {
    // Explicit opt-in to controlled simulation harness for fixture-based visual & accessibility QA
    await page.addInitScript(() => {
      ;(window as any).__HIREFLOW_SIMULATION_MODE__ = true
    })
  })

  test('Workshop responsive audit across 5 mandatory viewports', async ({ page }) => {
    const viewports = [
      { name: '390px_mobile', width: 390, height: 844 },
      { name: '768px_tablet', width: 768, height: 1024 },
      { name: '1024px_laptop', width: 1024, height: 768 },
      { name: '1440px_desktop', width: 1440, height: 900 },
      { name: '1920px_wide', width: 1920, height: 1080 },
    ]

    for (const vp of viewports) {
      await page.setViewportSize({ width: vp.width, height: vp.height })
      await page.goto('/workshop')
      await page.waitForLoadState('networkidle')
      await expect(page.getByRole('heading', { name: /HireFlow Component Workshop/i })).toBeVisible()
      await saveScreenshot(page, `workshop_${vp.name}.png`)
    }
  })

  test('Student Shell preview captures and dossier header', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/workshop')
    await page.getByRole('button', { name: 'Student Shell' }).click()
    await expect(page.getByRole('heading', { name: /Student Opportunity Hub/i })).toBeVisible()
    await expect(page.locator('text=2022CS0144').first()).toBeVisible()
    await saveScreenshot(page, 'student_shell_desktop.png')

    // Mobile view of student shell
    await page.setViewportSize({ width: 390, height: 844 })
    await saveScreenshot(page, 'student_shell_mobile_390.png')
  })

  test('Officer Shell preview captures and command center layout', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/workshop')
    await page.getByRole('button', { name: 'Officer Shell' }).click()
    await expect(page.getByRole('heading', { name: 'Placement Command Center' })).toBeVisible()
    await expect(page.getByText('Active Drives')).toBeVisible()
    await saveScreenshot(page, 'officer_shell_desktop.png')

    // Mobile view of officer shell
    await page.setViewportSize({ width: 390, height: 844 })
    await saveScreenshot(page, 'officer_shell_mobile_390.png')
  })

  test('Login view and validation error state', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/auth/login')
    await expect(page.getByRole('heading', { name: /Institutional Sign In/i })).toBeVisible()
    await saveScreenshot(page, 'login_page_clean.png')

    // Mobile login capture
    await page.setViewportSize({ width: 390, height: 844 })
    await saveScreenshot(page, 'login_page_mobile_390.png')

    // Trigger validation error by clicking submit with empty inputs
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.getByRole('button', { name: /Sign In to Portal/i }).click()
    await expect(page.getByText(/Please provide both institutional username and password/i)).toBeVisible()
    await saveScreenshot(page, 'login_page_error.png')
  })

  test('Status treatments and domain badges audit', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/workshop')

    // Scroll to Domain Status Badges section
    const badgeSection = page.getByRole('heading', { name: /5. Domain Status Badges/i })
    await badgeSection.scrollIntoViewIfNeeded()
    await expect(page.getByText('Application Status Machine')).toBeVisible()
    await saveScreenshot(page, 'status_treatments_desktop.png')
  })

  test('Data table sorting and interactive shortlisting modal', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/workshop')

    // Scroll to Data Table section
    const tableSection = page.getByRole('heading', { name: /6. Data Table Foundation/i })
    await tableSection.scrollIntoViewIfNeeded()
    await saveScreenshot(page, 'dense_data_table.png')

    // Open shortlist confirmation dialog for an applied candidate
    const shortlistBtns = page.getByRole('button', { name: 'Shortlist' })
    if (await shortlistBtns.count() > 0) {
      await shortlistBtns.first().click()
      await expect(page.getByText(/Confirm Candidate Shortlisting/i)).toBeVisible()
      await expect(page.getByText(/Operational Impact/i)).toBeVisible()
      await saveScreenshot(page, 'dialog_confirmation_state.png')
      // Confirm shortlist
      await page.getByRole('button', { name: /Confirm Shortlist/i }).click()
      await expect(page.getByText(/successfully shortlisted/i)).toBeVisible()
    }
  })

  test('Dialog and alert state screens', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/workshop')

    // Open Alert Dialog
    const alertBtn = page.getByRole('button', { name: /Open Alert Confirmation/i })
    await alertBtn.scrollIntoViewIfNeeded()
    await alertBtn.click()
    await expect(page.getByText(/Terminate Recruitment Drive\?/i)).toBeVisible()
    await expect(page.getByText(/Irrevocable Consequence/i)).toBeVisible()
    await saveScreenshot(page, 'alert_dialog_open.png')
  })
})
