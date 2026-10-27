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
import type { OfficerRoomDetailDto } from '@/types/api'
import { useCreateRoomMutation, useUpdateRoomMutation } from '../api/officerApi'

export interface RoomDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  room?: OfficerRoomDetailDto | null
  onSuccess?: () => void
}

export function RoomDialog({
  open,
  onOpenChange,
  room,
  onSuccess,
}: RoomDialogProps) {
  const isEdit = Boolean(room)

  const [id, setId] = React.useState('')
  const [name, setName] = React.useState('')
  const [capacity, setCapacity] = React.useState('4')
  const [errorMsg, setErrorMsg] = React.useState<string | null>(null)

  const createMutation = useCreateRoomMutation()
  const updateMutation = useUpdateRoomMutation()

  React.useEffect(() => {
    if (room) {
      setId(room.id)
      setName(room.name)
      setCapacity(String(room.capacity))
    } else {
      setId('')
      setName('')
      setCapacity('4')
    }
    setErrorMsg(null)
  }, [room, open])

  const isPending = createMutation.isPending || updateMutation.isPending

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setErrorMsg(null)

    if (!isEdit && !id.trim()) {
      setErrorMsg('Room identifier is required.')
      return
    }
    if (!name.trim()) {
      setErrorMsg('Room name is required.')
      return
    }
    const capNum = parseInt(capacity, 10)
    if (isNaN(capNum) || capNum < 1) {
      setErrorMsg('Room capacity must be a positive integer of at least 1.')
      return
    }

    try {
      if (isEdit && room) {
        await updateMutation.mutateAsync({
          id: room.id,
          dto: {
            name: name.trim(),
            capacity: capNum,
          },
        })
      } else {
        await createMutation.mutateAsync({
          id: id.trim().toUpperCase(),
          name: name.trim(),
          capacity: capNum,
        })
      }
      onOpenChange(false)
      onSuccess?.()
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to save room details.'
      setErrorMsg(msg)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>
            {isEdit ? 'Edit Interview Room' : 'Register Interview Room'}
          </DialogTitle>
          <DialogDescription>
            {isEdit
              ? `Update physical room specifications for venue '${room?.id}'.`
              : 'Add an authorized campus facility or interview cabin to the interview scheduling pool.'}
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
            label="Room Identifier"
            htmlFor="room-id"
            required={!isEdit}
            helperText={isEdit ? 'Immutable' : 'Max 20 chars, e.g. RM-101, RM-OAK'}
          >
            <Input
              id="room-id"
              value={id}
              onChange={(e) => setId(e.target.value)}
              disabled={isEdit || isPending}
              placeholder="RM-101"
              className="font-mono text-xs uppercase"
              maxLength={20}
              required={!isEdit}
            />
          </FormField>

          <FormField
            label="Room Location Name"
            htmlFor="room-name"
            required
            helperText="e.g. Interview Suite A (Oak Room), Boardroom 2"
          >
            <Input
              id="room-name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              disabled={isPending}
              placeholder="Venue name & wing"
              maxLength={100}
              required
            />
          </FormField>

          <FormField
            label="Room Seating Capacity (Persons)"
            htmlFor="room-capacity"
            required
            helperText="Positive integer (must be at least 1)"
          >
            <Input
              id="room-capacity"
              type="number"
              min="1"
              step="1"
              value={capacity}
              onChange={(e) => setCapacity(e.target.value)}
              disabled={isPending}
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
              {isPending ? 'Saving...' : isEdit ? 'Update Room' : 'Register Room'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
