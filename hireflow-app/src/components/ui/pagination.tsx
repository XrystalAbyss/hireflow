import * as React from "react"
import { ChevronLeft, ChevronRight } from "lucide-react"
import { Button } from "@/components/ui/button"
import { cn } from "@/lib/utils"

export interface PaginationProps extends React.HTMLAttributes<HTMLDivElement> {
  page: number // 0-indexed
  totalPages: number
  totalElements: number
  pageSize: number
  onPageChange: (newPage: number) => void
  onPageSizeChange?: (newSize: number) => void
}

export function Pagination({
  page,
  totalPages,
  totalElements,
  pageSize,
  onPageChange,
  onPageSizeChange,
  className,
  ...props
}: PaginationProps) {
  const fromIndex = totalElements === 0 ? 0 : page * pageSize + 1
  const toIndex = Math.min((page + 1) * pageSize, totalElements)

  return (
    <div
      className={cn(
        "flex flex-col sm:flex-row items-center justify-between gap-4 py-3 px-2 text-xs text-muted-foreground border-t border-border/80 select-none",
        className
      )}
      {...props}
    >
      <div className="flex items-center gap-2">
        <span>
          Showing <strong className="font-semibold text-foreground">{fromIndex}</strong> to{" "}
          <strong className="font-semibold text-foreground">{toIndex}</strong> of{" "}
          <strong className="font-semibold text-foreground">{totalElements}</strong> entries
        </span>
        {onPageSizeChange && (
          <div className="flex items-center gap-1 ml-4">
            <span>Rows:</span>
            <select
              value={pageSize}
              onChange={(e) => onPageSizeChange(Number(e.target.value))}
              aria-label="Rows per page"
              className="h-7 rounded-xs border border-input bg-card px-2 py-0.5 text-xs text-foreground focus:outline-none focus:ring-1 focus:ring-ring"
            >
              <option value={10}>10</option>
              <option value={20}>20</option>
              <option value={50}>50</option>
              <option value={100}>100</option>
            </select>
          </div>
        )}
      </div>

      <div className="flex items-center gap-1">
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={() => onPageChange(page - 1)}
          disabled={page <= 0}
          aria-label="Previous page"
          className="h-7 px-2"
        >
          <ChevronLeft className="h-3.5 w-3.5 mr-1" />
          Previous
        </Button>

        <span className="px-2 text-foreground font-medium">
          Page {page + 1} of {Math.max(1, totalPages)}
        </span>

        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={() => onPageChange(page + 1)}
          disabled={page >= totalPages - 1}
          aria-label="Next page"
          className="h-7 px-2"
        >
          Next
          <ChevronRight className="h-3.5 w-3.5 ml-1" />
        </Button>
      </div>
    </div>
  )
}
