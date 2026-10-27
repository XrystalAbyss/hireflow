import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Button } from "@/components/ui/button"
import { AlertTriangle, HelpCircle, Info } from "lucide-react"
import { cn } from "@/lib/utils"

export interface ConfirmationDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  description: string
  confirmText?: string
  cancelText?: string
  variant?: "default" | "accent" | "destructive"
  destructive?: boolean
  consequences?: string
  onConfirm: () => void | Promise<void>
  isLoading?: boolean
}

export function ConfirmationDialog({
  open,
  onOpenChange,
  title,
  description,
  confirmText = "Confirm Action",
  cancelText = "Cancel",
  variant = "accent",
  destructive = false,
  consequences,
  onConfirm,
  isLoading = false,
}: ConfirmationDialogProps) {
  const isDestructive = destructive || variant === "destructive"

  const handleConfirm = async () => {
    await onConfirm()
  }

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
              {isDestructive ? (
                <AlertTriangle className="h-5 w-5" aria-hidden="true" />
              ) : (
                <HelpCircle className="h-5 w-5" aria-hidden="true" />
              )}
            </div>
            <div>
              <DialogTitle className="font-serif text-lg font-bold text-foreground">
                {title}
              </DialogTitle>
              <span className="text-xs uppercase font-mono tracking-wider text-muted-foreground block">
                {isDestructive ? "Irrevocable Administrative Action" : "Operational Confirmation"}
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
            <Info className="h-4 w-4 shrink-0 mt-0.5 text-current" aria-hidden="true" />
            <div className="space-y-0.5">
              <span className="font-semibold block uppercase tracking-wider text-[11px]">
                {isDestructive ? "Notice of Consequences" : "Operational Impact"}
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
            disabled={isLoading}
            className="sm:mr-2"
          >
            {cancelText}
          </Button>
          <Button
            type="button"
            variant={isDestructive ? "destructive" : variant}
            onClick={handleConfirm}
            disabled={isLoading}
            loading={isLoading}
          >
            {isLoading ? "Processing..." : confirmText}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

