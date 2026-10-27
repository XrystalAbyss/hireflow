import * as React from "react"
import { Building2, ShieldCheck } from "lucide-react"

export interface AuthenticationLayoutProps {
  children: React.ReactNode
  title?: string
  subtitle?: string
}

const journey = [
  { number: "01", title: "Discover", detail: "Find recruitment drives that match your profile." },
  { number: "02", title: "Apply", detail: "Submit and follow your application." },
  { number: "03", title: "Meet", detail: "See published interview appointments." },
]

export const AuthenticationLayout: React.FC<AuthenticationLayoutProps> = ({
  children,
  title = "HireFlow Campus Recruitment",
  subtitle = "University recruitment and interview scheduling",
}) => (
  <div className="min-h-screen bg-ivory-50 text-midnight-950 selection:bg-teal-700/20 selection:text-teal-900">
    <header className="border-b border-ivory-300/80 bg-ivory-50 px-5 py-4 sm:px-8">
      <div className="mx-auto flex max-w-7xl items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          <div className="flex h-9 w-9 items-center justify-center rounded-sm bg-midnight-950 text-brass-500">
            <Building2 className="h-5 w-5" aria-hidden="true" />
          </div>
          <div>
            <span className="block font-serif text-lg font-bold leading-tight tracking-tight">HIREFLOW</span>
            <span className="block text-xs text-ivory-800">Campus recruitment</span>
          </div>
        </div>
        <div className="flex items-center gap-2 text-xs text-ivory-800">
          <ShieldCheck className="h-4 w-4 text-teal-800" aria-hidden="true" />
          <span className="hidden sm:inline">Secure institutional access</span>
          <span className="sm:hidden">Secure access</span>
        </div>
      </div>
    </header>

    <main className="mx-auto grid w-full max-w-7xl flex-1 items-stretch lg:min-h-[calc(100vh-137px)] lg:grid-cols-[minmax(0,1.08fr)_minmax(380px,0.92fr)]">
      <section className="relative isolate hidden overflow-hidden bg-midnight-950 px-10 py-14 text-ivory-50 lg:flex lg:flex-col lg:justify-between xl:px-16" aria-label="How HireFlow works">
        <div className="pointer-events-none absolute -right-28 -top-32 h-96 w-96 rounded-full border border-teal-500/25" aria-hidden="true" />
        <div className="pointer-events-none absolute -right-16 -top-20 h-96 w-96 rounded-full border border-brass-500/20" aria-hidden="true" />
        <div className="relative auth-enter">
          <p className="mb-8 text-sm font-medium text-brass-500">One place for the whole journey</p>
          <h2 className="max-w-lg font-serif text-5xl leading-[1.08] tracking-tight xl:text-6xl">From opportunity to interview.</h2>
          <p className="mt-6 max-w-md text-base leading-relaxed text-ivory-200">Explore openings, follow each application, and see confirmed interview appointments in one connected workspace.</p>
        </div>
        <ol className="relative mt-16 border-l border-ivory-300/30 pl-7" aria-label="Recruitment journey">
          {journey.map((step, index) => (
            <li key={step.number} className="auth-step relative py-4" style={{ animationDelay: `${130 + index * 90}ms` }}>
              <span className="absolute -left-[35px] top-6 h-3 w-3 rounded-full border-2 border-brass-500 bg-midnight-950" aria-hidden="true" />
              <span className="mr-5 font-mono text-xs text-brass-500">{step.number}</span>
              <span className="font-serif text-xl">{step.title}</span>
              <p className="ml-10 mt-1 text-sm text-ivory-200">{step.detail}</p>
            </li>
          ))}
        </ol>
        <p className="relative mt-12 text-xs text-ivory-300">For students and placement officers</p>
      </section>

      <section className="flex min-w-0 items-center px-5 py-12 sm:px-10 lg:px-12 xl:px-20" aria-labelledby="sign-in-title">
        <div className="auth-enter mx-auto w-full max-w-md">
          <p className="mb-4 text-sm font-semibold text-teal-800">Placement portal</p>
          <h1 id="sign-in-title" className="font-serif text-4xl leading-tight tracking-tight text-midnight-950 sm:text-5xl">{title}</h1>
          <p className="mt-4 max-w-sm text-sm leading-relaxed text-ivory-800">{subtitle}</p>
          <div className="mt-9 border-t border-ivory-300 pt-8">{children}</div>
          <p className="mt-8 text-xs leading-relaxed text-ivory-800">Use the credentials issued by your institution. Contact your placement office if you need access.</p>
        </div>
      </section>
    </main>

    <footer className="border-t border-ivory-300/80 bg-ivory-50 px-5 py-4 text-xs text-ivory-800 sm:px-8">
      <div className="mx-auto flex max-w-7xl flex-wrap items-center justify-between gap-2">
        <span>HireFlow Campus Recruitment Platform</span>
        <span>University Career & Placement Services</span>
      </div>
    </footer>
  </div>
)
