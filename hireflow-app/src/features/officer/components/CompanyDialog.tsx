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
import type { OfficerCompanyDetailDto } from '@/types/api'
import {
  useCreateCompanyMutation,
  useUpdateCompanyMutation,
} from '../api/officerApi'

export interface CompanyDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  company?: OfficerCompanyDetailDto | null // If present -> edit mode, else create mode
  onSuccess?: () => void
}

export function CompanyDialog({
  open,
  onOpenChange,
  company,
  onSuccess,
}: CompanyDialogProps) {
  const isEdit = Boolean(company)

  const [id, setId] = React.useState('')
  const [name, setName] = React.useState('')
  const [industry, setIndustry] = React.useState('')
  const [errorMsg, setErrorMsg] = React.useState<string | null>(null)

  const createMutation = useCreateCompanyMutation()
  const updateMutation = useUpdateCompanyMutation()

  React.useEffect(() => {
    if (company) {
      setId(company.id)
      setName(company.name)
      setIndustry(company.industry)
    } else {
      setId('')
      setName('')
      setIndustry('')
    }
    setErrorMsg(null)
  }, [company, open])

  const isPending = createMutation.isPending || updateMutation.isPending

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setErrorMsg(null)

    if (!isEdit && !id.trim()) {
      setErrorMsg('Company identifier is required.')
      return
    }
    if (!name.trim()) {
      setErrorMsg('Company name is required.')
      return
    }
    if (!industry.trim()) {
      setErrorMsg('Industry classification is required.')
      return
    }

    try {
      if (isEdit && company) {
        await updateMutation.mutateAsync({
          id: company.id,
          dto: {
            name: name.trim(),
            industry: industry.trim(),
          },
        })
      } else {
        await createMutation.mutateAsync({
          id: id.trim().toUpperCase(),
          name: name.trim(),
          industry: industry.trim(),
        })
      }
      onOpenChange(false)
      onSuccess?.()
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to save company details.'
      setErrorMsg(msg)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>
            {isEdit ? 'Edit Corporate Partner' : 'Register Corporate Partner'}
          </DialogTitle>
          <DialogDescription>
            {isEdit
              ? `Update corporate specifications for partner '${company?.id}'. The corporate identifier is immutable.`
              : 'Add an authorized recruiting organization to the university placement registry.'}
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
            label="Company Identifier"
            htmlFor="company-id"
            required={!isEdit}
            helperText={
              isEdit
                ? 'Immutable institutional identifier'
                : 'Max 20 chars, e.g., COMP-006 or GOOGLE'
            }
          >
            <Input
              id="company-id"
              value={id}
              onChange={(e) => setId(e.target.value)}
              disabled={isEdit || isPending}
              placeholder="COMP-001"
              className="font-mono text-xs uppercase"
              maxLength={20}
              required={!isEdit}
            />
          </FormField>

          <FormField
            label="Corporate Organization Name"
            htmlFor="company-name"
            required
            helperText="Official legal entity name"
          >
            <Input
              id="company-name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              disabled={isPending}
              placeholder="e.g. Acme Corporation India"
              maxLength={100}
              required
            />
          </FormField>

          <FormField
            label="Industry Classification"
            htmlFor="company-industry"
            required
            helperText="e.g. Financial Services, Enterprise Software"
          >
            <Input
              id="company-industry"
              value={industry}
              onChange={(e) => setIndustry(e.target.value)}
              disabled={isPending}
              placeholder="e.g. Enterprise Software & Cloud"
              maxLength={100}
              required
            />
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
              {isPending ? 'Saving...' : isEdit ? 'Update Partner' : 'Register Partner'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
