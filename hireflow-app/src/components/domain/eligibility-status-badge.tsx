import * as React from "react"
import { Badge } from "@/components/ui/badge"
import {
  Check,
  AlertCircle,
  CheckCircle2,
  Lock,
  Clock,
  MinusCircle,
} from "lucide-react"

export interface EligibilityStatusBadgeProps {
  status?: "ELIGIBLE" | "INELIGIBLE" | "APPLIED" | "CLOSED"
  canApply?: boolean
  hasApplied?: boolean
  closed?: boolean
  deadlinePassed?: boolean
  academicallyEligible?: boolean
  className?: string
}

export const EligibilityStatusBadge: React.FC<EligibilityStatusBadgeProps> = ({
  status,
  canApply,
  hasApplied,
  closed,
  deadlinePassed,
  academicallyEligible,
  className,
}) => {
  if (status) {
    switch (status) {
      case "ELIGIBLE":
        return (
          <Badge variant="confirmed" className={className}>
            <Check className="h-3 w-3 shrink-0" aria-hidden="true" />
            <span>Eligible to Apply</span>
          </Badge>
        )
      case "INELIGIBLE":
        return (
          <Badge variant="rejected" className={className}>
            <AlertCircle className="h-3 w-3 shrink-0" aria-hidden="true" />
            <span>Criteria Unmet</span>
          </Badge>
        )
      case "APPLIED":
        return (
          <Badge variant="applied" className={className}>
            <CheckCircle2 className="h-3 w-3 shrink-0" aria-hidden="true" />
            <span>Application Submitted</span>
          </Badge>
        )
      case "CLOSED":
        return (
          <Badge variant="rejected" className={className}>
            <Lock className="h-3 w-3 shrink-0" aria-hidden="true" />
            <span>Drive Closed</span>
          </Badge>
        )
      default:
        return (
          <Badge variant="outline" className={className}>
            <MinusCircle className="h-3 w-3 shrink-0 text-muted-foreground" aria-hidden="true" />
            <span>{status}</span>
          </Badge>
        )
    }
  }

  if (hasApplied) {
    return (
      <Badge variant="applied" className={className}>
        <CheckCircle2 className="h-3 w-3 shrink-0" aria-hidden="true" />
        <span>Application Submitted</span>
      </Badge>
    )
  }

  if (closed) {
    return (
      <Badge variant="rejected" className={className}>
        <Lock className="h-3 w-3 shrink-0" aria-hidden="true" />
        <span>Drive Closed</span>
      </Badge>
    )
  }

  if (deadlinePassed) {
    return (
      <Badge variant="rejected" className={className}>
        <Clock className="h-3 w-3 shrink-0" aria-hidden="true" />
        <span>Deadline Passed</span>
      </Badge>
    )
  }

  if (canApply) {
    return (
      <Badge variant="confirmed" className={className}>
        <Check className="h-3 w-3 shrink-0" aria-hidden="true" />
        <span>Eligible to Apply</span>
      </Badge>
    )
  }

  if (academicallyEligible === false) {
    return (
      <Badge variant="rejected" className={className}>
        <AlertCircle className="h-3 w-3 shrink-0" aria-hidden="true" />
        <span>Criteria Unmet</span>
      </Badge>
    )
  }

  return (
    <Badge variant="outline" className={className}>
      <MinusCircle className="h-3 w-3 shrink-0 text-muted-foreground" aria-hidden="true" />
      <span>Not Open</span>
    </Badge>
  )
}

