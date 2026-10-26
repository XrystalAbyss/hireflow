# HireFlow Institutional Design System (CAP-8.1 Baseline)

## 1. Visual Philosophy: Institutional × Contemporary

HireFlow's visual identity reflects the authority, rigor, and scholarly prestige of a collegiate career administration platform. It balances scholarly dignity with the responsive speed and precision of a modern web application.

### Anti-Generic Visual Governance
HireFlow rejects typical SaaS design tropes:
- **No Neon Gradients**: Avoid vibrant purple, violet, or cyan AI gradients.
- **No Glassmorphism**: Avoid blurry, translucent, frosted-glass surfaces that degrade legibility and contrast.
- **No Toy-Like Radii**: Rounded corners are restrained (`rounded-sm`: 2px, `rounded-md`: 6px). No pill buttons or excessive border radiuses.
- **No Vanity Dials or Empty Charts**: Every visual element corresponds to real backend operational data.
- **No Fictional University Crests**: Authentic typographical identity replaces artificial heraldry or fake partner logos.

---

## 2. Palette & Semantic Color Tokens

### 2.1 Foundational Brand Palette
All colors are managed as HSL custom properties in `src/styles/tokens.css` and mapped into Tailwind CSS via `@theme` in `src/index.css`.

| Name | Hex Code | HSL Representation | Semantic Application |
| :--- | :--- | :--- | :--- |
| **Midnight Navy** | `#17324B` | `hsl(208, 53%, 19%)` | Header bars, primary brand elements, prominent buttons |
| **Academic Teal** | `#176B69` | `hsl(179, 64%, 26%)` | Active nav items, scholarly accents, interactive highlights |
| **Warm Ivory** | `#F6F4EE` | `hsl(45, 27%, 95%)` | Base page canvas, warm card surfaces, academic backdrop |
| **Muted Brass** | `#C6A46B` | `hsl(38, 45%, 60%)` | Accents, institutional badges, pending scheduling alerts |

### 2.2 Surface & Structural Tokens
- `--hf-surface-page`: `hsl(45, 27%, 95%)` (Warm Ivory canvas)
- `--hf-surface-card`: `hsl(0, 0%, 100%)` (Clean white paper surface)
- `--hf-surface-muted`: `hsl(45, 15%, 91%)` (Muted table headers and toolbars)
- `--hf-border-subtle`: `hsl(45, 12%, 84%)` (Subtle card and divider borders)
- `--hf-border-strong`: `hsl(208, 30%, 35%)` (Active input and focus boundaries)

### 2.3 Semantic Status Machine Tokens
Every operational status is distinguished by color, border, and text label to ensure color-independent recognition:

| Status | Background Token | Border Token | Text Token | Icon |
| :--- | :--- | :--- | :--- | :--- |
| **APPLIED** | `hsl(208, 40%, 94%)` | `hsl(208, 45%, 80%)` | `hsl(208, 65%, 25%)` | Clock |
| **ELIGIBLE** | `hsl(179, 45%, 93%)` | `hsl(179, 50%, 75%)` | `hsl(179, 64%, 22%)` | Check |
| **SHORTLISTED** | `hsl(38, 65%, 93%)` | `hsl(38, 55%, 72%)` | `hsl(38, 70%, 25%)` | UserCheck |
| **SCHEDULED** | `hsl(215, 55%, 93%)` | `hsl(215, 50%, 78%)` | `hsl(215, 65%, 28%)` | Calendar |
| **SELECTED** | `hsl(152, 45%, 92%)` | `hsl(152, 45%, 72%)` | `hsl(152, 65%, 22%)` | Award |
| **REJECTED** | `hsl(0, 50%, 94%)` | `hsl(0, 45%, 80%)` | `hsl(0, 60%, 32%)` | XCircle |

---

## 3. Typography Pairings & Hierarchy

HireFlow utilizes an academic font pairing hosted via Google Fonts:

1. **Page Headings & Section Titles**: `Newsreader` (Editorial Serif, 600/700 weight). Conveys academic authority and dignified craftsmanship.
2. **Body Text, Controls & Dense Tables**: `Inter` (Sans-Serif, 400/500/600 weight). Provides exceptional micro-legibility at small administrative sizes.
3. **Identifiers, Codes & Numbers**: `JetBrains Mono` (Monospace, 500 weight). Tabular numbers, roll numbers, and session preview IDs.

### Typography Scale
- **Display 1 (H1)**: `text-3xl font-serif font-bold` (30px / 36px line-height)
- **Display 2 (H2)**: `text-xl font-serif font-semibold` (20px / 28px line-height)
- **Display 3 (H3)**: `text-base font-serif font-medium` (16px / 24px line-height)
- **Body Large**: `text-base font-sans` (16px / 24px line-height)
- **Body Standard**: `text-sm font-sans` (14px / 20px line-height)
- **Metadata / Dense Table**: `text-xs font-sans` (12px / 16px line-height)
- **Monospace Code**: `text-xs font-mono font-medium` (12px tabular figures)

---

## 4. Spacing, Density & Grid Layouts

- **Administrative Table Rows**: Compact height (36px to 40px) with subtle borders and clear column alignments (numbers right-aligned, text left-aligned).
- **Responsive Layout Breakpoints**:
  - `sm`: 640px (Mobile landscape / small tablet)
  - `md`: 768px (Tablet portrait)
  - `lg`: 1024px (Laptop)
  - `xl`: 1280px (Desktop)
  - `2xl`: 1440px+ (Wide desktop container boundary)
- **Reduced Motion Support**:
  - All transitions and CSS animations respect `@media (prefers-reduced-motion: reduce)`.
