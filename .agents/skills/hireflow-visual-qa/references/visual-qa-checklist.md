# HireFlow Visual Quality Assurance Checklist

This checklist defines the rigorous visual quality standards required for every HireFlow component, view, and template.

---

## 1. Design Token Fidelity
- [ ] **No Hardcoded Ad-hoc Hex Colors**: All colors must use CSS custom properties (`var(--hf-...)`) defined in `design-tokens.md`.
- [ ] **Palette Discipline**: Only Midnight Navy, Academic Teal, Warm Ivory, Muted Brass, Warm Slate scale, and semantic status colors are permitted.
- [ ] **Anti-AI Cliché Audit**:
  - [ ] Zero purple-to-pink gradients.
  - [ ] Zero frosted-glass (`backdrop-filter: blur(...)`) on primary surfaces.
  - [ ] Zero glowing neon borders or oversaturated shadows.
  - [ ] Zero decorative stock 3D icons or generic floating shapes.

---

## 2. Typography & Hierarchy
- [ ] **Heading Font**: Page titles and section headlines use `Newsreader` or `Literata` serif font with appropriate letter-spacing.
- [ ] **UI & Table Font**: Table cells, inputs, badges, and labels use `Plus Jakarta Sans` or `Inter`.
- [ ] **Tabular Figures**: Numeric columns (CGPA, counts, monetary CTC, durations) must enable tabular figures (`font-feature-settings: "tnum"`).
- [ ] **Monospace Tokens**: All session IDs, preview tokens (`PREV-...`), roll numbers, and ISO dates must use `JetBrains Mono`.

---

## 3. Data Density & Layout
- [ ] **Table Row Height**: Table rows must remain dense (36px to 42px) to allow high scan rates for officers reviewing dozens of candidates.
- [ ] **Sticky Table Headers**: Data tables maintain sticky headers during vertical scrolling.
- [ ] **No Horizontal Breakout**: Wide data tables must be wrapped in overflow containers (`overflow-x: auto`) with visual scroll indicators, preventing page-level layout breakage.
- [ ] **Responsive Breakpoints**:
  - Desktop: 1440px+ (sidebar expanded, full table view)
  - Laptop: 1024px (compact table columns)
  - Tablet: 768px (sidebar collapsible into drawer)
  - Mobile: 375px (cards stack vertically, responsive filters convert to bottom sheet)

---

## 4. State & Feedback Fidelity
- [ ] **Loading States**: Skeletons match exact component dimensions and geometric radii (`var(--hf-radius-md)`), avoiding jarring layout shifts (CLS < 0.05).
- [ ] **Empty States**: Clear, dignified empty state messages with institutional icons and actionable next steps (e.g., *"No active recruitment drives match your branch criteria"*).
- [ ] **Error States**: Display human-readable error messages extracted from `ApiErrorResponse.message`.
- [ ] **Focus Rings**: All interactive controls display high-contrast `2px solid var(--hf-color-academic-teal)` focus indicators with `2px` offset.
