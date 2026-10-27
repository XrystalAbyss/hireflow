import { test, expect, type Page } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'
import fs from 'node:fs'
import path from 'node:path'

const evidence = path.resolve('../.run/evidence/live-audit')
const credentials = JSON.parse(fs.readFileSync(path.resolve('../.run/demo.local.json'), 'utf8').replace(/^\uFEFF/, ''))
fs.mkdirSync(evidence, { recursive: true })
const widths = [390, 768, 1024, 1440, 1920]
test.use({ actionTimeout: 15000, navigationTimeout: 15000 })

async function login(page: Page, role: 'student' | 'officer') {
  await page.goto('/auth/login')
  await page.locator('#username').fill(credentials[(role === 'student' ? 'demoStudent' : 'demoOfficer') + 'Username'])
  await page.locator('#password').fill(credentials[(role === 'student' ? 'demoStudent' : 'demoOfficer') + 'Password'])
  await page.getByRole('button', { name: 'Sign In to Portal' }).click()
  await page.waitForURL(`**/${role}/dashboard`)
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
}

test('Live responsive, accessibility, route performance and authentication audit', async ({ page, context }) => {
  test.setTimeout(300000)
  const metrics: unknown[] = []
  const accessibility: unknown[] = []
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  const inspect = async (route: string, name: string, audit = false) => {
    for (const width of widths) {
      await page.setViewportSize({ width, height: width === 390 ? 844 : 900 })
      const requests: string[] = []
      const collect = (request: { url(): string }) => { if (new URL(request.url()).pathname.startsWith('/api/')) requests.push(new URL(request.url()).pathname) }
      page.on('request', collect)
      const start = Date.now()
      await page.goto(route)
      await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
      await expect(page.getByText('Checking session...', { exact: true })).toHaveCount(0)
      await page.waitForLoadState('networkidle')
      const ms = Date.now() - start
      page.off('request', collect)
      const overflow = await page.evaluate(() => ({ width: window.innerWidth, documentWidth: document.documentElement.scrollWidth }))
      metrics.push({ name, width, elapsedMs: ms, apiRequests: requests, ...overflow })
      await page.screenshot({ path: path.join(evidence, `${name}-${width}.png`), fullPage: true })
      expect.soft(overflow.documentWidth, `${name} horizontal overflow at ${width}`).toBeLessThanOrEqual(width + 1)
      if (audit && width === 1440) {
        const scan = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze()
        accessibility.push({ name, violations: scan.violations })
        expect.soft(scan.violations.map(v => ({ id: v.id, impact: v.impact, targets: v.nodes.map(n => n.target) })), `${name} accessibility`).toEqual([])
      }
    }
    fs.writeFileSync(path.join(evidence, 'route-metrics.json'), JSON.stringify(metrics, null, 2))
    fs.writeFileSync(path.join(evidence, 'accessibility.json'), JSON.stringify(accessibility, null, 2))
  }

  await inspect('/auth/login', 'login', true)
  await page.request.get('/api/v1/auth/csrf')
  const before = (await context.cookies()).find(c => c.name === 'JSESSIONID')?.value
  await login(page, 'student')
  const session = (await context.cookies()).find(c => c.name === 'JSESSIONID')
  expect(session?.httpOnly).toBe(true)
  expect(session?.sameSite).toBe('Lax')
  expect(session?.value).not.toBe(before)
  expect((await page.request.get('/api/v1/officer/companies')).status()).toBe(403)
  for (const name of ['dashboard', 'drives', 'applications', 'interviews']) await inspect(`/student/${name}`, `student-${name}`, true)
  const apps = await (await page.request.get('/api/v1/student/applications?size=10')).json()
  if (apps.content?.length) await inspect('/student/applications/' + apps.content[0].id, 'student-application-detail')
  await page.getByRole('button', { name: /Sign Out/i }).first().click()
  await page.waitForURL('**/auth/login')
  expect((await page.request.get('/api/v1/auth/me')).status()).toBe(401)
  await login(page, 'officer')
  for (const name of ['dashboard', 'companies', 'drives', 'resources', 'scheduling']) await inspect(`/officer/${name}`, `officer-${name}`, true)
  const drives = await (await page.request.get('/api/v1/officer/drives?size=100')).json()
  if (drives.content?.length) await inspect('/officer/drives/' + drives.content[0].id + '/applications', 'officer-candidates', true)
  const storage = await page.evaluate(() => ({ local: Object.keys(localStorage), session: Object.keys(sessionStorage) }))
  expect(storage.local.filter(k => /password|token|auth|session/i.test(k))).toEqual([])
  expect(storage.session.filter(k => /password|token|auth|session/i.test(k))).toEqual([])
  await page.getByRole('button', { name: /Sign Out/i }).first().click()
  await page.waitForURL('**/auth/login')
  expect((await page.request.get('/api/v1/auth/me')).status()).toBe(401)
  fs.writeFileSync(path.join(evidence, 'browser-errors.json'), JSON.stringify(errors, null, 2))
  expect(errors).toEqual([])
})
