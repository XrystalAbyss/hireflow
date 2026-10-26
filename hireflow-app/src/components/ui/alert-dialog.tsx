import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog"
import { Button } from "@/components/ui/button"
import { AlertTriangle, AlertCircle } from "lucide-react"
import { cn } from "@/lib/utils"

export interface AlertDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  description: string
  confirmLabel?: string
  cancelLabel?: string
  variant?: "destructive" | "default" | "accent"
  consequences?: string
  onConfirm: () => void
  isConfirming?: boolean
}

export function AlertDialog({
  open,
  onOpenChange,
  title,
  description,
  confirmLabel = "Confirm",
  cancelLabel = "Cancel",
  variant = "destructive",
  consequences = "This action will terminate ongoing operations and cannot be reversed.",
  onConfirm,
  isConfirming = false,
}: AlertDialogProps) {
  const isDestructive = variant === "destructive"

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md p-6">
        <DialogHeader className="space-y-3">
          <div className="flex items-center gap-3">
            <div
              className={cn(
                "h-10 w-10 rounded-sm flex items-center justify-center shrink-0 border",
                isDestructive
                  ? "bg-status-rejected-bg border-status-rejected-border text-status-rejected-text"
                  : "bg-academic-teal/10 border-academic-teal/30 text-academic-teal"
              )}
            >
              <AlertTriangle className="h-5 w-5" aria-hidden="true" />
            </div>
            <div>
              <DialogTitle className="font-serif text-lg font-bold text-foreground">
                {title}
              </DialogTitle>
              <span className="text-xs uppercase font-mono tracking-wider text-muted-foreground block">
                {isDestructive ? "Critical Administrative Action" : "Confirmation Required"}
              </span>
            </div>
          </div>

          <DialogDescription className="text-sm text-foreground/80 leading-relaxed pt-1">
            {description}
          </DialogDescription>
        </DialogHeader>

        {consequences && (
          <div
            className={cn(
              "rounded-sm p-3.5 text-xs leading-relaxed border flex items-start gap-2.5 my-2",
              isDestructive
                ? "bg-status-rejected-bg/80 border-status-rejected-border text-status-rejected-text font-medium"
                : "bg-muted/70 border-border text-foreground"
            )}
          >
            <AlertCircle className="h-4 w-4 shrink-0 mt-0.5 text-current" aria-hidden="true" />
            <div className="space-y-0.5">
              <span className="font-semibold block uppercase tracking-wider text-[11px]">
                {isDestructive ? "Irrevocable Consequence" : "Important Advisory"}
              </span>
              <span>{consequences}</span>
            </div>
          </div>
        )}

        <DialogFooter className="mt-5 gap-2.5 sm:gap-0">
          <Button
            type="button"
            variant="outline"
            onClick={() => onOpenChange(false)}
            disabled={isConfirming}
            className="sm:mr-2"
          >
            {cancelLabel}
          </Button>
          <Button
            type="button"
            variant={variant}
            onClick={() => onConfirm()}
            disabled={isConfirming}
            loading={isConfirming}
          >
            {isConfirming ? "Processing..." : confirmLabel}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

export { DialogTrigger as AlertDialogTrigger }

