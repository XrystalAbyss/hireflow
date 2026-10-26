import * as React from 'react'
import { Clock, User, DoorOpen } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import type { ProposedSessionDto } from '@/types/api'
import { parseCampusLocalDateTime } from '../../utils/schedulingUtils'

export interface SchedulingTimeGridProps {
  proposedSessions: ProposedSessionDto[]
  bufferMinutes: number
}

export const SchedulingTimeGrid: React.FC<SchedulingTimeGridProps> = ({
  proposedSessions,
  bufferMinutes,
}) => {
  // Group sessions by Room to create distinct lanes
  const roomLanes = React.useMemo(() => {
    const lanes: Record<string, { roomName: string; sessions: ProposedSessionDto[] }> = {}
    proposedSessions.forEach((sess) => {
      if (!lanes[sess.roomId]) {
        lanes[sess.roomId] = { roomName: sess.roomName, sessions: [] }
      }
      lanes[sess.roomId].sessions.push(sess)
    })
    // Sort sessions in each lane chronologically
    Object.values(lanes).forEach((lane) => {
      lane.sessions.sort((a, b) => a.startTime.localeCompare(b.startTime))
    })
    return lanes
  }, [proposedSessions])

  const roomIds = Object.keys(roomLanes)

  if (proposedSessions.length === 0) {
    return (
      <div className="p-8 text-center rounded-sm border border-dashed border-border bg-muted/10 text-muted-foreground text-xs">
        Zero appointments were allocated by the GreedyScheduler in this time window.
      </div>
    )
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 text-xs text-muted-foreground">
        <span>
          Concurrent and consecutive appointments organized across{' '}
          <strong className="text-foreground">{roomIds.length} room lane(s)</strong>.
        </span>
        <div className="flex items-center gap-3">
          <span className="flex items-center gap-1.5">
            <span className="h-3 w-3 rounded-xs bg-academic-teal/15 border border-academic-teal/40 inline-block" />
            <span>Advisory Appointment</span>
          </span>
          <span className="flex items-center gap-1.5">
            <span className="h-3 w-3 rounded-xs bg-brass-600/15 border border-brass-600/40 inline-block" />
            <span>Buffer Interval ({bufferMinutes}m)</span>
          </span>
        </div>
      </div>

      {/* Visual Timeline Lanes Grid */}
      <div className="overflow-x-auto pb-2">
        <div className="min-w-[700px] grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {roomIds.map((rId) => {
            const lane = roomLanes[rId]
            return (
              <div
                key={rId}
                className="rounded-md border border-border bg-card overflow-hidden shadow-xs flex flex-col"
              >
                {/* Lane Header */}
                <div className="bg-muted/40 px-3.5 py-2.5 border-b border-border flex items-center justify-between text-xs">
                  <div className="flex items-center gap-2 font-semibold text-foreground truncate">
                    <DoorOpen className="h-4 w-4 text-academic-teal shrink-0" />
                    <span className="truncate">{lane.roomName}</span>
                  </div>
                  <Badge variant="outline" className="font-mono text-[10px] shrink-0">
                    {rId}
                  </Badge>
                </div>

                {/* Session Blocks in Lane */}
                <div className="p-3 space-y-3 flex-1 bg-background/50">
                  {lane.sessions.map((sess, idx) => {
                    const start = parseCampusLocalDateTime(sess.startTime)
                    const end = parseCampusLocalDateTime(sess.endTime)
                    const isLast = idx === lane.sessions.length - 1

                    return (
                      <React.Fragment key={sess.sessionId}>
                        {/* Appointment Card */}
                        <div className="rounded-sm border border-academic-teal/30 bg-academic-teal/5 p-3 space-y-2 hover:border-academic-teal transition-colors shadow-2xs">
                          {/* Time Interval & Advisory Badge */}
                          <div className="flex items-center justify-between gap-2">
                            <span className="font-mono text-xs font-bold text-academic-teal flex items-center gap-1">
                              <Clock className="h-3.5 w-3.5" />
                              <span>{start.formattedTime} – {end.formattedTime}</span>
                            </span>
                            <Badge
                              variant="outline"
                              className="text-[9px] font-mono uppercase bg-academic-teal/10 text-academic-teal border-academic-teal/30"
                            >
                              Advisory Slot
                            </Badge>
                          </div>

                          {/* Candidate Identity */}
                          <div className="space-y-0.5 pt-1 border-t border-academic-teal/15">
                            <div className="flex items-center justify-between">
                              <span className="font-semibold text-xs text-foreground truncate">
                                {sess.studentName}
                              </span>
                              <span className="font-mono text-[11px] text-muted-foreground shrink-0">
                                {sess.studentId}
                              </span>
                            </div>
                            <p className="text-[11px] text-muted-foreground truncate">
                              {sess.studentBranch}
                            </p>
                          </div>

                          {/* Interviewer Identity */}
                          <div className="pt-1 flex items-center justify-between text-[11px] text-muted-foreground border-t border-academic-teal/15">
                            <div className="flex items-center gap-1.5 truncate">
                              <User className="h-3 w-3 text-academic-teal shrink-0" />
                              <span className="truncate">{sess.interviewerName}</span>
                            </div>
                            <span className="font-mono text-[10px] shrink-0 text-muted-foreground">
                              {sess.interviewerId}
                            </span>
                          </div>
                        </div>

                        {/* Buffer Interval indicator between consecutive appointments */}
                        {!isLast && bufferMinutes > 0 && (
                          <div className="flex items-center justify-center gap-2 py-1 text-[10px] font-mono text-brass-800 dark:text-brass-400">
                            <div className="h-px flex-1 bg-brass-600/30" />
                            <span className="px-2 py-0.5 rounded-xs bg-brass-600/10 border border-brass-600/20">
                              + {bufferMinutes}m buffer
                            </span>
                            <div className="h-px flex-1 bg-brass-600/30" />
                          </div>
                        )}
                      </React.Fragment>
                    )
                  })}
                </div>
              </div>
            )
          })}
        </div>
      </div>
    </div>
  )
}
