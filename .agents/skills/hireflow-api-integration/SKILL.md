---
name: hireflow-api-integration
description: >-
  Authoritative guidance for frontend API integration with the HireFlow Spring Boot backend.
  Use when creating or updating API clients, handling authentication sessions, CSRF headers,
  error envelopes, or verifying endpoint compatibility with the CAP-7 baseline.
---

# HireFlow Frontend API Integration Skill

## Purpose
Enforce strict, machine-verified adherence to the 40 Spring Boot REST endpoints implemented in `hireflow-api` (CAP-7 baseline: `bdbf0e1dce7407ab9034f893712ccea8d7fced56`). Prevent hallucinated endpoints, unauthorized HTTP verbs, unsafe token handling, and non-authoritative client mutations.

## Activation Conditions
Activate this skill whenever:
- Designing or modifying network services, API clients, or query hooks.
- Configuring authentication, session handling, or CSRF tokens.
- Handling backend error envelopes (`ApiErrorResponse`) or HTTP status codes.
- Integrating scheduling preview or publication flows.
- Reviewing PRs or code touching `/src/core/api` or `/src/types/api.ts`.

## Reference Materials
- [api-contract.md](./references/api-contract.md): Complete machine-grounded 40-route contract and DTO inventory.
- [hireflow-api Source](../../hireflow-api/src/main/java/com/hireflow): Authoritative backend source code (controllers, DTOs, security config).

## Concrete Domain Examples

### 1. CSRF Token Lifecycle & Session Rotation
```typescript
// Fetch token on startup or after login
const csrf = await apiClient.fetchCsrfToken();

// Mutating request includes X-CSRF-TOKEN
await apiClient.post('/api/v1/student/applications', { driveId }, {
  headers: { 'X-CSRF-TOKEN': csrf.token }
});

// CRITICAL: Refresh CSRF immediately after login session rotation
await apiClient.login({ username, password });
await apiClient.fetchCsrfToken(); // In-memory token updated for new JSESSIONID
```

### 2. Publication Response Handling (Non-Atomic Per-Session Persistence)
```typescript
// POST /api/v1/officer/scheduling/publish
const summary: OfficerPublicationSummaryDto = await apiClient.publishSchedule({ previewId });
if (summary.outcome === 'ALL_CONFIRMED') {
  // ALL_CONFIRMED: Newly published sessions plus skipped duplicates
  showSuccessToast(`${summary.publishedCount} interviews published (${summary.skippedDuplicatesCount} existing skipped)`);
} else if (summary.outcome === 'PARTIAL_SUCCESS') {
  // PARTIAL_SUCCESS: Per-session persistence. Conflicted candidates remain shortlisted
  showWarningNotice({
    published: summary.publishedCount,
    conflicts: summary.conflictsCount,
    conflictDetails: summary.conflictRejections,
    instructions: summary.recoveryInstructions,
  });
} else if (summary.outcome === 'NONE_CONFIRMED') {
  showErrorNotice({
    conflicts: summary.conflictRejections,
    message: "Zero sessions confirmed due to concurrent slot conflicts."
  });
}
```

## Failure & Rejection Criteria
Reject any implementation that:
- **Blindly retries failed mutations on HTTP 403**: Never auto-replay without classifying the error.
- **Assumes atomic scheduling publication**: Publication uses per-session persistence and can return HTTP 207 Multi-Status.
- **Implements client-side bulk shortlisting loops**: Backend does not support bulk shortlisting; bulk operations must be documented as unsupported.
- **Leaks tokens or preview IDs**: Never output CSRF tokens or `previewId` into URLs, query params, console logs, analytics, persistent client storage, or screenshots.
- **Invents unsupported endpoints**: No `DELETE` routes for companies/drives; no `PUT /student/profile`.
- **Uses JWT or localStorage authentication**: HireFlow relies strictly on server-side `JSESSIONID` cookies with `credentials: 'include'`.

## Verification Procedures
1. Run TypeScript check: `npm run typecheck` to verify DTO compliance against `src/types/api.ts`.
2. Inspect network calls to ensure mutations include `X-CSRF-TOKEN` and `credentials: 'include'`.
3. Check browser dev tools / console output to confirm zero tokens or preview IDs appear in logs or storage.
4. Verify HTTP 200, 207, and 409 status handling in scheduling modules.
