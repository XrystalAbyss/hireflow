import * as React from "react"
import { cn } from "@/lib/utils"

export interface SectionHeadingProps extends React.HTMLAttributes<HTMLDivElement> {
  title: string
  description?: string
  badge?: React.ReactNode
  actions?: React.ReactNode
}

export function SectionHeading({
  title,
  description,
  badge,
  actions,
  className,
  ...props
}: SectionHeadingProps) {
  return (
    <div
      className={cn(
        "flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 pb-3 border-b border-border/60",
        className
      )}
      {...props}
    >
      <div className="space-y-0.5">
        <div className="flex items-center gap-2">
          <h2 className="font-serif text-lg font-semibold tracking-tight text-foreground">
            {title}
          </h2>
          {badge}
        </div>
        {description && (
          <p className="text-xs text-muted-foreground">{description}</p>
        )}
      </div>
      {actions && <div className="flex items-center gap-2 shrink-0">{actions}</div>}
    </div>
  )
}
