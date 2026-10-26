import * as React from "react"
import { Badge } from "@/components/ui/badge"
import type { ApplicationStatus } from "@/types/api"
import {
  Clock,
  Sparkles,
  Calendar,
  CheckCircle2,
  XCircle,
  Check,
  AlertCircle,
  HelpCircle,
} from "lucide-react"

export interface ApplicationStatusBadgeProps {
  status: ApplicationStatus | string | null | undefined
  className?: string
}

export const ApplicationStatusBadge: React.FC<ApplicationStatusBadgeProps> = ({
  status,
  className,
}) => {
  if (!status) {
    return (
      <Badge variant="outline" className={className}>
        <HelpCircle className="h-3 w-3 shrink-0 text-muted-foreground" aria-hidden="true" />
        <span>Unknown</span>
      </Badge>
    )
  }

  const norm = status.toUpperCase()

  switch (norm) {
    case "APPLIED":
      return (
        <Badge variant="applied" className={className}>
          <Clock className="h-3 w-3 shrink-0" aria-hidden="true" />
          <span>Applied</span>
        </Badge>
      )
    case "SHORTLISTED":
      return (
        <Badge variant="shortlisted" className={className}>
          <Sparkles className="h-3 w-3 shrink-0" aria-hidden="true" />
          <span>Shortlisted</span>
        </Badge>
      )
    case "SCHEDULED":
      return (
        <Badge variant="confirmed" className={className}>
          <Calendar className="h-3 w-3 shrink-0" aria-hidden="true" />
          <span>Scheduled</span>
        </Badge>
      )
    case "SELECTED":
      return (
        <Badge variant="confirmed" className={className}>
          <CheckCircle2 className="h-3 w-3 shrink-0" aria-hidden="true" />
          <span>Selected</span>
        </Badge>
      )
    case "REJECTED":
      return (
        <Badge variant="rejected" className={className}>
          <XCircle className="h-3 w-3 shrink-0" aria-hidden="true" />
          <span>Rejected</span>
        </Badge>
      )
    case "ELIGIBLE":
      return (
        <Badge variant="confirmed" className={className}>
          <Check className="h-3 w-3 shrink-0" aria-hidden="true" />
          <span>Eligible</span>
        </Badge>
      )
    case "INELIGIBLE":
      return (
        <Badge variant="rejected" className={className}>
          <AlertCircle className="h-3 w-3 shrink-0" aria-hidden="true" />
          <span>Ineligible</span>
        </Badge>
      )
    default:
      return (
        <Badge variant="outline" className={className}>
          <span className="h-1.5 w-1.5 rounded-full bg-muted-foreground shrink-0" aria-hidden="true" />
          <span>{status}</span>
        </Badge>
      )
  }
}

