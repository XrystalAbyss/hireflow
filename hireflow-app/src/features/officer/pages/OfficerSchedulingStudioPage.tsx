import * as React from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import {
  Sparkles,
  ArrowLeft,
  History,
} from 'lucide-react'
import { PageHeading } from '@/components/domain/page-heading'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs'
import type {
  OfficerSchedulingPreviewRequestDto,
  OfficerSchedulingPreviewResponseDto,
} from '@/types/api'
import {
  useOfficerDrives,
  useOfficerInterviewers,
  useOfficerRooms,
  useOfficerCompanies,
} from '../api/officerApi'
import {
  useSchedulingPreviewMutation,
  usePublishScheduleMutation,
  usePublishedScheduleQuery,
} from '../api/schedulingApi'
import { SchedulingConfigPanel } from '../components/scheduling/SchedulingConfigPanel'
import { AdvisoryPreviewView } from '../components/scheduling/AdvisoryPreviewView'
import { PublicationConfirmDialog } from '../components/scheduling/PublicationConfirmDialog'
import {
  PublicationOutcomeView,
  type PublicationViewStatus,
} from '../components/scheduling/PublicationOutcomeView'
import { ConfirmedTimetableView } from '../components/scheduling/ConfirmedTimetableView'
import { NoticeBanner } from '@/components/domain/notice-banner'
import { ApiError } from '@/core/api/client'

export function OfficerSchedulingStudioPage() {
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()

  // Active Tab: 'studio' | 'confirmed'
  const activeTab = searchParams.get('tab') || 'studio'
  const setActiveTab = (tab: string) => {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev)
      next.set('tab', tab)
      return next
    })
  }

  // Pre-requisite data for configuration
  const { data: pagedDrives, isLoading: isLoadingDrives } = useOfficerDrives(0, 100)
  const { data: interviewers = [] } = useOfficerInterviewers()
  const { data: rooms = [] } = useOfficerRooms()
  const { data: pagedCompanies } = useOfficerCompanies(0, 100)

  const drives = React.useMemo(() => pagedDrives?.content || [], [pagedDrives])
  const companies = React.useMemo(() => pagedCompanies?.content || [], [pagedCompanies])

  // In-memory Scheduling Studio State Machine
  // Raw preview token is kept exclusively in React memory, never persisted or logged!
  const [currentRequest, setCurrentRequest] = React.useState<OfficerSchedulingPreviewRequestDto | null>(null)
  const [previewData, setPreviewData] = React.useState<OfficerSchedulingPreviewResponseDto | null>(null)
  const [outcomeStatus, setOutcomeStatus] = React.useState<PublicationViewStatus | null>(null)
  const [isConfirmDialogOpen, setIsConfirmDialogOpen] = React.useState(false)
  const [previewError, setPreviewError] = React.useState<string | null>(null)

  // Confirmed Timetable filter & pagination state
  const [confirmedPage, setConfirmedPage] = React.useState(0)
  const [confirmedSize, setConfirmedSize] = React.useState(20)
  const [filterDriveId, setFilterDriveId] = React.useState<string | undefined>(
    searchParams.get('driveId') || undefined
  )
  const [filterCompanyId, setFilterCompanyId] = React.useState<string | undefined>(undefined)
  const [filterDate, setFilterDate] = React.useState<string | undefined>(undefined)

  const {
    data: publishedScheduleData,
    isLoading: isLoadingPublished,
    refetch: refetchPublished,
  } = usePublishedScheduleQuery({
    driveId: filterDriveId,
    companyId: filterCompanyId,
    date: filterDate,
    page: confirmedPage,
    size: confirmedSize,
  })

  // API Mutations
  const previewMutation = useSchedulingPreviewMutation()
  const publishMutation = usePublishScheduleMutation()

  // Clear memory preview token on unmount or navigation away
  React.useEffect(() => {
    return () => {
      setPreviewData(null)
    }
  }, [])

  // Handle Form Submit: Request Advisory Preview
  const handleGeneratePreview = async (request: OfficerSchedulingPreviewRequestDto) => {
    setCurrentRequest(request)
    setOutcomeStatus(null)
    setPreviewError(null)

    try {
      const result = await previewMutation.mutateAsync(request)
      setPreviewData(result)
    } catch (err) {
      // Keep the configuration form visible so the officer can adjust, but never fail silently.
      console.error('[HireFlow] Preview generation error:', err)
      setPreviewError(
        err instanceof ApiError
          ? err.message
          : err instanceof Error
            ? err.message
            : 'Advisory preview could not be generated. Check the interview window and resource selection.'
      )
    }
  }

  // Handle Publish Timetable
  const handleConfirmPublish = async () => {
    if (!previewData) return

    setIsConfirmDialogOpen(false)

    try {
      const result = await publishMutation.mutateAsync({
        previewId: previewData.previewId,
      })

      // Token consumed atomically on server: clear raw preview token from memory
      setPreviewData(null)

      if (result.status === 200) {
        setOutcomeStatus({
          type: 'ALL_CONFIRMED',
          summary: result.data,
        })
      } else if (result.status === 207) {
        setOutcomeStatus({
          type: 'PARTIAL_SUCCESS',
          summary: result.data,
        })
      } else if (result.status === 409) {
        setOutcomeStatus({
          type: 'NONE_CONFIRMED',
          summary: result.data,
        })
      }
    } catch (err) {
      // Clear consumed token on error
      setPreviewData(null)

      if (err instanceof ApiError) {
        if (err.status === 404) {
          setOutcomeStatus({
            type: 'NOT_FOUND_EVICTED',
            message: err.message,
            driveId: currentRequest?.driveId,
          })
          return
        }
        if (err.status === 409) {
          setOutcomeStatus({
            type: 'ALREADY_CONSUMED',
            message: err.message,
            driveId: currentRequest?.driveId,
          })
          return
        }
        if (err.status === 410) {
          setOutcomeStatus({
            type: 'EXPIRED',
            message: err.message,
          })
          return
        }
        if (err.status >= 500) {
          setOutcomeStatus({
            type: 'SERVER_ERROR_UNCERTAIN',
            message: err.message,
            driveId: currentRequest?.driveId,
          })
          return
        }
      }

      // Network loss / ambiguous response
      setOutcomeStatus({
        type: 'NETWORK_ERROR_AMBIGUOUS',
        message: err instanceof Error ? err.message : 'Unknown network failure',
        driveId: currentRequest?.driveId,
      })
    }
  }

  const handleAdjustConfig = () => {
    setPreviewData(null)
    setOutcomeStatus(null)
  }

  const handleRefreshPreview = () => {
    if (currentRequest) {
      handleGeneratePreview(currentRequest)
    } else {
      setPreviewData(null)
    }
  }

  const handleNavigateToConfirmedTimetable = (driveId?: string) => {
    if (driveId) {
      setFilterDriveId(driveId)
    }
    setOutcomeStatus(null)
    setPreviewData(null)
    setActiveTab('confirmed')
  }

  const handleGenerateNewPreview = () => {
    setPreviewData(null)
    setOutcomeStatus(null)
    setActiveTab('studio')
  }

  return (
    <div className="space-y-6">
      {/* Institutional Page Heading */}
      <PageHeading
        title="Interview Scheduling Studio"
        description="Greedy interval scheduling engine, advisory preview visualizer, and transactional timetable publication."
        breadcrumbs={[
          { label: 'Officer', href: '/officer/dashboard' },
          { label: 'Scheduling Studio' },
        ]}
        badge={
          <Badge variant="outline" className="font-mono text-xs text-brass-800 dark:text-brass-400 border-brass-600/40">
            CAP-8.4 ACTIVE STUDIO
          </Badge>
        }
        actions={
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={() => navigate('/officer/dashboard')}
              className="text-xs gap-1.5"
            >
              <ArrowLeft className="h-3.5 w-3.5" />
              <span>Command Center</span>
            </Button>
          </div>
        }
      />

      {/* Main Tab Controls */}
      <Tabs value={activeTab} onValueChange={setActiveTab} className="space-y-6">
        <TabsList className="bg-muted/60 p-1 border border-border flex h-auto w-full flex-wrap justify-start sm:w-auto">
          <TabsTrigger value="studio" className="text-xs gap-2 font-medium">
            <Sparkles className="h-3.5 w-3.5 text-academic-teal" />
            <span>Generate & Publish Schedule</span>
          </TabsTrigger>
          <TabsTrigger value="confirmed" className="text-xs gap-2 font-medium">
            <History className="h-3.5 w-3.5 text-accent" />
            <span>Confirmed Timetables & Sessions</span>
          </TabsTrigger>
        </TabsList>

        {/* Tab 1: Studio Workflow */}
        <TabsContent value="studio" className="space-y-6">
          {outcomeStatus ? (
            <PublicationOutcomeView
              outcomeState={outcomeStatus}
              onNavigateToConfirmedTimetable={handleNavigateToConfirmedTimetable}
              onGenerateNewPreview={handleGenerateNewPreview}
            />
          ) : previewData ? (
            <>
              <AdvisoryPreviewView
                preview={previewData}
                onProceedToPublish={() => setIsConfirmDialogOpen(true)}
                onAdjustConfig={handleAdjustConfig}
                onRefreshPreview={handleRefreshPreview}
                isPublishing={publishMutation.isPending}
              />

              <PublicationConfirmDialog
                open={isConfirmDialogOpen}
                onOpenChange={setIsConfirmDialogOpen}
                preview={previewData}
                onConfirmPublish={handleConfirmPublish}
                isPublishing={publishMutation.isPending}
              />
            </>
          ) : (
            <div className="space-y-6">
              {previewError && (
                <NoticeBanner
                  variant="error"
                  title="Advisory Preview Could Not Be Generated"
                  onDismiss={() => setPreviewError(null)}
                >
                  {previewError}
                </NoticeBanner>
              )}
              <SchedulingConfigPanel
                drives={drives}
                interviewers={interviewers}
                rooms={rooms}
                isLoadingDrives={isLoadingDrives}
                onSubmit={handleGeneratePreview}
                isGenerating={previewMutation.isPending}
                initialValues={currentRequest || undefined}
              />
            </div>
          )}
        </TabsContent>

        {/* Tab 2: Confirmed Timetables */}
        <TabsContent value="confirmed" className="space-y-6">
          <ConfirmedTimetableView
            sessions={publishedScheduleData?.content || []}
            totalElements={publishedScheduleData?.totalElements || 0}
            totalPages={publishedScheduleData?.totalPages || 0}
            page={confirmedPage}
            size={confirmedSize}
            onPageChange={setConfirmedPage}
            onPageSizeChange={setConfirmedSize}
            drives={drives}
            companies={companies}
            selectedDriveId={filterDriveId}
            onDriveChange={setFilterDriveId}
            selectedCompanyId={filterCompanyId}
            onCompanyChange={setFilterCompanyId}
            selectedDate={filterDate}
            onDateChange={setFilterDate}
            isLoading={isLoadingPublished}
            onRefetch={refetchPublished}
          />
        </TabsContent>
      </Tabs>
    </div>
  )
}
