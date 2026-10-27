# HireFlow navigation cache and motion — 24 October 2026

HireFlow now warms the selected student or officer destination when its navigation link is hovered or focused. The browser can load that screen's code and its first data set before the click. Query keys match the destination hooks, so a fresh result is reused instead of fetched again. Prefetch failures remain non-blocking; each destination still handles its own loading and error state.

The in-memory query cache treats data as fresh for 20 seconds and discards inactive entries after five minutes. Live polling, reconnect refreshes, and mutation invalidation remain active. HTTP 4xx responses no longer receive an automatic retry. Login, logout, and session expiry continue to clear the cache, preventing data from carrying between accounts. No data was added to persistent browser storage.

Student and officer route changes use a 180 ms opacity/position entrance. The animation is disabled by `prefers-reduced-motion: reduce`.

Verification: production build passed; lint passed with existing warnings; 146 pre-existing non-live frontend tests passed; the focused cache reuse/clear test passed. The legacy fixture browser suite redirected officer routes to sign-in in the current live-only configuration, so it cannot validate authenticated route transitions. No live service was listening on 55432, 8080, or 5173 during this work; database-backed browser checks were not run. Existing work was preserved, with no commit or release.
