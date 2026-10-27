import * as React from 'react'
import { Search, Clock, User, DoorOpen } from 'lucide-react'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import type { ProposedSessionDto } from '@/types/api'
import { parseCampusLocalDateTime } from '../../utils/schedulingUtils'

export interface SchedulingDataTableProps {
  proposedSessions: ProposedSessionDto[]
}

export const SchedulingDataTable: React.FC<SchedulingDataTableProps> = ({
  proposedSessions,
}) => {
  const [search, setSearch] = React.useState('')

  const filteredSessions = React.useMemo(() => {
    if (!search.trim()) return proposedSessions
    const q = search.trim().toLowerCase()
    return proposedSessions.filter(
      (s) =>
        s.studentName.toLowerCase().includes(q) ||
        s.studentId.toLowerCase().includes(q) ||
        s.interviewerName.toLowerCase().includes(q) ||
        s.roomName.toLowerCase().includes(q) ||
        s.studentBranch.toLowerCase().includes(q)
    )
  }, [proposedSessions, search])

  return (
    <div className="space-y-3">
      {/* Search Filter Bar */}
      <div className="flex items-center justify-between gap-3">
        <div className="relative flex-1 max-w-sm">
          <Search className="absolute left-2.5 top-2.5 h-3.5 w-3.5 text-muted-foreground" />
          <Input
            type="search"
            placeholder="Search candidate, student ID, interviewer, or room..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="h-8 pl-8 text-xs"
          />
        </div>
        <span className="text-xs text-muted-foreground font-mono">
          Showing {filteredSessions.length} of {proposedSessions.length} proposals
        </span>
      </div>

      {/* Accessible Table */}
      <div className="rounded-md border border-border bg-card overflow-hidden shadow-xs">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs border-collapse">
            <thead className="bg-muted/50 border-b border-border text-[11px] font-semibold text-muted-foreground uppercase tracking-wider">
              <tr>
                <th className="px-3.5 py-2.5">Slot ID</th>
                <th className="px-3.5 py-2.5">Candidate (Student ID)</th>
                <th className="px-3.5 py-2.5">Branch</th>
                <th className="px-3.5 py-2.5">Interviewer</th>
                <th className="px-3.5 py-2.5">Interview Room</th>
                <th className="px-3.5 py-2.5">Allocated Time</th>
                <th className="px-3.5 py-2.5 text-right">Advisory Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border/60">
              {filteredSessions.length === 0 ? (
                <tr>
                  <td colSpan={7} className="px-4 py-8 text-center text-muted-foreground">
                    No proposed interview appointments match the filter criteria.
                  </td>
                </tr>
              ) : (
                filteredSessions.map((sess) => {
                  const start = parseCampusLocalDateTime(sess.startTime)
                  const end = parseCampusLocalDateTime(sess.endTime)

                  return (
                    <tr
                      key={sess.sessionId}
                      className="hover:bg-muted/40 transition-colors"
                    >
                      {/* Slot ID */}
                      <td className="px-3.5 py-2.5 font-mono text-[11px] text-muted-foreground whitespace-nowrap">
                        {sess.sessionId}
                      </td>

                      {/* Candidate Identity */}
                      <td className="px-3.5 py-2.5">
                        <div className="font-semibold text-foreground">
                          {sess.studentName}
                        </div>
                        <div className="font-mono text-[11px] text-muted-foreground">
                          {sess.studentId}
                        </div>
                      </td>

                      {/* Branch */}
                      <td className="px-3.5 py-2.5 text-muted-foreground">
                        {sess.studentBranch}
                      </td>

                      {/* Interviewer */}
                      <td className="px-3.5 py-2.5">
                        <div className="flex items-center gap-1.5 text-foreground font-medium">
                          <User className="h-3 w-3 text-academic-teal shrink-0" />
                          <span>{sess.interviewerName}</span>
                        </div>
                        <div className="font-mono text-[10px] text-muted-foreground">
                          {sess.interviewerId}
                        </div>
                      </td>

                      {/* Room */}
                      <td className="px-3.5 py-2.5">
                        <div className="flex items-center gap-1.5 text-foreground font-medium">
                          <DoorOpen className="h-3 w-3 text-academic-teal shrink-0" />
                          <span>{sess.roomName}</span>
                        </div>
                        <div className="font-mono text-[10px] text-muted-foreground">
                          {sess.roomId}
                        </div>
                      </td>

                      {/* Time Range */}
                      <td className="px-3.5 py-2.5 font-mono text-xs text-foreground whitespace-nowrap">
                        <div className="flex items-center gap-1 font-semibold text-academic-teal">
                          <Clock className="h-3 w-3" />
                          <span>{start.formattedTime} – {end.formattedTime}</span>
                        </div>
                        <div className="text-[10px] text-muted-foreground">
                          {start.formattedDate}
                        </div>
                      </td>

                      {/* Advisory Status */}
                      <td className="px-3.5 py-2.5 text-right whitespace-nowrap">
                        <Badge
                          variant="outline"
                          className="font-mono text-[10px] uppercase bg-academic-teal/10 text-academic-teal border-academic-teal/30"
                        >
                          PROPOSED
                        </Badge>
                      </td>
                    </tr>
                  )
                })
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}
