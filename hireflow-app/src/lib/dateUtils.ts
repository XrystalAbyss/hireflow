/**
 * Utilities for handling Java LocalDateTime and LocalDate representations.
 *
 * Spring Boot's default Jackson serialization produces offset-free ISO-8601 strings
 * for java.time.LocalDateTime (e.g. "2026-10-19T01:30:00").
 *
 * Direct parsing via standard `new Date("YYYY-MM-DDTHH:mm:ss")` or component decomposition
 * guarantees deterministic local wall-clock presentation without unexpected UTC offsets.
 */

/**
 * Parses an offset-free LocalDateTime string into its constituent parts
 * and returns a Date instance representing the exact calendar date and time.
 */
export function parseLocalDateTime(isoStr: string): Date | null {
  if (!isoStr || typeof isoStr !== 'string') return null

  const match = isoStr.match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}(?:\.\d+)?))?$/)
  if (!match) {
    const fallback = new Date(isoStr)
    return isNaN(fallback.getTime()) ? null : fallback
  }

  const [, year, month, day, hours, minutes, secondsStr = '00'] = match
  const seconds = Math.floor(parseFloat(secondsStr))
  const millis = Math.round((parseFloat(secondsStr) - seconds) * 1000)

  return new Date(
    Number(year),
    Number(month) - 1,
    Number(day),
    Number(hours),
    Number(minutes),
    seconds,
    millis
  )
}

/**
 * Formats an offset-free LocalDateTime string for human display.
 * Default format: "Oct 19, 2026, 1:30 AM"
 */
export function formatLocalDateTime(
  isoStr: string,
  options?: Intl.DateTimeFormatOptions
): string {
  if (!isoStr) return ''

  const date = parseLocalDateTime(isoStr)
  if (!date) return isoStr

  const defaultOptions: Intl.DateTimeFormatOptions = {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
    hour12: true,
  }

  return new Intl.DateTimeFormat('en-US', options ?? defaultOptions).format(date)
}

/**
 * Formats an offset-free LocalDateTime string for date-only display.
 * E.g., "Oct 19, 2026"
 */
export function formatLocalDateOnly(isoStr: string): string {
  return formatLocalDateTime(isoStr, {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  })
}

/**
 * Returns today's calendar date as "YYYY-MM-DD" in the operator's local timezone.
 * Unlike `new Date().toISOString().slice(0, 10)`, this never reports yesterday
 * for operators located ahead of UTC during the early hours of the day.
 */
export function localToday(): string {
  const now = new Date()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${now.getFullYear()}-${month}-${day}`
}
