import type { ApplicationStatus } from '@/types/api'

/**
 * Presentation-layer display helpers.
 *
 * Strictly read-only transformations:
 * - Never mutate source API objects.
 * - Never alter canonical IDs used for routing, API requests, or filtering.
 * - Remove only exact known fixture suffixes/prefixes without stripping
 *   legitimate occurrences of words like "demo" or "synthetic".
 */

const DEMO_SUFFIX_RE = /\s*\[Demo\]$/
const SYNTHETIC_COMPANY_SUFFIX_RE = /\s*\[Synthetic\]$/
const SYNTHETIC_PANEL_SUFFIX_RE = /\s*\[Synthetic(?:\s+Panel)?\]$/
const FIXTURE_REMARK_PREFIX_RE = /^\[NBA261128\]\s*/i

export function formatStudentDisplayName(name?: string | null): string {
  if (!name) return ''
  return name.replace(DEMO_SUFFIX_RE, '').trim()
}

export function formatCompanyDisplayName(name?: string | null): string {
  if (!name) return ''
  return name.replace(SYNTHETIC_COMPANY_SUFFIX_RE, '').trim()
}

export function formatInterviewerDisplayName(name?: string | null): string {
  if (!name) return ''
  return name.replace(SYNTHETIC_PANEL_SUFFIX_RE, '').trim()
}

export function isPlaceholderEmail(email?: string | null): boolean {
  if (!email) return false
  return email.trim().toLowerCase().endsWith('@example.test')
}

export interface ContactDisplayInfo {
  display: string
  isPlaceholder: boolean
  canContact: boolean
}

export function formatContactEmail(email?: string | null): ContactDisplayInfo {
  if (!email || !email.trim()) {
    return {
      display: 'Contact unavailable',
      isPlaceholder: false,
      canContact: false,
    }
  }
  const trimmed = email.trim()
  if (isPlaceholderEmail(trimmed)) {
    return {
      display: 'Institutional contact unavailable',
      isPlaceholder: true,
      canContact: false,
    }
  }
  return {
    display: trimmed,
    isPlaceholder: false,
    canContact: true,
  }
}

export function hasInternalFixtureMarker(text?: string | null): boolean {
  if (!text) return false
  return FIXTURE_REMARK_PREFIX_RE.test(text.trim()) || /\bSynthetic\b/i.test(text)
}

export interface EligibilityExplanationInput {
  status: ApplicationStatus
  remarks?: string | null
  cgpa?: number
  minimumCgpa?: number
  branch?: string
  eligibleBranches?: string[]
  driveClosed?: boolean
  deadlinePassed?: boolean
}

function getNeutralStatusExplanation(status: ApplicationStatus): string {
  switch (status) {
    case 'APPLIED':
      return 'Application submitted and awaiting screening review.'
    case 'ELIGIBLE':
      return 'Meets academic eligibility criteria and is ready for shortlisting review.'
    case 'SHORTLISTED':
      return 'Shortlisted for interview scheduling.'
    case 'SCHEDULED':
      return 'Confirmed for an interview round.'
    case 'SELECTED':
      return 'Selected following interview evaluation.'
    case 'REJECTED':
      return 'Not selected to advance after screening or interview evaluation.'
    case 'INELIGIBLE':
      return 'The candidate does not meet the eligibility criteria for this recruitment drive.'
    default:
      return 'Application status recorded in the placement portal.'
  }
}

/**
 * Maps known seeded fixture remarks and domain criteria into concise,
 * human-readable explanations without altering stored remarks or statuses.
 */
export function formatEligibilityExplanation({
  status,
  remarks,
  cgpa,
  minimumCgpa,
  branch,
  eligibleBranches,
  driveClosed,
  deadlinePassed,
}: EligibilityExplanationInput): string {
  const raw = remarks?.trim() || ''
  const lower = raw.toLowerCase()

  // 1. Explicit inactive student profile
  if (lower.includes('inactive student') || lower.includes('marked inactive')) {
    return "The student's profile is currently inactive."
  }

  // 2. Closed drive / deadline passed
  if (driveClosed || lower.includes('closed drive') || lower.includes('drive is closed')) {
    return 'Applications are closed for this recruitment drive.'
  }
  if (deadlinePassed || lower.includes('deadline has passed')) {
    return 'The application deadline has passed.'
  }

  // 3. Branch or CGPA threshold failures
  if (
    typeof cgpa === 'number' &&
    typeof minimumCgpa === 'number' &&
    cgpa < minimumCgpa
  ) {
    return 'The candidate does not meet the minimum CGPA requirement.'
  }

  if (
    branch &&
    Array.isArray(eligibleBranches) &&
    eligibleBranches.length > 0 &&
    !eligibleBranches.some((b) => b.trim().toLowerCase() === branch.trim().toLowerCase())
  ) {
    return "The candidate's branch is not eligible for this recruitment drive."
  }

  if (lower.includes('cgpa threshold') || lower.includes('minimum cgpa')) {
    return 'The candidate does not meet the minimum CGPA requirement.'
  }

  if (lower.includes('branch or cgpa does not meet')) {
    return 'The candidate does not meet the academic CGPA or branch criteria for this recruitment drive.'
  }

  // 4. If remarks do not contain internal fixture markers, preserve legitimate officer/system notes
  if (raw && !hasInternalFixtureMarker(raw)) {
    return raw
  }

  // 5. Known synthetic fixture remarks or unknown seeded remarks -> neutral status explanation
  return getNeutralStatusExplanation(status)
}
