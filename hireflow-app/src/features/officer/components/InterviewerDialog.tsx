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
import type { OfficerInterviewerDetailDto, OfficerCompanyDetailDto } from '@/types/api'
import {
  useCreateInterviewerMutation,
  useUpdateInterviewerMutation,
  useOfficerCompanies,
} from '../api/officerApi'

export interface InterviewerDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  interviewer?: OfficerInterviewerDetailDto | null
  preselectedCompanyId?: string
  onSuccess?: () => void
}

export function InterviewerDialog({
  open,
  onOpenChange,
  interviewer,
  preselectedCompanyId,
  onSuccess,
}: InterviewerDialogProps) {
  const isEdit = Boolean(interviewer)

  const [id, setId] = React.useState('')
  const [name, setName] = React.useState('')
  const [companyId, setCompanyId] = React.useState('')
  const [errorMsg, setErrorMsg] = React.useState<string | null>(null)

  const { data: companiesData } = useOfficerCompanies(0, 100)
  const companies: OfficerCompanyDetailDto[] = React.useMemo(
    () => companiesData?.content || [],
    [companiesData?.content]
  )

  const createMutation = useCreateInterviewerMutation()
  const updateMutation = useUpdateInterviewerMutation()

  React.useEffect(() => {
    if (interviewer) {
      setId(interviewer.id)
      setName(interviewer.name)
      setCompanyId(interviewer.companyId)
    } else {
      setId('')
      setName('')
      setCompanyId(preselectedCompanyId || (companies[0]?.id || ''))
    }
    setErrorMsg(null)
  }, [interviewer, open, preselectedCompanyId, companies])

  const isPending = createMutation.isPending || updateMutation.isPending

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setErrorMsg(null)

    if (!isEdit && !id.trim()) {
      setErrorMsg('Interviewer identifier is required.')
      return
    }
    if (!name.trim()) {
      setErrorMsg('Interviewer name is required.')
      return
    }
    if (!companyId.trim()) {
      setErrorMsg('Corporate partner organization is required.')
      return
    }

    try {
      if (isEdit && interviewer) {
        await updateMutation.mutateAsync({
          id: interviewer.id,
          dto: {
            name: name.trim(),
            companyId: companyId.trim().toUpperCase(),
          },
        })
      } else {
        await createMutation.mutateAsync({
          id: id.trim().toUpperCase(),
          name: name.trim(),
          companyId: companyId.trim().toUpperCase(),
        })
      }
      onOpenChange(false)
      onSuccess?.()
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to save interviewer.'
      setErrorMsg(msg)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>
            {isEdit ? 'Edit Corporate Interviewer' : 'Register Corporate Interviewer'}
          </DialogTitle>
          <DialogDescription>
            {isEdit
              ? `Update professional profile for interviewer '${interviewer?.id}'.`
              : 'Add an authorized recruiting interviewer associated with a corporate partner.'}
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
          <FormField
            label="Interviewer Identifier"
            htmlFor="interviewer-id"
            required={!isEdit}
            helperText={isEdit ? 'Immutable' : 'Max 20 chars, e.g. INTV-006'}
          >
            <Input
              id="interviewer-id"
              value={id}
              onChange={(e) => setId(e.target.value)}
              disabled={isEdit || isPending}
              placeholder="INTV-001"
              className="font-mono text-xs uppercase"
              maxLength={20}
              required={!isEdit}
            />
          </FormField>

          <FormField
            label="Interviewer Full Name"
            htmlFor="interviewer-name"
            required
            helperText="e.g. Dr. Sarah Jenkins, David Chen"
          >
            <Input
              id="interviewer-name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              disabled={isPending}
              placeholder="Full Name & Title"
              maxLength={100}
              required
            />
          </FormField>

          <FormField
            label="Associated Corporate Partner"
            htmlFor="interviewer-company"
            required
            helperText="Organization employing this interviewer"
          >
            <select
              id="interviewer-company"
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
              {isPending ? 'Saving...' : isEdit ? 'Update Interviewer' : 'Register Interviewer'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
