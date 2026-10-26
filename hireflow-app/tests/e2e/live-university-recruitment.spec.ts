import { test, expect } from '@playwright/test'
import path from 'node:path'
import fs from 'node:fs'

const localCredentials = JSON.parse(
  fs.readFileSync(path.resolve('../.run/demo.local.json'), 'utf8').replace(/^\uFEFF/, '')
)

test.describe('CAP-8.6.3 University Recruitment Season & K/L Block Room Verification', () => {

  test('Officer verifies 36 K/L Block room inventory, wing filtering, and multi-candidate shortlist in Scheduling Studio', async ({ page }) => {
    // 1. Authenticate as placement officer
    await page.goto('/auth/login')
    await page.locator('#username').fill(localCredentials.demoOfficerUsername)
    await page.locator('#password').fill(localCredentials.demoOfficerPassword)
    await page.getByRole('button', { name: 'Sign In to Portal' }).click()
    await page.waitForURL('**/officer/dashboard')
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible()

    // 2. Navigate to Campus Facilities (/officer/resources)
    await page.goto('/officer/resources')
    await expect(page.getByRole('heading', { level: 1, name: /Interview Resource Administration/i })).toBeVisible()

    // Switch to Campus Interview Rooms tab
    await page.getByRole('button', { name: /Campus Interview Rooms/i }).click()

    // Verify campus wing filter buttons are present and reflect exact counts
    const allBtn = page.getByRole('button', { name: /All Facilities \(41\)/i })
    const kBlockBtn = page.getByRole('button', { name: /K Block Engineering \(18\)/i })
    const lBlockBtn = page.getByRole('button', { name: /L Block Engineering \(18\)/i })
    const legacyBtn = page.getByRole('button', { name: /Preserved \/ Legacy \(5\)/i })

    await expect(allBtn).toBeVisible()
    await expect(kBlockBtn).toBeVisible()
    await expect(lBlockBtn).toBeVisible()
    await expect(legacyBtn).toBeVisible()

    // Filter by K Block Engineering
    await kBlockBtn.click()
    await expect(page.getByText('KF02', { exact: true })).toBeVisible()
    await expect(page.getByText('KS02', { exact: true })).toBeVisible()
    await expect(page.getByText('KT02', { exact: true })).toBeVisible()
    await expect(page.getByText('LF02', { exact: true })).toHaveCount(0)

    // Filter by L Block Engineering
    await lBlockBtn.click()
    await expect(page.getByText('LF02', { exact: true })).toBeVisible()
    await expect(page.getByText('LS02', { exact: true })).toBeVisible()
    await expect(page.getByText('LT02', { exact: true })).toBeVisible()
    await expect(page.getByText('KF02', { exact: true })).toHaveCount(0)

    // 3. Navigate to Candidate Review for DRV-U26-01
    await page.goto('/officer/drives/DRV-U26-01/applications')
    await expect(page.getByRole('heading', { level: 1, name: /Candidate Application Review/i })).toBeVisible()

    // Verify multiple candidate applications are visible
    await expect(page.getByText('SHORTLISTED').first()).toBeVisible()

    // 4. Navigate to Scheduling Studio (/officer/scheduling)
    await page.goto('/officer/scheduling')
    await expect(page.getByRole('heading', { name: /Interview Scheduling Studio/i })).toBeVisible()

    // Select DRV-U26-01
    const studioDriveSelect = page.locator('#scheduling-drive-select')
    await expect(studioDriveSelect).toBeVisible()
    await studioDriveSelect.selectOption('DRV-U26-01')

    // Verify candidate review card lists the 4 shortlisted candidates
    await expect(page.getByText('Shortlisted Candidate Pool:')).toBeVisible()
    await expect(page.getByText(/4 candidates/i)).toBeVisible()
  })

  test('Student browses university placement season and verifies closed historical drive retention', async ({ page }) => {
    // 1. Authenticate as student
    await page.goto('/auth/login')
    await page.locator('#username').fill(localCredentials.demoStudentUsername)
    await page.locator('#password').fill(localCredentials.demoStudentPassword)
    await page.getByRole('button', { name: 'Sign In to Portal' }).click()
    await page.waitForURL('**/student/dashboard')
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible()

    // 2. Navigate to Student Opportunities (/student/drives)
    await page.goto('/student/drives')
    await expect(page.getByRole('heading', { level: 1, name: /Recruitment Opportunity Explorer/i })).toBeVisible()

    // Verify open U26 drives are listed
    await expect(page.getByText(/Aether Cloud Networks/i)).toBeVisible()
    await expect(page.getByText(/Cognitive Robotics Labs/i)).toBeVisible()

    // Verify closed historical drive DRV-U26-06 is listed with closed badge
    await expect(page.getByText(/BioInformatics Core Systems/i)).toBeVisible()
    await expect(page.getByText(/Drive Closed/i).first()).toBeVisible()
  })

})
