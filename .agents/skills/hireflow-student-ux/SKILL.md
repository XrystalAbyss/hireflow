---
name: hireflow-student-ux
description: >-
  UX patterns, view specifications, and workflow rules for the HireFlow Student Opportunity Hub.
  Use when implementing or refining student dashboards, drive browsing, eligibility checking,
  application submission, or student interview timetables.
---

# HireFlow Student UX Skill

## Purpose
Govern student-facing workflows, eligibility transparency, application submission journeys, and personal interview timetable views. Enforce institutional dignity, read-only academic record boundaries, and clear communication of academic eligibility rules.

## Activation Conditions
Activate this skill whenever:
- Designing or implementing views under `/student/*` (StudentShell, Opportunity Hub, Timetable).
- Implementing eligibility checks (`GET /api/v1/student/drives/{id}/eligibility`).
- Building the application submission modal dialog and confirmation flow.
- Displaying student academic profile records or interview schedule cards.

## Reference Materials
- [student-workflows.md](./references/student-workflows.md): Screen blueprints and interaction flows.
- [api-contract.md](../hireflow-api-integration/references/api-contract.md): Student REST API endpoints.

## Concrete Domain Examples

### 1. Itemized Eligibility Breakdown Card
```tsx
// Deterministic eligibility presentation
<div className="rounded-lg border border-border bg-card p-4 space-y-3">
  <div className="flex items-center justify-between">
    <span className="text-sm font-medium text-foreground">Academic Eligibility</span>
    <EligibilityStatusBadge status={eligibility.academicallyEligible ? 'ELIGIBLE' : 'INELIGIBLE'} />
  </div>
  <div className="text-xs text-muted-foreground space-y-1">
    <p>Required CGPA: {eligibility.minimumCgpa} (Your CGPA: {eligibility.studentCgpa})</p>
    <p>Eligible Branches: {Array.from(eligibility.eligibleBranches).join(', ')}</p>
  </div>
  {!eligibility.canApply && eligibility.reasons.length > 0 && (
    <ul className="text-xs text-destructive list-disc list-inside space-y-0.5">
      {eligibility.reasons.map((r, i) => <li key={i}>{r}</li>)}
    </ul>
  )}
</div>
```

### 2. Read-Only Academic Dossier Header
```tsx
// Authoritative, read-only academic profile (no fake registrar seal or edit button)
<div className="flex items-center gap-4 py-2 border-b border-border text-sm">
  <div>
    <span className="text-xs text-muted-foreground uppercase tracking-wider">Roll No</span>
    <p className="font-mono font-medium text-foreground">{profile.id}</p>
  </div>
  <div>
    <span className="text-xs text-muted-foreground uppercase tracking-wider">Branch</span>
    <p className="font-medium text-foreground">{profile.branch}</p>
  </div>
  <div>
    <span className="text-xs text-muted-foreground uppercase tracking-wider">Verified CGPA</span>
    <p className="font-mono font-bold text-academic-teal">{profile.cgpa.toFixed(2)}</p>
  </div>
</div>
```

## Failure & Rejection Criteria
Reject any implementation that:
- **Displays fake registrar seals or claims external registrar integration**: Student records are backend-authoritative in HireFlow; do not claim unverified external integrations.
- **Renders editable student profile inputs**: Student records are strictly read-only on the frontend.
- **Enables application without eligibility verification**: Always query `GET /student/drives/{id}/eligibility` before rendering submit actions.
- **Submits applications without explicit user confirmation**: Student applications cannot be retracted; an explicit confirmation modal is mandatory.
- **Uses non-standard font sizes (<12px)**: Ensure all metadata is clearly legible per WCAG 2.2 AA.

## Verification Procedures
1. Verify that the application button is disabled whenever `canApply === false` with itemized reasons rendered.
2. Confirm the submission confirmation modal explicitly informs the student that applications cannot be retracted.
3. Verify that student profile fields contain no edit buttons or form inputs.
4. Test responsive layout on mobile viewport (390px) to ensure no horizontal overflow.
