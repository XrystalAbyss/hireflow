import * as React from "react"
import { NavLink } from "react-router-dom"
import {
  GraduationCap,
  Briefcase,
  FileCheck2,
  CalendarDays,
  LayoutDashboard,
  LogOut,
  Menu,
  X,
  User,
} from "lucide-react"
import { useAuth } from "@/core/auth/AuthContext"
import { Button } from "@/components/ui/button"
import { cn } from "@/lib/utils"
import { useQueryClient } from "@tanstack/react-query"
import { warmRoute } from "@/core/navigation/warmRoute"

export interface StudentShellProps {
  children: React.ReactNode
  academicProfile?: {
    name: string
    studentId: string
    branch: string
    cgpa: number
  }
}

export const StudentShell: React.FC<StudentShellProps> = ({
  children,
  academicProfile,
}) => {
  const { user, logout } = useAuth()
  const queryClient = useQueryClient()
  const [mobileMenuOpen, setMobileMenuOpen] = React.useState(false)

  const navLinks = [
    { to: "/student/dashboard", label: "Dashboard", icon: LayoutDashboard },
    { to: "/student/drives", label: "Recruitment Drives", icon: Briefcase },
    { to: "/student/applications", label: "My Applications", icon: FileCheck2 },
    { to: "/student/interviews", label: "Interview Timetable", icon: CalendarDays },
  ]

  return (
    <div className="min-h-screen bg-background flex flex-col font-sans selection:bg-teal-700/20 selection:text-teal-900">
      {/* Top Application Header */}
      <header className="sticky top-0 z-40 border-b border-border bg-card/95 backdrop-blur-xs">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex h-16 items-center justify-between gap-3 min-w-0">
            {/* Logo & Portal Identity */}
            <div className="flex items-center gap-3 shrink-0">
              <div className="h-9 w-9 rounded-sm bg-primary flex items-center justify-center text-primary-foreground shadow-xs">
                <GraduationCap className="h-5 w-5 text-brass-600" aria-hidden="true" />
              </div>
              <div>
                <span className="font-serif font-bold text-lg text-foreground tracking-tight block leading-tight">
                  HIREFLOW
                </span>
                <span className="text-xs uppercase font-mono tracking-wider text-muted-foreground block">
                  Candidate Opportunity Hub
                </span>
              </div>
            </div>

            {/* User Session Bar */}
            <div className="hidden sm:flex items-center gap-3 text-xs shrink-0">
              <div className="flex items-center gap-2 px-3 py-1 rounded-sm bg-muted/60 border border-border/60 max-w-[15rem] min-w-0">
                <User className="h-3.5 w-3.5 text-accent shrink-0" aria-hidden="true" />
                <span className="font-semibold text-foreground truncate">
                  {user?.displayName || academicProfile?.name || "Student"}
                </span>
                <span className="font-mono text-muted-foreground shrink-0">
                  ({user?.studentId || academicProfile?.studentId || user?.username || "Profile unavailable"})
                </span>
              </div>

              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={logout}
                className="h-8 text-xs gap-1.5 whitespace-nowrap"
                aria-label="Sign out of student account"
              >
                <LogOut className="h-3.5 w-3.5" aria-hidden="true" />
                <span>Sign Out</span>
              </Button>
            </div>

            {/* Mobile Hamburger Button */}
            <div className="flex md:hidden">
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

          {/* Desktop Navigation */}
          <nav className="hidden md:flex items-center gap-1 pb-2" aria-label="Candidate Navigation">
            {navLinks.map((link) => {
              const Icon = link.icon
              return (
                <NavLink
                  key={link.to}
                  to={link.to}
                  onPointerEnter={() => { void warmRoute(link.to, queryClient) }}
                  onFocus={() => { void warmRoute(link.to, queryClient) }}
                  className={({ isActive }) =>
                    cn(
                      "flex items-center gap-2 px-3 py-1.5 rounded-sm text-xs sm:text-sm font-medium whitespace-nowrap transition-colors",
                      isActive
                        ? "bg-secondary text-primary font-semibold shadow-xs"
                        : "text-muted-foreground hover:text-foreground hover:bg-muted/60"
                    )
                  }
                >
                  <Icon className="h-4 w-4 shrink-0 text-accent" aria-hidden="true" />
                  <span>{link.label}</span>
                </NavLink>
              )
            })}
          </nav>
        </div>

        {/* Mobile Navigation Drawer */}
        {mobileMenuOpen && (
          <div className="md:hidden border-b border-border bg-card px-4 pt-2 pb-4 space-y-2">
            <div className="py-2 border-b border-border/60 text-xs text-muted-foreground">
              Signed in as{" "}
              <strong className="text-foreground">
                {user?.displayName || academicProfile?.name || "Student"}
              </strong>
            </div>
            {navLinks.map((link) => {
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
                        ? "bg-secondary text-primary font-semibold"
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

      {/* Backend-Authoritative Academic Profile Bar */}
      <section
        className="bg-ivory-100/90 border-b border-border px-4 py-2.5 text-xs text-muted-foreground"
        aria-label="Verified Academic Profile"
      >
        <div className="max-w-7xl mx-auto flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-2.5">
            <span className="font-semibold uppercase tracking-wider text-primary flex items-center gap-1.5 text-xs">
              <span className="h-2 w-2 rounded-full bg-accent" aria-hidden="true" />
              Academic Profile (Read-Only):
            </span>
            <span className="text-foreground font-semibold text-xs">
              {academicProfile ? `${academicProfile.branch} Branch` : "Verified profile unavailable"}
            </span>
          </div>

          <div className="flex items-center gap-4 text-xs font-mono">
            <span className="flex items-center gap-1.5">
              <span className="text-muted-foreground">Cumulative GPA:</span>
              <strong className="font-mono font-bold text-primary text-xs px-2 py-0.5 rounded bg-card border border-border/80">
                {academicProfile ? academicProfile.cgpa.toFixed(2) : "—"}
              </strong>
            </span>
            <span className="hidden sm:inline border-l border-border pl-3 text-muted-foreground">
              {academicProfile ? "Backend Authoritative Record" : "Refresh to retry profile lookup"}
            </span>
          </div>
        </div>
      </section>

      {/* Main Content Area */}
      <main className="flex-1 max-w-7xl w-full mx-auto p-4 sm:p-6 lg:p-8 space-y-6">
        {children}
      </main>

      {/* Institutional Footer */}
      <footer className="border-t border-border bg-card/60 py-4 px-4 sm:px-6 text-xs text-muted-foreground select-none">
        <div className="max-w-7xl mx-auto flex flex-col sm:flex-row items-center justify-between gap-2 text-xs">
          <span>HireFlow Candidate Opportunity Hub • Career Services</span>
          <span>Student Portal</span>
        </div>
      </footer>
    </div>
  )
}

