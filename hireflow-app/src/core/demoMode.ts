// Only the explicitly selected local development launcher enables this mode.
// Production builds erase the launcher mode in vite.config.ts as a second boundary.
export const isOfflineShowcase = import.meta.env.DEV && import.meta.env.VITE_HIREFLOW_MODE === 'simulation'
export const isLiveDemo = import.meta.env.DEV && import.meta.env.VITE_HIREFLOW_MODE === 'live'
