import * as React from "react"
import { cn } from "@/lib/utils"

export interface LoadingSkeletonProps extends React.HTMLAttributes<HTMLDivElement> {
  rows?: number
  height?: string
}

export function LoadingSkeleton({
  rows = 3,
  height = "h-4",
  className,
  ...props
}: LoadingSkeletonProps) {
  return (
    <div className={cn("w-full space-y-2.5 animate-pulse", className)} {...props}>
      {Array.from({ length: rows }).map((_, i) => (
        <div
          key={i}
          className={cn(
            "w-full rounded-xs bg-muted/80",
            height,
            i === rows - 1 && rows > 1 ? "w-4/5" : "w-full"
          )}
        />
      ))}
    </div>
  )
}
