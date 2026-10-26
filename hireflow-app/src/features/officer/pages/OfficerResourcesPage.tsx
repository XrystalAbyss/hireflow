import * as React from 'react'
import {
  Users,
  DoorOpen,
  Plus,
  Edit3,
  Info,
} from 'lucide-react'
import { PageHeading } from '@/components/domain/page-heading'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { InterviewerDialog } from '../components/InterviewerDialog'
import { RoomDialog } from '../components/RoomDialog'
import {
  useOfficerInterviewers,
  useOfficerRooms,
  useOfficerCompanies,
} from '../api/officerApi'
import type {
  OfficerInterviewerDetailDto,
  OfficerRoomDetailDto,
  OfficerCompanyDetailDto,
} from '@/types/api'

export function OfficerResourcesPage() {
  const [activeTab, setActiveTab] = React.useState<'interviewers' | 'rooms'>('interviewers')
  const [selectedCompanyId, setSelectedCompanyId] = React.useState('')

  const [interviewerModalOpen, setInterviewerModalOpen] = React.useState(false)
  const [editingInterviewer, setEditingInterviewer] = React.useState<OfficerInterviewerDetailDto | null>(null)

  const [roomModalOpen, setRoomModalOpen] = React.useState(false)
  const [editingRoom, setEditingRoom] = React.useState<OfficerRoomDetailDto | null>(null)

  const { data: companiesData } = useOfficerCompanies(0, 100)
  const companies: OfficerCompanyDetailDto[] = companiesData?.content || []

  const {
    data: interviewers = [],
    isLoading: loadingInterviewers,
    refetch: refetchInterviewers,
  } = useOfficerInterviewers(selectedCompanyId || undefined)

  const {
    data: rooms = [],
    isLoading: loadingRooms,
    refetch: refetchRooms,
  } = useOfficerRooms()

  const handleCreateInterviewer = () => {
    setEditingInterviewer(null)
    setInterviewerModalOpen(true)
  }

  const handleEditInterviewer = (item: OfficerInterviewerDetailDto) => {
    setEditingInterviewer(item)
    setInterviewerModalOpen(true)
  }

  const handleCreateRoom = () => {
    setEditingRoom(null)
    setRoomModalOpen(true)
  }

  const handleEditRoom = (item: OfficerRoomDetailDto) => {
    setEditingRoom(item)
    setRoomModalOpen(true)
  }

  return (
    <div className="space-y-6">
      <PageHeading
        title="Interview Resource Administration"
        description="Manage the institutional pools of corporate interviewers and campus interview facilities for timetable allocation."
        breadcrumbs={[
          { label: 'Officer', href: '/officer/dashboard' },
          { label: 'Resource Pools' },
        ]}
        actions={
          activeTab === 'interviewers' ? (
            <Button
              size="sm"
              onClick={handleCreateInterviewer}
              className="text-xs gap-1.5"
            >
              <Plus className="h-3.5 w-3.5" />
              <span>Register Interviewer</span>
            </Button>
          ) : (
            <Button
              size="sm"
              onClick={handleCreateRoom}
              className="text-xs gap-1.5"
            >
              <Plus className="h-3.5 w-3.5" />
              <span>Register Interview Room</span>
            </Button>
          )
        }
      />

      {/* Advisory Context Banner */}
      <div className="rounded-md border border-accent/30 bg-accent/5 p-3.5 flex items-start gap-3 text-xs text-foreground">
        <Info className="h-4 w-4 text-accent shrink-0 mt-0.5" />
        <div className="space-y-1">
          <p className="font-semibold text-accent">
            Resource Directory Policy
          </p>
          <p className="text-muted-foreground leading-relaxed">
            Registered rooms and interviewers form the campus availability pool. The Interview Scheduling Studio checks booking conflicts and slot constraints when officers prepare a timetable.
          </p>
        </div>
      </div>

      {/* Resource Category Tabs */}
      <div className="flex items-center gap-2 border-b border-border">
        <button
          type="button"
          onClick={() => setActiveTab('interviewers')}
          className={`flex items-center gap-2 px-4 py-2.5 text-xs font-medium border-b-2 transition-colors cursor-pointer select-none ${
            activeTab === 'interviewers'
              ? 'border-primary text-foreground font-semibold'
              : 'border-transparent text-muted-foreground hover:text-foreground'
          }`}
        >
          <Users className="h-4 w-4 text-accent" />
          <span>Corporate Interviewers</span>
          <Badge variant="secondary" className="text-[11px] font-mono ml-1 px-1.5">
            {interviewers.length}
          </Badge>
        </button>

        <button
          type="button"
          onClick={() => setActiveTab('rooms')}
          className={`flex items-center gap-2 px-4 py-2.5 text-xs font-medium border-b-2 transition-colors cursor-pointer select-none ${
            activeTab === 'rooms'
              ? 'border-primary text-foreground font-semibold'
              : 'border-transparent text-muted-foreground hover:text-foreground'
          }`}
        >
          <DoorOpen className="h-4 w-4 text-brass-600" />
          <span>Campus Interview Rooms</span>
          <Badge variant="secondary" className="text-[11px] font-mono ml-1 px-1.5">
            {rooms.length}
          </Badge>
        </button>
      </div>

      {/* TAB 1: INTERVIEWERS */}
      {activeTab === 'interviewers' && (
        <div className="space-y-4">
          {/* Company Filter Bar */}
          <div className="flex flex-col sm:flex-row items-center justify-between gap-3 p-3.5 rounded-md border border-border bg-card shadow-xs">
            <div className="w-full sm:w-72">
              <select
                value={selectedCompanyId}
                onChange={(e) => setSelectedCompanyId(e.target.value)}
                className="w-full h-9 rounded-sm border border-input bg-card px-2.5 text-xs text-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
                aria-label="Filter interviewers by corporate partner"
              >
                <option value="">All Corporate Partners</option>
                {companies.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name} ({c.id})
                  </option>
                ))}
              </select>
            </div>
            <div className="text-xs text-muted-foreground font-mono self-end sm:self-center">
              Showing {interviewers.length} corporate assessment leads
            </div>
          </div>

          {/* Interviewers Table */}
          <div className="rounded-md border border-border bg-card shadow-xs overflow-hidden">
            {loadingInterviewers ? (
              <div className="p-8 text-center text-xs text-muted-foreground animate-pulse">
                Loading interviewer directory...
              </div>
            ) : interviewers.length === 0 ? (
              <div className="p-8 text-center text-xs text-muted-foreground">
                No corporate interviewers registered. Click "Register Interviewer" to add personnel.
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs border-collapse">
                  <thead className="bg-muted/60 border-b border-border text-muted-foreground font-mono uppercase tracking-wider">
                    <tr>
                      <th className="py-2.5 px-3.5">Interviewer ID</th>
                      <th className="py-2.5 px-3.5">Full Name & Title</th>
                      <th className="py-2.5 px-3.5">Employer Organization</th>
                      <th className="py-2.5 px-3.5 text-right">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border">
                    {interviewers.map((i) => (
                      <tr key={i.id} className="hover:bg-muted/40 transition-colors">
                        <td className="py-2.5 px-3.5 font-mono text-muted-foreground font-semibold">
                          {i.id}
                        </td>
                        <td className="py-2.5 px-3.5 font-medium text-foreground">
                          {i.name}
                        </td>
                        <td className="py-2.5 px-3.5 text-muted-foreground">
                          {i.companyName} ({i.companyId})
                        </td>
                        <td className="py-2.5 px-3.5 text-right whitespace-nowrap">
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => handleEditInterviewer(i)}
                            className="h-7 text-xs px-2 gap-1"
                            aria-label={`Edit interviewer ${i.name}`}
                          >
                            <Edit3 className="h-3 w-3" />
                            <span>Edit</span>
                          </Button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>
      )}

      {/* TAB 2: ROOMS */}
      {activeTab === 'rooms' && (
        <div className="space-y-4">
          <div className="rounded-md border border-border bg-card shadow-xs overflow-hidden">
            {loadingRooms ? (
              <div className="p-8 text-center text-xs text-muted-foreground animate-pulse">
                Loading campus rooms directory...
              </div>
            ) : rooms.length === 0 ? (
              <div className="p-8 text-center text-xs text-muted-foreground">
                No interview rooms registered. Click "Register Interview Room" to allocate facilities.
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs border-collapse">
                  <thead className="bg-muted/60 border-b border-border text-muted-foreground font-mono uppercase tracking-wider">
                    <tr>
                      <th className="py-2.5 px-3.5">Room ID</th>
                      <th className="py-2.5 px-3.5">Facility Location & Wing</th>
                      <th className="py-2.5 px-3.5 text-center">Seating Capacity</th>
                      <th className="py-2.5 px-3.5 text-right">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border">
                    {rooms.map((r) => (
                      <tr key={r.id} className="hover:bg-muted/40 transition-colors">
                        <td className="py-2.5 px-3.5 font-mono text-muted-foreground font-semibold">
                          {r.id}
                        </td>
                        <td className="py-2.5 px-3.5 font-medium text-foreground">
                          {r.name}
                        </td>
                        <td className="py-2.5 px-3.5 text-center font-mono">
                          <Badge variant="outline" className="text-xs px-2 py-0.5">
                            {r.capacity} Persons
                          </Badge>
                        </td>
                        <td className="py-2.5 px-3.5 text-right whitespace-nowrap">
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => handleEditRoom(r)}
                            className="h-7 text-xs px-2 gap-1"
                            aria-label={`Edit room ${r.name}`}
                          >
                            <Edit3 className="h-3 w-3" />
                            <span>Edit</span>
                          </Button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>
      )}

      {/* Dialog Modals */}
      <InterviewerDialog
        open={interviewerModalOpen}
        onOpenChange={setInterviewerModalOpen}
        interviewer={editingInterviewer}
        onSuccess={() => refetchInterviewers()}
      />
      <RoomDialog
        open={roomModalOpen}
        onOpenChange={setRoomModalOpen}
        room={editingRoom}
        onSuccess={() => refetchRooms()}
      />
    </div>
  )
}
