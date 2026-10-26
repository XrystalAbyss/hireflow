import * as React from "react"
import { NavLink } from "react-router-dom"
import {
  Building2,
  Briefcase,
  Users,
  DoorOpen,
  CalendarClock,
  LayoutDashboard,
  LogOut,
  Menu,
  X,
  Shield,
  Activity,
} from "lucide-react"
import { useAuth } from "@/core/auth/AuthContext"
import { Button } from "@/components/ui/button"
import { cn } from "@/lib/utils"
import { useQueryClient } from "@tanstack/react-query"
import { warmRoute } from "@/core/navigation/warmRoute"

export interface OfficerShellProps {
  children: React.ReactNode
}

export const OfficerShell: React.FC<OfficerShellProps> = ({ children }) => {
  const { user, logout } = useAuth()
  const queryClient = useQueryClient()
  const [mobileMenuOpen, setMobileMenuOpen] = React.useState(false)

  const officerNavLinks = [
    { to: "/officer/dashboard", label: "Command Center", icon: LayoutDashboard },
    { to: "/officer/companies", label: "Corporate Partners", icon: Building2 },
    { to: "/officer/drives", label: "Recruitment Drives", icon: Briefcase },
    { to: "/officer/applications", label: "Candidate Review", icon: Users },
    { to: "/officer/resources", label: "Resource Pools", icon: DoorOpen },
    { to: "/officer/scheduling", label: "Scheduling Studio", icon: CalendarClock },
  ]

  return (
    <div className="min-h-screen bg-background flex flex-col font-sans selection:bg-teal-700/20 selection:text-teal-900">
      {/* Top Officer Command Header */}
      <header className="sticky top-0 z-40 border-b border-border bg-card/95 backdrop-blur-xs">
        <div className="max-w-7xl mx-auto px-4 sm:px-6">
          <div className="flex h-16 items-center justify-between gap-3 min-w-0">
            {/* Brand Logo & Officer Tag */}
            <div className="flex items-center gap-3 shrink-0">
              <div className="h-9 w-9 rounded-sm bg-primary flex items-center justify-center text-primary-foreground shadow-xs">
                <Shield className="h-5 w-5 text-brass-600" aria-hidden="true" />
              </div>
              <div>
                <div className="flex items-center gap-2">
                  <span className="font-serif font-bold text-lg text-foreground tracking-tight block leading-tight">
                    HIREFLOW
                  </span>
                  <span className="px-2 py-0.5 rounded-xs bg-primary text-primary-foreground text-xs font-mono uppercase tracking-wider">
                    Officer
                  </span>
                </div>
                <span className="hidden xl:block text-xs uppercase font-mono tracking-wider text-muted-foreground">
                  Placement Administration & Timetable Studio
                </span>
              </div>
            </div>

            {/* Operational Session Controls */}
            <div className="hidden sm:flex items-center gap-2.5 text-xs shrink-0">
              <div className="hidden xl:flex items-center gap-1.5 px-2.5 py-1 rounded-sm bg-muted/70 border border-border/80 text-xs max-w-[16rem] min-w-0">
                <Activity className="h-3.5 w-3.5 text-accent shrink-0" aria-hidden="true" />
                <span className="text-muted-foreground shrink-0">Officer:</span>
                <strong className="font-semibold text-foreground truncate">
                  {user?.displayName || "Placement Officer"}
                </strong>
              </div>

              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={logout}
                className="h-8 text-xs gap-1.5 whitespace-nowrap"
                aria-label="Sign out of placement officer account"
              >
                <LogOut className="h-3.5 w-3.5" aria-hidden="true" />
                <span>Sign Out</span>
              </Button>
            </div>

            {/* Mobile Hamburger Button */}
            <div className="flex lg:hidden">
              <button
                type="button"
                onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
                className="p-2 rounded-sm text-muted-foreground hover:text-foreground hover:bg-muted focus:outline-none focus:ring-2 focus:ring-ring"
                aria-label={mobileMenuOpen ? "Close menu" : "Open menu"}
                aria-expanded={mobileMenuOpen}
              >
                {mobileMenuOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
              </button>
            </div>
          </div>

          {/* Desktop Operational Navigation */}
          <nav className="hidden lg:flex items-center gap-1 pb-2" aria-label="Placement Officer Navigation">
            {officerNavLinks.map((link) => {
              const Icon = link.icon
              return (
                <NavLink
                  key={link.to}
                  to={link.to}
                  onPointerEnter={() => { void warmRoute(link.to, queryClient) }}
                  onFocus={() => { void warmRoute(link.to, queryClient) }}
                  className={({ isActive }) =>
                    cn(
                      "flex items-center gap-1.5 px-2.5 py-1.5 rounded-sm text-xs font-medium whitespace-nowrap transition-colors",
                      isActive
                        ? "bg-primary text-primary-foreground font-semibold shadow-xs"
                        : "text-muted-foreground hover:text-foreground hover:bg-muted/70"
                    )
                  }
                >
                  <Icon className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                  <span>{link.label}</span>
                </NavLink>
              )
            })}
          </nav>
        </div>

        {/* Mobile Navigation Drawer */}
        {mobileMenuOpen && (
          <div className="lg:hidden border-b border-border bg-card px-4 pt-2 pb-4 space-y-2">
            <div className="py-2 border-b border-border/60 text-xs text-muted-foreground">
              Officer Account:{" "}
              <strong className="text-foreground">
                {user?.displayName || "Placement Officer"}
              </strong>
            </div>
            {officerNavLinks.map((link) => {
              const Icon = link.icon
              return (
                <NavLink
                  key={link.to}
                  to={link.to}
                  onPointerEnter={() => { void warmRoute(link.to, queryClient) }}
                  onFocus={() => { void warmRoute(link.to, queryClient) }}
                  onClick={() => setMobileMenuOpen(false)}
                  className={({ isActive }) =>
                    cn(
                      "flex items-center gap-2.5 px-3 py-2 rounded-sm text-sm font-medium transition-colors",
                      isActive
                        ? "bg-primary text-primary-foreground font-semibold"
                        : "text-muted-foreground hover:bg-muted"
                    )
                  }
                >
                  <Icon className="h-4 w-4 text-accent" />
                  <span>{link.label}</span>
                </NavLink>
              )
            })}
            <div className="pt-2">
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={logout}
                className="w-full justify-center gap-2 text-xs"
              >
                <LogOut className="h-3.5 w-3.5" />
                Sign Out
              </Button>
            </div>
          </div>
        )}
      </header>

      {/* Main High-Density Operational Content */}
      <main className="flex-1 max-w-7xl w-full mx-auto p-4 sm:p-6 lg:p-8 space-y-6">
        {children}
      </main>

      {/* Institutional System Status Footer */}
      <footer className="border-t border-border bg-card/60 py-3.5 px-4 sm:px-6 text-xs text-muted-foreground select-none">
        <div className="max-w-7xl mx-auto flex flex-col sm:flex-row items-center justify-between gap-2 text-xs">
          <span>Placement Command Center • Interview Scheduling Studio</span>
          <span>HireFlow Operational Administration</span>
        </div>
      </footer>
    </div>
  )
}
