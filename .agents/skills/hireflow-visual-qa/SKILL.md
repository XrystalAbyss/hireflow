---
name: hireflow-visual-qa
description: >-
  Auditing and enforcing visual quality standards, responsive viewport testing, design token compliance,
  and anti-generic aesthetics for HireFlow frontend views and components.
  Use during frontend code reviews, styling validation, or responsive audits.
---

# HireFlow Visual QA Skill

## Purpose
Audit and validate visual presentation, responsive layout stability, typography balance, and token adherence across all HireFlow frontend views and components. Ensure real browser screenshot captures at mandatory viewports and eliminate visual defects before shipping.

## Activation Conditions
Activate this skill whenever:
- Executing visual regression or smoke tests.
- Capturing and reviewing browser screenshots across viewports.
- Auditing new UI components or layouts for spacing, alignment, and responsiveness.
- Preparing milestone release evidence and visual quality reports.

## Reference Materials
- [visual-qa-checklist.md](./references/visual-qa-checklist.md): Itemized audit criteria for surfaces, borders, typography, and states.

## Concrete Domain Examples

### 1. Multi-Viewport Inspection Matrix
Every screen and major component must be validated across the 5 canonical viewports:
- **390px (Mobile portrait)**: Check for horizontal scrolling, button stacking, hamburger drawer ergonomics, and minimum touch target size (44x44px).
- **768px (Tablet portrait)**: Check grid transitions, table responsiveness, and header padding.
- **1024px (Laptop)**: Check navigation bar density, sidebar docking, and modal dialog centering.
- **1440px (Desktop baseline)**: Verify container max-widths, table column proportions, and typography scale.
- **1920px (Wide monitor)**: Verify content centering without awkward whitespace gaps or stretched hero sections.

### 2. Defect Detection & Correction Pattern
- *Defect*: Data table columns truncate critical numbers or cause horizontal page scroll.
  *Fix*: Add `overflow-x-auto` to table wrapper, apply fixed width to short columns (e.g., status, roll number), and allow flexible width on title/company columns.
- *Defect*: Buttons look like toy pill shapes with excessive radii.
  *Fix*: Apply `rounded-md` (6px) per design token guidelines.

## Failure & Rejection Criteria
Reject any implementation or review that:
- **Approves screenshots solely because they render**: A critical visual review must identify defects in hierarchy, spacing, alignment, and contrast.
- **Claims visual verification without actual screenshot artifacts**: Real browser captures saved to disk are mandatory.
- **Tolerates horizontal page overflow**: Body container must never produce horizontal scrollbars on mobile (390px).
- **Allows generic SaaS aesthetics**: Unbranded gradients, floating blur elements, or non-tokenized styling.

## Verification Procedures
1. Run automated Playwright screenshot suite across 390px, 768px, 1024px, 1440px, and 1920px viewports.
2. Inspect generated images for visual defects: alignment, clipping, overlapping text, or jarring margins.
3. Verify that all components render with crisp typography and distinct visual hierarchy.
4. Document all visual defects discovered, the specific fixes applied in React/CSS, and confirm clean post-fix renders.
