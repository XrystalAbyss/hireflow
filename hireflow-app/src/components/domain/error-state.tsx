import * as React from "react"
import { AlertCircle, RotateCcw } from "lucide-react"
import { Button } from "@/components/ui/button"
import { cn } from "@/lib/utils"

export interface ErrorStateProps extends React.HTMLAttributes<HTMLDivElement> {
  title?: string
  message: string
  statusCode?: number
  onRetry?: () => void
  action?: React.ReactNode
}

export function ErrorState({
  title = "Unable to complete request",
  message,
  statusCode,
  onRetry,
  action,
  className,
  ...props
}: ErrorStateProps) {
  return (
    <div
      role="alert"
      className={cn(
        "flex flex-col items-center justify-center p-8 text-center rounded-sm border border-status-rejected-border bg-status-rejected-bg/40 select-none",
        className
      )}
      {...props}
    >
      <div className="flex h-10 w-10 items-center justify-center rounded-full bg-status-rejected-bg text-status-rejected-text border border-status-rejected-border mb-3">
        <AlertCircle className="h-5 w-5" aria-hidden="true" />
      </div>

      <div className="flex items-center gap-2 mb-1">
        {statusCode && (
          <span className="font-mono text-xs font-semibold px-1.5 py-0.5 rounded-xs bg-status-rejected-border/40 text-status-rejected-text">
            HTTP {statusCode}
          </span>
        )}
        <h3 className="font-serif text-base font-semibold text-status-rejected-text tracking-tight">
          {title}
        </h3>
      </div>

      <p className="text-xs text-status-rejected-text/90 max-w-md leading-relaxed">
        {message}
      </p>

      <div className="mt-4 flex items-center gap-2.5">
        {onRetry && (
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={onRetry}
            className="border-status-rejected-border text-status-rejected-text hover:bg-status-rejected-bg"
          >
            <RotateCcw className="h-3.5 w-3.5 mr-1.5" />
            Retry Request
          </Button>
        )}
        {action}
      </div>
    </div>
  )
}
