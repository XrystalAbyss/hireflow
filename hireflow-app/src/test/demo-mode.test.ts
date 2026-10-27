import { afterEach, describe, expect, it, vi } from 'vitest'

afterEach(() => { vi.unstubAllEnvs(); vi.unstubAllGlobals(); vi.resetModules() })
describe('Explicit demo-mode security boundaries', () => {
  it('production never enables offline showcase, including a custom build mode', async () => {
    vi.stubEnv('DEV', false); vi.stubEnv('PROD', true); vi.stubEnv('MODE', 'custom'); vi.stubEnv('VITE_HIREFLOW_MODE', 'simulation')
    expect((await import('@/core/demoMode')).isOfflineShowcase).toBe(false)
  })
  it('Live mode refuses scheduling simulation opt-in', async () => {
    vi.stubEnv('VITE_HIREFLOW_MODE', 'live')
    const simulation = await import('@/features/officer/api/schedulingSimulation')
    simulation.setSimulationModeActive(true)
    expect(simulation.isSimulationModeActive()).toBe(false)
  })
  it('production custom build mode refuses scheduling simulation', async () => {
    vi.stubEnv('PROD', true); vi.stubEnv('MODE', 'custom')
    const simulation = await import('@/features/officer/api/schedulingSimulation')
    simulation.setSimulationModeActive(true)
    expect(simulation.isSimulationModeActive()).toBe(false)
  })
  it('a legacy simulation flag cannot divert reads or mutations from the real API', async () => {
    vi.stubEnv('DEV', true); vi.stubEnv('VITE_HIREFLOW_MODE', 'simulation')
    const fetch = vi.fn()
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => ({ id: 'STU-01' }) })
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => ({ token: 'csrf-test', headerName: 'X-CSRF-TOKEN' }) })
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => ({ id: 'DRV-01' }) })
    vi.stubGlobal('fetch', fetch)
    const { apiClient } = await import('@/core/api/client')
    expect(await apiClient.request('/api/v1/student/profile')).toEqual({ id: 'STU-01' })
    expect(await apiClient.request('/api/v1/officer/drives', { method: 'POST', body: '{}' })).toEqual({ id: 'DRV-01' })
    expect(fetch.mock.calls.map(([url]) => url)).toEqual([
      '/api/v1/student/profile', '/api/v1/auth/csrf', '/api/v1/officer/drives',
    ])
    expect(new Headers(fetch.mock.calls[2][1].headers).get('X-CSRF-TOKEN')).toBe('csrf-test')
  })
  it('Live mode does not activate showcase', async () => {
    vi.stubEnv('VITE_HIREFLOW_MODE', 'live')
    expect((await import('@/core/demoMode')).isOfflineShowcase).toBe(false)
  })
})

