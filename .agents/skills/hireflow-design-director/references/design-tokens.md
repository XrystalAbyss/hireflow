# HireFlow Design Tokens & Visual Identity System
**Direction: Institutional × Contemporary**

The visual identity of HireFlow conveys the academic prestige, bureaucratic dependability, and structured integrity of a premier university institution, fused with the snappy responsiveness and clean typography of modern software.

---

## 1. Color Palette

### 1.1 Brand & Institutional Colors
```css
:root {
  /* Primary Institutional Foundations */
  --hf-color-midnight-navy: #17324B;  /* Primary brand, headers, primary buttons, active state */
  --hf-color-academic-teal: #176B69;  /* Secondary accent, links, focused state, active tab indicators */
  --hf-color-warm-ivory:    #F6F4EE;  /* Default background, card fills, calm canvas */
  --hf-color-muted-brass:   #C6A46B;  /* Distinction badge, honors, shortlist highlight, advisory preview accent */

  /* Neutral Surface & Text Scale (Warm Slate) */
  --hf-slate-50:  #FAF9F6;  /* Purest ivory canvas */
  --hf-slate-100: #F3F1EB;  /* Table zebra striping, inactive card borders */
  --hf-slate-200: #E5E2D9;  /* Card borders, divider rules, subtle separators */
  --hf-slate-300: #D0CCC1;  /* Input borders, disabled states */
  --hf-slate-400: #9E998D;  /* Placeholder text, table column sort indicators */
  --hf-slate-500: #6F6A60;  /* Secondary metadata, captions, helper text */
  --hf-slate-600: #514D45;  /* Secondary body text, table cells */
  --hf-slate-700: #38352F;  /* Primary readable body text */
  --hf-slate-800: #23211D;  /* Emphasized titles, subheadings */
  --hf-slate-900: #141310;  /* High-contrast headers, maximum readability */

  /* Semantic Status Palette (Accessible on warm canvas) */
  --hf-status-success-bg:     #E8F5EE;
  --hf-status-success-text:   #0E5A33;
  --hf-status-success-border: #9DD8B3;

  --hf-status-warning-bg:     #FEF7E8;
  --hf-status-warning-text:   #82530A;
  --hf-status-warning-border: #F7D186;

  --hf-status-danger-bg:      #FDF0F0;
  --hf-status-danger-text:    #911B1B;
  --hf-status-danger-border:  #F4A7A7;

  --hf-status-info-bg:        #EBF3FA;
  --hf-status-info-text:      #124B78;
  --hf-status-info-border:    #A3CBED;

  --hf-status-neutral-bg:     #F0EDE6;
  --hf-status-neutral-text:   #4A4740;
  --hf-status-neutral-border: #D5D1C7;
}
```

### 1.2 Dark Mode Strategy & Palette
HireFlow employs an **institutional light-first default** optimized for daytime office operations, audit environments, and high-density tabular scanning. A high-contrast dark theme is supported via `data-theme="dark"`:

```css
[data-theme="dark"] {
  --hf-slate-50:  #0F1923;  /* Deepest navy-black background */
  --hf-slate-100: #15222E;  /* Card surface fill */
  --hf-slate-200: #203345;  /* Subtle borders */
  --hf-slate-300: #2F4960;  /* Input borders, dividers */
  --hf-slate-400: #6B88A3;  /* Muted metadata */
  --hf-slate-500: #93B0CB;  /* Secondary labels */
  --hf-slate-600: #B8D1E6;  /* Secondary text */
  --hf-slate-700: #D8E7F3;  /* Body text */
  --hf-slate-800: #EDF4FA;  /* Titles */
  --hf-slate-900: #FFFFFF;  /* Maximum contrast headers */

  --hf-color-warm-ivory: #0F1923;
  --hf-color-academic-teal: #3AB4B1;
  --hf-color-muted-brass: #D4B67D;
}
```

---

## 2. Typography System

### 2.1 Font Pairings & Licenses
- **Display / Editorial Headings**: `Newsreader` or `Literata` (SIL Open Font License)
  - Conveys academic heritage, official authority, and archival gravitas.
- **UI / Body / Tables**: `Plus Jakarta Sans` or `Inter` (SIL Open Font License)
  - Geometric clarity, large x-height, extensive tabular figure support (`font-feature-settings: "tnum"`).
- **Monospace (Tokens, IDs, Timestamps)**: `JetBrains Mono` (Apache 2.0)
  - Clear distinction of session IDs (`SES-...`), preview tokens (`PREV-...`), and ISO timestamps.

### 2.2 Type Scale
| Token | Size | Line Height | Weight | Usage |
| :--- | :--- | :--- | :--- | :--- |
| `--hf-text-2xs` | 11px (0.6875rem) | 16px | 500 / 600 | Badges, table column headers, uppercase labels |
| `--hf-text-xs`  | 12px (0.75rem)   | 18px | 400 / 500 | Metadata, helper text, footnote captions |
| `--hf-text-sm`  | 13px (0.8125rem) | 20px | 400 / 500 | Dense table cells, form inputs, navigation items |
| `--hf-text-base`| 14px (0.875rem)  | 22px | 400 / 500 | Standard body copy, modal text, descriptions |
| `--hf-text-md`  | 16px (1.000rem)  | 24px | 500 / 600 | Card titles, section subheadings, button labels |
| `--hf-text-lg`  | 18px (1.125rem)  | 26px | 600       | Panel headers, drawer titles |
| `--hf-text-xl`  | 22px (1.375rem)  | 30px | 600       | Page headers, primary dashboard titles |
| `--hf-text-2xl` | 28px (1.750rem)  | 36px | 700 (Serif)| Hero banners, portal welcome headlines |

---

## 3. Spatial & Elevation System

### 3.1 Spacing Scale (4px Base Grid)
- `4px` (`--hf-space-1`): Icon gaps, tight padding
- `8px` (`--hf-space-2`): Badge padding, input vertical padding
- `12px` (`--hf-space-3`): Card internal item gap, table cell vertical padding
- `16px` (`--hf-space-4`): Standard element spacing, card padding
- `20px` (`--hf-space-5`): Section margins, toolbar spacing
- `24px` (`--hf-space-6`): Modal container padding, grid column gaps
- `32px` (`--hf-space-8`): Page container gutter, major block margins
- `48px` (`--hf-space-12`): Page section divider spacing

### 3.2 Border & Radius Rules
- Card & container radius: `6px` (`--hf-radius-md`) — Crisp, authoritative, non-toyish.
- Buttons & form inputs: `4px` (`--hf-radius-sm`) — Tactile and utilitarian.
- Badges & pills: `9999px` (`--hf-radius-full`) — Unmistakable status classification.
- Border widths: Uniform `1px solid var(--hf-slate-200)`.

### 3.3 Elevation & Shadows (Diffused Institutional Depth)
- **Flat Surface**: `border: 1px solid var(--hf-slate-200); background: #FFFFFF;`
- **Subtle Elevation (`--hf-shadow-sm`)**: `0 1px 2px 0 rgba(23, 50, 75, 0.05)`
- **Interactive Card Hover (`--hf-shadow-md`)**: `0 4px 6px -1px rgba(23, 50, 75, 0.08), 0 2px 4px -2px rgba(23, 50, 75, 0.04)`
- **Modal / Popover (`--hf-shadow-lg`)**: `0 10px 15px -3px rgba(23, 50, 75, 0.10), 0 4px 6px -4px rgba(23, 50, 75, 0.05)`

---

## 4. Interaction, Focus & Accessibility Tokens

- **Focus Ring**: `outline: 2px solid var(--hf-color-academic-teal); outline-offset: 2px;`
- **Transition**: `transition: background-color 150ms ease, border-color 150ms ease, box-shadow 150ms ease;`
- **Forbidden Elements**:
  - NO purple-pink AI gradients.
  - NO frosted blur glassmorphism over illegible backgrounds.
  - NO decorative glowing card borders.
  - NO unlabelled icon-only primary actions.
  - NO meaningless decorative wave shapes or stock graphics.
