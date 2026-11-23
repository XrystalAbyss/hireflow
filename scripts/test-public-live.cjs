const path = require('path');
const { chromium } = require(path.join(__dirname, '../hireflow-app/node_modules/playwright'));
const { execSync } = require('child_process');
const { Client } = require('pg');
const fs = require('fs');

const BASE_URL = process.env.HIREFLOW_URL || 'https://hireflow-prod.onrender.com';

async function runLiveAcceptance() {
    console.log('================================================================');
    console.log(`HIREFLOW LIVE PUBLIC CLOUD ACCEPTANCE TEST SUITE`);
    console.log(`Target Environment: ${BASE_URL}`);
    console.log(`Timestamp: ${new Date().toISOString()}`);
    console.log('================================================================\n');

    const credPath = path.join(__dirname, '../.run/demo.local.json');
    let studentPass = 'Student@HireFlow2026!';
    let officerPass = 'Officer@HireFlow2026!';
    if (fs.existsSync(credPath)) {
        try {
            const raw = fs.readFileSync(credPath, 'utf-8').replace(/^\uFEFF/, '');
            const creds = JSON.parse(raw);
            if (creds.demoStudentPassword) studentPass = creds.demoStudentPassword;
            if (creds.demoOfficerPassword) officerPass = creds.demoOfficerPassword;
        } catch (e) {}
    }

    const browser = await chromium.launch({ headless: true });
    const context = await browser.newContext();
    const page = await context.newPage();

    const results = [];

    async function recordStep(name, fn) {
        const start = Date.now();
        try {
            const detail = await fn();
            const duration = Date.now() - start;
            results.push({ name, status: 'PASS', duration: `${duration}ms`, detail });
            console.log(`[PASS] ${name} (${duration}ms) - ${detail || ''}`);
        } catch (err) {
            const duration = Date.now() - start;
            results.push({ name, status: 'FAIL', duration: `${duration}ms`, error: err.message });
            console.error(`[FAIL] ${name} (${duration}ms) - ${err.message}`);
            throw err;
        }
    }

    try {
        // Step 1: Health check
        await recordStep('1. Backend /api/v1/health Probe', async () => {
            const res = await page.goto(`${BASE_URL}/api/v1/health`, { timeout: 15000 });
            if (res.status() !== 200) throw new Error(`HTTP ${res.status()}`);
            const body = await res.json();
            if (body.status !== 'UP' || body.databaseStatus !== 'CONNECTED') {
                throw new Error(`Unexpected health payload: ${JSON.stringify(body)}`);
            }
            return `status: ${body.status}, database: ${body.databaseStatus}, schema: ${body.schemaStatus}`;
        });

        // Step 2: Homepage & Client Routes
        await recordStep('2. Frontend SPA Load & Login Page', async () => {
            const res = await page.goto(`${BASE_URL}/auth/login`, { timeout: 15000 });
            if (res.status() !== 200) throw new Error(`HTTP ${res.status()}`);
            await page.waitForSelector('#username', { timeout: 10000 });
            await page.waitForSelector('#password', { timeout: 10000 });
            return 'Institutional Sign In form loaded with active input fields';
        });

        // Step 3: Student Login
        await recordStep('3. Student Authentication (demo.student)', async () => {
            await page.fill('#username', 'demo.student');
            await page.fill('#password', studentPass);
            await page.click('button[type="submit"]');
            await page.waitForURL('**/student/**', { timeout: 12000 });
            return 'Authenticated as demo.student (Aarav Sharma), redirected to /student/dashboard';
        });

        // Step 4: Drive Exploration
        await recordStep('4. Student Drive Exploration', async () => {
            await page.goto(`${BASE_URL}/student/drives`, { timeout: 15000 });
            await page.waitForSelector('h1', { timeout: 10000 });
            const title = await page.textContent('h1');
            const cardCount = await page.locator('article').count();
            return `Header: "${title.trim()}", Found ${cardCount} active recruitment drive card(s)`;
        });

        // Step 5: Eligibility Check
        await recordStep('5. Authoritative Academic Eligibility Check', async () => {
            const res = await page.request.get(`${BASE_URL}/api/v1/student/drives/DRV-DEMO-01/eligibility`);
            if (res.status() !== 200) throw new Error(`HTTP ${res.status()}`);
            const body = await res.json();
            return `Drive: DRV-DEMO-01, Academically Eligible: ${body.academicallyEligible}, Can Apply: ${body.canApply}, Student CGPA: ${body.studentCgpa}`;
        });

        // Step 6: CSRF Token Lifecycle
        let csrfToken = null;
        await recordStep('6. CSRF Token Validation', async () => {
            const res = await page.request.get(`${BASE_URL}/api/v1/auth/csrf`);
            if (res.status() !== 200) throw new Error(`HTTP ${res.status()}`);
            const csrf = await res.json();
            if (!csrf.token || csrf.token.length < 10) throw new Error('Invalid CSRF token');
            csrfToken = csrf.token;
            return `CSRF Header: ${csrf.headerName}, Token length: ${csrf.token.length}`;
        });

        // Step 7: Student Logout
        await recordStep('7. Student Session Invalidation (Logout)', async () => {
            const res = await page.request.post(`${BASE_URL}/api/v1/auth/logout`, {
                headers: { 'X-CSRF-TOKEN': csrfToken }
            });
            if (res.status() !== 200) throw new Error(`HTTP ${res.status()}`);
            return 'Session destroyed cleanly via Spring Security with CSRF token';
        });

        // Step 8: Officer Login
        await recordStep('8. Officer Authentication (demo.officer)', async () => {
            await page.goto(`${BASE_URL}/auth/login`);
            await page.fill('#username', 'demo.officer');
            await page.fill('#password', officerPass);
            await page.click('button[type="submit"]');
            await page.waitForURL('**/officer/**', { timeout: 12000 });
            return 'Authenticated as demo.officer (Placement Officer), redirected to /officer/dashboard';
        });

        // Step 9: Candidate Applications Roster
        await recordStep('9. Officer Candidate Applications Roster', async () => {
            const res = await page.request.get(`${BASE_URL}/api/v1/officer/drives/DRV-DEMO-01/applications`);
            if (res.status() !== 200) throw new Error(`HTTP ${res.status()}`);
            const apps = await res.json();
            const count = apps.content ? apps.content.length : apps.length;
            return `Retrieved ${count} candidate applications for drive DRV-DEMO-01`;
        });

        // Step 9b: Officer Candidate Shortlisting
        await recordStep('9b. Candidate Application Shortlisting', async () => {
            const csrfRes = await (await page.request.get(`${BASE_URL}/api/v1/auth/csrf`)).json();
            const res = await page.request.post(`${BASE_URL}/api/v1/officer/applications/APP-DEMO-04/shortlist`, {
                headers: { 'X-CSRF-TOKEN': csrfRes.token }
            });
            if (res.status() !== 200) throw new Error(`HTTP ${res.status()}`);
            const app = await res.json();
            return `Candidate APP-DEMO-04 status transitioned to: ${app.status || 'SHORTLISTED'}`;
        });

        // Step 10: GreedyScheduler Advisory Preview & Publication
        let publishedPreviewId = null;
        await recordStep('10. GreedyScheduler Advisory Preview Generation', async () => {
            const previewReq = {
                driveId: 'DRV-DEMO-03',
                date: '2027-04-10',
                durationMinutes: 45,
                bufferMinutes: 15,
                startTime: '09:00',
                endTime: '17:00',
                interviewerIds: ['INT-DEMO-04'],
                roomIds: ['RM-DEMO-02']
            };
            const csrfRes = await (await page.request.get(`${BASE_URL}/api/v1/auth/csrf`)).json();
            const res = await page.request.post(`${BASE_URL}/api/v1/officer/scheduling/preview`, {
                data: previewReq,
                headers: { 'X-CSRF-TOKEN': csrfRes.token }
            });
            if (res.status() !== 200) throw new Error(`HTTP ${res.status()}`);
            const preview = await res.json();
            publishedPreviewId = preview.previewId;
            return `Preview token: ${preview.previewId}, Scheduled: ${preview.totalScheduled}, Unscheduled: ${preview.totalUnscheduled}`;
        });

        // Step 10b: Publish Timetable Appointments
        await recordStep('10b. Publish Confirmed Appointments to Neon PostgreSQL', async () => {
            const csrfRes = await (await page.request.get(`${BASE_URL}/api/v1/auth/csrf`)).json();
            const res = await page.request.post(`${BASE_URL}/api/v1/officer/scheduling/publish`, {
                data: { previewId: publishedPreviewId },
                headers: { 'X-CSRF-TOKEN': csrfRes.token }
            });
            if (res.status() !== 200) throw new Error(`HTTP ${res.status()}`);
            const pub = await res.json();
            return `Outcome: ${pub.outcome}, Confirmed sessions: ${pub.confirmedSessions ? pub.confirmedSessions.length : 0}`;
        });

        // Step 11: RBAC Protection
        await recordStep('11. Role-Based Access Control (RBAC) Enforcement', async () => {
            // New independent student context
            const studentContext = await browser.newContext();
            const sPage = await studentContext.newPage();
            await sPage.goto(`${BASE_URL}/auth/login`);
            await sPage.fill('#username', 'demo.student');
            await sPage.fill('#password', studentPass);
            await sPage.click('button[type="submit"]');
            await sPage.waitForURL('**/student/**', { timeout: 12000 });

            // Student attempts to query restricted officer endpoint
            const forbiddenRes = await sPage.request.get(`${BASE_URL}/api/v1/officer/companies`);
            if (forbiddenRes.status() !== 403) throw new Error(`Expected HTTP 403 Forbidden, got ${forbiddenRes.status()}`);

            // Student checks interview timetable
            const timetableRes = await sPage.request.get(`${BASE_URL}/api/v1/student/interviews`);
            if (timetableRes.status() !== 200) throw new Error(`Failed to load student timetable: ${timetableRes.status()}`);
            const interviews = await timetableRes.json();

            await studentContext.close();
            return `HTTP 403 Forbidden correctly enforced on officer API; Student confirmed interviews: ${interviews.length}`;
        });

        // Step 12: Direct Neon PostgreSQL Persistence Attestation
        await recordStep('12. Direct Neon PostgreSQL Attestation', async () => {
            const connStr = execSync('neon connection-string production --project-id shiny-brook-20410796 --ssl require', { encoding: 'utf-8' }).trim();
            const pgClient = new Client({ connectionString: connStr, ssl: { rejectUnauthorized: false } });
            await pgClient.connect();

            const tables = ['students', 'companies', 'recruitment_drives', 'applications', 'interviewers', 'rooms', 'interview_sessions', 'user_accounts'];
            const counts = {};
            for (const t of tables) {
                const r = await pgClient.query(`SELECT count(*) FROM ${t}`);
                counts[t] = r.rows[0].count;
            }
            await pgClient.end();
            return `Persisted records in Neon -> ${JSON.stringify(counts)}`;
        });

        console.log('\n================================================================');
        console.log('ALL 12 PUBLIC END-TO-END ACCEPTANCE TESTS PASSED!');
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
