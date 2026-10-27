import '@testing-library/jest-dom/vitest'

// Global ResizeObserver mock for Radix UI components in jsdom
if (typeof window !== 'undefined') {
  ;(window as any).__HIREFLOW_SIMULATION_MODE__ = true
  if (!window.ResizeObserver) {
    window.ResizeObserver = class ResizeObserver {
      observe() {}
      unobserve() {}
      disconnect() {}
    }
  }
}
