import * as React from 'react'
import { AlertCircle, UserX, Info } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import type { UnscheduledCandidateDto } from '@/types/api'

export interface UnscheduledCandidatesListProps {
  unscheduledCandidates: UnscheduledCandidateDto[]
}

export const UnscheduledCandidatesList: React.FC<UnscheduledCandidatesListProps> = ({
  unscheduledCandidates,
}) => {
  if (unscheduledCandidates.length === 0) {
    return null
  }

  return (
    <div className="rounded-md border border-brass-600/30 bg-card p-5 space-y-4 shadow-xs">
      {/* Section Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 pb-3 border-b border-border/80">
        <div className="flex items-center gap-2">
          <div className="p-1 rounded-sm bg-brass-600/10 text-brass-800 dark:text-brass-400">
            <UserX className="h-4 w-4" />
          </div>
          <div>
            <h3 className="font-serif text-sm font-bold text-foreground flex items-center gap-2">
              <span>Unscheduled Shortlisted Candidates</span>
              <Badge variant="outline" className="font-mono text-[10px] text-brass-800 dark:text-brass-400 border-brass-600/40">
                {unscheduledCandidates.length} unallocated
              </Badge>
            </h3>
            <p className="text-[11px] text-muted-foreground mt-0.5">
              The GreedyScheduler could not allocate conflict-free interview appointments for these candidates within the requested parameters.
            </p>
          </div>
        </div>
      </div>

      {/* Explanatory Context Notice */}
      <div className="rounded-sm border border-brass-600/20 bg-brass-600/5 p-3 text-xs flex items-start gap-2.5">
        <Info className="h-4 w-4 text-brass-800 dark:text-brass-400 shrink-0 mt-0.5" />
        <div className="space-y-1 text-muted-foreground leading-normal">
          <p>
            <strong className="text-foreground font-semibold">Important Status Distinction: </strong>
            These candidate applications are <strong className="text-foreground">NOT rejected</strong>. They remain in{' '}
            <strong className="text-foreground">SHORTLISTED</strong> status in the institutional audit registry.
          </p>
          <p className="text-[11px]">
            To accommodate these candidates, you may expand the scheduling window, add additional interview rooms or registered interviewers, or schedule an additional interview session date.
          </p>
        </div>
      </div>

      {/* Unscheduled Candidate Table */}
      <div className="rounded-sm border border-border overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs border-collapse">
            <thead className="bg-muted/40 border-b border-border text-[11px] font-semibold text-muted-foreground uppercase tracking-wider">
              <tr>
                <th className="px-3 py-2">Student Name</th>
                <th className="px-3 py-2">Student ID</th>
                <th className="px-3 py-2">Branch</th>
                <th className="px-3 py-2">Application ID</th>
                <th className="px-3 py-2">Algorithmic Non-Allocation Reason</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border/60">
              {unscheduledCandidates.map((c) => (
                <tr key={c.applicationId} className="hover:bg-muted/30 transition-colors">
                  <td className="px-3 py-2.5 font-semibold text-foreground">
                    {c.studentName}
                  </td>
                  <td className="px-3 py-2.5 font-mono text-[11px] text-muted-foreground">
                    {c.studentId}
                  </td>
                  <td className="px-3 py-2.5 text-muted-foreground">
                    {c.studentBranch}
                  </td>
                  <td className="px-3 py-2.5 font-mono text-[11px] text-muted-foreground">
                    {c.applicationId}
                  </td>
                  <td className="px-3 py-2.5">
                    <div className="flex items-start gap-1.5 text-brass-800 dark:text-brass-300 font-medium">
                      <AlertCircle className="h-3.5 w-3.5 text-brass-600 shrink-0 mt-0.5" />
                      <span>{c.reason}</span>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}
