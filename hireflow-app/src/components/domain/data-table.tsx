import * as React from "react"
import {
  type ColumnDef,
  flexRender,
  getCoreRowModel,
  useReactTable,
  type SortingState,
  getSortedRowModel,
} from "@tanstack/react-table"
import { ArrowUpDown, ArrowUp, ArrowDown } from "lucide-react"
import { cn } from "@/lib/utils"
import { LoadingSkeleton } from "@/components/domain/loading-skeleton"
import { EmptyState } from "@/components/domain/empty-state"

export interface DataTableProps<TData, TValue> {
  columns: ColumnDef<TData, TValue>[]
  data: TData[]
  isLoading?: boolean
  emptyTitle?: string
  emptyDescription?: string
  onRowClick?: (row: TData) => void
  density?: "comfortable" | "compact"
  className?: string
}

export function DataTable<TData, TValue>({
  columns,
  data,
  isLoading = false,
  emptyTitle = "No records found",
  emptyDescription = "There are no entries matching your current criteria.",
  onRowClick,
  density = "compact",
  className,
}: DataTableProps<TData, TValue>) {
  const [sorting, setSorting] = React.useState<SortingState>([])

  const table = useReactTable({
    data,
    columns,
    state: {
      sorting,
    },
    onSortingChange: setSorting,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
  })

  const rowPadding = density === "compact" ? "py-2.5 px-3.5 text-xs sm:text-sm" : "py-3.5 px-4 text-sm"
  const headerPadding = density === "compact" ? "py-3 px-3.5 text-xs" : "py-3.5 px-4 text-xs"

  return (
    <div className={cn("min-w-0 max-w-full w-full overflow-hidden rounded-sm border border-border bg-card shadow-xs", className)}>
      <div className="overflow-x-auto">
        <table className="w-full text-left border-collapse">
          <thead className="bg-muted/80 border-b-2 border-border text-foreground font-semibold uppercase tracking-wider select-none sticky top-0 z-10">
            {table.getHeaderGroups().map((headerGroup) => (
              <tr key={headerGroup.id}>
                {headerGroup.headers.map((header) => {
                  const canSort = header.column.getCanSort()
                  const sortDir = header.column.getIsSorted()
                  return (
                    <th
                      key={header.id}
                      className={cn(
                        headerPadding,
                        "font-semibold text-foreground tracking-wider whitespace-nowrap",
                        canSort && "cursor-pointer hover:bg-muted/90 focus-visible:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-accent transition-colors",
                        sortDir && "bg-muted text-accent font-bold"
                      )}
                      onClick={header.column.getToggleSortingHandler()}
                      onKeyDown={(e) => {
                        if (canSort && (e.key === "Enter" || e.key === " ")) {
                          e.preventDefault()
                          header.column.getToggleSortingHandler()?.(e)
                        }
                      }}
                      tabIndex={canSort ? 0 : undefined}

                      aria-sort={
                        sortDir === "asc"
                          ? "ascending"
                          : sortDir === "desc"
                          ? "descending"
                          : canSort
                          ? "none"
                          : undefined
                      }
                      scope="col"
                    >
                      <div className="flex items-center gap-1.5">
                        <span className="truncate">
                          {header.isPlaceholder
                            ? null
                            : flexRender(
                                header.column.columnDef.header,
                                header.getContext()
                              )}
                        </span>
                        {canSort && (
                          <span
                            className={cn(
                              "inline-flex items-center justify-center rounded p-0.5 transition-colors shrink-0",
                              sortDir
                                ? "bg-accent/15 text-accent"
                                : "text-muted-foreground/60 hover:text-foreground"
                            )}
                          >
                            {sortDir === "asc" ? (
                              <ArrowUp className="h-3.5 w-3.5 stroke-[2.5]" aria-hidden="true" />
                            ) : sortDir === "desc" ? (
                              <ArrowDown className="h-3.5 w-3.5 stroke-[2.5]" aria-hidden="true" />
                            ) : (
                              <ArrowUpDown className="h-3 w-3" aria-hidden="true" />
                            )}
                          </span>
                        )}
                      </div>
                    </th>
                  )
                })}
              </tr>
            ))}
          </thead>
          <tbody className="divide-y divide-border/70">
            {isLoading ? (
              <tr>
                <td colSpan={columns.length} className="p-4">
                  <LoadingSkeleton rows={5} />
                </td>
              </tr>
            ) : table.getRowModel().rows.length > 0 ? (
              table.getRowModel().rows.map((row) => (
                <tr
                  key={row.id}
                  onClick={() => onRowClick && onRowClick(row.original)}
                  tabIndex={onRowClick ? 0 : undefined}
                  role={onRowClick ? "button" : undefined}
                  onKeyDown={
                    onRowClick
                      ? (e) => {
                          if (e.key === "Enter" || e.key === " ") {
                            e.preventDefault()
                            onRowClick(row.original)
                          }
                        }
                      : undefined
                  }
                  className={cn(
                    "hover:bg-academic-teal/[0.04] transition-colors odd:bg-card even:bg-muted/20",
                    onRowClick && "cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-accent"
                  )}
                >
                  {row.getVisibleCells().map((cell) => (
                    <td key={cell.id} className={cn(rowPadding, "text-foreground font-normal align-middle")}>
                      {flexRender(cell.column.columnDef.cell, cell.getContext())}
                    </td>
                  ))}
                </tr>
              ))
            ) : (
              <tr>
                <td colSpan={columns.length} className="py-12 px-4 text-center">
                  <EmptyState title={emptyTitle} description={emptyDescription} />
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}
