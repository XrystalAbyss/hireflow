import { test, expect } from '@playwright/test'

test.skip(!process.env.HIREFLOW_SIMPLE_STUDENT_PASSWORD, 'Requires the isolated demo Student password')

test('requested Student demo account signs in and signs out in the live portal', async ({ page }) => {
  await page.goto('/auth/login')
  await page.locator('#username').fill('student')
  await page.locator('#password').fill(process.env.HIREFLOW_SIMPLE_STUDENT_PASSWORD!)
  await page.getByRole('button', { name: 'Sign In to Portal' }).click()
  await page.waitForURL('**/student/dashboard')
  await expect(page.getByText('Student', { exact: true }).first()).toBeVisible()
  expect((await page.request.get('/api/v1/student/profile')).status()).toBe(200)
  expect((await page.request.get('/api/v1/officer/companies')).status()).toBe(403)
  await page.getByRole('button', { name: /Sign Out/i }).first().click()
  await page.waitForURL('**/auth/login')
  expect((await page.request.get('/api/v1/auth/me')).status()).toBe(401)
})
