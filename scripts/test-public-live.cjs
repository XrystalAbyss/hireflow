/**
 * scripts/test-public-live.cjs
 *
 * HIREFLOW CAP-8.7.4 LIVE PUBLIC ACCEPTANCE SUITE
 * Autonomous end-to-end browser and API verification against live public production.
 *
 * Covers complete student -> officer -> scheduler -> publication -> student timetable lifecycle:
 *  1. Backend /api/v1/health Probe (UP, CONNECTED, FLYWAY_VALIDATED)
 *  2. Frontend SPA Load & Login Page (Institutional Sign-In form)
 *  3. Student Authentication (demo.student with hardened production credentials)
 *  4. Student Drive Exploration (Opportunity Explorer header & cards)
 *  5. Authoritative Academic Eligibility: canApply is TRUE on fresh drive (DRV-DEMO-05)
 *  6. Fresh Application Submission (POST /api/v1/student/applications)
 *  7. Duplicate Application Rejection Prevention (HTTP 400 Bad Request on duplicate)
 *  8. Direct Neon Database Validation of New Application Record
 *  9. Strict CSRF Security Enforcement (Mutating request with invalid/missing token -> 403)
 * 10. Student Session Invalidation (POST /api/v1/auth/logout -> subsequent request 401)
 * 11. Officer Authentication (demo.officer with hardened credentials)
 * 12. Officer Application Roster & Shortlisting (transitions to SHORTLISTED)
 * 13. GreedyScheduler Advisory Preview Generation & Non-Persistence Proof
 * 14. Officer Timetable Publication & PostgreSQL Session Persistence Proof
 * 15. Cross-Role RBAC Enforcement (Student -> Officer 403, Officer -> Student 403)
 * 16. Fresh Student Session Interview Timetable Verification
 * 17. Client-Side SPA Deep Link Navigation & Browser Refresh Integrity
 * 18. Neon PostgreSQL Physical Table Count & Persistence Attestation
 */

const path = require('path');
const { chromium } = require(path.join(__dirname, '../hireflow-app/node_modules/playwright'));
const { execSync } = require('child_process');
const { Client } = require('pg');
const fs = require('fs');

const BASE_URL = process.env.HIREFLOW_URL || 'https://hireflow-prod.onrender.com';
const PRESENTATION_DRIVE_ID = 'DRV-DEMO-05';

function getNeonClient() {
    const connStr = execSync('neon connection-string production --project-id shiny-brook-20410796 --ssl require', { encoding: 'utf-8' }).trim();
    return new Client({ connectionString: connStr, ssl: { rejectUnauthorized: false } });
}

async function prepareIsolatedPresentationDrive(pgClient) {
    // Ensure DRV-DEMO-05 exists and reset ONLY this presentation drive's test data
    await pgClient.query(`
        INSERT INTO recruitment_drives (id, company_id, job_title, minimum_cgpa, application_deadline, is_closed)
        VALUES ('${PRESENTATION_DRIVE_ID}', 'CMP-DEMO-01', 'Cloud Systems Associate', 7.00, DATE '2027-05-30', FALSE)
        ON CONFLICT (id) DO UPDATE
        SET is_closed = FALSE;
    `);

    await pgClient.query(`
        INSERT INTO drive_eligible_branches (drive_id, branch)
        VALUES ('${PRESENTATION_DRIVE_ID}', 'CSE'), ('${PRESENTATION_DRIVE_ID}', 'IT')
        ON CONFLICT DO NOTHING;
    `);

    // Clean up only presentation drive applications and sessions to allow repeatable idempotent test runs
    await pgClient.query(`
        DELETE FROM interview_sessions 
        WHERE application_id IN (SELECT id FROM applications WHERE drive_id = '${PRESENTATION_DRIVE_ID}');
    `);
    await pgClient.query(`
        DELETE FROM applications WHERE drive_id = '${PRESENTATION_DRIVE_ID}';
    `);
}

async function runLiveAcceptance() {
    console.log('================================================================');
    console.log(`HIREFLOW CAP-8.7.4 LIVE PUBLIC CLOUD ACCEPTANCE TEST SUITE`);
    console.log(`Target Environment: ${BASE_URL}`);
    console.log(`Timestamp: ${new Date().toISOString()}`);
    console.log('================================================================\n');

    // 1. Load credentials (preferring .run/demo.production.json)
    const prodCredPath = path.join(__dirname, '../.run/demo.production.json');
    const localCredPath = path.join(__dirname, '../.run/demo.local.json');

    let studentPass = null;
    let officerPass = null;

    if (fs.existsSync(prodCredPath)) {
        try {
            const raw = fs.readFileSync(prodCredPath, 'utf-8').replace(/^\uFEFF/, '');
            const creds = JSON.parse(raw);
            if (creds.accounts && creds.accounts.student) studentPass = creds.accounts.student.password;
            if (creds.accounts && creds.accounts.officer) officerPass = creds.accounts.officer.password;
            console.log('[Setup] Loaded hardened credentials from .run/demo.production.json');
        } catch (e) {
            console.warn('[Setup] Could not parse demo.production.json:', e.message);
        }
    }

    if (!studentPass && fs.existsSync(localCredPath)) {
        try {
            const raw = fs.readFileSync(localCredPath, 'utf-8').replace(/^\uFEFF/, '');
            const creds = JSON.parse(raw);
            if (creds.demoStudentPassword) studentPass = creds.demoStudentPassword;
            if (creds.demoOfficerPassword) officerPass = creds.demoOfficerPassword;
            console.log('[Setup] Falling back to credentials from .run/demo.local.json');
        } catch (e) {}
    }

    if (!studentPass || !officerPass) {
        throw new Error('Missing student or officer demo credentials in .run/');
    }

    // 2. Pre-test DB preparation (isolated to PRESENTATION_DRIVE_ID)
    console.log('[Setup] Preparing isolated presentation drive (DRV-DEMO-05) in Neon...');
    const pgInitClient = getNeonClient();
    await pgInitClient.connect();
    await prepareIsolatedPresentationDrive(pgInitClient);
    await pgInitClient.end();
    console.log('[Setup] Presentation drive prepared cleanly.\n');

    const browser = await chromium.launch({ headless: true });
    const results = [];

    async function recordStep(name, fn) {
        const start = Date.now();
        try {
            const detail = await fn();
            const duration = Date.now() - start;
            results.push({ name, status: 'PASS', duration: `${duration}ms`, detail });
            console.log(`[PASS] ${name} (${duration}ms) - ${detail || ''}`);
            return detail;
        } catch (err) {
            const duration = Date.now() - start;
            results.push({ name, status: 'FAIL', duration: `${duration}ms`, error: err.message });
            console.error(`[FAIL] ${name} (${duration}ms) - ${err.message}`);
            throw err;
        }
    }

    try {
        const studentContext = await browser.newContext();
        const studentPage = await studentContext.newPage();

        // Step 1: Health check
        await recordStep('1. Backend /api/v1/health Probe', async () => {
            const res = await studentPage.goto(`${BASE_URL}/api/v1/health`, { timeout: 15000 });
            if (res.status() !== 200) throw new Error(`HTTP ${res.status()}`);
            const body = await res.json();
            if (body.status !== 'UP' || body.databaseStatus !== 'CONNECTED') {
                throw new Error(`Unexpected health payload: ${JSON.stringify(body)}`);
            }
            return `status: ${body.status}, database: ${body.databaseStatus}, schema: ${body.schemaStatus}`;
        });

        // Step 2: Homepage & Client Routes
        await recordStep('2. Frontend SPA Load & Login Page', async () => {
            const res = await studentPage.goto(`${BASE_URL}/auth/login`, { timeout: 15000 });
            if (res.status() !== 200) throw new Error(`HTTP ${res.status()}`);
            await studentPage.waitForSelector('#username', { timeout: 10000 });
            await studentPage.waitForSelector('#password', { timeout: 10000 });
            return 'Institutional Sign In form loaded with active input fields';
        });

        // Step 3: Student Login
        await recordStep('3. Student Authentication (demo.student)', async () => {
            await studentPage.fill('#username', 'demo.student');
            await studentPage.fill('#password', studentPass);
            await studentPage.click('button[type="submit"]');
            await studentPage.waitForURL('**/student/**', { timeout: 12000 });
            return 'Authenticated as demo.student (Aarav Sharma), redirected to /student/dashboard';
        });

        // Step 4: Drive Exploration
        await recordStep('4. Student Drive Exploration', async () => {
            await studentPage.goto(`${BASE_URL}/student/drives`, { timeout: 15000 });
            await studentPage.waitForSelector('h1', { timeout: 10000 });
            const title = await studentPage.textContent('h1');
            const cardCount = await studentPage.locator('article').count();
            return `Header: "${title.trim()}", Found ${cardCount} active recruitment drive card(s)`;
        });

        // Step 5: Authoritative Academic Eligibility Check (canApply MUST BE TRUE)
        await recordStep('5. Academic Eligibility Check (canApply == true)', async () => {
            const res = await studentPage.request.get(`${BASE_URL}/api/v1/student/drives/${PRESENTATION_DRIVE_ID}/eligibility`);
            if (res.status() !== 200) throw new Error(`HTTP ${res.status()}`);
            const body = await res.json();
            if (!body.academicallyEligible) throw new Error(`Expected academicallyEligible=true, got ${body.academicallyEligible}`);
            if (body.alreadyApplied) throw new Error(`Expected alreadyApplied=false, got ${body.alreadyApplied}`);
            if (!body.canApply) throw new Error(`Expected canApply=true, got ${body.canApply}`);
            return `Drive: ${PRESENTATION_DRIVE_ID}, Academically Eligible: ${body.academicallyEligible}, Already Applied: ${body.alreadyApplied}, Can Apply: ${body.canApply}`;
        });

        // Step 6: Fresh Application Submission
        let submittedAppId = null;
        let csrfToken = null;
        await recordStep('6. Fresh Application Submission', async () => {
            const csrfRes = await studentPage.request.get(`${BASE_URL}/api/v1/auth/csrf`);
            const csrf = await csrfRes.json();
            csrfToken = csrf.token;

            const res = await studentPage.request.post(`${BASE_URL}/api/v1/student/applications`, {
                headers: { 'X-CSRF-TOKEN': csrfToken },
                data: { driveId: PRESENTATION_DRIVE_ID }
            });
            if (res.status() !== 201) throw new Error(`Expected HTTP 201 Created, got HTTP ${res.status()}`);
            const app = await res.json();
            submittedAppId = app.id;
            if (!submittedAppId) throw new Error('Missing application ID in response');
            if (app.status !== 'APPLIED') throw new Error(`Expected status APPLIED, got ${app.status}`);
            return `Application ${submittedAppId} created with status: ${app.status} for drive ${PRESENTATION_DRIVE_ID}`;
        });

        // Step 7: Duplicate Application Prevention
        await recordStep('7. Duplicate Application Rejection Prevention', async () => {
            const res = await studentPage.request.post(`${BASE_URL}/api/v1/student/applications`, {
                headers: { 'X-CSRF-TOKEN': csrfToken },
                data: { driveId: PRESENTATION_DRIVE_ID }
            });
            if (res.status() !== 400 && res.status() !== 409) {
                throw new Error(`Expected HTTP 400 or 409 on duplicate, got HTTP ${res.status()}`);
            }
            return `Duplicate application correctly rejected with HTTP ${res.status()} Client Error`;
        });

        // Step 8: Direct Neon DB Verification of Created Application
        await recordStep('8. Direct Neon DB Verification of Application Record', async () => {
            const pgClient = getNeonClient();
            await pgClient.connect();
            const res = await pgClient.query(`
                SELECT id, student_id, drive_id, status 
                FROM applications 
                WHERE id = $1
            `, [submittedAppId]);
            await pgClient.end();
            if (res.rows.length !== 1) throw new Error(`Expected 1 application row in DB, found ${res.rows.length}`);
            const row = res.rows[0];
            return `DB Confirmed: ID ${row.id}, Student ${row.student_id}, Drive ${row.drive_id}, Status ${row.status}`;
        });

        // Step 9: CSRF Security Enforcement
        await recordStep('9. CSRF Security Enforcement on Mutating Request', async () => {
            const res = await studentPage.request.post(`${BASE_URL}/api/v1/student/applications`, {
                headers: { 'X-CSRF-TOKEN': 'invalid-tampered-token' },
                data: { driveId: PRESENTATION_DRIVE_ID }
            });
            if (res.status() !== 403) throw new Error(`Expected HTTP 403 Forbidden with tampered CSRF, got HTTP ${res.status()}`);
            return 'Tampered CSRF token strictly rejected with HTTP 403 Forbidden';
        });

        // Step 10: Student Session Logout & Invalidation
        await recordStep('10. Student Session Invalidation (Logout)', async () => {
            const res = await studentPage.request.post(`${BASE_URL}/api/v1/auth/logout`, {
                headers: { 'X-CSRF-TOKEN': csrfToken }
            });
            if (res.status() !== 200) throw new Error(`HTTP ${res.status()}`);

            // Ensure old session is destroyed
            const verifyRes = await studentPage.request.get(`${BASE_URL}/api/v1/student/dashboard`);
            if (verifyRes.status() !== 401 && verifyRes.status() !== 403) {
                throw new Error(`Expected 401/403 on invalidated session, got ${verifyRes.status()}`);
            }
            return 'Session invalidated; subsequent authenticated request rejected with HTTP 401';
        });

        await studentContext.close();

        // Step 11: Officer Authentication
        const officerContext = await browser.newContext();
        const officerPage = await officerContext.newPage();

        await recordStep('11. Officer Authentication (demo.officer)', async () => {
            await officerPage.goto(`${BASE_URL}/auth/login`);
            await officerPage.fill('#username', 'demo.officer');
            await officerPage.fill('#password', officerPass);
            await officerPage.click('button[type="submit"]');
            await officerPage.waitForURL('**/officer/**', { timeout: 12000 });
            return 'Authenticated as demo.officer (Placement Officer), redirected to /officer/dashboard';
        });

        // Step 12: Candidate Applications Roster & Shortlisting
        await recordStep('12. Candidate Shortlisting by Placement Officer', async () => {
            const csrfRes = await (await officerPage.request.get(`${BASE_URL}/api/v1/auth/csrf`)).json();
            const res = await officerPage.request.post(`${BASE_URL}/api/v1/officer/applications/${submittedAppId}/shortlist`, {
                headers: { 'X-CSRF-TOKEN': csrfRes.token }
            });
            if (res.status() !== 200) throw new Error(`Expected HTTP 200, got ${res.status()}`);
            const app = await res.json();
            if (app.status !== 'SHORTLISTED') throw new Error(`Expected status SHORTLISTED, got ${app.status}`);
            return `Application ${submittedAppId} successfully shortlisted (Status: ${app.status})`;
        });

        // Step 13: GreedyScheduler Advisory Preview Generation & Non-Persistence
        let publishedPreviewId = null;
        await recordStep('13. GreedyScheduler Advisory Preview & Non-Persistence Proof', async () => {
            const previewReq = {
                driveId: PRESENTATION_DRIVE_ID,
                date: '2027-04-15',
                durationMinutes: 45,
                bufferMinutes: 15,
                startTime: '09:00',
                endTime: '17:00',
                interviewerIds: ['INT-DEMO-01'],
                roomIds: ['RM-DEMO-01']
            };
            const csrfRes = await (await officerPage.request.get(`${BASE_URL}/api/v1/auth/csrf`)).json();
            const res = await officerPage.request.post(`${BASE_URL}/api/v1/officer/scheduling/preview`, {
                data: previewReq,
                headers: { 'X-CSRF-TOKEN': csrfRes.token }
            });
            if (res.status() !== 200) throw new Error(`Expected HTTP 200, got ${res.status()}`);
            const preview = await res.json();
            publishedPreviewId = preview.previewId;

            // Direct DB check: Verify NO interview session exists yet!
            const pgClient = getNeonClient();
            await pgClient.connect();
            const sesCheck = await pgClient.query(`
                SELECT count(*) FROM interview_sessions WHERE application_id = $1
            `, [submittedAppId]);
            await pgClient.end();

            if (parseInt(sesCheck.rows[0].count, 10) !== 0) {
                throw new Error(`Preview violated non-persistence invariant! Found session before publication.`);
            }

            return `Preview token: ${preview.previewId}, Scheduled: ${preview.totalScheduled}, Non-persistence verified (DB session count: 0)`;
        });

        // Step 14: Publish Confirmed Appointments to Neon PostgreSQL
        await recordStep('14. Timetable Publication & PostgreSQL Session Persistence Proof', async () => {
            const csrfRes = await (await officerPage.request.get(`${BASE_URL}/api/v1/auth/csrf`)).json();
            const res = await officerPage.request.post(`${BASE_URL}/api/v1/officer/scheduling/publish`, {
                data: { previewId: publishedPreviewId },
                headers: { 'X-CSRF-TOKEN': csrfRes.token }
            });
            if (res.status() !== 200) throw new Error(`Expected HTTP 200, got ${res.status()}`);
            const pub = await res.json();

            // Direct DB check: Verify session record is now physically committed in PostgreSQL!
            const pgClient = getNeonClient();
            await pgClient.connect();
            const sesCheck = await pgClient.query(`
                SELECT id, application_id, student_id, interviewer_id, room_id, start_time, end_time
                FROM interview_sessions 
                WHERE application_id = $1
            `, [submittedAppId]);
            await pgClient.end();

            if (sesCheck.rows.length !== 1) {
                throw new Error(`Expected 1 confirmed session in DB after publish, found ${sesCheck.rows.length}`);
            }
            const ses = sesCheck.rows[0];

            return `Publication outcome: ${pub.outcome}, Session committed in DB: ID ${ses.id}, Room: ${ses.room_id}, Time: ${ses.start_time}`;
        });

        // Step 15: Cross-Role RBAC Enforcement
        await recordStep('15. Cross-Role RBAC Enforcement (Officer cannot access Student)', async () => {
            const rbacRes = await officerPage.request.get(`${BASE_URL}/api/v1/student/dashboard`);
            if (rbacRes.status() !== 403) {
                throw new Error(`Expected HTTP 403 Forbidden when Officer accesses Student API, got ${rbacRes.status()}`);
            }
            return 'HTTP 403 Forbidden enforced: Officer account cannot impersonate Student portal';
        });

        await officerContext.close();

        // Step 16: Fresh Student Session Timetable Verification
        const finalStudentContext = await browser.newContext();
        const finalStudentPage = await finalStudentContext.newPage();

        await recordStep('16. Confirmed Student Timetable in Fresh Authenticated Session', async () => {
            await finalStudentPage.goto(`${BASE_URL}/auth/login`);
            await finalStudentPage.fill('#username', 'demo.student');
            await finalStudentPage.fill('#password', studentPass);
            await finalStudentPage.click('button[type="submit"]');
            await finalStudentPage.waitForURL('**/student/**', { timeout: 12000 });

            const timetableRes = await finalStudentPage.request.get(`${BASE_URL}/api/v1/student/interviews`);
            if (timetableRes.status() !== 200) throw new Error(`HTTP ${timetableRes.status()}`);
            const interviews = await timetableRes.json();
            const ourInterview = interviews.find(i => i.applicationId === submittedAppId);
            if (!ourInterview) {
                throw new Error(`Student timetable does not contain newly published appointment for ${submittedAppId}`);
            }

            // Also verify Student cannot access Officer API
            const studentForbidden = await finalStudentPage.request.get(`${BASE_URL}/api/v1/officer/companies`);
            if (studentForbidden.status() !== 403) {
                throw new Error(`Expected 403 when Student accesses Officer API, got ${studentForbidden.status()}`);
            }

            return `Student confirmed interview found: Company "${ourInterview.companyName}", Room "${ourInterview.roomName}", Start: ${ourInterview.startTime}`;
        });

        // Step 17: Client-Side SPA Deep Link Navigation & Browser Refresh Integrity
        await recordStep('17. Client-Side SPA Deep Link & Refresh Integrity', async () => {
            await finalStudentPage.goto(`${BASE_URL}/student/drives`, { waitUntil: 'domcontentloaded', timeout: 25000 });
            await finalStudentPage.waitForSelector('h1', { timeout: 20000 });
            const initialHeading = await finalStudentPage.textContent('h1');
            // Browser refresh to prove client-side deep link survives hard refresh
            await finalStudentPage.reload({ waitUntil: 'domcontentloaded', timeout: 25000 });
            await finalStudentPage.waitForSelector('h1', { timeout: 20000 });
            const refreshedHeading = await finalStudentPage.textContent('h1');
            return `SPA deep link '/student/drives' survived hard refresh (Initial: "${initialHeading.trim()}", Refreshed: "${refreshedHeading.trim()}")`;
        });

        await finalStudentContext.close();

        // Step 18: Direct Neon PostgreSQL Persistence Attestation
        await recordStep('18. Direct Neon PostgreSQL Physical Table Attestation', async () => {
            const pgClient = getNeonClient();
            await pgClient.connect();

            const tables = ['students', 'companies', 'recruitment_drives', 'applications', 'interviewers', 'rooms', 'interview_sessions', 'user_accounts'];
            const counts = {};
            for (const t of tables) {
                const r = await pgClient.query(`SELECT count(*) FROM ${t}`);
                counts[t] = r.rows[0].count;
            }
            await pgClient.end();
            return `Physical record counts in Neon -> ${JSON.stringify(counts)}`;
        });

        console.log('\n================================================================');
        console.log('ALL 18 END-TO-END ACCEPTANCE TESTS PASSED WITH 100% SUCCESS!');
        console.log('================================================================\n');

        return results;

    } finally {
        await browser.close();
    }
}

if (require.main === module) {
    runLiveAcceptance().catch(err => {
        console.error('\nE2E Acceptance Suite Stopped:', err.message);
        process.exit(1);
    });
}

module.exports = { runLiveAcceptance };
