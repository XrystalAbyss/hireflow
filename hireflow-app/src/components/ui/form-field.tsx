import * as React from "react"
import { cn } from "@/lib/utils"

export interface FormFieldProps extends React.HTMLAttributes<HTMLDivElement> {
  label?: string
  htmlFor?: string
  id?: string
  required?: boolean
  error?: string
  helperText?: string
  description?: string
}

export const FormField = React.forwardRef<HTMLDivElement, FormFieldProps>(
  ({ className, label, htmlFor, id, required, error, helperText, description, children, ...props }, ref) => {
    const targetId = htmlFor || id
    const hint = helperText || description

    return (
      <div ref={ref} className={cn("space-y-1.5 w-full", className)} {...props}>
        {label && (
          <label
            htmlFor={targetId}
            className="block text-sm font-medium text-foreground tracking-tight select-none"
          >
            {label}
            {required && <span className="text-status-rejected-text ml-0.5" aria-hidden="true">*</span>}
          </label>
        )}
        {children}
        {error ? (
          <p className="text-xs text-status-rejected-text font-medium" role="alert">
            {error}
          </p>
        ) : hint ? (
          <p className="text-xs text-muted-foreground">{hint}</p>
        ) : null}
      </div>
    )
  }
)
FormField.displayName = "FormField"
