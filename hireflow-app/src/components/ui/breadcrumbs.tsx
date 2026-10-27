import * as React from "react"
import { ChevronRight } from "lucide-react"
import { cn } from "@/lib/utils"

export interface BreadcrumbItem {
  label: string
  href?: string
}

export interface BreadcrumbsProps extends React.HTMLAttributes<HTMLElement> {
  items: BreadcrumbItem[]
}

export function Breadcrumbs({ items, className, ...props }: BreadcrumbsProps) {
  return (
    <nav aria-label="Breadcrumb" className={cn("flex items-center text-xs text-muted-foreground", className)} {...props}>
      <ol className="flex items-center space-x-1.5">
        {items.map((item, index) => {
          const isLast = index === items.length - 1
          return (
            <li key={index} className="flex items-center space-x-1.5">
              {index > 0 && <ChevronRight className="h-3 w-3 text-muted-foreground/60" aria-hidden="true" />}
              {isLast || !item.href ? (
                <span className="font-medium text-foreground tracking-tight" aria-current={isLast ? "page" : undefined}>
                  {item.label}
                </span>
              ) : (
                <a
                  href={item.href}
                  className="hover:text-accent hover:underline transition-colors"
                >
                  {item.label}
                </a>
              )}
            </li>
          )
        })}
      </ol>
    </nav>
  )
}
