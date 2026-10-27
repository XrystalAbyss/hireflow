import * as React from "react"
import { cva, type VariantProps } from "class-variance-authority"
import { cn } from "@/lib/utils"

const badgeVariants = cva(
  "inline-flex items-center gap-1.5 rounded-xs border px-2.5 py-0.5 text-xs font-semibold tracking-tight transition-colors focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2 select-none shrink-0",
  {
    variants: {
      variant: {
        default:
          "border-transparent bg-primary text-primary-foreground",
        secondary:
          "border-border bg-secondary text-secondary-foreground",
        accent:
          "border-transparent bg-accent text-accent-foreground",
        outline:
          "border-border text-foreground bg-card",
        confirmed:
          "border-status-confirmed-border bg-status-confirmed-bg text-status-confirmed-text",
        shortlisted:
          "border-status-shortlisted-border bg-status-shortlisted-bg text-status-shortlisted-text",
        applied:
          "border-status-applied-border bg-status-applied-bg text-status-applied-text",
        rejected:
          "border-status-rejected-border bg-status-rejected-bg text-status-rejected-text",
        brass:
          "border-brass-600/40 bg-brass-100 text-brass-800",
      },
    },
    defaultVariants: {
      variant: "default",
    },
  }
)

export interface BadgeProps
  extends React.HTMLAttributes<HTMLDivElement>,
    VariantProps<typeof badgeVariants> {}

function Badge({ className, variant, ...props }: BadgeProps) {
  return (
    <div className={cn(badgeVariants({ variant }), className)} {...props} />
  )
}

export { Badge, badgeVariants }
