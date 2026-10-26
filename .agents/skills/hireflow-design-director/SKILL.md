---
name: hireflow-design-director
description: >-
  Oversees the HireFlow Institutional x Contemporary design system, design token usage,
  typography pairings, spacing rules, and anti-generic visual governance.
  Use when creating or styling UI components, layouts, cards, typography, or theme variables.
---

# HireFlow Design Director Skill

## Purpose
Oversee the aesthetic direction, design token hierarchy, and visual governance of HireFlow. Enforce the **Institutional × Contemporary** design philosophy: dignified, scholarly, authoritative, and clean. Prevent generic AI/SaaS dashboard tropes and ensure disciplined design execution.

## Activation Conditions
Activate this skill whenever:
- Designing or modifying UI components, tokens, or styling rules.
- Reviewing CSS files (`src/styles/tokens.css`, `src/index.css`).
- Implementing layouts, cards, typography, badges, or dialogs.
- Evaluating light-mode aesthetics and color contrast.

## Reference Materials
- [design-tokens.md](./references/design-tokens.md): Semantic color tokens, typography scales, spacing, radii, and shadows.

## Concrete Domain Examples

### 1. Palette & Token Application
```css
/* Core Institutional Palette */
--hf-color-midnight-navy: #17324B;  /* Deep header / brand primary */
--hf-color-academic-teal: #176B69;  /* Scholarly accents / active links */
--hf-color-warm-ivory:    #F6F4EE;  /* Dignified page background */
--hf-color-muted-brass:   #C6A46B;  /* Institutional badges & highlights */

/* Semantic Surface & Foreground */
background-color: var(--hf-surface-card);
color: var(--hf-text-primary);
border: 1px solid var(--hf-border-subtle);
```

### 2. Typography Hierarchy Pairing
- **Headings (Page titles, editorial accents)**: `Newsreader` (serif) or `Inter` (sans-serif bold). Gives academic authority without being archaic.
- **Body & Dense UI Tables**: `Inter` (sans-serif, 14px to 16px). High legibility at small sizes.
- **Codes & Identifiers**: `JetBrains Mono` (tabular numbers, roll numbers, session IDs).

## Failure & Rejection Criteria
Reject any implementation that:
- **Uses generic AI SaaS aesthetics**: Reject floating purple/cyan neon gradients, blurry glassmorphism/frosted glass, and excessive drop shadows.
- **Forces complete dark mode in CAP-8.1**: Light mode first; complete dark mode is reserved for a later approved milestone.
- **Uses raw hardcoded colors**: All styling must consume semantic design tokens (`var(--hf-*)` or Tailwind theme aliases).
- **Uses illegibly small fonts (<12px)**: Never force 11px operational metadata for aesthetic reasons; maintain WCAG 2.2 AA legibility.
- **Uses excessive border radii**: Rounded corners must remain restrained (max `rounded-lg` / 8px).
- **Employs decorative charts without real data**: Reject empty graph placeholders or vanity dials.

## Verification Procedures
1. Audit CSS and JSX for hardcoded hex codes; verify 100% token usage.
2. Verify heading and body font pairings render properly in the browser.
3. Validate minimum 4.5:1 text contrast for body text and 3:1 for large headings across all surfaces.
4. Ensure reduced-motion media query (`prefers-reduced-motion: reduce`) is respected across all animations.
