# HireFlow Accessibility (a11y) Patterns & WCAG 2.1 AA Standards

HireFlow is an institutional platform that must be fully usable by students, officers, and administrators with varying abilities and assistive technologies. Every interface must strictly comply with **WCAG 2.1 Level AA**.

---

## 1. Color Contrast & Color Independence

1. **Contrast Requirements**:
   - Normal text (< 18pt or < 14pt bold): Minimum **4.5:1** contrast against background.
   - Large text ($\ge$ 18pt or $\ge$ 14pt bold): Minimum **3:0:1** contrast.
   - Interactive UI components and form borders: Minimum **3:0:1** contrast against adjacent surfaces.
   - All text tokens in `design-tokens.md` (`--hf-slate-700`, `--hf-slate-900`) exceed 7:1 against `--hf-color-warm-ivory` and `#FFFFFF`.

2. **Color Independence Rule**:
   - **Never convey meaning through color alone**.
   - Eligibility Pass/Fail: Must pair green/red colors with explicit textual indicators (e.g., `"Eligible"` with Checkmark icon, `"Ineligible"` with AlertCircle icon) and detailed textual reasons.
   - Status badges must include hidden screen-reader labels:
     ```html
     <span class="badge badge-shortlisted">
       <span class="sr-only">Application Status: </span>Shortlisted
     </span>
     ```

---

## 2. Keyboard Navigation & Focus Management

1. **Focus Ring**:
   - Every interactive element (links, buttons, inputs, tabs) must exhibit a distinct, high-contrast focus ring:
     ```css
     :focus-visible {
       outline: 2px solid var(--hf-color-academic-teal);
       outline-offset: 2px;
     }
     ```
   - Never suppress `:focus` or `:focus-visible` without providing an equal or superior custom outline.

2. **Modal & Drawer Focus Trapping**:
   - When a modal or drawer opens (e.g., drive application modal or scheduling configuration drawer):
     - Background content is marked `aria-hidden="true"`.
     - Focus is programmatically shifted to the modal header or primary input.
     - `Tab` and `Shift+Tab` are trapped within the modal boundary.
     - Pressing `Escape` closes the modal and returns focus to the initiating trigger button.

3. **Skip Links**:
   - Provide a top-level skip link: `<a href="#main-content" class="skip-to-content">Skip to primary content</a>`.

---

## 3. Screen Reader & ARIA Semantics

1. **Complex Tables**:
   - Use standard `<table>`, `<thead>`, `<tbody>`, `<th>`, and `<td>` elements.
   - Every column header must specify `scope="col"`.
   - Sortable column headers must declare `aria-sort="ascending"`, `aria-sort="descending"`, or `aria-sort="none"`.
   - Action buttons within table rows must include accessible names (e.g., `aria-label="Shortlist candidate John Doe (Roll CS102)"`).

2. **Live Regions (`aria-live`)**:
   - **Scheduling Countdown Timer**: Declare `aria-live="polite"` and update the screen reader announcement only at major intervals (e.g., at 10 min, 5 min, 1 min remaining) rather than every second to prevent reader thrashing.
   - **Publication Outcome Notifications**:
     - `ALL_CONFIRMED`: Announced via `role="status"` (`aria-live="polite"`).
     - `NONE_CONFIRMED` or `PARTIAL_SUCCESS`: Announced via `role="alert"` (`aria-live="assertive"`).

3. **Forms & Error Handling**:
   - Every input has an explicitly linked `<label htmlFor="...">`.
   - Invalid fields declare `aria-invalid="true"` and `aria-describedby="field-error-id"`.

---

## 4. Reduced Motion Support

Respect user operating system motion preferences:
```css
@media (prefers-reduced-motion: reduce) {
  *, ::before, ::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
    scroll-behavior: auto !important;
  }
}
```
