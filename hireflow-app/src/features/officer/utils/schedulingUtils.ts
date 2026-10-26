/**
 * Authoritative temporal and presentation utilities for HireFlow Interview Scheduling Studio.
 * 
 * CRITICAL TEMPORAL RULES:
 * 1. Appointment start/end values are Java LocalDateTime (offset-free ISO strings, e.g. 2026-10-22T09:00:00).
 *    They represent campus wall-clock times. They MUST NOT be converted or parsed using `new Date(...)`
 *    which causes browser timezone shifting.
 * 2. `expiresAt` is a Java Instant (UTC timestamp, e.g. 2026-10-19T17:30:00Z).
 *    It HAS timezone semantics and MUST be parsed against UTC epoch for the TTL countdown.
 * 3. Raw previewId tokens must be masked whenever rendered in user-facing components.
 */

export interface ParsedCampusTime {
  datePart: string // "2026-10-22"
  timePart: string // "09:00"
  hours: number    // 9
  minutes: number  // 0
  year: number     // 2026
  month: number    // 10
  day: number      // 22
  formattedTime: string // "09:00 AM"
  formattedDate: string // "Oct 22, 2026"
}

/**
 * Extracts campus wall-clock components without browser timezone offset alteration.
 */
export function parseCampusLocalDateTime(isoString: string): ParsedCampusTime {
  if (!isoString || !isoString.includes('T')) {
    return {
      datePart: '',
      timePart: '',
      hours: 0,
      minutes: 0,
      year: 0,
      month: 0,
      day: 0,
      formattedTime: 'N/A',
      formattedDate: 'N/A',
    }
  }

  const [datePart, timeWithOptionalSec] = isoString.split('T')
  const timePart = timeWithOptionalSec.slice(0, 5) // "HH:mm"
  const [hStr, mStr] = timePart.split(':')
  const hours = parseInt(hStr, 10) || 0
  const minutes = parseInt(mStr, 10) || 0

  const period = hours >= 12 ? 'PM' : 'AM'
  const displayHours = hours % 12 === 0 ? 12 : hours % 12
  const paddedMinutes = minutes.toString().padStart(2, '0')
  const formattedTime = `${displayHours}:${paddedMinutes} ${period}`

  // Parse date components safely
  let year = 0
  let month = 0
  let day = 0
  let formattedDate = datePart

  try {
    const parts = datePart.split('-').map(Number)
    year = parts[0] || 0
    month = parts[1] || 0
    day = parts[2] || 0

    const monthNames = [
      'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
      'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec',
    ]
    if (month >= 1 && month <= 12) {
      formattedDate = `${monthNames[month - 1]} ${day}, ${year}`
    }
  } catch {
    formattedDate = datePart
  }

  return {
    datePart,
    timePart,
    hours,
    minutes,
    year,
    month,
    day,
    formattedTime,
    formattedDate,
  }
}

/**
 * Formats a LocalTime string "HH:mm" to 12-hour display.
 */
export function formatLocalTime(timeStr: string): string {
  if (!timeStr) return ''
  const [hStr, mStr] = timeStr.split(':')
  const hours = parseInt(hStr, 10) || 0
  const minutes = parseInt(mStr, 10) || 0
  const period = hours >= 12 ? 'PM' : 'AM'
  const displayHours = hours % 12 === 0 ? 12 : hours % 12
  const paddedMinutes = minutes.toString().padStart(2, '0')
  return `${displayHours}:${paddedMinutes} ${period}`
}

/**
 * Formats a campus appointment interval: e.g. "09:00 – 09:45"
 */
export function formatCampusAppointmentTime(startIso: string, endIso: string): string {
  const startTimePart = startIso.includes('T') ? startIso.split('T')[1].slice(0, 5) : startIso.slice(0, 5)
  const endTimePart = endIso.includes('T') ? endIso.split('T')[1].slice(0, 5) : endIso.slice(0, 5)
  return `${startTimePart} – ${endTimePart}`
}

/**
 * Formats a campus appointment interval with 12-hour display: e.g. "09:00 AM – 09:45 AM"
 */
export function formatAppointmentInterval(startIso: string, endIso: string): string {
  const start = parseCampusLocalDateTime(startIso)
  const end = parseCampusLocalDateTime(endIso)
  return `${start.formattedTime} – ${end.formattedTime}`
}

/**
 * Parses an Instant string with strict UTC timezone semantics for countdown measurement.
 */
export function parseUtcInstant(instantIso: string): Date {
  return new Date(instantIso)
}

/**
 * Calculates remaining TTL seconds from an Instant string against a reference date.
 */
export function calculateRemainingTtlSeconds(expiresAtIso: string, now: Date = new Date()): number {
  const target = new Date(expiresAtIso).getTime()
  const current = now.getTime()
  const diffSec = Math.floor((target - current) / 1000)
  return diffSec > 0 ? diffSec : 0
}

/**
 * Formats remaining seconds (or milliseconds if > 100000) into mm:ss countdown.
 */
export function formatTtlCountdown(remainingValue: number): string {
  if (remainingValue <= 0) return '00:00'
  // If passed milliseconds (e.g. > 100,000), convert to seconds
  const totalSeconds = remainingValue > 100000 ? Math.floor(remainingValue / 1000) : Math.floor(remainingValue)
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`
}

/**
 * Masks raw preview token following the backend standard (prev-3fa8...afa6 or prev-••••••••).
 */
export function maskPreviewToken(token?: string | null): string {
  if (!token || token.trim().length < 8) {
    return 'prev-••••••••'
  }
  const clean = token.trim()
  if (clean.length <= 12) {
    return `prev-${clean.substring(0, 4)}...${clean.substring(clean.length - 4)}`
  }
  return `prev-${clean.substring(0, 4)}...${clean.substring(clean.length - 4)}`
}
