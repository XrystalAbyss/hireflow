# HireFlow RFC — Verified Academic Records, DigiLocker/NAD and ABC/APAAR

**Status:** Architecture proposal / provider access not established (2026-11-07)  
**Baseline:** `main` at `e53b4be6dc511f910e4b1390aa93f8c56e6e7099`  
**Owners:** HireFlow project maintainers, academic records issuer/placement cell and authorized DigiLocker requester (if approved)

## 1. Problem and product promise

HireFlow currently stores editable student CGPA in `src/main/java/com/hireflow/model/Student.java` and evaluates it in `EligibilityService.checkAcademicEligibility()`. The server checks a numeric range (0–10) but not institutional origin. `StudentProfileDto` exposes the recorded CGPA and the React student/officer interfaces previously presented this as "verified". The existing data is **not independently verified**. This RFC must not be represented as a live provider integration.

**Goal:** Minimize manual academic data entry, represent evidence/provenance accurately, and make eligibility decisions distinguish **verified facts** from **self-reported, unverified or unavailable facts**. Preserve all existing HireFlow workflows.

## 2. Findings grounded in official sources

- **DigiLocker requester access** is an approved organizational partnership, not an unrestricted API key available to any project developer. Requester onboarding, agreements and consent apply. See https://apisetu.gov.in/digilocker , https://api.apisetu.gov.in/ and https://www.digilocker.gov.in/web/implementation-model .
- **NAD** hosts academic awards including marksheets and transcripts **when an issuing institution has lodged them**. NAD verifiers require student consent and provider authorization. See https://nad.digilocker.gov.in/Verifier and https://nad.digilocker.gov.in/faq .
- **ABC / APAAR** stores academic *credits* and exposes publicly listed API categories including student details and subject-wise credit data; listing an API does **not** grant permission or imply that grade/result fields are present. See https://directory.apisetu.gov.in/api-collection/abc and https://abc.digilocker.gov.in/faq .
- MRU has previously documented ABC participation and NAD registration (https://mru.edu.in/wp-content/uploads/2023/06/NAAC-SSR.pdf), but **availability of current-semester individual transcript data and third-party read permission are not verified**. Do not conflate Manav Rachna University with Manav Rachna International Institute of Research & Studies; these are distinct issuers.
- **Missing ABC credits do NOT prove a backlog.** Missing/late uploads, recognition and transfer/redemption can explain an absence. ABC credits are not identical to CGPA. A backlog state needs an authoritative course result, applicable institution grading policy and updated attempt/clearance history.

## 3. Data trust and verification vocabulary

Model *evidence authenticity*, *academic fact verification* and *account identity matching* separately, rather than one misleading `verified` boolean.

- `SELF_REPORTED`: Student/admin-entered figure with no authoritative evidence.
- `EVIDENCE_SUBMITTED`: File or metadata provided, not yet validated.
- `ISSUER_DOCUMENT_VERIFIED`: Approved verifier has authenticated a DigiLocker/NAD **issued** document and linked it to the authenticated student and correct institution/term.
- `REGISTRAR_VERIFIED`: Specifically authorized institutional reviewer confirmed the relevant academic values against official records and recorded immutable audit evidence.
- `CONFLICTED`: Two sources disagree; no automatic downgrade or overwrite.
- `STALE`: Evidence does not cover the latest relevant academic term or has been superseded.
- `REVOKED`: Consent or evidence access revoked; historic permitted decision audit retained only according to a reviewed retention policy.
- `UNAVAILABLE`: Provider inaccessible, institution record absent, or access not approved.

A valid signed issued document can verify its contents; **CGPA is only verified when that CGPA is explicitly provided, or a university-approved formula computes it from complete authoritative grades/credits**. Do not derive CGPA by averaging semester SGPAs unless an approved policy explicitly requires that formula.

Use independent `backlogAssessment`: `CONFIRMED_ACTIVE`, `CONFIRMED_CLEAR`, `REVIEW_REQUIRED`, `UNKNOWN`, with source, applicable term, course attempts and exact issuer status. Never use ABC credit absence to assert `CONFIRMED_ACTIVE` or `CONFIRMED_CLEAR`.

## 4. Proposed provider-neutral architecture

```text
React /student/academic-profile → Spring Boot Academic Verification API
                                       │ auth principal + CSRF + consent
                                       ├── VerificationService
                                       │    ├── AcademicEvidenceProvider interface
                                       │    ├── DigiLockerRequesterAdapter [disabled until approved]
                                       │    ├── AbcCreditsAdapter [disabled until authorized]
                                       │    ├── InstitutionVerifiedImportAdapter [role-restricted]
                                       │    └── ExplicitTestFixtureAdapter [test-only, never live]
                                       ├── VerificationPolicyService
                                       ├── AcademicProfileProjectionService
                                       └── JDBC transaction + Flyway-owned schema
                                                     │
                                                  PostgreSQL
```

DigiLocker and ABC adapters must not be simulated as live connections. Provider feature flags default **OFF** until onboarding, documentation, credential provisioning and tests are complete.

### Suggested immutable / append-only records

1. `AcademicEvidence`: internal evidence ID, student ID, provider, issuer identifier, document type, document URI/reference **if permitted**, semester/coverage, issuedAt, retrievedAt, content hash, authenticity status, field coverage and provenance; avoid storing original PDFs unless essential and authorized.
2. `AcademicFact`: student ID, field name (CGPA, branch, course result), value, evidence ID, university-specific grading rule version, effective period, reviewer decision and supersession link.
3. `CourseAttempt`: course code, academic term, attempt, credits attempted/earned, grade/result, publishedOn, explicit backlog/cleared marker only if source supports it.
4. `ConsentReceipt`: student principal, provider, requested scopes/record types, purpose, notice version, grant time, expiry/revocation, correlation ID; never store provider secrets in audit rows.
5. `VerificationReview`: authorized reviewer ID, evidence ID, outcome, reason code, timestamp, immutable audit event.
6. `ProviderConnection`: minimal encrypted token metadata and expiry; stored server-side only, refresh/revoke securely.

Prefer append-only evidence and a separately computed **effective academic snapshot**. Preserve `students.cgpa` and existing demo records during migration; avoid silently overwriting values. Later migrate eligibility through a reviewed compatibility bridge.

## 5. Privacy, security and partner compliance

- Require approved requester onboarding and institutional authorization. **An individual portfolio app is not guaranteed approval.** Verify terms, permitted uses, rates/cost and go-live requirements before integration.
- Student-initiated authorization, explicit purpose/record scopes, HTTPS authorized redirects, state and PKCE where supported, short-lived credentials and replay protection.
- Tie provider callback to the same authenticated HireFlow student; reject account swapping, wrong issuer, wrong academic year, repeated callbacks and mismatched profile identity.
- **Never ask HireFlow users for DigiLocker passwords, Aadhaar OTPs or PINs**. Avoid collecting/storing Aadhaar numbers or raw APAAR IDs unless a documented approved requirement demands it. Show masked identifiers.
- Encrypted provider tokens (key outside Git), secure at-rest sensitive evidence, no tokens/PII in logs or React localStorage, outbound allowlists and SSRF-safe URI handling, rate limits and timeouts.
- Role and ownership boundaries: students read their own facts and consent; officers receive only eligibility-relevant verified summaries and permitted provenance; approved reviewers can inspect evidence; admins cannot pretend to be an issuer.
- Clear withdrawal/deletion/retention flows with legally reviewed audit retention. Indian DPDP requirements are phased; verify current legal obligations before launch. See https://www.meity.gov.in/documents/act-and-policies/digital-personal-data-protection-rules-2025-gDOxUjMtQWa .
- Correct language: "Institution verified" / "DigiLocker-issued document checked" only after corresponding evidence is verified, otherwise "Recorded, not verified".

## 6. Student and officer UX

**Student / Academic Profile**
- Summary: recorded CGPA, trust status, source name, last verified date, term coverage, and a concise "This is not yet independently verified" notice while unverified.
- **Connect DigiLocker** CTA is shown only after production/sandbox requester integration is genuinely configured; otherwise present an unavailable explanation without a fake sign-in button.
- When authorized: explicit consent screen showing which records and purpose, redirection to trusted provider, return status, document selection, mismatch resolution and revoke access.
- Separate **ABC/APAAR Credits** panel: credits earned/recognized, institution, term, as-of date, and completeness. No fabricated backlog inference.
- Dispute workflow: "Request academic correction", with a human review queue and no self-certification.

**Officer / Candidate review**
- Replace "Verified CGPA" with **"Recorded CGPA"** unless real verification evidence exists.
- Show distinct status: `Verified issuer document`, `Institution-verified`, `Recorded / unverified`, `Pending review`, `Conflicting evidence`, `Stale`.
- Show only authorized source summary; not documents, Aadhaar/APAAR values, provider tokens or full transcript by default.
- Configuration per recruitment drive: `verificationPolicy = OPTIONAL | REVIEW_REQUIRED | VERIFIED_REQUIRED`. Do not enable strict denial for historical records without migration and fairness review.

## 7. Eligibility behavior and backwards compatibility

Existing application/shortlisting/scheduling flows MUST remain functional.

- Without verified data, explain **"CGPA cutoff satisfied using recorded academic data; academic verification pending"**. Do not call it institution-verified.
- For strict verified-data drives, keep an application in `PENDING_VERIFICATION` or a **separate review state**; do not auto-assign `INELIGIBLE` or `REJECTED` merely because DigiLocker/ABC is down. Decide domain status migration carefully to avoid invalidating current state-machine and SQL constraints.
- Require a human conflict-resolution path if verified document CGPA differs from saved CGPA; do not auto overwrite existing `Student`, applications or confirmed appointments.
- Treat backlog requirements as independent drive policy only when an authoritative explicit active/clear status exists and term freshness is sufficient. Otherwise mark **UNKNOWN/REVIEW_REQUIRED**, not "no backlogs".
- Add test cases for historical compatibility, offline provider, stale documents, repeated attempts, credit-transfer anomalies, and conflicting records.

## 8. Incremental delivery / acceptance gates

**A — Truthfulness fix (immediate):** Remove current false "Verified Student"/"Verified CGPA" UI assertions, leaving all existing values, calculations, routes and stored data unchanged. Test screenshot copy and UI regression.

**B — Institutional verification (works before DigiLocker approval):** Implement append-only academic evidence + officer/registrar review workflow, official university data import only if authorized, proof of provenance and audit. Student-uploaded documents start `EVIDENCE_SUBMITTED`, never automatically `VERIFIED`.

**C — DigiLocker / NAD partnership:** Obtain authorized requester agreement/credentials, legal review, sanctioned sandbox; implement OAuth + real verified issued documents with synthetic sandbox tests. No scraping, OTP interception, credentials harvesting or misrepresented production integration. Confirm issuer and coverage availability for the intended university.

**D — ABC credit enrichment:** With appropriate authorization, fetch academic credit data using approved API; show credit history, transfer/recognition and term coverage. Map backlog status only from explicit official course results and university policy, never from missing credits.

**E — Eligibility decision policy:** Gradual opt-in verified-data policy for new drives after supervised acceptance. Snapshot source/decision for audit. Preserve existing admissions and published sessions.

## 9. Required validation

- JUnit/MockMvc for authorization, CSRF, identity binding, migrations, audit, rollback, concurrency, revoke, stale evidence and mismatches.
- Real PostgreSQL isolated integration tests only at `127.0.0.1:55432/hireflow_cap8_it`; never change host PostgreSQL `5432`.
- Vitest/Zod for new DTOs, states and consent UI; Playwright for truthful unverified, provider unavailable, approved sandbox consent and officer reviews.
- No test must depend on personal DigiLocker, Aadhaar or real student academic data.
- Strict negative tests: a forged PDF, a user-uploaded document, a made-up ABC ID, partial credits and no issued marksheet **must never** create a verified CGPA / no-backlog badge.
- Typecheck, lint, accessibility, frontend build and CI pass on exact PR head; no public deployment required.

## 10. External dependencies and stop conditions

Before enabling the "Connect DigiLocker" flow:
1. Confirm a **legitimate** requester organization (institution/company) and onboarding approval.
2. Obtain official sandbox access and approved callback/origin configuration.
3. Verify document types and exact issuer/semester coverage for targeted institutions. **Do not conflate MRU with MRIIRS**.
4. Verify whether approved ABC APIs actually return course statuses, grades and credits; design to missing optional fields.
5. Review legal, costs, data agreements and storage/retention policy.

**Stop:** If onboarding or data coverage is unavailable, continue with accurate recorded profile + authorized institutional review. Do not manufacture verification, bypass auth or scrape DigiLocker/ABC portals.

---

**Decision:** It is feasible to make HireFlow academically verifiable, but **a real DigiLocker/NAD or ABC connection is PLANNED, not implemented or authorized**. The first safe PR only corrects misleading current UI labels and provides this design for engineering review.
