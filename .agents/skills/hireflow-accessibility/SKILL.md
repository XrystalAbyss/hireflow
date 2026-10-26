---
name: hireflow-accessibility
description: >-
  Auditing and enforcing WCAG 2.1 AA accessibility standards, ARIA semantics, keyboard navigation,
  focus trapping, and screen-reader compatibility across HireFlow components.
  Use when implementing or reviewing interactive dialogs, tables, forms, or status live regions.
---

# HireFlow Accessibility Skill

## Purpose
Ensure comprehensive compliance with **WCAG 2.2 Level AA** standards across all HireFlow interfaces. Enforce keyboard operability, screen-reader semantics, accessible name calculation, visible focus indicators, and rigorous contrast measurement.

## Activation Conditions
Activate this skill whenever:
- Creating or updating interactive components (buttons, dialogs, dropdowns, tabs, forms).
- Implementing status badges, live notifications, or error banners.
- Reviewing modal dialogs, drawers, or alert confirmations for focus trapping.
- Conducting automated accessibility scans (axe-core) or keyboard tab-order audits.

## Reference Materials
- [a11y-patterns.md](./references/a11y-patterns.md): Concrete Radix ARIA patterns, contrast tables, and screen-reader recipes.

## Concrete Domain Examples

### 1. Accessible Form Field Association
```tsx
// Every input has an explicitly associated label and error description
<div className="space-y-1.5">
  <label htmlFor="username" className="text-sm font-medium text-foreground">
    Institutional Username
  </label>
  <Input
    id="username"
    aria-invalid={!!errors.username}
    aria-describedby={errors.username ? "username-error" : undefined}
    {...register("username")}
  />
  {errors.username && (
    <p id="username-error" className="text-xs font-medium text-destructive" role="alert">
      {errors.username.message}
    </p>
  )}
</div>
```

### 2. Dialog Focus Management & ARIA Live Regions
- **Radix UI Dialog**: Focus is trapped automatically upon opening; `Escape` key closes the dialog; focus returns to the triggering button on dismissal.
- **Dynamic Scheduling Updates**: Use `aria-live="polite"` for countdown updates; use `aria-live="assertive"` for scheduling conflict or error banners.
- **Visible Focus Ring**: `focus-visible:ring-2 focus-visible:ring-academic-teal focus-visible:ring-offset-2`. Focus indicators must never be removed (`outline: none` without replacement is forbidden).

## Failure & Rejection Criteria
Reject any implementation that:
- **Claims compliance merely because colors appear acceptable**: Contrast must be measured and verified (4.5:1 for normal text, 3:1 for large text / UI boundaries).
- **Omits form input labels**: Inputs without `htmlFor`/`id` or `aria-label` are unacceptable.
- **Omits visible focus rings**: All interactive elements must show a distinct focus ring on keyboard navigation.
- **Fails modal focus trapping**: Modals allowing background elements to receive focus while open violate WCAG 2.2 AA.
- **Uses color alone to convey status**: Badges and status indicators must pair color with distinct text and optional icons.
- **Renders text below 12px**: Metadata must remain readable and accessible.

## Verification Procedures
1. Audit components using automated accessibility tools (e.g. axe / Playwright axe checks).
2. Execute full keyboard-only navigation walkthrough: ensure all interactive controls are reachable via `Tab` and activatable via `Enter`/`Space`.
3. Verify `Escape` key closes all active popups, menus, and modals.
4. Verify screen reader announcements for error states and dynamic live regions.
