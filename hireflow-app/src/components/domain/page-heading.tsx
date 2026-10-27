import * as React from "react"
import { Breadcrumbs, type BreadcrumbItem } from "@/components/ui/breadcrumbs"
import { cn } from "@/lib/utils"

export interface PageHeadingProps extends React.HTMLAttributes<HTMLDivElement> {
  title: string
  description?: string
  breadcrumbs?: BreadcrumbItem[]
  badge?: React.ReactNode
  actions?: React.ReactNode
}

export function PageHeading({
  title,
  description,
  breadcrumbs,
  badge,
  actions,
  className,
  ...props
}: PageHeadingProps) {
  return (
    <div
      className={cn(
        "flex flex-col gap-3 pb-5 pt-1 border-b border-border/80 select-none",
        className
      )}
      {...props}
    >
      {breadcrumbs && breadcrumbs.length > 0 && (
        <Breadcrumbs items={breadcrumbs} className="mb-0.5" />
      )}
      <div className="flex flex-col sm:flex-row sm:flex-wrap sm:items-center sm:justify-between gap-4">
        <div className="min-w-0 flex-1 space-y-1">
          <div className="flex items-center gap-2.5 flex-wrap">
            <h1 className="font-serif text-2xl font-bold tracking-tight text-foreground sm:text-3xl">
              {title}
            </h1>
            {badge}
          </div>
          {description && (
            <p className="text-sm text-muted-foreground leading-normal max-w-3xl">
              {description}
            </p>
          )}
        </div>
        {actions && <div className="flex max-w-full min-w-0 items-center gap-2.5">{actions}</div>}
      </div>
    </div>
  )
}
