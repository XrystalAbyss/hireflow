import * as React from "react"
import { Eye, EyeOff } from "lucide-react"
import { Input, type InputProps } from "@/components/ui/input"
import { cn } from "@/lib/utils"

export interface PasswordFieldProps extends Omit<InputProps, "type"> {
  showToggle?: boolean
}

const PasswordField = React.forwardRef<HTMLInputElement, PasswordFieldProps>(
  ({ className, showToggle = true, ...props }, ref) => {
    const [visible, setVisible] = React.useState(false)

    return (
      <div className="relative w-full">
        <Input
          type={visible ? "text" : "password"}
          className={cn(showToggle && "pr-10", className)}
          ref={ref}
          {...props}
        />
        {showToggle && (
          <button
            type="button"
            onClick={() => setVisible((prev) => !prev)}
            aria-label={visible ? "Hide password" : "Show password"}
            className="absolute right-0 top-0 flex h-full items-center px-3 text-muted-foreground hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-1 rounded-sm"
            tabIndex={0}
          >
            {visible ? (
              <EyeOff className="h-4 w-4" aria-hidden="true" />
            ) : (
              <Eye className="h-4 w-4" aria-hidden="true" />
            )}
          </button>
        )}
      </div>
    )
  }
)
PasswordField.displayName = "PasswordField"

export { PasswordField }
