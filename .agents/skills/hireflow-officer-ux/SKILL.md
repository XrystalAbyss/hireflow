---
name: hireflow-officer-ux
description: >-
  Operational UX specifications and workflow rules for the HireFlow Placement Officer Command Center
  and Interview Scheduling Studio. Use when implementing or refining officer dashboards,
  company/drive administration, candidate shortlisting, resource management, or the scheduling studio.
---

# HireFlow Officer UX Skill

## Purpose
Govern the operational workflows, high-density layouts, administrative tools, and interview scheduling studio interfaces for university placement officers and administrators. Ensure strict compliance with backend state machines and prevent unsupported workflows.

## Activation Conditions
Activate this skill whenever:
- Designing or implementing views under `/officer/*` (OfficerShell, Command Center, Scheduling Studio).
- Implementing company, drive, room, or interviewer administrative tables and forms.
- Building candidate shortlisting interactions or recruitment drive closure dialogues.
- Constructing the GreedyScheduler timetable preview, TTL countdown, or publication workflow.

## Reference Materials
- [officer-workflows.md](./references/officer-workflows.md): Detailed screen blueprints and workflow diagrams.
- [api-contract.md](../hireflow-api-integration/references/api-contract.md): Authoritative officer REST API endpoints.

## Concrete Domain Examples

### 1. High-Density Candidate Shortlisting
```tsx
// Individual candidate row shortlisting (no client-side bulk loops)
<tr key={candidate.applicationId} className="h-10 border-b border-border text-sm">
  <td className="px-3 font-mono text-xs">{candidate.studentRollNo}</td>
  <td className="px-3 font-medium text-foreground">{candidate.studentName}</td>
  <td className="px-3 text-muted-foreground">{candidate.branch}</td>
  <td className="px-3 font-mono">{candidate.cgpa.toFixed(2)}</td>
  <td className="px-3">
    <ApplicationStatusBadge status={candidate.status} />
  </td>
  <td className="px-3 text-right">
    {candidate.status === 'APPLIED' && (
      <Button
        size="sm"
        variant="outline"
        onClick={() => handleShortlist(candidate.applicationId)}
        disabled={isShortlisting(candidate.applicationId)}
      >
        Shortlist
      </Button>
    )}
  </td>
</tr>
```

### 2. Scheduling Publication Handling (200 vs 207 vs 409 vs 410 vs 5xx)
```tsx
// Handled in Interview Scheduling Studio (PublicationOutcomeView.tsx)
const handlePublish = async () => {
  try {
    const summary: OfficerPublicationSummaryDto = await apiClient.publishSchedule({ previewId });
    if (summary.outcome === 'ALL_CONFIRMED') {
      // 200 OK: Newly published sessions plus distinct skipped duplicates count
      setOutcomeState({
        type: 'ALL_CONFIRMED',
        publishedCount: summary.publishedCount,
        skippedDuplicatesCount: summary.skippedDuplicatesCount,
        sessions: summary.publishedSessions,
      });
    } else if (summary.outcome === 'PARTIAL_SUCCESS') {
      // 207 Multi-Status: Per-session persistence. Itemized conflict rejections
      setOutcomeState({
        type: 'PARTIAL_SUCCESS',
        publishedCount: summary.publishedCount,
        conflictsCount: summary.conflictsCount,
        conflicts: summary.conflictRejections,
        instructions: summary.recoveryInstructions,
      });
    } else if (summary.outcome === 'NONE_CONFIRMED') {
      // 409 Conflict: Zero sessions confirmed
      setOutcomeState({
        type: 'NONE_CONFIRMED',
        conflicts: summary.conflictRejections,
      });
    }
  } catch (error: any) {
    if (error.status === 410) {
      setOutcomeState({ type: 'EXPIRED' }); // 15-min cache eviction
    } else if (error.status === 409 && error.isReplay) {
      setOutcomeState({ type: 'CONSUMED_REPLAY' }); // Token already used
    } else if (error.status >= 500) {
      setOutcomeState({ type: 'DATABASE_ERROR', uncertain: true }); // Ambiguous persistence
    }
  }
};
```

## Failure & Rejection Criteria
Reject any implementation that:
- **Builds drag-to-reschedule calendars**: Scheduling is generated algorithmically by the backend greedy scheduler. The preview timetable is immutable.
- **Claims publication is all-or-nothing**: Backend uses per-session persistence; 207 Multi-Status must be handled with partial success recovery guidance.
- **Implements client-side bulk shortlisting loops**: Backend only provides individual shortlisting (`POST /applications/{id}/shortlist`). Document bulk operations as unsupported.
- **Renders unsupported DELETE actions**: The backend does not have DELETE endpoints for companies, drives, or candidates.
- **Leaks preview tokens**: Never put `previewId` into the browser URL or query string.
- **Displays fake metrics**: No fictional placement percentages, partner logos, or fake stats.

## Verification Procedures
1. Verify all interactive controls map 1-to-1 with an authoritative endpoint in `api-contract.md`.
2. Verify that tables maintain a dense, compact layout (36px to 42px row height) with accessible focus rings.
3. Test scheduling publication error handling for 200, 207, and 409 responses.
4. Verify full keyboard operability (`Tab`, `Enter`, `Escape`) across all officer forms and modal dialogs.
