import * as React from "react"
import { AlertCircle, CheckCircle2, Info, AlertTriangle, X } from "lucide-react"
import { cn } from "@/lib/utils"

export type NoticeBannerVariant = "info" | "success" | "warning" | "error"

export interface NoticeBannerProps extends React.HTMLAttributes<HTMLDivElement> {
  variant?: NoticeBannerVariant
  title?: string
  children: React.ReactNode
  onDismiss?: () => void
}

export function NoticeBanner({
  variant = "info",
  title,
  children,
  onDismiss,
  className,
  ...props
}: NoticeBannerProps) {
  const variantStyles = {
    info: "bg-status-applied-bg text-status-applied-text border-status-applied-border",
    success: "bg-status-confirmed-bg text-status-confirmed-text border-status-confirmed-border",
    warning: "bg-status-shortlisted-bg text-status-shortlisted-text border-status-shortlisted-border",
    error: "bg-status-rejected-bg text-status-rejected-text border-status-rejected-border",
  }

  const icons = {
    info: <Info className="h-4 w-4 shrink-0 mt-0.5 text-accent" aria-hidden="true" />,
    success: <CheckCircle2 className="h-4 w-4 shrink-0 mt-0.5 text-status-confirmed-text" aria-hidden="true" />,
    warning: <AlertTriangle className="h-4 w-4 shrink-0 mt-0.5 text-status-shortlisted-text" aria-hidden="true" />,
    error: <AlertCircle className="h-4 w-4 shrink-0 mt-0.5 text-status-rejected-text" aria-hidden="true" />,
  }

  return (
    <div
      role="status"
      className={cn(
        "flex items-start justify-between gap-3 p-3.5 rounded-sm border text-xs shadow-xs leading-relaxed",
        variantStyles[variant],
        className
      )}
      {...props}
    >
      <div className="flex items-start gap-2.5">
        {icons[variant]}
        <div className="space-y-0.5">
          {title && <p className="font-semibold">{title}</p>}
          <div>{children}</div>
        </div>
      </div>
      {onDismiss && (
        <button
          type="button"
          onClick={onDismiss}
          className="shrink-0 p-0.5 opacity-70 hover:opacity-100 focus:outline-none focus:ring-1 focus:ring-ring rounded-xs cursor-pointer"
          aria-label="Dismiss notice"
        >
          <X className="h-3.5 w-3.5" />
        </button>
      )}
    </div>
  )
}
