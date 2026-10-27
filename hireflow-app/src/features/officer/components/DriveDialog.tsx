import * as React from 'react'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { FormField } from '@/components/ui/form-field'
import type { OfficerDriveDetailDto, OfficerCompanyDetailDto } from '@/types/api'
import {
  useCreateDriveMutation,
  useUpdateDriveMutation,
  useOfficerCompanies,
} from '../api/officerApi'

const STANDARD_BRANCHES = ['CSE', 'ECE', 'EE', 'ME', 'CE', 'IT', 'MATH', 'CHEM', 'BIOTECH']

export interface DriveDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  drive?: OfficerDriveDetailDto | null
  preselectedCompanyId?: string
  onSuccess?: () => void
}

export function DriveDialog({
  open,
  onOpenChange,
  drive,
  preselectedCompanyId,
  onSuccess,
}: DriveDialogProps) {
  const isEdit = Boolean(drive)

  const [id, setId] = React.useState('')
  const [companyId, setCompanyId] = React.useState('')
  const [jobTitle, setJobTitle] = React.useState('')
  const [minimumCgpa, setMinimumCgpa] = React.useState('7.0')
  const [eligibleBranches, setEligibleBranches] = React.useState<string[]>(['CSE', 'ECE'])
  const [applicationDeadline, setApplicationDeadline] = React.useState('')
  const [errorMsg, setErrorMsg] = React.useState<string | null>(null)

  const { data: companiesData } = useOfficerCompanies(0, 100)
  const companies: OfficerCompanyDetailDto[] = React.useMemo(
    () => companiesData?.content || [],
    [companiesData?.content]
  )

  const createMutation = useCreateDriveMutation()
  const updateMutation = useUpdateDriveMutation()

  React.useEffect(() => {
    if (drive) {
      setId(drive.id)
      setCompanyId(drive.companyId)
      setJobTitle(drive.jobTitle)
      setMinimumCgpa(String(drive.minimumCgpa))
      setEligibleBranches(drive.eligibleBranches || [])
      setApplicationDeadline(drive.applicationDeadline)
    } else {
      setId('')
      setCompanyId(preselectedCompanyId || (companies[0]?.id || ''))
      setJobTitle('')
      setMinimumCgpa('7.0')
      setEligibleBranches(['CSE', 'ECE'])
      // Default to 14 days in future
      const defaultDate = new Date()
      defaultDate.setDate(defaultDate.getDate() + 14)
      setApplicationDeadline(defaultDate.toISOString().slice(0, 10))
    }
    setErrorMsg(null)
  }, [drive, open, preselectedCompanyId, companies])

  const toggleBranch = (b: string) => {
    if (eligibleBranches.includes(b)) {
      if (eligibleBranches.length === 1) return // Keep at least one
      setEligibleBranches(eligibleBranches.filter((x) => x !== b))
    } else {
      setEligibleBranches([...eligibleBranches, b])
    }
  }

  const isPending = createMutation.isPending || updateMutation.isPending

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setErrorMsg(null)

    if (!isEdit && !id.trim()) {
      setErrorMsg('Drive identifier is required.')
      return
    }
    if (!isEdit && !companyId.trim()) {
      setErrorMsg('Corporate partner selection is required.')
      return
    }
    if (!jobTitle.trim()) {
      setErrorMsg('Job title is required.')
      return
    }
    const cgpaNum = parseFloat(minimumCgpa)
    if (isNaN(cgpaNum) || cgpaNum < 0 || cgpaNum > 10) {
      setErrorMsg('Minimum CGPA must be between 0.0 and 10.0.')
      return
    }
    if (eligibleBranches.length === 0) {
      setErrorMsg('At least one eligible branch must be selected.')
      return
    }
    if (!applicationDeadline.trim()) {
      setErrorMsg('Application deadline date is required.')
      return
    }

    try {
      if (isEdit && drive) {
        await updateMutation.mutateAsync({
          id: drive.id,
          dto: {
            jobTitle: jobTitle.trim(),
            minimumCgpa: cgpaNum,
            eligibleBranches,
            applicationDeadline: applicationDeadline.trim(),
          },
        })
      } else {
        await createMutation.mutateAsync({
          id: id.trim().toUpperCase(),
          companyId: companyId.trim().toUpperCase(),
          jobTitle: jobTitle.trim(),
          minimumCgpa: cgpaNum,
          eligibleBranches,
          applicationDeadline: applicationDeadline.trim(),
        })
      }
      onOpenChange(false)
      onSuccess?.()
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to save drive details.'
      setErrorMsg(msg)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>
            {isEdit ? 'Edit Recruitment Drive' : 'Launch Recruitment Drive'}
          </DialogTitle>
          <DialogDescription>
            {isEdit
              ? `Update academic criteria and position profile for recruitment campaign '${drive?.id}'.`
              : 'Create a new campus recruitment campaign with institutional eligibility constraints.'}
          </DialogDescription>
        </DialogHeader>

        {errorMsg && (
          <div
            className="p-3 text-xs rounded-sm bg-destructive/10 text-destructive border border-destructive/20 font-medium"
            role="alert"
          >
            {errorMsg}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <FormField
              label="Drive Identifier"
              htmlFor="drive-id"
              required={!isEdit}
              helperText={isEdit ? 'Immutable' : 'Max 20 chars, e.g. DRV-2026-006'}
            >
              <Input
                id="drive-id"
                value={id}
                onChange={(e) => setId(e.target.value)}
                disabled={isEdit || isPending}
                placeholder="DRV-2026-001"
                className="font-mono text-xs uppercase"
                maxLength={20}
                required={!isEdit}
              />
            </FormField>

            <FormField
              label="Corporate Partner"
              htmlFor="drive-company"
              required={!isEdit}
              helperText={isEdit ? 'Immutable partner' : 'Select sponsoring organization'}
            >
              {isEdit ? (
                <Input
                  id="drive-company"
                  value={drive?.companyName || drive?.companyId || ''}
                  disabled
                  className="text-xs"
                />
              ) : (
                <select
                  id="drive-company"
                  value={companyId}
                  onChange={(e) => setCompanyId(e.target.value)}
                  disabled={isPending}
                  className="w-full h-9 rounded-sm border border-input bg-card px-3 text-xs text-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
                  required
                >
                  <option value="" disabled>
                    Select Corporate Partner
                  </option>
                  {companies.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.name} ({c.id})
                    </option>
                  ))}
                </select>
              )}
            </FormField>
          </div>

          <FormField
            label="Job Position Title"
            htmlFor="drive-job-title"
            required
            helperText="e.g. Graduate Software Engineer, Systems Architect"
          >
            <Input
              id="drive-job-title"
              value={jobTitle}
              onChange={(e) => setJobTitle(e.target.value)}
              disabled={isPending}
              placeholder="e.g. Associate Software Engineer"
              maxLength={100}
              required
            />
          </FormField>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <FormField
              label="Minimum CGPA (0.00 - 10.00)"
              htmlFor="drive-cgpa"
              required
              helperText="Strict academic threshold"
            >
              <Input
                id="drive-cgpa"
                type="number"
                step="0.01"
                min="0.0"
                max="10.0"
                value={minimumCgpa}
                onChange={(e) => setMinimumCgpa(e.target.value)}
                disabled={isPending}
                required
              />
            </FormField>

            <FormField
              label="Application Deadline"
              htmlFor="drive-deadline"
              required
              helperText="Date after which submissions close"
            >
              <Input
                id="drive-deadline"
                type="date"
                value={applicationDeadline}
                onChange={(e) => setApplicationDeadline(e.target.value)}
                disabled={isPending}
                required
              />
            </FormField>
          </div>

          <FormField
            label="Eligible Branches & Programs"
            required
            helperText="Click branches to toggle eligibility (at least one required)"
          >
            <div className="flex flex-wrap gap-1.5 pt-1">
              {STANDARD_BRANCHES.map((branch) => {
                const isSelected = eligibleBranches.includes(branch)
                return (
                  <button
                    type="button"
                    key={branch}
                    onClick={() => toggleBranch(branch)}
                    disabled={isPending}
                    className={`px-2.5 py-1 text-xs font-mono font-medium rounded-xs border transition-colors cursor-pointer select-none ${
                      isSelected
                        ? 'bg-primary text-primary-foreground border-primary'
                        : 'bg-muted/60 text-muted-foreground border-border hover:bg-muted'
                    }`}
                  >
                    {branch}
                  </button>
                )
              })}
            </div>
          </FormField>

          <DialogFooter className="pt-2">
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => onOpenChange(false)}
              disabled={isPending}
            >
              Cancel
            </Button>
            <Button type="submit" size="sm" disabled={isPending}>
              {isPending ? 'Saving...' : isEdit ? 'Update Drive' : 'Launch Drive'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
