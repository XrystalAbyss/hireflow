import * as React from 'react'
import {
  CheckCircle2,
  AlertTriangle,
  XCircle,
  ArrowRight,
  RefreshCw,
  ExternalLink,
  ShieldAlert,
  Clock,
  Info,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import type {
  OfficerPublicationSummaryDto,
} from '@/types/api'
import { parseCampusLocalDateTime } from '../../utils/schedulingUtils'

export type PublicationViewStatus =
  | { type: 'ALL_CONFIRMED'; summary: OfficerPublicationSummaryDto }
  | { type: 'PARTIAL_SUCCESS'; summary: OfficerPublicationSummaryDto }
  | { type: 'NONE_CONFIRMED'; summary: OfficerPublicationSummaryDto }
  | { type: 'ALREADY_CONSUMED'; message: string; driveId?: string }
  | { type: 'EXPIRED'; message: string }
  | { type: 'NOT_FOUND_EVICTED'; message: string; driveId?: string }
  | { type: 'SERVER_ERROR_UNCERTAIN'; message: string; driveId?: string }
  | { type: 'NETWORK_ERROR_AMBIGUOUS'; message: string; driveId?: string }

export interface PublicationOutcomeViewProps {
  outcomeState: PublicationViewStatus
  onNavigateToConfirmedTimetable: (driveId?: string) => void
  onGenerateNewPreview: () => void
}

export const PublicationOutcomeView: React.FC<PublicationOutcomeViewProps> = ({
  outcomeState,
  onNavigateToConfirmedTimetable,
  onGenerateNewPreview,
}) => {
  // ============================================================================
  // Outcome 1: ALL_CONFIRMED (HTTP 200)
  // ============================================================================
  if (outcomeState.type === 'ALL_CONFIRMED') {
    const s = outcomeState.summary
    return (
      <div className="rounded-md border border-border bg-card p-6 space-y-6 shadow-xs max-w-4xl mx-auto">
        {/* Banner */}
        <div className="flex items-start gap-4">
          <div className="p-3 rounded-md bg-academic-teal/10 text-academic-teal shrink-0">
            <CheckCircle2 className="h-8 w-8" />
          </div>
          <div className="space-y-1">
            <div className="flex items-center gap-2">
              <h2 className="font-serif text-lg font-bold text-foreground">
                Interview Timetable Successfully Published
              </h2>
              <Badge className="font-mono text-xs bg-academic-teal text-warm-ivory">
                HTTP 200 OK
              </Badge>
            </div>
            <p className="text-xs text-muted-foreground leading-relaxed">
              All proposed interview appointments were successfully validated, confirmed, and persisted in PostgreSQL. Candidates may now inspect their scheduled interview slots in their Student Portals.
            </p>
          </div>
        </div>

        {/* Counts Matrix */}
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-xs">
          <div className="p-3 rounded-sm border border-border bg-muted/20 space-y-1">
            <span className="text-muted-foreground block">Total Proposals</span>
            <strong className="text-lg font-mono font-bold text-foreground block">
              {s.totalProposals}
            </strong>
          </div>

          <div className="p-3 rounded-sm border border-academic-teal/30 bg-academic-teal/5 space-y-1">
            <span className="text-academic-teal font-semibold block">Newly Confirmed</span>
            <strong className="text-lg font-mono font-bold text-academic-teal block">
              {s.publishedCount}
            </strong>
            <span className="text-[10px] text-muted-foreground">Committed to PostgreSQL</span>
          </div>

          <div className="p-3 rounded-sm border border-border bg-muted/20 space-y-1">
            <span className="text-muted-foreground block">Skipped Duplicates</span>
            <strong className="text-lg font-mono font-bold text-foreground block">
              {s.skippedDuplicatesCount}
            </strong>
            <span className="text-[10px] text-muted-foreground">Pre-existing sessions</span>
          </div>

          <div className="p-3 rounded-sm border border-border bg-muted/20 space-y-1">
            <span className="text-muted-foreground block">Conflicts / Errors</span>
            <strong className="text-lg font-mono font-bold text-foreground block">
              0
            </strong>
            <span className="text-[10px] text-muted-foreground">Zero conflicts</span>
          </div>
        </div>

        {/* Detailed Persisted Sessions Roster */}
        {s.publishedSessions.length > 0 && (
          <div className="space-y-3 pt-2">
            <h3 className="font-serif text-sm font-bold text-foreground flex items-center justify-between">
              <span>Newly Persisted Sessions ({s.publishedSessions.length})</span>
              <span className="font-mono text-[11px] text-muted-foreground">
                Attempt Ref: {s.attemptId}
              </span>
            </h3>

            <div className="rounded-sm border border-border overflow-hidden">
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs border-collapse">
                  <thead className="bg-muted/40 border-b border-border text-[11px] font-semibold text-muted-foreground uppercase">
                    <tr>
                      <th className="px-3 py-2">Session ID</th>
                      <th className="px-3 py-2">Candidate</th>
                      <th className="px-3 py-2">Interviewer</th>
                      <th className="px-3 py-2">Room</th>
                      <th className="px-3 py-2">Confirmed Time</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border/60">
                    {s.publishedSessions.map((sess) => {
                      const start = parseCampusLocalDateTime(sess.startTime)
                      const end = parseCampusLocalDateTime(sess.endTime)
                      return (
                        <tr key={sess.sessionId} className="hover:bg-muted/30">
                          <td className="px-3 py-2 font-mono text-[11px] font-semibold text-academic-teal">
                            {sess.sessionId}
                          </td>
                          <td className="px-3 py-2">
                            <span className="font-medium text-foreground">{sess.studentName}</span>
                            <span className="font-mono text-[10px] text-muted-foreground block">{sess.studentId}</span>
                          </td>
                          <td className="px-3 py-2 text-muted-foreground">{sess.interviewerName}</td>
                          <td className="px-3 py-2 text-muted-foreground">{sess.roomName}</td>
                          <td className="px-3 py-2 font-mono text-xs text-foreground">
                            {start.formattedTime} – {end.formattedTime}
                          </td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        )}

        {/* Actions */}
        <div className="flex flex-wrap items-center gap-3 pt-4 border-t border-border">
          <Button
            size="sm"
            onClick={() => onNavigateToConfirmedTimetable(s.driveId)}
            className="text-xs gap-1.5 bg-academic-teal hover:bg-academic-teal/90 text-warm-ivory shadow-xs"
          >
            <span>Inspect Confirmed Timetable</span>
            <ArrowRight className="h-3.5 w-3.5" />
          </Button>

          <Button
            variant="outline"
            size="sm"
            onClick={onGenerateNewPreview}
            className="text-xs"
          >
            Schedule Another Recruitment Drive
          </Button>
        </div>
      </div>
    )
  }

  // ============================================================================
  // Outcome 2: PARTIAL_SUCCESS (HTTP 207 Multi-Status)
  // ============================================================================
  if (outcomeState.type === 'PARTIAL_SUCCESS') {
    const s = outcomeState.summary
    return (
      <div className="rounded-md border border-brass-600/40 bg-card p-6 space-y-6 shadow-xs max-w-4xl mx-auto">
        {/* Banner */}
        <div className="flex items-start gap-4">
          <div className="p-3 rounded-md bg-brass-600/10 text-brass-800 dark:text-brass-400 shrink-0">
            <AlertTriangle className="h-8 w-8" />
          </div>
          <div className="space-y-1">
            <div className="flex items-center gap-2">
              <h2 className="font-serif text-lg font-bold text-foreground">
                Partial Publication Committed — Multi-Status
              </h2>
              <Badge variant="outline" className="font-mono text-xs text-brass-800 dark:text-brass-400 border-brass-600/40">
                HTTP 207 MULTI_STATUS
              </Badge>
            </div>
            <p className="text-xs text-muted-foreground leading-relaxed">
              Publication executed with per-session persistence. Confirmed sessions are durably saved in PostgreSQL. Conflicted candidate proposals encountered concurrent slot clashes and remain in <strong className="text-foreground">SHORTLISTED</strong> status without data loss.
            </p>
          </div>
        </div>

        {/* Operational Recovery Instructions Box */}
        <div className="rounded-sm border border-brass-600/30 bg-brass-600/5 p-3.5 text-xs space-y-1.5">
          <span className="font-semibold text-brass-800 dark:text-brass-300 flex items-center gap-1.5">
            <Info className="h-4 w-4 text-brass-600" />
            <span>Backend Reconciliation Guidance:</span>
          </span>
          <p className="text-muted-foreground leading-relaxed">
            {s.recoveryInstructions}
          </p>
        </div>

        {/* Counts Matrix */}
        <div className="grid grid-cols-2 sm:grid-cols-5 gap-3 text-xs">
          <div className="p-3 rounded-sm border border-border bg-muted/20 space-y-1">
            <span className="text-muted-foreground block">Total Proposals</span>
            <strong className="text-lg font-mono font-bold text-foreground block">
              {s.totalProposals}
            </strong>
          </div>

          <div className="p-3 rounded-sm border border-academic-teal/30 bg-academic-teal/5 space-y-1">
            <span className="text-academic-teal font-semibold block">Confirmed</span>
            <strong className="text-lg font-mono font-bold text-academic-teal block">
              {s.publishedCount}
            </strong>
            <span className="text-[10px] text-muted-foreground">Committed in DB</span>
          </div>

          <div className="p-3 rounded-sm border border-destructive/30 bg-destructive/5 space-y-1">
            <span className="text-destructive font-semibold block">Conflicts</span>
            <strong className="text-lg font-mono font-bold text-destructive block">
              {s.conflictsCount}
            </strong>
            <span className="text-[10px] text-muted-foreground">Remain shortlisted</span>
          </div>

          <div className="p-3 rounded-sm border border-border bg-muted/20 space-y-1">
            <span className="text-muted-foreground block">Duplicates Skipped</span>
            <strong className="text-lg font-mono font-bold text-foreground block">
              {s.skippedDuplicatesCount}
            </strong>
          </div>

          <div className="p-3 rounded-sm border border-border bg-muted/20 space-y-1">
            <span className="text-muted-foreground block">Unattempted</span>
            <strong className="text-lg font-mono font-bold text-foreground block">
              {s.unattemptedCount}
            </strong>
          </div>
        </div>

        {/* Conflict Rejections List */}
        {s.conflictRejections.length > 0 && (
          <div className="space-y-3 pt-2">
            <h3 className="font-serif text-sm font-bold text-foreground flex items-center justify-between">
              <span>Concurrent Conflicts Encountered ({s.conflictRejections.length})</span>
              <span className="text-[11px] text-muted-foreground font-mono">
                Candidates remain SHORTLISTED
              </span>
            </h3>

            <div className="rounded-sm border border-border overflow-hidden">
              <table className="w-full text-left text-xs border-collapse">
                <thead className="bg-muted/40 border-b border-border text-[11px] font-semibold text-muted-foreground uppercase">
                  <tr>
                    <th className="px-3 py-2">Application ID</th>
                    <th className="px-3 py-2">Conflict Category</th>
                    <th className="px-3 py-2">Backend Diagnostics</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border/60">
                  {s.conflictRejections.map((conf, idx) => (
                    <tr key={idx} className="hover:bg-muted/30">
                      <td className="px-3 py-2 font-mono text-[11px] text-muted-foreground">
                        {conf.applicationId}
                      </td>
                      <td className="px-3 py-2">
                        <Badge variant="outline" className="font-mono text-[10px] border-destructive/40 text-destructive">
                          {conf.category}
                        </Badge>
                      </td>
                      <td className="px-3 py-2 text-foreground font-medium">
                        {conf.message}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* Confirmed Sessions List */}
        {s.publishedSessions.length > 0 && (
          <div className="space-y-3 pt-2">
            <h3 className="font-serif text-sm font-bold text-foreground">
              Committed Interview Sessions ({s.publishedSessions.length})
            </h3>
            <div className="rounded-sm border border-border overflow-hidden">
              <table className="w-full text-left text-xs border-collapse">
                <thead className="bg-muted/40 border-b border-border text-[11px] font-semibold text-muted-foreground uppercase">
                  <tr>
                    <th className="px-3 py-2">Session ID</th>
                    <th className="px-3 py-2">Candidate</th>
                    <th className="px-3 py-2">Interviewer</th>
                    <th className="px-3 py-2">Room</th>
                    <th className="px-3 py-2">Time</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border/60">
                  {s.publishedSessions.map((sess) => (
                    <tr key={sess.sessionId} className="hover:bg-muted/30">
                      <td className="px-3 py-2 font-mono text-[11px] font-semibold text-academic-teal">
                        {sess.sessionId}
                      </td>
                      <td className="px-3 py-2 font-medium text-foreground">{sess.studentName}</td>
                      <td className="px-3 py-2 text-muted-foreground">{sess.interviewerName}</td>
                      <td className="px-3 py-2 text-muted-foreground">{sess.roomName}</td>
                      <td className="px-3 py-2 font-mono text-xs text-foreground">
                        {parseCampusLocalDateTime(sess.startTime).formattedTime}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* Actions */}
        <div className="flex flex-wrap items-center gap-3 pt-4 border-t border-border">
          <Button
            size="sm"
            onClick={() => onNavigateToConfirmedTimetable(s.driveId)}
            className="text-xs gap-1.5 bg-academic-teal hover:bg-academic-teal/90 text-warm-ivory shadow-xs"
          >
            <span>Inspect Confirmed Timetable</span>
            <ArrowRight className="h-3.5 w-3.5" />
          </Button>

          <Button
            variant="outline"
            size="sm"
            onClick={onGenerateNewPreview}
            className="text-xs gap-1.5"
          >
            <RefreshCw className="h-3.5 w-3.5" />
            <span>Generate Fresh Preview for Unconfirmed Candidates</span>
          </Button>
        </div>
      </div>
    )
  }

  // ============================================================================
  // Outcome 3: NONE_CONFIRMED (HTTP 409 from publication outcome)
  // ============================================================================
  if (outcomeState.type === 'NONE_CONFIRMED') {
    const s = outcomeState.summary
    return (
      <div className="rounded-md border border-destructive/40 bg-card p-6 space-y-6 shadow-xs max-w-4xl mx-auto">
        <div className="flex items-start gap-4">
          <div className="p-3 rounded-md bg-destructive/10 text-destructive shrink-0">
            <XCircle className="h-8 w-8" />
          </div>
          <div className="space-y-1">
            <div className="flex items-center gap-2">
              <h2 className="font-serif text-lg font-bold text-foreground">
                Timetable Publication Blocked — Zero Appointments Confirmed
              </h2>
              <Badge variant="outline" className="font-mono text-xs text-destructive border-destructive/40">
                HTTP 409 CONFLICT
              </Badge>
            </div>
            <p className="text-xs text-muted-foreground leading-relaxed">
              Zero appointments were confirmed. All proposals encountered concurrent resource conflicts or stale application states. Zero database mutations occurred in PostgreSQL.
            </p>
          </div>
        </div>

        {/* Recovery guidance */}
        <div className="rounded-sm border border-destructive/20 bg-destructive/5 p-3.5 text-xs space-y-1">
          <span className="font-semibold text-destructive">Resolution Guidance:</span>
          <p className="text-muted-foreground leading-relaxed">
            {s.recoveryInstructions}
          </p>
        </div>

        {/* Conflict Details */}
        {s.conflictRejections.length > 0 && (
          <div className="space-y-3 pt-2">
            <h3 className="font-serif text-sm font-bold text-foreground">
              Returned Business Conflicts ({s.conflictRejections.length})
            </h3>
            <div className="rounded-sm border border-border overflow-hidden">
              <table className="w-full text-left text-xs border-collapse">
                <thead className="bg-muted/40 border-b border-border text-[11px] font-semibold text-muted-foreground uppercase">
                  <tr>
                    <th className="px-3 py-2">Application ID</th>
                    <th className="px-3 py-2">Category</th>
                    <th className="px-3 py-2">Reason</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border/60">
                  {s.conflictRejections.map((conf, idx) => (
                    <tr key={idx} className="hover:bg-muted/30">
                      <td className="px-3 py-2 font-mono text-[11px] text-muted-foreground">{conf.applicationId}</td>
                      <td className="px-3 py-2">
                        <Badge variant="outline" className="font-mono text-[10px] text-destructive border-destructive/40">
                          {conf.category}
                        </Badge>
                      </td>
                      <td className="px-3 py-2 text-foreground font-medium">{conf.message}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        <div className="flex flex-wrap items-center gap-3 pt-4 border-t border-border">
          <Button
            size="sm"
            onClick={onGenerateNewPreview}
            className="text-xs gap-1.5 bg-academic-teal hover:bg-academic-teal/90 text-warm-ivory shadow-xs"
          >
            <RefreshCw className="h-3.5 w-3.5" />
            <span>Adjust Parameters & Generate Fresh Preview</span>
          </Button>

          <Button
            variant="outline"
            size="sm"
            onClick={() => onNavigateToConfirmedTimetable(s.driveId)}
            className="text-xs"
          >
            Inspect Confirmed Timetable
          </Button>
        </div>
      </div>
    )
  }

  // ============================================================================
  // Outcome 4: ALREADY_CONSUMED (HTTP 409 from PreviewConsumedException)
  // ============================================================================
  if (outcomeState.type === 'ALREADY_CONSUMED') {
    return (
      <div className="rounded-md border border-brass-600/40 bg-card p-6 space-y-5 shadow-xs max-w-3xl mx-auto">
        <div className="flex items-start gap-4">
          <div className="p-3 rounded-md bg-brass-600/10 text-brass-800 dark:text-brass-400 shrink-0">
            <ShieldAlert className="h-8 w-8" />
          </div>
          <div className="space-y-1">
            <div className="flex items-center gap-2">
              <h2 className="font-serif text-lg font-bold text-foreground">
                Preview Token Already Consumed — Replay Prevented
              </h2>
              <Badge variant="outline" className="font-mono text-xs text-brass-800 dark:text-brass-400 border-brass-600/40">
                HTTP 409 REPLAY
              </Badge>
            </div>
            <p className="text-xs text-muted-foreground leading-relaxed">
              This preview token has already been published. The backend strictly enforces single-use atomic consumption to prevent duplicate interview allocations.
            </p>
          </div>
        </div>

        <div className="rounded-sm border border-border bg-muted/20 p-3 text-xs text-muted-foreground leading-normal">
          <strong className="text-foreground">Backend Notice: </strong>
          {outcomeState.message}
        </div>

        <div className="flex flex-wrap items-center gap-3 pt-3 border-t border-border">
          <Button
            size="sm"
            onClick={() => onNavigateToConfirmedTimetable(outcomeState.driveId)}
            className="text-xs gap-1.5 bg-academic-teal hover:bg-academic-teal/90 text-warm-ivory shadow-xs"
          >
            <span>Inspect Confirmed Timetable</span>
            <ArrowRight className="h-3.5 w-3.5" />
          </Button>

          <Button
            variant="outline"
            size="sm"
            onClick={onGenerateNewPreview}
            className="text-xs"
          >
            Generate Fresh Preview
          </Button>
        </div>
      </div>
    )
  }

  // ============================================================================
  // Outcome 5: EXPIRED (HTTP 410 Gone from PreviewExpiredException)
  // ============================================================================
  if (outcomeState.type === 'EXPIRED') {
    return (
      <div className="rounded-md border border-border bg-card p-6 space-y-5 shadow-xs max-w-3xl mx-auto">
        <div className="flex items-start gap-4">
          <div className="p-3 rounded-md bg-muted/40 text-muted-foreground shrink-0">
            <Clock className="h-8 w-8" />
          </div>
          <div className="space-y-1">
            <div className="flex items-center gap-2">
              <h2 className="font-serif text-lg font-bold text-foreground">
                Scheduling Preview Token Expired
              </h2>
              <Badge variant="outline" className="font-mono text-xs">
                HTTP 410 GONE
              </Badge>
            </div>
            <p className="text-xs text-muted-foreground leading-relaxed">
              The 15-minute validity window for this advisory preview has elapsed on the server. Your configuration inputs have been preserved.
            </p>
          </div>
        </div>

        <div className="rounded-sm border border-border bg-muted/20 p-3 text-xs text-muted-foreground">
          {outcomeState.message}
        </div>

        <div className="pt-3 border-t border-border">
          <Button
            size="sm"
            onClick={onGenerateNewPreview}
            className="text-xs gap-1.5 bg-academic-teal hover:bg-academic-teal/90 text-warm-ivory shadow-xs"
          >
            <RefreshCw className="h-3.5 w-3.5" />
            <span>Generate Fresh Preview</span>
          </Button>
        </div>
      </div>
    )
  }

  // ============================================================================
  // Outcome 5b: NOT_FOUND_EVICTED (HTTP 404 Not Found from PreviewNotFoundException / Restart)
  // ============================================================================
  if (outcomeState.type === 'NOT_FOUND_EVICTED') {
    return (
      <div className="rounded-md border border-border bg-card p-6 space-y-5 shadow-xs max-w-3xl mx-auto">
        <div className="flex items-start gap-4">
          <div className="p-3 rounded-md bg-muted/40 text-muted-foreground shrink-0">
            <Info className="h-8 w-8" />
          </div>
          <div className="space-y-1">
            <div className="flex items-center gap-2">
              <h2 className="font-serif text-lg font-bold text-foreground">
                Scheduling Preview Not Found or Evicted
              </h2>
              <Badge variant="outline" className="font-mono text-xs text-muted-foreground border-border">
                HTTP 404 NOT FOUND
              </Badge>
            </div>
            <p className="text-xs text-muted-foreground leading-relaxed">
              This preview token is unknown or was cleared from server memory (e.g. backend process restart, cache eviction, or session boundary). Advisory previews are stored in instance memory and do not survive a server restart.
            </p>
          </div>
        </div>

        <div className="rounded-sm border border-border bg-muted/20 p-3 text-xs text-muted-foreground">
          <strong className="text-foreground">Server Message: </strong>
          {outcomeState.message}
        </div>

        <div className="flex flex-wrap items-center gap-3 pt-3 border-t border-border">
          <Button
            size="sm"
            onClick={onGenerateNewPreview}
            className="text-xs gap-1.5 bg-academic-teal hover:bg-academic-teal/90 text-warm-ivory shadow-xs"
          >
            <RefreshCw className="h-3.5 w-3.5" />
            <span>Generate Fresh Preview</span>
          </Button>

          <Button
            variant="outline"
            size="sm"
            onClick={() => onNavigateToConfirmedTimetable(outcomeState.driveId)}
            className="text-xs"
          >
            Inspect Confirmed Timetable
          </Button>
        </div>
      </div>
    )
  }

  // ============================================================================
  // Outcome 6: SERVER_ERROR_UNCERTAIN (HTTP 5xx Server / Database Error)
  // ============================================================================
  if (outcomeState.type === 'SERVER_ERROR_UNCERTAIN') {
    return (
      <div className="rounded-md border-2 border-destructive/60 bg-card p-6 space-y-5 shadow-xs max-w-3xl mx-auto">
        <div className="flex items-start gap-4">
          <div className="p-3 rounded-md bg-destructive/15 text-destructive shrink-0">
            <ShieldAlert className="h-8 w-8" />
          </div>
          <div className="space-y-1">
            <div className="flex items-center gap-2">
              <h2 className="font-serif text-lg font-bold text-destructive">
                Publication State Uncertain — Infrastructure Outage
              </h2>
              <Badge variant="outline" className="font-mono text-xs text-destructive border-destructive/40">
                HTTP 5xx OUTAGE
              </Badge>
            </div>
            <p className="text-xs text-muted-foreground leading-relaxed">
              An infrastructure error occurred during timetable publication. The backend consumes the preview token prior to persistence; some sessions may have already committed before the outage.
            </p>
          </div>
        </div>

        <div className="rounded-sm border border-destructive/30 bg-destructive/5 p-3.5 space-y-2 text-xs">
          <span className="font-semibold text-destructive block">
            Critical Reconciliation Protocol:
          </span>
          <ul className="list-disc list-inside space-y-1 text-muted-foreground leading-relaxed">
            <li>
              <strong className="text-foreground">DO NOT assume zero sessions were committed:</strong> Because transactions are per-session, individual sessions may have persisted in PostgreSQL before the connection dropped.
            </li>
            <li>
              <strong className="text-foreground">Inspect Persisted Sessions:</strong> Navigate to Confirmed Timetable to reconcile which candidate sessions are actively committed in the database.
            </li>
            <li>
              <strong className="text-foreground">Do not blindly retry:</strong> Generating a new preview is safe only after reviewing confirmed sessions.
            </li>
          </ul>
        </div>

        <div className="flex flex-wrap items-center gap-3 pt-3 border-t border-border">
          <Button
            size="sm"
            onClick={() => onNavigateToConfirmedTimetable(outcomeState.driveId)}
            className="text-xs gap-1.5 bg-academic-teal hover:bg-academic-teal/90 text-warm-ivory shadow-xs"
          >
            <span>Inspect Confirmed Timetable for Reconciliation</span>
            <ExternalLink className="h-3.5 w-3.5" />
          </Button>

          <Button
            variant="outline"
            size="sm"
            onClick={onGenerateNewPreview}
            className="text-xs"
          >
            Create New Preview After Review
          </Button>
        </div>
      </div>
    )
  }

  // ============================================================================
  // Outcome 7: NETWORK_ERROR_AMBIGUOUS (Lost HTTP Response / Network Failure)
  // ============================================================================
  return (
    <div className="rounded-md border-2 border-brass-600/60 bg-card p-6 space-y-5 shadow-xs max-w-3xl mx-auto">
      <div className="flex items-start gap-4">
        <div className="p-3 rounded-md bg-brass-600/15 text-brass-800 dark:text-brass-400 shrink-0">
          <AlertTriangle className="h-8 w-8" />
        </div>
        <div className="space-y-1">
          <div className="flex items-center gap-2">
            <h2 className="font-serif text-lg font-bold text-foreground">
              Lost HTTP Connection — Publication Status Ambiguous
            </h2>
            <Badge variant="outline" className="font-mono text-xs text-brass-800 dark:text-brass-400 border-brass-600/40">
              NETWORK UNCERTAIN
            </Badge>
          </div>
          <p className="text-xs text-muted-foreground leading-relaxed">
            The network connection dropped while awaiting the publication response from the server. Automatic retry has been blocked to protect against duplicate database mutations.
          </p>
        </div>
      </div>

      <div className="rounded-sm border border-border bg-muted/20 p-3.5 text-xs text-muted-foreground leading-relaxed">
        <p>
          <strong className="text-foreground">Reconciliation Step: </strong>
          Please navigate to the Confirmed Timetable to determine whether the server successfully processed and committed the publication attempt.
        </p>
      </div>

      <div className="flex flex-wrap items-center gap-3 pt-3 border-t border-border">
        <Button
          size="sm"
          onClick={() => onNavigateToConfirmedTimetable(outcomeState.driveId)}
          className="text-xs gap-1.5 bg-academic-teal hover:bg-academic-teal/90 text-warm-ivory shadow-xs"
        >
          <span>Check Confirmed Timetable</span>
          <ArrowRight className="h-3.5 w-3.5" />
        </Button>

        <Button
          variant="outline"
          size="sm"
          onClick={onGenerateNewPreview}
          className="text-xs"
        >
          Back to Scheduling Studio
        </Button>
      </div>
    </div>
  )
}
