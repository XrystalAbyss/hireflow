import * as React from 'react'
import {
  Calendar,
  Clock,
  Users,
  DoorOpen,
  Sparkles,
  AlertCircle,
  HelpCircle,
  Briefcase,
  ChevronRight,
  SlidersHorizontal,
  FlaskConical,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { Checkbox } from '@/components/ui/checkbox'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { localToday } from '@/lib/dateUtils'
import type {
  OfficerDriveDetailDto,
  OfficerInterviewerDetailDto,
  OfficerRoomDetailDto,
  OfficerSchedulingPreviewRequestDto,
} from '@/types/api'
import {
  isSimulationModeActive,
  setSimulationModeActive,
  setSimulationScenario,
  getSimulationScenario,
  type SimulationScenario,
} from '../../api/schedulingSimulation'
import {
  FIXTURE_OFFICER_DRIVES,
  FIXTURE_OFFICER_INTERVIEWERS,
  FIXTURE_OFFICER_ROOMS,
} from '../../fixtures'

export interface SchedulingConfigPanelProps {
  drives: OfficerDriveDetailDto[]
  interviewers: OfficerInterviewerDetailDto[]
  rooms: OfficerRoomDetailDto[]
  isLoadingDrives: boolean
  onSubmit: (request: OfficerSchedulingPreviewRequestDto) => void
  isGenerating: boolean
  initialValues?: Partial<OfficerSchedulingPreviewRequestDto>
  backendPendingNotice?: boolean
}

export const SchedulingConfigPanel: React.FC<SchedulingConfigPanelProps> = ({
  drives,
  interviewers,
  rooms,
  isLoadingDrives,
  onSubmit,
  isGenerating,
  initialValues,
  backendPendingNotice = true,
}) => {
  // Form State
  const [selectedDriveId, setSelectedDriveId] = React.useState<string>(
    initialValues?.driveId || ''
  )
  const [date, setDate] = React.useState<string>(() => initialValues?.date || localToday())
  const [startTime, setStartTime] = React.useState<string>(
    initialValues?.startTime || '09:00'
  )
  const [endTime, setEndTime] = React.useState<string>(
    initialValues?.endTime || '17:00'
  )
  const [durationMinutes, setDurationMinutes] = React.useState<number>(
    initialValues?.durationMinutes ?? 45
  )
  const [bufferMinutes, setBufferMinutes] = React.useState<number>(
    initialValues?.bufferMinutes ?? 15
  )
  const [selectedInterviewerIds, setSelectedInterviewerIds] = React.useState<string[]>(
    initialValues?.interviewerIds || []
  )
  const [selectedRoomIds, setSelectedRoomIds] = React.useState<string[]>(
    initialValues?.roomIds || []
  )

  // Simulation controls (dev opt-in)
  const [simActive, setSimActive] = React.useState<boolean>(isSimulationModeActive())
  const [simScenario, setSimScenario] = React.useState<SimulationScenario>(getSimulationScenario())

  // Form Validation Errors
  const [errors, setErrors] = React.useState<Record<string, string>>({})

// Resolve effective drives (fallback to fixtures if live drives have no shortlisted candidates and simulation is active)
const displayDrives = React.useMemo(() => {
  if (simActive && (drives.length === 0 || drives.every((d) => (d.shortlistedApplications ?? 0) === 0))) {
    return FIXTURE_OFFICER_DRIVES
  }
  return drives.length > 0 ? drives : (simActive ? FIXTURE_OFFICER_DRIVES : [])
}, [drives, simActive])

// Resolve effective selected drive ID
const effectiveDriveId = selectedDriveId || displayDrives[0]?.id || ''

const selectedDrive = React.useMemo(() => {
  return displayDrives.find((d) => d.id === effectiveDriveId) || (simActive ? FIXTURE_OFFICER_DRIVES[0] : undefined)
}, [displayDrives, effectiveDriveId, simActive])

// Resources display fallback for simulation
const displayInterviewers = React.useMemo(() => {
  if (simActive && interviewers.length === 0) return FIXTURE_OFFICER_INTERVIEWERS
  return interviewers
}, [interviewers, simActive])

const displayRooms = React.useMemo(() => {
  if (simActive && rooms.length === 0) return FIXTURE_OFFICER_ROOMS
  return rooms
}, [rooms, simActive])

// Interviewers matching selected drive's company
const companyInterviewers = React.useMemo(() => {
  if (!selectedDrive) return []
  const matching = displayInterviewers.filter((i) => i.companyId === selectedDrive.companyId)
  if (matching.length === 0 && simActive) {
    return FIXTURE_OFFICER_INTERVIEWERS
  }
  return matching
}, [selectedDrive, displayInterviewers, simActive])

  const toggleInterviewer = (id: string) => {
    setSelectedInterviewerIds((prev) =>
      prev.includes(id) ? prev.filter((item) => item !== id) : [...prev, id]
    )
  }

  const toggleRoom = (id: string) => {
    setSelectedRoomIds((prev) =>
      prev.includes(id) ? prev.filter((item) => item !== id) : [...prev, id]
    )
  }

  const handleSimToggle = (enabled: boolean) => {
    setSimulationModeActive(enabled)
    setSimActive(enabled)
  }

  const handleScenarioChange = (scen: SimulationScenario) => {
    setSimulationScenario(scen)
    setSimScenario(scen)
  }

  const validate = (): boolean => {
    const errs: Record<string, string> = {}

    if (!effectiveDriveId) {
      errs.driveId = 'Please select a recruitment drive.'
    }

    if (!date || !/^\d{4}-\d{2}-\d{2}$/.test(date)) {
      errs.date = 'Valid interview date (YYYY-MM-DD) is required.'
    }

    if (!startTime || !/^\d{2}:\d{2}$/.test(startTime)) {
      errs.startTime = 'Valid start time (HH:mm) is required.'
    }

    if (!endTime || !/^\d{2}:\d{2}$/.test(endTime)) {
      errs.endTime = 'Valid end time (HH:mm) is required.'
    }

    if (startTime && endTime && startTime >= endTime) {
      errs.startTime = 'Start time must be strictly before end time.'
    }

    if (durationMinutes < 1 || durationMinutes > 240) {
      errs.durationMinutes = 'Duration must be between 1 and 240 minutes.'
    }

    if (bufferMinutes < 0 || bufferMinutes > 120) {
      errs.bufferMinutes = 'Buffer duration must be between 0 and 120 minutes.'
    }

    setErrors(errs)
    return Object.keys(errs).length === 0
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!validate()) return

    onSubmit({
      driveId: effectiveDriveId,
      date,
      startTime,
      endTime,
      durationMinutes,
      bufferMinutes,
      interviewerIds: selectedInterviewerIds.length > 0 ? selectedInterviewerIds : undefined,
      roomIds: selectedRoomIds.length > 0 ? selectedRoomIds : undefined,
    })
  }

  return (
    <div className="space-y-6">
      {/* Simulation / Backend Status Bar */}
      {backendPendingNotice && import.meta.env.DEV && import.meta.env.VITE_HIREFLOW_MODE !== 'live' && (window as Window & { __HIREFLOW_SIMULATION_MODE__?: boolean }).__HIREFLOW_SIMULATION_MODE__ === true && (
        <div className="rounded-md border border-brass-600/30 bg-brass-500/5 p-3.5 text-xs text-foreground flex flex-col sm:flex-row sm:items-center justify-between gap-3 shadow-xs">
          <div className="flex items-center gap-2.5">
            <div className="p-1.5 rounded-sm bg-brass-600/10 text-brass-800 dark:text-brass-400 shrink-0">
              <FlaskConical className="h-4 w-4" />
            </div>
            <div>
              <span className="font-semibold text-brass-800 dark:text-brass-300">
                Scheduling demonstration controls
              </span>
              <p className="text-muted-foreground mt-0.5">
                Simulation uses fictional in-memory scenarios. Published simulations never create PostgreSQL appointments.
              </p>
            </div>
          </div>
          <div className="flex items-center gap-2 shrink-0">
            <label className="flex items-center gap-2 cursor-pointer bg-card px-2.5 py-1.5 rounded-sm border border-border">
              <input
                type="checkbox"
                id="dev-simulation-toggle"
                checked={simActive}
                onChange={(e) => handleSimToggle(e.target.checked)}
                className="h-3.5 w-3.5 rounded-xs text-academic-teal focus:ring-academic-teal cursor-pointer"
              />
              <span className="font-medium text-xs">Dev Simulation Mode</span>
            </label>
          </div>
        </div>
      )}

      {/* Simulation Scenario Selector (when active) */}
      {simActive && (
        <div className="rounded-md border border-academic-teal/40 bg-academic-teal/5 p-3 text-xs space-y-2">
          <div className="flex items-center justify-between">
            <span className="font-semibold text-academic-teal flex items-center gap-1.5">
              <SlidersHorizontal className="h-3.5 w-3.5" />
              <span>Simulation Sandbox Scenarios:</span>
            </span>
            <Badge variant="outline" className="text-[10px] uppercase font-mono border-academic-teal/40 text-academic-teal">
              Deterministic In-Memory
            </Badge>
          </div>
          <div className="flex flex-wrap gap-2 pt-1">
            {(
              [
                ['ALL_CONFIRMED', 'HTTP 200 (Full Success)'],
                ['PARTIAL_SUCCESS', 'HTTP 207 (Partial Conflict)'],
                ['NONE_CONFIRMED', 'HTTP 409 (All Conflicted)'],
                ['CONSUMED_REPLAY', 'HTTP 409 (Token Replay)'],
                ['EXPIRED', 'HTTP 410 (Token Expired)'],
                ['DATABASE_ERROR', 'HTTP 500 (PostgreSQL Outage)'],
              ] as const
            ).map(([scen, label]) => (
              <button
                key={scen}
                type="button"
                onClick={() => handleScenarioChange(scen)}
                className={`px-2 py-1 rounded-xs text-xs font-mono transition-colors ${
                  simScenario === scen
                    ? 'bg-academic-teal text-warm-ivory font-semibold'
                    : 'bg-card border border-border text-muted-foreground hover:text-foreground'
                }`}
              >
                {label}
              </button>
            ))}
          </div>
        </div>
      )}

      {/* Scheduling Configuration Form */}
      <form onSubmit={handleSubmit} className="space-y-6">
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Column 1: Recruitment Drive & Timing Window */}
          <div className="rounded-md border border-border bg-card p-5 space-y-5 shadow-xs">
            <div className="flex items-center gap-2 pb-3 border-b border-border/80">
              <Briefcase className="h-4 w-4 text-academic-teal" />
              <h2 className="font-serif text-sm font-bold text-foreground">
                1. Recruitment Drive Context
              </h2>
            </div>

            {/* Drive Selection */}
            <div className="space-y-1.5">
              <label htmlFor="scheduling-drive-select" className="text-xs font-semibold text-foreground flex items-center justify-between">
                <span>Select Active Drive *</span>
                {selectedDrive && (
                  <Badge variant="outline" className="text-[10px] font-mono">
                    {selectedDrive.companyName}
                  </Badge>
                )}
              </label>
              <select
                id="scheduling-drive-select"
                value={effectiveDriveId}
                onChange={(e) => setSelectedDriveId(e.target.value)}
                disabled={isLoadingDrives}
                aria-describedby={errors.driveId ? 'drive-error' : undefined}
                className="w-full h-9 rounded-sm border border-input bg-background px-3 py-1.5 text-xs text-foreground focus:outline-none focus:ring-2 focus:ring-academic-teal"
              >
                {displayDrives.map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.jobTitle} ({d.id}) — {d.companyName}
                  </option>
                ))}
              </select>
              {errors.driveId && (
                <p id="drive-error" className="text-xs font-medium text-destructive" role="alert">
                  {errors.driveId}
                </p>
              )}
            </div>

            {/* Drive Context Summary Card */}
            {selectedDrive && (
              <div className="rounded-sm border border-border/70 bg-muted/20 p-3 space-y-2 text-xs">
                <div className="flex justify-between items-center text-muted-foreground">
                  <span>Job Role:</span>
                  <span className="font-semibold text-foreground">{selectedDrive.jobTitle}</span>
                </div>
                <div className="flex justify-between items-center text-muted-foreground">
                  <span>Target Company:</span>
                  <span className="font-medium text-foreground">{selectedDrive.companyName}</span>
                </div>
                <div className="flex justify-between items-center text-muted-foreground">
                  <span>Min CGPA:</span>
                  <span className="font-mono text-foreground">
                    {selectedDrive.minimumCgpa != null ? selectedDrive.minimumCgpa.toFixed(2) : 'N/A'}
                  </span>
                </div>
                <div className="flex justify-between items-center text-muted-foreground">
                  <span>Eligible Branches:</span>
                  <span className="font-mono text-foreground text-[11px]">
                    {selectedDrive.eligibleBranches?.join(', ') ?? 'All Branches'}
                  </span>
                </div>
                <div className="pt-2 border-t border-border/60 flex justify-between items-center">
                  <span className="font-medium text-foreground">Shortlisted Candidate Pool:</span>
                  <Badge
                    variant={(selectedDrive.shortlistedApplications ?? 0) > 0 ? 'default' : 'outline'}
                    className={`font-mono text-xs ${
                      (selectedDrive.shortlistedApplications ?? 0) === 0
                        ? 'border-destructive text-destructive'
                        : 'bg-academic-teal text-warm-ivory'
                    }`}
                  >
                    {selectedDrive.shortlistedApplications ?? 0} candidates
                  </Badge>
                </div>

                {(selectedDrive.shortlistedApplications ?? 0) === 0 && (
                  <div className="pt-1 flex items-start gap-1.5 text-destructive text-[11px] leading-tight">
                    <AlertCircle className="h-3.5 w-3.5 shrink-0 mt-0.5" />
                    <span>
                      Zero shortlisted candidates. The GreedyScheduler requires applications in SHORTLISTED status.
                    </span>
                  </div>
                )}
              </div>
            )}

            {/* Date Selection */}
            <div className="space-y-1.5 pt-2 border-t border-border/80">
              <label htmlFor="scheduling-date" className="text-xs font-semibold text-foreground flex items-center gap-1.5">
                <Calendar className="h-3.5 w-3.5 text-muted-foreground" />
                <span>Interview Date (LocalDate) *</span>
              </label>
              <Input
                id="scheduling-date"
                type="date"
                value={date}
                onChange={(e) => setDate(e.target.value)}
                aria-describedby={errors.date ? 'date-error' : undefined}
                className="h-9 text-xs font-mono"
              />
              {errors.date && (
                <p id="date-error" className="text-xs font-medium text-destructive" role="alert">
                  {errors.date}
                </p>
              )}
            </div>

            {/* Time Window */}
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <label htmlFor="scheduling-start-time" className="text-xs font-semibold text-foreground flex items-center gap-1">
                  <Clock className="h-3.5 w-3.5 text-muted-foreground" />
                  <span>Start Time *</span>
                </label>
                <Input
                  id="scheduling-start-time"
                  type="time"
                  value={startTime}
                  onChange={(e) => setStartTime(e.target.value)}
                  className="h-9 text-xs font-mono"
                />
              </div>

              <div className="space-y-1.5">
                <label htmlFor="scheduling-end-time" className="text-xs font-semibold text-foreground flex items-center gap-1">
                  <Clock className="h-3.5 w-3.5 text-muted-foreground" />
                  <span>End Time *</span>
                </label>
                <Input
                  id="scheduling-end-time"
                  type="time"
                  value={endTime}
                  onChange={(e) => setEndTime(e.target.value)}
                  className="h-9 text-xs font-mono"
                />
              </div>
            </div>
            {errors.startTime && (
              <p className="text-xs font-medium text-destructive" role="alert">
                {errors.startTime}
              </p>
            )}
          </div>

          {/* Column 2: Slot Durations & Interviewer Allocation Pool */}
          <div className="rounded-md border border-border bg-card p-5 space-y-5 shadow-xs">
            <div className="flex items-center gap-2 pb-3 border-b border-border/80">
              <Clock className="h-4 w-4 text-academic-teal" />
              <h2 className="font-serif text-sm font-bold text-foreground">
                2. Slot Durations & Intervals
              </h2>
            </div>

            {/* Durations */}
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <label htmlFor="scheduling-duration" className="text-xs font-semibold text-foreground flex items-center gap-1">
                  <span>Duration (mins)</span>
                  <Tooltip>
                    <TooltipTrigger asChild>
                      <HelpCircle className="h-3 w-3 text-muted-foreground cursor-pointer" />
                    </TooltipTrigger>
                    <TooltipContent>
                      Backend default is 45 minutes (1–240 minutes permitted).
                    </TooltipContent>
                  </Tooltip>
                </label>
                <Input
                  id="scheduling-duration"
                  type="number"
                  min={1}
                  max={240}
                  value={durationMinutes}
                  onChange={(e) => setDurationMinutes(parseInt(e.target.value, 10) || 45)}
                  className="h-9 text-xs font-mono"
                />
                {errors.durationMinutes && (
                  <p className="text-[11px] font-medium text-destructive">{errors.durationMinutes}</p>
                )}
              </div>

              <div className="space-y-1.5">
                <label htmlFor="scheduling-buffer" className="text-xs font-semibold text-foreground flex items-center gap-1">
                  <span>Buffer (mins)</span>
                  <Tooltip>
                    <TooltipTrigger asChild>
                      <HelpCircle className="h-3 w-3 text-muted-foreground cursor-pointer" />
                    </TooltipTrigger>
                    <TooltipContent>
                      Backend default is 15 minutes (0–120 minutes permitted).
                    </TooltipContent>
                  </Tooltip>
                </label>
                <Input
                  id="scheduling-buffer"
                  type="number"
                  min={0}
                  max={120}
                  value={bufferMinutes}
                  onChange={(e) => setBufferMinutes(parseInt(e.target.value, 10) || 0)}
                  className="h-9 text-xs font-mono"
                />
                {errors.bufferMinutes && (
                  <p className="text-[11px] font-medium text-destructive">{errors.bufferMinutes}</p>
                )}
              </div>
            </div>

            {/* Interviewer Resource Selection */}
            <div className="space-y-2 pt-2 border-t border-border/80">
              <div className="flex items-center justify-between">
                <label className="text-xs font-semibold text-foreground flex items-center gap-1.5">
                  <Users className="h-3.5 w-3.5 text-muted-foreground" />
                  <span>Interviewer Pool Selection</span>
                </label>
                <span className="text-[11px] font-mono text-muted-foreground">
                  {selectedInterviewerIds.length === 0
                    ? 'Default Pool'
                    : `${selectedInterviewerIds.length} Selected`}
                </span>
              </div>

              {/* Explanatory note on backend default pool behavior */}
              <div className="p-2.5 rounded-sm bg-muted/20 border border-border/60 text-[11px] text-muted-foreground leading-normal">
                {selectedInterviewerIds.length === 0 ? (
                  <span className="text-brass-800 dark:text-brass-400 font-medium">
                    No interviewers explicitly selected — backend will automatically evaluate company-specific interviewers for this drive ({companyInterviewers.length} registered), falling back to all available interviewers.
                  </span>
                ) : (
                  <span>
                    The scheduler will restrict allocation strictly to the {selectedInterviewerIds.length} selected interviewer(s).
                  </span>
                )}
              </div>

              {/* Interviewer Checklist */}
              <div className="max-h-48 overflow-y-auto rounded-sm border border-border bg-background p-2 space-y-1.5">
                {interviewers.map((i) => {
                  const isChecked = selectedInterviewerIds.includes(i.id)
                  const isCompanyMatch = selectedDrive && i.companyId === selectedDrive.companyId
                  return (
                    <label
                      key={i.id}
                      className={`flex items-center justify-between p-1.5 rounded-xs text-xs cursor-pointer hover:bg-muted/50 transition-colors ${
                        isChecked ? 'bg-academic-teal/10' : ''
                      }`}
                    >
                      <div className="flex items-center gap-2 min-w-0">
                        <Checkbox
                          id={`intv-${i.id}`}
                          checked={isChecked}
                          onCheckedChange={() => toggleInterviewer(i.id)}
                        />
                        <span className="truncate font-medium text-foreground">{i.name}</span>
                      </div>
                      <div className="flex items-center gap-1.5 shrink-0">
                        {isCompanyMatch && (
                          <Badge variant="outline" className="text-[9px] px-1 py-0 text-academic-teal border-academic-teal/30">
                            Partner
                          </Badge>
                        )}
                        <span className="text-[10px] font-mono text-muted-foreground">{i.id}</span>
                      </div>
                    </label>
                  )
                })}
              </div>
            </div>
          </div>

          {/* Column 3: Room Resource Pool & Submit Action */}
          <div className="rounded-md border border-border bg-card p-5 space-y-5 shadow-xs flex flex-col justify-between">
            <div className="space-y-4">
              <div className="flex items-center gap-2 pb-3 border-b border-border/80">
                <DoorOpen className="h-4 w-4 text-academic-teal" />
                <h2 className="font-serif text-sm font-bold text-foreground">
                  3. Room Resource Allocation
                </h2>
              </div>

              {/* Room Selection */}
              <div className="space-y-2">
                <div className="flex items-center justify-between">
                  <label className="text-xs font-semibold text-foreground flex items-center gap-1.5">
                    <span>Interview Room Pool</span>
                  </label>
                  <span className="text-[11px] font-mono text-muted-foreground">
                    {selectedRoomIds.length === 0
                      ? 'All Rooms Pool'
                      : `${selectedRoomIds.length} Selected`}
                  </span>
                </div>

                {/* Explanatory note on backend room default behavior */}
                <div className="p-2.5 rounded-sm bg-muted/20 border border-border/60 text-[11px] text-muted-foreground leading-normal">
                  {selectedRoomIds.length === 0 ? (
                    <span className="text-brass-800 dark:text-brass-400 font-medium">
                      No rooms explicitly selected — backend GreedyScheduler will automatically evaluate all {rooms.length} registered institutional interview rooms.
                    </span>
                  ) : (
                    <span>
                      The scheduler will restrict room assignment strictly to the {selectedRoomIds.length} selected room(s).
                    </span>
                  )}
                </div>

                {/* Room Checklist */}
                <div className="max-h-48 overflow-y-auto rounded-sm border border-border bg-background p-2 space-y-1.5">
                  {displayRooms.map((r) => {
                    const isChecked = selectedRoomIds.includes(r.id)
                    return (
                      <label
                        key={r.id}
                        className={`flex items-center justify-between p-1.5 rounded-xs text-xs cursor-pointer hover:bg-muted/50 transition-colors ${
                          isChecked ? 'bg-academic-teal/10' : ''
                        }`}
                      >
                        <div className="flex items-center gap-2 min-w-0">
                          <Checkbox
                            id={`rm-${r.id}`}
                            checked={isChecked}
                            onCheckedChange={() => toggleRoom(r.id)}
                          />
                          <span className="truncate font-medium text-foreground">{r.name}</span>
                        </div>
                        <div className="flex items-center gap-1.5 shrink-0 text-muted-foreground">
                          <span className="text-[10px] font-mono">Cap: {r.capacity}</span>
                          <span className="text-[10px] font-mono">{r.id}</span>
                        </div>
                      </label>
                    )
                  })}
                </div>
              </div>
            </div>

            {/* Advisory Preview Generation Action */}
            <div className="pt-4 border-t border-border/80 space-y-3">
              <div className="text-[11px] text-muted-foreground leading-tight">
                Advisory preview is completely non-mutating: zero database records are written, zero candidate notifications are dispatched.
              </div>
              <Button
                type="submit"
                disabled={isGenerating || (!simActive && selectedDrive && (selectedDrive.shortlistedApplications ?? 0) === 0)}
                className="w-full h-10 text-xs font-semibold bg-academic-teal hover:bg-academic-teal/90 text-warm-ivory shadow-xs gap-2"
              >
                {isGenerating ? (
                  <>
                    <span className="h-4 w-4 rounded-full border-2 border-warm-ivory/30 border-t-warm-ivory animate-spin" />
                    <span>Executing GreedyScheduler Algorithm...</span>
                  </>
                ) : (
                  <>
                    <Sparkles className="h-4 w-4" />
                    <span>Generate Advisory Timetable Preview</span>
                    <ChevronRight className="h-3.5 w-3.5 ml-auto opacity-70" />
                  </>
                )}
              </Button>
            </div>
          </div>
        </div>
      </form>
    </div>
  )
}

