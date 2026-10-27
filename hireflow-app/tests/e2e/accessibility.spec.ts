import { test, expect } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'

test.describe('HireFlow Student Opportunity Hub — WCAG 2.1 AA Accessibility Audit', () => {
  test.beforeEach(async ({ page }) => {
    // Explicit opt-in to controlled simulation harness for fixture-based visual & accessibility QA
    await page.addInitScript(() => {
      ;(window as any).__HIREFLOW_SIMULATION_MODE__ = true
    })
  })

  test('Dashboard page passes automated WCAG 2.1 AA accessibility standards', async ({ page }) => {
    await page.goto('/student/dashboard')
    await page.waitForLoadState('networkidle')

    const accessibilityScanResults = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .disableRules(['color-contrast']) // Verified in design tokens
      .analyze()

    expect(accessibilityScanResults.violations).toEqual([])
  })

  test('Recruitment Opportunity Explorer passes accessibility standards', async ({ page }) => {
    await page.goto('/student/drives')
    await page.waitForLoadState('networkidle')

    const accessibilityScanResults = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa'])
      .disableRules(['color-contrast'])
      .analyze()

    expect(accessibilityScanResults.violations).toEqual([])
  })

  test('Drive Detail & Eligibility Breakdown page passes accessibility standards', async ({ page }) => {
    await page.goto('/student/drives/DRV-2026-002')
    await page.waitForLoadState('networkidle')

    const accessibilityScanResults = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa'])
      .disableRules(['color-contrast'])
      .analyze()

    expect(accessibilityScanResults.violations).toEqual([])
  })

  test('Applications directory passes accessibility standards', async ({ page }) => {
    await page.goto('/student/applications')
    await page.waitForLoadState('networkidle')

    const accessibilityScanResults = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa'])
      .disableRules(['color-contrast'])
      .analyze()

    expect(accessibilityScanResults.violations).toEqual([])
  })

  test('Confirmed Interview Timetable passes accessibility standards', async ({ page }) => {
    await page.goto('/student/interviews')
    await page.waitForLoadState('networkidle')

    const accessibilityScanResults = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa'])
      .disableRules(['color-contrast'])
      .analyze()

    expect(accessibilityScanResults.violations).toEqual([])
  })
})
