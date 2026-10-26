import * as React from 'react'
import type { StudentProfileDto } from '@/types/api'
import { GraduationCap, ShieldCheck, Mail, Calendar, HelpCircle } from 'lucide-react'

export interface StudentDossierBannerProps {
  profile: StudentProfileDto
  className?: string
}

export const StudentDossierBanner: React.FC<StudentDossierBannerProps> = ({
  profile,
  className = '',
}) => {
  return (
    <div
      className={`rounded-lg border border-border bg-card p-5 sm:p-6 shadow-xs ${className}`}
      aria-label="Verified Academic Dossier"
    >
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 pb-4 border-b border-border/80">
        <div className="flex items-start sm:items-center gap-3.5">
          <div className="h-11 w-11 rounded-md bg-primary/10 border border-primary/20 flex items-center justify-center text-primary shrink-0">
            <GraduationCap className="h-6 w-6 text-accent" aria-hidden="true" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h2 className="font-serif font-bold text-lg sm:text-xl text-foreground">
                {profile.name}
              </h2>
              <span className="inline-flex items-center gap-1 text-xs font-medium text-teal-800 bg-teal-900/10 border border-teal-700/20 px-2 py-0.5 rounded-full">
                <ShieldCheck className="h-3 w-3" aria-hidden="true" />
                Verified Student
              </span>
            </div>
            <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-muted-foreground mt-0.5">
              <span className="font-mono text-foreground font-medium">Roll No: {profile.id}</span>
              <span className="flex items-center gap-1">
                <Mail className="h-3 w-3" aria-hidden="true" />
                {profile.email}
              </span>
            </div>
          </div>
        </div>

        {/* Academic GPA Highlight pill */}
        <div className="flex items-center gap-3 shrink-0 self-start md:self-auto bg-ivory-100/90 border border-border px-3.5 py-2 rounded-md">
          <div className="text-right">
            <span className="text-xs uppercase tracking-wider text-muted-foreground block font-sans">
              Cumulative GPA
            </span>
            <span className="font-mono font-bold text-lg text-primary leading-tight">
              {profile.cgpa.toFixed(2)}
            </span>
          </div>
          <div className="h-7 w-px bg-border/80 mx-1" aria-hidden="true" />
          <div className="text-left">
            <span className="text-xs uppercase tracking-wider text-muted-foreground block font-sans">
              Class of
            </span>
            <span className="font-mono font-semibold text-foreground text-sm flex items-center gap-1">
              <Calendar className="h-3.5 w-3.5 text-accent" aria-hidden="true" />
              {profile.graduationYear}
            </span>
          </div>
        </div>
      </div>

      {/* Academic Affiliation & Administrative Notice */}
      <div className="pt-3.5 flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-xs text-muted-foreground">
        <div>
          <span className="font-medium text-foreground">Academic Department:</span>{' '}
          <span className="text-primary font-semibold">{profile.branch}</span>
        </div>
        <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
          <HelpCircle className="h-3.5 w-3.5 shrink-0 text-muted-foreground" aria-hidden="true" />
          <span>To report CGPA or branch discrepancies, contact the University Placement Cell.</span>
        </div>
      </div>
    </div>
  )
}
