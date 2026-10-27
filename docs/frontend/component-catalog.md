# HireFlow Component Catalog (CAP-8.1 Baseline)

This document provides a technical inventory of all reusable components implemented in `hireflow-app/src/components/`. All components strictly conform to the **Institutional × Contemporary** visual identity, WCAG 2.2 AA accessibility guidelines, and consume semantic design tokens.

---

## 1. Foundational UI Components (17 Primitives)

| Component | Source Path | Primitives / Dependencies | Accessibility & Variants |
| :--- | :--- | :--- | :--- |
| **Button** | `src/components/ui/button.tsx` | Radix Slot, Class Variance Authority | Variants: `default`, `accent`, `secondary`, `outline`, `ghost`, `destructive`, `link`. Supports `loading` with `Loader2` and `aria-busy`. |
| **Input** | `src/components/ui/input.tsx` | Native HTML Input | Accessible focus rings (`ring-2 ring-ring`). Distinct error borders on `aria-invalid`. |
| **PasswordField** | `src/components/ui/password-field.tsx` | Input, Lucide Eye/EyeOff | Accessible visibility toggle with `aria-label` ("Show password" / "Hide password"). |
| **Select** | `src/components/ui/select.tsx` | Radix UI Select | Accessible dropdown with arrow key navigation, focus trapping, and screen-reader value announcement. |
| **Textarea** | `src/components/ui/textarea.tsx` | Native HTML Textarea | Expandable multiline input with consistent institutional border tokens. |
| **Checkbox** | `src/components/ui/checkbox.tsx` | Radix UI Checkbox | Accessible checked, unchecked, and indeterminate states. |
| **FormField** | `src/components/ui/form-field.tsx` | Custom Wrapper | Associates `label` with `htmlFor`/`id`, renders `role="alert"` on errors, supports `description`/`helperText`. |
| **Dialog** | `src/components/ui/dialog.tsx` | Radix UI Dialog | Modal dialog with automatic focus trapping, `Escape` key dismissal, and backdrop overlay. |
| **AlertDialog** | `src/components/ui/alert-dialog.tsx` | Precomposed Dialog | Dedicated modal confirmation with destructive/accent variants and confirm/cancel actions. |
| **Popover** | `src/components/ui/popover.tsx` | Radix UI Popover | Floating popover with automatic positioning and viewport boundary collision detection. |
| **DropdownMenu** | `src/components/ui/dropdown-menu.tsx` | Radix UI Dropdown Menu | Full keyboard navigation (`ArrowUp`/`ArrowDown`, `Enter`), submenus, checkable items, and shortcuts. |
| **Tooltip** | `src/components/ui/tooltip.tsx` | Radix UI Tooltip | Hover/focus accessible tooltip with configurable delay. |
| **Tabs** | `src/components/ui/tabs.tsx` | Radix UI Tabs | Accessible tab navigation with `ArrowLeft`/`ArrowRight` keyboard traversal. |
| **Badge** | `src/components/ui/badge.tsx` | Class Variance Authority | Institutional badges (`default`, `accent`, `secondary`, `outline`, `applied`, `shortlisted`, `confirmed`, `rejected`). |
| **Separator** | `src/components/ui/separator.tsx` | Radix UI Separator | Horizontal and vertical dividers with semantic `role="none"` or `role="separator"`. |
| **Breadcrumbs** | `src/components/ui/breadcrumbs.tsx` | Custom Nav | Structured navigation breadcrumbs with `aria-label="Breadcrumb"` and chevron separators. |
| **Pagination** | `src/components/ui/pagination.tsx` | Custom Controls | Accessible page numbers, Previous/Next buttons, and current page `aria-current="page"`. |

---

## 2. Domain-Specific Components (11 Components)

| Component | Source Path | Description & Purpose |
| :--- | :--- | :--- |
| **ApplicationStatusBadge** | `src/components/domain/application-status-badge.tsx` | Renders color-independent badges for the 6 candidate statuses (`APPLIED`, `ELIGIBLE`, `SHORTLISTED`, `SCHEDULED`, `SELECTED`, `REJECTED`). Combines distinct colors, borders, and icons. |
| **EligibilityStatusBadge** | `src/components/domain/eligibility-status-badge.tsx` | Deterministic eligibility badge (`ELIGIBLE`, `INELIGIBLE`, `APPLIED`, `CLOSED`) or derived from 5 boolean eligibility flags. |
| **PageHeading** | `src/components/domain/page-heading.tsx` | Editorial page header featuring Newsreader serif typography, breadcrumb integration, and action buttons. |
| **SectionHeading** | `src/components/domain/section-heading.tsx` | Secondary section header with divider line, optional badges, and contextual action buttons. |
| **DataTable** | `src/components/domain/data-table.tsx` | High-density TanStack Table v8 implementation with column sorting, skeleton loading state, and empty fallbacks. |
| **TableToolbar** | `src/components/domain/table-toolbar.tsx` | Table action toolbar with search input, clear button, and slot for filter dropdowns and batch actions. |
| **EmptyState** | `src/components/domain/empty-state.tsx` | Dignified empty state card with icon, title, description, and recovery action button. |
| **ErrorState** | `src/components/domain/error-state.tsx` | Accessible error state card with `AlertTriangle` icon, descriptive error message, and retry button. |
| **LoadingSkeleton** | `src/components/domain/loading-skeleton.tsx` | Animated shimmer skeleton rows for tables and data containers. |
| **ConfirmationDialog** | `src/components/domain/confirmation-dialog.tsx` | Modal confirmation dialog used for irreversible operations (e.g. candidate shortlisting). |
| **NoticeBanner** | `src/components/domain/notice-banner.tsx` | Accessible operational banner (`role="status"`) supporting `info`, `success`, `warning`, and `error` variants. |

---

## 3. Application Shells & Layouts (3 Layouts)

| Shell | Source Path | Operational Characteristics |
| :--- | :--- | :--- |
| **AuthenticationLayout** | `src/components/shells/AuthenticationLayout.tsx` | Academic authentication container featuring institutional navy header bar, centered card layout, and university placement footer. |
| **StudentShell** | `src/components/shells/StudentShell.tsx` | Comfortable reading density, mobile hamburger drawer, authoritative read-only academic dossier header (Roll No, Branch, verified CGPA), and sign out action. |
| **OfficerShell** | `src/components/shells/OfficerShell.tsx` | High-information operational layout, quick navigation to Corporate Partners, Drives, Applications, and Scheduling Studio, and system status footer. |
