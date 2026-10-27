import * as React from 'react'
import {
  Clock,
  LayoutGrid,
  Table as TableIcon,
  ShieldAlert,
  ArrowLeft,
  Send,
  RefreshCw,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import type { OfficerSchedulingPreviewResponseDto } from '@/types/api'
import { SchedulingTimeGrid } from './SchedulingTimeGrid'
import { SchedulingDataTable } from './SchedulingDataTable'
import { UnscheduledCandidatesList } from './UnscheduledCandidatesList'
import {
  parseUtcInstant,
  formatTtlCountdown,
  maskPreviewToken,
  formatLocalTime,
} from '../../utils/schedulingUtils'

export interface AdvisoryPreviewViewProps {
  preview: OfficerSchedulingPreviewResponseDto
  onProceedToPublish: () => void
  onAdjustConfig: () => void
  onRefreshPreview: () => void
  isPublishing: boolean
}

export const AdvisoryPreviewView: React.FC<AdvisoryPreviewViewProps> = ({
  preview,
  onProceedToPublish,
  onAdjustConfig,
  onRefreshPreview,
  isPublishing,
}) => {
  const [viewMode, setViewMode] = React.useState<'grid' | 'table'>('grid')
  const [remainingMs, setRemainingMs] = React.useState<number>(() => {
    const expires = parseUtcInstant(preview.expiresAt)
    return Math.max(0, expires.getTime() - Date.now())
  })

  // Dynamic TTL Countdown update loop
  React.useEffect(() => {
    const expires = parseUtcInstant(preview.expiresAt)

    const timer = setInterval(() => {
      const remaining = Math.max(0, expires.getTime() - Date.now())
      setRemainingMs(remaining)
      if (remaining <= 0) {
        clearInterval(timer)
      }
    }, 1000)

    return () => clearInterval(timer)
  }, [preview.expiresAt])

  const isExpired = remainingMs <= 0

  return (
    <div className="space-y-6">
      {/* Prominent Advisory Institutional Warning Banner */}
      <div className="rounded-md border-2 border-brass-600/40 bg-brass-600/10 p-4 text-xs shadow-xs space-y-2">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
          <div className="flex items-center gap-2 text-brass-800 dark:text-brass-300 font-bold uppercase tracking-wider font-mono">
            <ShieldAlert className="h-4 w-4 text-brass-800 dark:text-brass-400 shrink-0" />
            <span>ADVISORY — NOT YET PUBLISHED</span>
            <span className="px-2 py-0.5 rounded-xs bg-brass-600/20 text-brass-800 dark:text-brass-400 border border-brass-600/30 text-[10px]">
              VOLATILE IN-MEMORY PROPOSAL
            </span>
          </div>

          {/* Masked Preview Reference Display */}
          <div className="flex items-center gap-2 text-muted-foreground font-mono text-[11px]">
            <span>Token Ref:</span>
            <strong className="text-foreground bg-card px-2 py-0.5 rounded-xs border border-border">
              {maskPreviewToken(preview.previewId)}
            </strong>
          </div>
        </div>

        <p className="text-muted-foreground leading-relaxed">
          Zero database mutations occurred. The appointments below are held strictly in server cache with 15-minute validity. Candidates and interviewers cannot view these sessions until transactional publication is explicitly confirmed.
        </p>
      </div>

      {/* Operational Header & Key Metrics */}
      <div className="rounded-md border border-border bg-card p-5 space-y-5 shadow-xs">
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 pb-4 border-b border-border/80">
          <div>
            <div className="flex items-center gap-2">
              <h2 className="font-serif text-lg font-bold text-foreground">
                {preview.jobTitle}
              </h2>
              <Badge variant="outline" className="font-mono text-xs">
                {preview.driveId}
              </Badge>
            </div>
            <p className="text-xs text-muted-foreground mt-0.5">
              Target Company: <strong className="text-foreground">{preview.companyName}</strong> • Date: <strong className="text-foreground">{preview.date}</strong> ({formatLocalTime(preview.startTime)} – {formatLocalTime(preview.endTime)})
            </p>
          </div>

          {/* TTL Countdown & Actions */}
          <div className="flex flex-wrap items-center gap-3">
            {/* TTL Countdown Box */}
            <div
              role="status"
              aria-live="polite"
              className={`flex items-center gap-2 px-3 py-1.5 rounded-sm border text-xs font-mono shadow-2xs ${
                isExpired
                  ? 'border-destructive/40 bg-destructive/10 text-destructive font-bold'
                  : remainingMs < 120000
                  ? 'border-brass-600/40 bg-brass-600/10 text-brass-800 dark:text-brass-400 font-bold'
                  : 'border-border bg-muted/30 text-foreground'
              }`}
            >
              <Clock className="h-3.5 w-3.5 shrink-0" />
              <span>TTL: {formatTtlCountdown(remainingMs)}</span>
            </div>

            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={onAdjustConfig}
              className="text-xs gap-1.5"
            >
              <ArrowLeft className="h-3.5 w-3.5" />
              <span>Adjust Config</span>
            </Button>

            {isExpired ? (
              <Button
                type="button"
                size="sm"
                onClick={onRefreshPreview}
                className="text-xs gap-1.5 bg-brass-600 hover:bg-brass-700 text-warm-ivory"
              >
                <RefreshCw className="h-3.5 w-3.5" />
                <span>Generate Fresh Preview</span>
              </Button>
            ) : (
              <Button
                type="button"
                size="sm"
                onClick={onProceedToPublish}
                disabled={isPublishing || preview.totalScheduled === 0}
                className="text-xs gap-1.5 bg-academic-teal hover:bg-academic-teal/90 text-warm-ivory shadow-xs"
              >
                <Send className="h-3.5 w-3.5" />
                <span>Review & Publish Timetable</span>
              </Button>
            )}
          </div>
        </div>

        {/* Metrics Row */}
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-xs">
          <div className="p-3 rounded-sm border border-border bg-muted/20 space-y-1">
            <span className="text-muted-foreground block">Shortlisted Pool</span>
            <strong className="text-lg font-mono font-bold text-foreground block">
              {preview.totalRequested}
            </strong>
            <span className="text-[11px] text-muted-foreground">Shortlisted candidates</span>
          </div>

          <div className="p-3 rounded-sm border border-academic-teal/30 bg-academic-teal/5 space-y-1">
            <span className="text-academic-teal font-semibold block">Proposed Sessions</span>
            <strong className="text-lg font-mono font-bold text-academic-teal block">
              {preview.totalScheduled}
            </strong>
            <span className="text-[11px] text-muted-foreground">Conflict-free slots</span>
          </div>

          <div className="p-3 rounded-sm border border-brass-600/30 bg-brass-600/5 space-y-1">
            <span className="text-brass-800 dark:text-brass-400 font-semibold block">Unscheduled</span>
            <strong className="text-lg font-mono font-bold text-brass-800 dark:text-brass-400 block">
              {preview.totalUnscheduled}
            </strong>
            <span className="text-[11px] text-muted-foreground">Remain shortlisted</span>
          </div>

          <div className="p-3 rounded-sm border border-border bg-muted/20 space-y-1">
            <span className="text-muted-foreground block">Slot Configuration</span>
            <strong className="text-sm font-mono font-bold text-foreground block">
              {preview.durationMinutes}m + {preview.bufferMinutes}m
            </strong>
            <span className="text-[11px] text-muted-foreground">Duration & buffer</span>
          </div>
        </div>

        {/* View Mode Toggle */}
        <div className="flex items-center justify-between pt-2 border-t border-border/80">
          <div className="flex items-center gap-1.5">
            <button
              type="button"
              onClick={() => setViewMode('grid')}
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-sm text-xs font-medium transition-colors cursor-pointer ${
                viewMode === 'grid'
                  ? 'bg-academic-teal text-warm-ivory font-semibold shadow-xs'
                  : 'bg-muted/40 text-muted-foreground hover:text-foreground'
              }`}
            >
              <LayoutGrid className="h-3.5 w-3.5" />
              <span>Visual Timeline</span>
            </button>
            <button
              type="button"
              onClick={() => setViewMode('table')}
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-sm text-xs font-medium transition-colors cursor-pointer ${
                viewMode === 'table'
                  ? 'bg-academic-teal text-warm-ivory font-semibold shadow-xs'
                  : 'bg-muted/40 text-muted-foreground hover:text-foreground'
              }`}
            >
              <TableIcon className="h-3.5 w-3.5" />
              <span>Accessible Tabular View</span>
            </button>
          </div>

          <span className="text-xs text-muted-foreground font-mono">
            {preview.proposedSessions.length} Appointments Proposed
          </span>
        </div>

        {/* Render Selected View */}
        {viewMode === 'grid' ? (
          <SchedulingTimeGrid
            proposedSessions={preview.proposedSessions}
            bufferMinutes={preview.bufferMinutes}
          />
        ) : (
          <SchedulingDataTable proposedSessions={preview.proposedSessions} />
        )}
      </div>

      {/* Dedicated Unscheduled Candidates Section */}
      {preview.unscheduledCandidates.length > 0 && (
        <UnscheduledCandidatesList
          unscheduledCandidates={preview.unscheduledCandidates}
        />
      )}
    </div>
  )
}
