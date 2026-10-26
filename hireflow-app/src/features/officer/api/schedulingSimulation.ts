/**
 * Deterministic In-Memory Simulation Engine for Interview Scheduling Studio (Dev Opt-In Only).
 * 
 * STRICT ARCHITECTURAL CONSTRAINTS:
 * 1. Simulation requires explicit developer opt-in. Failed real requests NEVER silently fall back here.
 * 2. In production builds (import.meta.env.MODE === 'production'), simulation is completely disabled.
 * 3. Simulated previews and publications are explicitly and visibly labeled.
 * 4. Zero claims of PostgreSQL persistence: all results are marked as volatile local simulations.
 */

import type {
  OfficerSchedulingPreviewRequestDto,
  OfficerSchedulingPreviewResponseDto,
  OfficerPublicationSummaryDto,
  OfficerInterviewDetailDto,
  ProposedSessionDto,
  UnscheduledCandidateDto,
  PublishedSessionDetailDto,
  PublicationConflictDetailDto,
  PagedResponse,
} from '@/types/api'
import {
  FIXTURE_OFFICER_DRIVES,
  FIXTURE_OFFICER_APPLICATIONS,
  FIXTURE_OFFICER_INTERVIEWERS,
  FIXTURE_OFFICER_ROOMS,
} from '../fixtures'
import { ApiError } from '@/core/api/client'

// In-memory simulation state (persists across page transitions within session)
let simulationEnabled = false
let simulatedPreviewCache: Map<string, OfficerSchedulingPreviewResponseDto> = new Map()
let simulatedPublishedSessions: OfficerInterviewDetailDto[] = [
  {
    sessionId: 'SES-SIM-001',
    applicationId: 'APP-2026-001',
    studentId: 'STU-2022-0144',
    studentName: 'Aarav Sharma',
    studentBranch: 'Computer Science & Engineering',
    driveId: 'DRV-2026-001',
    jobTitle: 'Software Development Engineer I',
    companyId: 'COMP-001',
    companyName: 'Google India',
    interviewerId: 'INTV-001',
    interviewerName: 'Dr. Sarah Jenkins',
    roomId: 'RM-101',
    roomName: 'Interview Suite A (Oak Room)',
    startTime: '2026-11-16T09:30:00',
    endTime: '2026-11-16T10:15:00',
    status: 'SCHEDULED',
  },
  {
    sessionId: 'SES-SIM-002',
    applicationId: 'APP-2026-003',
    studentId: 'STU-2022-0210',
    studentName: 'Rohan Mehta',
    studentBranch: 'Information Technology',
    driveId: 'DRV-2026-001',
    jobTitle: 'Software Development Engineer I',
    companyId: 'COMP-001',
    companyName: 'Google India',
    interviewerId: 'INTV-002',
    interviewerName: 'David Chen',
    roomId: 'RM-102',
    roomName: 'Interview Suite B (Pine Room)',
    startTime: '2026-11-16T10:30:00',
    endTime: '2026-11-16T11:15:00',
    status: 'SCHEDULED',
  },
]
let simulatedConsumedTokens: Set<string> = new Set()

export type SimulationScenario =
  | 'ALL_CONFIRMED'
  | 'PARTIAL_SUCCESS'
  | 'NONE_CONFIRMED'
  | 'CONSUMED_REPLAY'
  | 'EXPIRED'
  | 'NOT_FOUND'
  | 'DATABASE_ERROR'

let currentSimulationScenario: SimulationScenario = 'ALL_CONFIRMED'

export function isSimulationModeActive(): boolean {
  if (import.meta.env.PROD || import.meta.env.VITE_HIREFLOW_MODE === 'live') return false
  return simulationEnabled
}

export function setSimulationModeActive(enabled: boolean): void {
  if (import.meta.env.PROD || import.meta.env.VITE_HIREFLOW_MODE === 'live') {
    simulationEnabled = false
    return
  }
  simulationEnabled = enabled
}

export function setSimulationScenario(scenario: SimulationScenario): void {
  currentSimulationScenario = scenario
}

export function getSimulationScenario(): SimulationScenario {
  return currentSimulationScenario
}

/**
 * Deterministically generates an advisory preview proposal mimicking GreedyScheduler.
 */
export function simulateSchedulingPreview(
  req: OfficerSchedulingPreviewRequestDto
): OfficerSchedulingPreviewResponseDto {
  const drive = FIXTURE_OFFICER_DRIVES.find((d) => d.id === req.driveId)
  const jobTitle = drive ? drive.jobTitle : 'Campus Recruitment Interview'
  const companyName = drive ? drive.companyName : 'Corporate Partner'

  const duration = req.durationMinutes ?? 45
  const buffer = req.bufferMinutes ?? 15

  // Filter candidate applications for drive in SHORTLISTED status
  const candidates = FIXTURE_OFFICER_APPLICATIONS.filter(
    (a) => a.driveId === req.driveId && a.status === 'SHORTLISTED'
  )

  // Resolve interviewers
  let availableInterviewers = FIXTURE_OFFICER_INTERVIEWERS
  if (req.interviewerIds && req.interviewerIds.length > 0) {
    availableInterviewers = FIXTURE_OFFICER_INTERVIEWERS.filter((i) =>
      req.interviewerIds!.includes(i.id)
    )
  } else if (drive) {
    const compInts = FIXTURE_OFFICER_INTERVIEWERS.filter((i) => i.companyId === drive.companyId)
    if (compInts.length > 0) availableInterviewers = compInts
  }

  // Resolve rooms
  let availableRooms = FIXTURE_OFFICER_ROOMS
  if (req.roomIds && req.roomIds.length > 0) {
    availableRooms = FIXTURE_OFFICER_ROOMS.filter((r) => req.roomIds!.includes(r.id))
  }

  const [startH, startM] = req.startTime.split(':').map(Number)
  const [endH, endM] = req.endTime.split(':').map(Number)
  const windowStartMinutes = startH * 60 + startM
  const windowEndMinutes = endH * 60 + endM

  const slotIncrement = duration + buffer

  const proposedSessions: ProposedSessionDto[] = []
  const unscheduledCandidates: UnscheduledCandidateDto[] = []

  let currentSlotMinute = windowStartMinutes
  let sessionIndex = 1

  candidates.forEach((cand, idx) => {
    const slotEndMinute = currentSlotMinute + duration

    // Check if slot fits within window and resources exist
    if (
      slotEndMinute <= windowEndMinutes &&
      availableInterviewers.length > 0 &&
      availableRooms.length > 0
    ) {
      const interviewer = availableInterviewers[idx % availableInterviewers.length]
      const room = availableRooms[idx % availableRooms.length]

      const startHour = Math.floor(currentSlotMinute / 60)
      const startMin = currentSlotMinute % 60
      const endHour = Math.floor(slotEndMinute / 60)
      const endMin = slotEndMinute % 60

      const pad = (n: number) => n.toString().padStart(2, '0')
      const startTimeIso = `${req.date}T${pad(startHour)}:${pad(startMin)}:00`
      const endTimeIso = `${req.date}T${pad(endHour)}:${pad(endMin)}:00`

      proposedSessions.push({
        sessionId: `SES-SIM-PROP-${pad(sessionIndex++)}`,
        applicationId: cand.id,
        studentId: cand.studentId,
        studentName: cand.studentName,
        studentBranch: cand.branch,
        interviewerId: interviewer.id,
        interviewerName: interviewer.name,
        roomId: room.id,
        roomName: room.name,
        startTime: startTimeIso,
        endTime: endTimeIso,
      })

      currentSlotMinute += slotIncrement
    } else {
      unscheduledCandidates.push({
        applicationId: cand.id,
        studentId: cand.studentId,
        studentName: cand.studentName,
        studentBranch: cand.branch,
        reason: 'No conflict-free slot, interviewer, or room available within the specified scheduling window.',
      })
    }
  })

  // If no candidates were shortlisted in fixtures, synthesize 1 unscheduled or empty
  if (candidates.length === 0) {
    unscheduledCandidates.push({
      applicationId: 'APP-SYNTH-001',
      studentId: 'STU-SYNTH-001',
      studentName: 'Simulated Candidate',
      studentBranch: 'Computer Science',
      reason: 'No candidate applications in SHORTLISTED status found for recruitment drive.',
    })
  }

  const previewId = `SIM-PREV-${Date.now().toString(36).toUpperCase()}`
  const expiresAt = new Date(Date.now() + 15 * 60 * 1000).toISOString()

  const previewDto: OfficerSchedulingPreviewResponseDto = {
    previewId,
    driveId: req.driveId,
    jobTitle,
    companyName,
    date: req.date,
    startTime: req.startTime,
    endTime: req.endTime,
    durationMinutes: duration,
    bufferMinutes: buffer,
    expiresAt,
    totalRequested: candidates.length || 1,
    totalScheduled: proposedSessions.length,
    totalUnscheduled: unscheduledCandidates.length,
    proposedSessions,
    unscheduledCandidates,
  }

  simulatedPreviewCache.set(previewId, previewDto)
  return previewDto
}

/**
 * Simulates timetable publication with explicit scenario modeling.
 */
export function simulatePublication(
  previewId: string
): { status: number; data: OfficerPublicationSummaryDto } {
  // Scenario: Token already consumed replay
  if (currentSimulationScenario === 'CONSUMED_REPLAY' || simulatedConsumedTokens.has(previewId)) {
    throw new ApiError({
      status: 409,
      error: 'Conflict',
      message: `Scheduling preview '${previewId}' has already been published. Replay prevented. Consult GET /api/v1/officer/scheduling/published to inspect confirmed appointments.`,
      path: '/api/v1/officer/scheduling/publish',
      timestamp: new Date().toISOString(),
    })
  }

  // Scenario: Expired preview (HTTP 410 Gone)
  if (currentSimulationScenario === 'EXPIRED') {
    throw new ApiError({
      status: 410,
      error: 'Gone',
      message: `Scheduling preview '${previewId}' has expired (15-minute validity). Please generate a fresh preview.`,
      path: '/api/v1/officer/scheduling/publish',
      timestamp: new Date().toISOString(),
    })
  }

  // Scenario: Not Found or Evicted preview (HTTP 404 Not Found from PreviewNotFoundException / Restart)
  if (currentSimulationScenario === 'NOT_FOUND') {
    throw new ApiError({
      status: 404,
      error: 'Not Found',
      message: `Scheduling preview '${previewId}' was not found or has been evicted from memory. Advisory previews do not survive backend restart.`,
      path: '/api/v1/officer/scheduling/publish',
      timestamp: new Date().toISOString(),
    })
  }

  // Scenario: PostgreSQL database infrastructure outage (HTTP 500)
  if (currentSimulationScenario === 'DATABASE_ERROR') {
    simulatedConsumedTokens.add(previewId)
    throw new ApiError({
      status: 500,
      error: 'Internal Server Error',
      message: 'PostgreSQL publication failure: Connection to backend database pool timed out during transactional session commit.',
      path: '/api/v1/officer/scheduling/publish',
      timestamp: new Date().toISOString(),
    })
  }

  const preview = simulatedPreviewCache.get(previewId)
  const driveId = preview ? preview.driveId : 'DRV-2026-001'
  const jobTitle = preview ? preview.jobTitle : 'Software Development Engineer I'
  const companyName = preview ? preview.companyName : 'Corporate Partner'
  const proposals = preview ? preview.proposedSessions : []

  // Mark token consumed
  simulatedConsumedTokens.add(previewId)

  const attemptId = `PUB-ATTEMPT-${Math.random().toString(36).substring(2, 10).toUpperCase()}`

  // Scenario: NONE_CONFIRMED (HTTP 409)
  if (currentSimulationScenario === 'NONE_CONFIRMED' || proposals.length === 0) {
    const conflictRejections: PublicationConflictDetailDto[] = proposals.map((p) => ({
      applicationId: p.applicationId,
      message: `Interviewer conflict: '${p.interviewerName}' is already committed to an overlapping appointment.`,
      category: 'INTERVIEWER_CONFLICT',
    }))

    const summary: OfficerPublicationSummaryDto = {
      attemptId,
      previewId,
      previewReference: previewId.substring(0, 8) + '****',
      driveId,
      jobTitle,
      companyName,
      outcome: 'NONE_CONFIRMED',
      recoveryInstructions: `Zero appointments confirmed out of ${proposals.length} proposals. All proposals encountered resource conflicts or stale application states. No database mutations occurred. Please resolve resource conflicts and generate a fresh preview.`,
      publishedAt: new Date().toISOString(),
      totalProposals: proposals.length,
      publishedCount: 0,
      skippedDuplicatesCount: 0,
      conflictsCount: proposals.length,
      errorsCount: 0,
      unattemptedCount: 0,
      confirmedSessionIds: [],
      publishedSessions: [],
      skippedDuplicateApplicationIds: [],
      conflictRejections,
      infrastructureErrors: [],
    }

    return { status: 409, data: summary }
  }

  // Scenario: PARTIAL_SUCCESS (HTTP 207)
  if (currentSimulationScenario === 'PARTIAL_SUCCESS' && proposals.length > 1) {
    const confirmedCount = 1
    const p0 = proposals[0]
    const p1 = proposals[1]

    const publishedSession: PublishedSessionDetailDto = {
      sessionId: `SES-CONF-${Math.random().toString(36).substring(2, 8).toUpperCase()}`,
      applicationId: p0.applicationId,
      studentId: p0.studentId,
      studentName: p0.studentName,
      interviewerName: p0.interviewerName,
      roomName: p0.roomName,
      startTime: p0.startTime,
      endTime: p0.endTime,
    }

    const conflictRejection: PublicationConflictDetailDto = {
      applicationId: p1.applicationId,
      message: `Room conflict: Room '${p1.roomName}' was concurrently claimed by another department session.`,
      category: 'ROOM_CONFLICT',
    }

    // Add confirmed to published sessions
    simulatedPublishedSessions.unshift({
      sessionId: publishedSession.sessionId,
      applicationId: publishedSession.applicationId,
      studentId: publishedSession.studentId,
      studentName: publishedSession.studentName,
      studentBranch: p0.studentBranch,
      driveId,
      jobTitle,
      companyId: 'COMP-001',
      companyName,
      interviewerId: p0.interviewerId,
      interviewerName: p0.interviewerName,
      roomId: p0.roomId,
      roomName: p0.roomName,
      startTime: p0.startTime,
      endTime: p0.endTime,
      status: 'SCHEDULED',
    })

    const summary: OfficerPublicationSummaryDto = {
      attemptId,
      previewId,
      previewReference: previewId.substring(0, 8) + '****',
      driveId,
      jobTitle,
      companyName,
      outcome: 'PARTIAL_SUCCESS',
      recoveryInstructions: `Partial publication: ${confirmedCount} of ${proposals.length} appointments confirmed. 1 rejected due to conflicts. Confirmed sessions are durably committed in PostgreSQL. Please query GET /api/v1/officer/scheduling/published?driveId=${driveId} to inspect confirmed sessions, and generate a fresh preview for remaining unconfirmed candidates.`,
      publishedAt: new Date().toISOString(),
      totalProposals: proposals.length,
      publishedCount: confirmedCount,
      skippedDuplicatesCount: 0,
      conflictsCount: 1,
      errorsCount: 0,
      unattemptedCount: Math.max(0, proposals.length - 2),
      confirmedSessionIds: [publishedSession.sessionId],
      publishedSessions: [publishedSession],
      skippedDuplicateApplicationIds: [],
      conflictRejections: [conflictRejection],
      infrastructureErrors: [],
    }

    return { status: 207, data: summary }
  }

  // Default: ALL_CONFIRMED (HTTP 200)
  const publishedSessions: PublishedSessionDetailDto[] = proposals.map((p) => {
    const sessId = `SES-CONF-${Math.random().toString(36).substring(2, 8).toUpperCase()}`
    simulatedPublishedSessions.unshift({
      sessionId: sessId,
      applicationId: p.applicationId,
      studentId: p.studentId,
      studentName: p.studentName,
      studentBranch: p.studentBranch,
      driveId,
      jobTitle,
      companyId: 'COMP-001',
      companyName,
      interviewerId: p.interviewerId,
      interviewerName: p.interviewerName,
      roomId: p.roomId,
      roomName: p.roomName,
      startTime: p.startTime,
      endTime: p.endTime,
      status: 'SCHEDULED',
    })

    return {
      sessionId: sessId,
      applicationId: p.applicationId,
      studentId: p.studentId,
      studentName: p.studentName,
      interviewerName: p.interviewerName,
      roomName: p.roomName,
      startTime: p.startTime,
      endTime: p.endTime,
    }
  })

  const summary: OfficerPublicationSummaryDto = {
    attemptId,
    previewId,
    previewReference: previewId.substring(0, 8) + '****',
    driveId,
    jobTitle,
    companyName,
    outcome: 'ALL_CONFIRMED',
    recoveryInstructions:
      'All proposed interview appointments were successfully confirmed and persisted. Students may view their confirmed timetables.',
    publishedAt: new Date().toISOString(),
    totalProposals: proposals.length,
    publishedCount: proposals.length,
    skippedDuplicatesCount: 0,
    conflictsCount: 0,
    errorsCount: 0,
    unattemptedCount: 0,
    confirmedSessionIds: publishedSessions.map((s) => s.sessionId),
    publishedSessions,
    skippedDuplicateApplicationIds: [],
    conflictRejections: [],
    infrastructureErrors: [],
  }

  return { status: 200, data: summary }
}

/**
 * Returns simulated confirmed sessions with deterministic filtering and pagination.
 */
export function simulatePublishedTimetable(params: {
  driveId?: string
  companyId?: string
  date?: string
  page?: number
  size?: number
}): PagedResponse<OfficerInterviewDetailDto> {
  const page = Math.max(0, params.page ?? 0)
  const size = Math.min(100, Math.max(1, params.size ?? 20))

  let filtered = [...simulatedPublishedSessions]

  if (params.driveId && params.driveId.trim()) {
    filtered = filtered.filter(
      (s) => s.driveId.toLowerCase() === params.driveId!.trim().toLowerCase()
    )
  }
  if (params.companyId && params.companyId.trim()) {
    filtered = filtered.filter(
      (s) => s.companyId.toLowerCase() === params.companyId!.trim().toLowerCase()
    )
  }
  if (params.date && params.date.trim()) {
    filtered = filtered.filter((s) => s.startTime.startsWith(params.date!.trim()))
  }

  const totalElements = filtered.length
  const totalPages = Math.ceil(totalElements / size) || 1
  const fromIndex = page * size
  const pagedContent = filtered.slice(fromIndex, fromIndex + size)

  return {
    content: pagedContent,
    page,
    size,
    totalElements,
    totalPages,
    last: page >= totalPages - 1,
  }
}

/**
 * Returns simulated single published session by ID.
 */
export function simulatePublishedSessionById(id: string): OfficerInterviewDetailDto {
  const found = simulatedPublishedSessions.find(
    (s) => s.sessionId.toLowerCase() === id.trim().toLowerCase()
  )
  if (!found) {
    throw new ApiError({
      status: 404,
      error: 'Not Found',
      message: `Interview session with ID '${id}' was not found.`,
      path: `/api/v1/officer/scheduling/published/${id}`,
      timestamp: new Date().toISOString(),
    })
  }
  return found
}
