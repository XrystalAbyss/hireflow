import * as React from 'react'
import {
  Clock,
  User,
  DoorOpen,
  Filter,
  Eye,
  RefreshCw,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Pagination } from '@/components/ui/pagination'
import { ApplicationStatusBadge } from '@/components/domain/application-status-badge'
import type {
  OfficerInterviewDetailDto,
  OfficerDriveDetailDto,
  OfficerCompanyDetailDto,
  ApplicationStatus,
} from '@/types/api'
import { parseCampusLocalDateTime } from '../../utils/schedulingUtils'
import { ConfirmedSessionDetailDialog } from './ConfirmedSessionDetailDialog'

export interface ConfirmedTimetableViewProps {
  sessions: OfficerInterviewDetailDto[]
  totalElements: number
  totalPages: number
  page: number
  size: number
  onPageChange: (page: number) => void
  onPageSizeChange: (size: number) => void
  drives: OfficerDriveDetailDto[]
  companies: OfficerCompanyDetailDto[]
  selectedDriveId?: string
  onDriveChange: (driveId?: string) => void
  selectedCompanyId?: string
  onCompanyChange: (companyId?: string) => void
  selectedDate?: string
  onDateChange: (date?: string) => void
  isLoading: boolean
  onRefetch: () => void
}

export const ConfirmedTimetableView: React.FC<ConfirmedTimetableViewProps> = ({
  sessions,
  totalElements,
  totalPages,
  page,
  size,
  onPageChange,
  onPageSizeChange,
  drives,
  companies,
  selectedDriveId,
  onDriveChange,
  selectedCompanyId,
  onCompanyChange,
  selectedDate,
  onDateChange,
  isLoading,
  onRefetch,
}) => {
  const [activeSession, setActiveSession] = React.useState<OfficerInterviewDetailDto | null>(null)
  const [dialogOpen, setDialogOpen] = React.useState(false)

  const handleOpenDetail = (sess: OfficerInterviewDetailDto) => {
    setActiveSession(sess)
    setDialogOpen(true)
  }

  const hasActiveFilters = Boolean(selectedDriveId || selectedCompanyId || selectedDate)

  const clearFilters = () => {
    onDriveChange(undefined)
    onCompanyChange(undefined)
    onDateChange(undefined)
  }

  return (
    <div className="space-y-4">
      {/* Filter Toolbar */}
      <div className="rounded-md border border-border bg-card p-4 space-y-3 shadow-xs">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
          <div className="flex items-center gap-2">
            <Filter className="h-4 w-4 text-academic-teal" />
            <h3 className="font-serif text-sm font-bold text-foreground">
              Filter Confirmed Timetable
            </h3>
          </div>
          {hasActiveFilters && (
            <button
              type="button"
              onClick={clearFilters}
              className="text-xs text-academic-teal hover:underline font-medium cursor-pointer"
            >
              Clear all filters
            </button>
          )}
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 text-xs">
          {/* Drive Filter */}
          <div className="space-y-1">
            <label htmlFor="filter-drive-select" className="text-muted-foreground font-medium">Recruitment Drive</label>
            <select
              id="filter-drive-select"
              value={selectedDriveId || ''}
              onChange={(e) => onDriveChange(e.target.value || undefined)}
              className="w-full h-8 rounded-sm border border-input bg-background px-2.5 text-xs text-foreground focus:outline-none focus:ring-1 focus:ring-academic-teal"
            >
              <option value="">All Recruitment Drives</option>
              {drives.map((d) => (
                <option key={d.id} value={d.id}>
                  {d.jobTitle} ({d.id})
                </option>
              ))}
            </select>
          </div>

          {/* Company Filter */}
          <div className="space-y-1">
            <label htmlFor="filter-company-select" className="text-muted-foreground font-medium">Corporate Partner</label>
            <select
              id="filter-company-select"
              value={selectedCompanyId || ''}
              onChange={(e) => onCompanyChange(e.target.value || undefined)}
              className="w-full h-8 rounded-sm border border-input bg-background px-2.5 text-xs text-foreground focus:outline-none focus:ring-1 focus:ring-academic-teal"
            >
              <option value="">All Corporate Partners</option>
              {companies.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name} ({c.id})
                </option>
              ))}
            </select>
          </div>

          {/* Date Filter */}
          <div className="space-y-1">
            <label htmlFor="filter-date-input" className="text-muted-foreground font-medium">Interview Date</label>
            <Input
              id="filter-date-input"
              type="date"
              value={selectedDate || ''}
              onChange={(e) => onDateChange(e.target.value || undefined)}
              className="h-8 text-xs font-mono"
            />
          </div>
        </div>
      </div>

      {/* Results Header */}
      <div className="flex items-center justify-between text-xs text-muted-foreground">
        <span>
          Confirmed Appointments:{' '}
          <strong className="text-foreground font-semibold">{totalElements}</strong>
        </span>
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={onRefetch}
          disabled={isLoading}
          className="h-7 text-xs gap-1.5"
        >
          <RefreshCw className={`h-3 w-3 ${isLoading ? 'animate-spin' : ''}`} />
          <span>Refresh</span>
        </Button>
      </div>

      {/* Desktop Timetable View (>= 768px) */}
      <div className="hidden md:block rounded-md border border-border bg-card overflow-hidden shadow-xs">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs border-collapse">
            <thead className="bg-muted/50 border-b border-border text-[11px] font-semibold text-muted-foreground uppercase tracking-wider">
              <tr>
                <th className="px-3.5 py-2.5">Session ID</th>
                <th className="px-3.5 py-2.5">Candidate (Student ID)</th>
                <th className="px-3.5 py-2.5">Recruitment Drive</th>
                <th className="px-3.5 py-2.5">Interviewer</th>
                <th className="px-3.5 py-2.5">Room</th>
                <th className="px-3.5 py-2.5">Interview Time</th>
                <th className="px-3.5 py-2.5">Status</th>
                <th className="px-3.5 py-2.5 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border/60">
              {sessions.length === 0 ? (
                <tr>
                  <td colSpan={8} className="px-4 py-12 text-center text-muted-foreground">
                    <p className="font-medium text-foreground">No confirmed interview appointments found.</p>
                    <p className="text-[11px] mt-1">
                      {hasActiveFilters
                        ? 'Try clearing or modifying the search filters above.'
                        : 'Generate and publish an advisory timetable in the Scheduling Studio to commit sessions.'}
                    </p>
                  </td>
                </tr>
              ) : (
                sessions.map((sess) => {
                  const start = parseCampusLocalDateTime(sess.startTime)
                  const end = parseCampusLocalDateTime(sess.endTime)

                  return (
                    <tr
                      key={sess.sessionId}
                      className="hover:bg-muted/40 transition-colors cursor-pointer"
                      onClick={() => handleOpenDetail(sess)}
                    >
                      <td className="px-3.5 py-2.5 font-mono text-[11px] font-semibold text-academic-teal">
                        {sess.sessionId}
                      </td>
                      <td className="px-3.5 py-2.5">
                        <div className="font-semibold text-foreground">{sess.studentName}</div>
                        <div className="font-mono text-[11px] text-muted-foreground">{sess.studentId}</div>
                      </td>
                      <td className="px-3.5 py-2.5">
                        <div className="font-medium text-foreground truncate max-w-[200px]">{sess.jobTitle}</div>
                        <div className="text-[11px] text-muted-foreground">{sess.companyName}</div>
                      </td>
                      <td className="px-3.5 py-2.5">
                        <div className="flex items-center gap-1.5 text-foreground font-medium">
                          <User className="h-3 w-3 text-academic-teal shrink-0" />
                          <span>{sess.interviewerName}</span>
                        </div>
                      </td>
                      <td className="px-3.5 py-2.5">
                        <div className="flex items-center gap-1.5 text-foreground font-medium">
                          <DoorOpen className="h-3 w-3 text-academic-teal shrink-0" />
                          <span>{sess.roomName}</span>
                        </div>
                      </td>
                      <td className="px-3.5 py-2.5 font-mono text-xs whitespace-nowrap">
                        <div className="font-semibold text-academic-teal">
                          {start.formattedTime} – {end.formattedTime}
                        </div>
                        <div className="text-[10px] text-muted-foreground">
                          {start.formattedDate}
                        </div>
                      </td>
                      <td className="px-3.5 py-2.5">
                        <ApplicationStatusBadge status={sess.status as ApplicationStatus} />
                      </td>
                      <td className="px-3.5 py-2.5 text-right">
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={(e) => {
                            e.stopPropagation()
                            handleOpenDetail(sess)
                          }}
                          className="h-7 px-2 text-xs text-academic-teal gap-1 hover:bg-academic-teal/10"
                        >
                          <Eye className="h-3 w-3" />
                          <span>Briefing</span>
                        </Button>
                      </td>
                    </tr>
                  )
                })
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Mobile Stacked Appointment Cards (< 768px) */}
      <div className="md:hidden space-y-3">
        {sessions.length === 0 ? (
          <div className="rounded-md border border-border bg-card p-6 text-center text-xs text-muted-foreground">
            No confirmed appointments found matching criteria.
          </div>
        ) : (
          sessions.map((sess) => {
            const start = parseCampusLocalDateTime(sess.startTime)
            const end = parseCampusLocalDateTime(sess.endTime)

            return (
              <div
                key={sess.sessionId}
                onClick={() => handleOpenDetail(sess)}
                className="rounded-md border border-border bg-card p-4 space-y-2.5 shadow-2xs cursor-pointer hover:border-academic-teal transition-colors"
              >
                <div className="flex justify-between items-start">
                  <div>
                    <span className="font-mono text-[11px] font-semibold text-academic-teal">
                      {sess.sessionId}
                    </span>
                    <h4 className="font-semibold text-xs text-foreground mt-0.5">
                      {sess.studentName}
                    </h4>
                    <span className="font-mono text-[10px] text-muted-foreground">
                      {sess.studentId} • {sess.studentBranch}
                    </span>
                  </div>
                  <ApplicationStatusBadge status={sess.status as ApplicationStatus} />
                </div>

                <div className="pt-2 border-t border-border/60 text-xs space-y-1">
                  <div className="flex items-center gap-1.5 text-foreground font-semibold font-mono text-academic-teal">
                    <Clock className="h-3.5 w-3.5" />
                    <span>{start.formattedTime} – {end.formattedTime}</span>
                    <span className="text-muted-foreground font-normal text-[11px]">({start.formattedDate})</span>
                  </div>
                  <div className="flex items-center justify-between text-muted-foreground text-[11px] pt-1">
                    <span className="truncate">{sess.interviewerName}</span>
                    <span className="font-medium text-foreground">{sess.roomName}</span>
                  </div>
                </div>
              </div>
            )
          })
        )}
      </div>

      {/* Pagination */}
      {totalPages > 1 && (
        <Pagination
          page={page}
          totalPages={totalPages}
          pageSize={size}
          totalElements={totalElements}
          onPageChange={onPageChange}
          onPageSizeChange={onPageSizeChange}
        />
      )}

      {/* Session Detail Modal */}
      <ConfirmedSessionDetailDialog
        session={activeSession}
        open={dialogOpen}
        onOpenChange={setDialogOpen}
      />
    </div>
  )
}
