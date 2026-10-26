import * as React from "react"
import { Slot } from "@radix-ui/react-slot"
import { cva, type VariantProps } from "class-variance-authority"
import { cn } from "@/lib/utils"

const buttonVariants = cva(
  "inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-sm text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 [&_svg]:pointer-events-none [&_svg]:size-4 [&_svg]:shrink-0 cursor-pointer select-none",
  {
    variants: {
      variant: {
        default:
          "bg-primary text-primary-foreground hover:bg-midnight-900 active:bg-midnight-950 border border-transparent shadow-xs",
        accent:
          "bg-accent text-accent-foreground hover:bg-teal-800 active:bg-teal-900 border border-transparent shadow-xs",
        secondary:
          "bg-secondary text-secondary-foreground hover:bg-ivory-200 active:bg-ivory-300 border border-border/80 shadow-xs",
        outline:
          "border border-border bg-card hover:bg-ivory-100 text-foreground active:bg-ivory-200 shadow-xs",
        ghost:
          "hover:bg-muted text-foreground active:bg-ivory-200",
        destructive:
          "bg-status-rejected-bg text-status-rejected-text border border-status-rejected-border hover:bg-red-100 active:bg-red-200",
        link:
          "text-accent underline-offset-4 hover:underline p-0 h-auto font-normal",
      },
      size: {
        sm: "h-8 rounded-sm px-3 text-xs",
        default: "h-9 px-4 py-2 text-sm",
        lg: "h-10 rounded-sm px-6 text-base",
        icon: "h-9 w-9 p-0",
      },
    },
    defaultVariants: {
      variant: "default",
      size: "default",
    },
  }
)

import { Loader2 } from "lucide-react"

export interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {
  asChild?: boolean
  loading?: boolean
}

const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant, size, asChild = false, loading = false, disabled, children, ...props }, ref) => {
    const Comp = asChild ? Slot : "button"
    return (
      <Comp
        className={cn(buttonVariants({ variant, size, className }))}
        ref={ref}
        disabled={disabled || loading}
        aria-busy={loading ? "true" : undefined}
        {...props}
      >
        {loading && <Loader2 className="h-4 w-4 animate-spin shrink-0" aria-hidden="true" />}
        {children}
      </Comp>
    )
  }
)
Button.displayName = "Button"

export { Button, buttonVariants }
