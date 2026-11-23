/**
 * scripts/audit-and-harden-credentials.cjs
 * 
 * CAP-8.7.4 Credential Hardening & Presentation Scenario Provisioning:
 * 1. Audits existing accounts and eliminates weak development passwords (e.g. 12345678).
 * 2. Generates high-entropy, cryptographically unique production passwords.
 * 3. Safely stores production credentials in .run/demo.production.json (gitignored).
 * 4. Applies BCrypt hashes to user_accounts on Neon PostgreSQL.
 * 5. Safely locks down demo.master (enabled = false) to eliminate public admin attack surface.
 * 6. Seeds/verifies the isolated presentation drive (DRV-DEMO-05) for end-to-end demo.
 * 7. Verifies against https://hireflow-prod.onrender.com that old passwords fail (401)
 *    and new credentials succeed (200) without exposing secrets in output.
 */

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { execSync } = require('child_process');
const { Client } = require('pg');
const bcrypt = require('bcryptjs');

const TARGET_HOST = 'https://hireflow-prod.onrender.com';
const PROD_CREDS_PATH = path.join(__dirname, '../.run/demo.production.json');
const LOCAL_CREDS_PATH = path.join(__dirname, '../.run/demo.local.json');

function generateSecurePassword(length = 24) {
    return crypto.randomBytes(length).toString('base64url').slice(0, length);
}

async function runAuditAndHardening() {
    console.log('================================================================');
    console.log('HIREFLOW CAP-8.7.4 CREDENTIAL HARDENING & PROVISIONING');
    console.log('Target API:', TARGET_HOST);
    console.log('Timestamp:', new Date().toISOString());
    console.log('================================================================\n');

    // 1. Retrieve Neon connection string
    console.log('[1/7] Connecting to Neon production database...');
    const connStr = execSync('neon connection-string production --project-id shiny-brook-20410796 --ssl require', { encoding: 'utf-8' }).trim();
    const client = new Client({ connectionString: connStr, ssl: { rejectUnauthorized: false } });
    await client.connect();
    console.log('Connected to Neon PostgreSQL.');

    // 2. Audit current user_accounts
    console.log('\n[2/7] Auditing existing database credentials...');
    const currentUsers = await client.query('SELECT id, username, role, enabled, password_hash FROM user_accounts ORDER BY username');
    
    let weakAdminDetected = false;
    let localCredsReused = false;

    let oldLocalMaster = null;
    let oldLocalOfficer = null;
    let oldLocalStudent = null;

    if (fs.existsSync(LOCAL_CREDS_PATH)) {
        try {
            const raw = fs.readFileSync(LOCAL_CREDS_PATH, 'utf-8').replace(/^\uFEFF/, '');
            const local = JSON.parse(raw);
            oldLocalMaster = local.demoMasterPassword;
            oldLocalOfficer = local.demoOfficerPassword;
            oldLocalStudent = local.demoStudentPassword;
        } catch (e) {}
    }

    for (const u of currentUsers.rows) {
        if (u.username === 'demo.master' && oldLocalMaster && bcrypt.compareSync(oldLocalMaster, u.password_hash)) {
            weakAdminDetected = true;
        }
        if (u.username === 'demo.officer' && oldLocalOfficer && bcrypt.compareSync(oldLocalOfficer, u.password_hash)) {
            localCredsReused = true;
        }
        if (u.username === 'demo.student' && oldLocalStudent && bcrypt.compareSync(oldLocalStudent, u.password_hash)) {
            localCredsReused = true;
        }
    }

    console.log('  - Weak "12345678" administrator password detected in DB:', weakAdminDetected);
    console.log('  - Local dev password reuse detected in DB:', localCredsReused);

    // 3. Generate high-entropy production credentials
    console.log('\n[3/7] Generating high-entropy unique production credentials...');
    const prodCreds = {
        generatedAt: new Date().toISOString(),
        targetEnvironment: TARGET_HOST,
        accounts: {
            student: {
                username: 'demo.student',
                role: 'STUDENT',
                studentId: 'DEMO-STU-01',
                displayName: 'Aarav Sharma',
                password: generateSecurePassword(24),
                enabled: true
            },
            officer: {
                username: 'demo.officer',
                role: 'PLACEMENT_OFFICER',
                studentId: null,
                displayName: 'Placement Officer',
                password: generateSecurePassword(24),
                enabled: true
            },
            master: {
                username: 'demo.master',
                role: 'ADMINISTRATOR',
                studentId: null,
                displayName: 'System Administrator (Locked)',
                password: generateSecurePassword(32),
                enabled: false // Disabled in production to eliminate attack surface
            }
        }
    };

    // Ensure .run directory exists and write credentials
    const runDir = path.dirname(PROD_CREDS_PATH);
    if (!fs.existsSync(runDir)) fs.mkdirSync(runDir, { recursive: true });
    fs.writeFileSync(PROD_CREDS_PATH, JSON.stringify(prodCreds, null, 2), { mode: 0o600 });
    console.log('  - Production credentials securely stored at:', PROD_CREDS_PATH);
    console.log('  - Administrator account locked in production (enabled = false)');

    // 4. Update Neon database with fresh BCrypt hashes
    console.log('\n[4/7] Updating BCrypt hashes in Neon PostgreSQL...');
    for (const key of Object.keys(prodCreds.accounts)) {
        const acc = prodCreds.accounts[key];
        const salt = bcrypt.genSaltSync(10);
        const hash = bcrypt.hashSync(acc.password, salt);

        await client.query(`
            UPDATE user_accounts
            SET password_hash = $1,
                enabled = $2,
                updated_at = CURRENT_TIMESTAMP
            WHERE username = $3
        `, [hash, acc.enabled, acc.username]);
        console.log(`  - Updated ${acc.username} (enabled: ${acc.enabled}, role: ${acc.role})`);
    }

    // 5. Ensure presentation drive (DRV-DEMO-05) exists for repeatable demo workflow
    console.log('\n[5/7] Provisioning isolated presentation drive (DRV-DEMO-05)...');
    await client.query(`
        INSERT INTO recruitment_drives (id, company_id, job_title, minimum_cgpa, application_deadline, is_closed)
        VALUES ('DRV-DEMO-05', 'CMP-DEMO-01', 'Cloud Systems Associate', 7.00, DATE '2027-05-30', FALSE)
        ON CONFLICT (id) DO UPDATE
        SET job_title = EXCLUDED.job_title,
            minimum_cgpa = EXCLUDED.minimum_cgpa,
            application_deadline = EXCLUDED.application_deadline,
            is_closed = FALSE;
    `);

    await client.query(`
        INSERT INTO drive_eligible_branches (drive_id, branch)
        VALUES ('DRV-DEMO-05', 'CSE'), ('DRV-DEMO-05', 'IT')
        ON CONFLICT DO NOTHING;
    `);

    // Clean up any previous test applications for DRV-DEMO-05 to ensure fresh repeatable scenario
    await client.query(`
        DELETE FROM interview_sessions 
        WHERE application_id IN (SELECT id FROM applications WHERE drive_id = 'DRV-DEMO-05');
    `);
    await client.query(`
        DELETE FROM applications WHERE drive_id = 'DRV-DEMO-05';
    `);
    console.log('  - Presentation drive DRV-DEMO-05 provisioned and reset for fresh application workflow.');

    await client.end();
    console.log('Neon database updates committed.');

    // 6. Test authentication against live public endpoint
    console.log('\n[6/7] Verifying authentication behavior against public endpoint...');
    
    // Step 6a: Fetch CSRF token
    const csrfRes = await fetch(`${TARGET_HOST}/api/v1/auth/csrf`);
    const setCookie = csrfRes.headers.get('set-cookie') || '';
    const csrfData = await csrfRes.json();
    const csrfToken = csrfData.token;

    async function testLogin(username, password) {
        const res = await fetch(`${TARGET_HOST}/api/v1/auth/login`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-CSRF-TOKEN': csrfToken,
                'Cookie': setCookie
            },
            body: JSON.stringify({ username, password })
        });
        return res.status;
    }

    // Step 6b: Verify old weak admin password is REJECTED
    console.log('  - Testing old weak admin password (12345678)...');
    const weakStatus = await testLogin('demo.master', '12345678');
    console.log('    Result: Status', weakStatus, weakStatus === 401 ? '[PASS - REJECTED]' : '[FAIL]');

    // Step 6c: Verify disabled admin cannot log in even with new password
    console.log('  - Testing locked admin with new password...');
    const lockedStatus = await testLogin('demo.master', prodCreds.accounts.master.password);
    console.log('    Result: Status', lockedStatus, lockedStatus === 401 ? '[PASS - LOCKED OUT]' : '[FAIL]');

    // Step 6d: Verify old student password is REJECTED
    if (oldLocalStudent) {
        console.log('  - Testing old student password...');
        const oldStuStatus = await testLogin('demo.student', oldLocalStudent);
        console.log('    Result: Status', oldStuStatus, oldStuStatus === 401 ? '[PASS - REJECTED]' : '[FAIL]');
    }

    // Step 6e: Verify new student password SUCCEEDS
    console.log('  - Testing new hardened student credentials...');
    const newStuStatus = await testLogin('demo.student', prodCreds.accounts.student.password);
    console.log('    Result: Status', newStuStatus, newStuStatus === 200 ? '[PASS - AUTHENTICATED]' : '[FAIL]');

    // Step 6f: Verify new officer password SUCCEEDS
    console.log('  - Testing new hardened officer credentials...');
    const newOffStatus = await testLogin('demo.officer', prodCreds.accounts.officer.password);
    console.log('    Result: Status', newOffStatus, newOffStatus === 200 ? '[PASS - AUTHENTICATED]' : '[FAIL]');

    console.log('\n[7/7] Credential Hardening & Provisioning COMPLETE.');
    console.log('================================================================');
}

runAuditAndHardening().catch(err => {
    console.error('Fatal hardening error:', err);
    process.exit(1);
});
