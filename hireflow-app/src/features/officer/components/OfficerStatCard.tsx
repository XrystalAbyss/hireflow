import * as React from 'react'
import { cn } from '@/lib/utils'

export interface OfficerStatCardProps {
  label: string
  value: string | number
  subtext?: string
  icon?: React.ComponentType<{ className?: string }>
  badge?: string
  tone?: 'default' | 'accent' | 'warning' | 'brass'
  className?: string
  onClick?: () => void
}

export function OfficerStatCard({ label, value, subtext, badge, className, onClick }: OfficerStatCardProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={!onClick}
      className={cn(
        'min-w-0 p-4 text-left transition-colors sm:p-5',
        onClick ? 'cursor-pointer hover:bg-muted/40' : 'cursor-default',
        className
      )}
    >
      <span className="block text-xs font-medium text-muted-foreground">{label}</span>
      <span className="mt-2 flex items-baseline gap-2 text-2xl font-medium tabular-nums text-foreground sm:text-3xl">
        {value}
        {badge && <span className="text-xs font-normal text-muted-foreground">{badge}</span>}
      </span>
      {subtext && <span className="mt-1 block text-xs leading-snug text-muted-foreground">{subtext}</span>}
    </button>
  )
}
