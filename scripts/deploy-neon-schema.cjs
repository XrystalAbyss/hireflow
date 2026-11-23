const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');
const { Client } = require('pg');
const bcrypt = require('bcryptjs');

async function deploySchema() {
    console.log('Connecting to Neon PostgreSQL...');
    const connStr = execSync('neon connection-string production --project-id shiny-brook-20410796 --ssl require', { encoding: 'utf-8' }).trim();
    const client = new Client({
        connectionString: connStr,
        ssl: { rejectUnauthorized: false }
    });
    await client.connect();
    console.log('Connected to Neon.');

    const v1 = fs.readFileSync(path.join(__dirname, '../src/main/resources/db/migration/V1__initial_schema.sql'), 'utf-8');
    const v2 = fs.readFileSync(path.join(__dirname, '../src/main/resources/db/migration/V2__user_accounts.sql'), 'utf-8');
    const v3 = fs.readFileSync(path.join(__dirname, '../src/main/resources/db/migration/V3__drive_closure.sql'), 'utf-8');
    const scenario = fs.readFileSync(path.join(__dirname, '../infra/cap8-integration/demo-scenario.sql'), 'utf-8');

    console.log('Applying Flyway V1, V2, V3 migrations...');
    await client.query(v1);
    await client.query(v2);
    await client.query(v3);
    console.log('Migrations executed successfully.');

    console.log('Ensuring flyway_schema_history table...');
    await client.query(`
        CREATE TABLE IF NOT EXISTS flyway_schema_history (
            installed_rank INT NOT NULL,
            version VARCHAR(50),
            description VARCHAR(200) NOT NULL,
            type VARCHAR(20) NOT NULL,
            script VARCHAR(1000) NOT NULL,
            checksum INT,
            installed_by VARCHAR(100) NOT NULL,
            installed_on TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            execution_time INT NOT NULL,
            success BOOLEAN NOT NULL,
            CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank)
        );
    `);

    // Record migrations in flyway history so Spring Boot Flyway recognizes them
    const migrations = [
        { rank: 1, ver: '1', desc: 'initial schema', script: 'V1__initial_schema.sql' },
        { rank: 2, ver: '2', desc: 'user accounts', script: 'V2__user_accounts.sql' },
        { rank: 3, ver: '3', desc: 'drive closure', script: 'V3__drive_closure.sql' }
    ];

    for (const m of migrations) {
        await client.query(`
            INSERT INTO flyway_schema_history (installed_rank, version, description, type, script, installed_by, execution_time, success)
            VALUES ($1, $2, $3, 'SQL', $4, 'neondb_owner', 10, true)
            ON CONFLICT (installed_rank) DO NOTHING;
        `, [m.rank, m.ver, m.desc, m.script]);
    }

    console.log('Applying demo scenario records...');
    await client.query(scenario);
    console.log('Demo scenario applied.');

    // Seed demo accounts
    let studentPass = 'Student@HireFlow2026!';
    let officerPass = 'Officer@HireFlow2026!';
    let masterPass = 'Admin@HireFlow2026!';

    const credPath = path.join(__dirname, '../.run/demo.local.json');
    if (fs.existsSync(credPath)) {
        try {
            const raw = fs.readFileSync(credPath, 'utf-8').replace(/^\uFEFF/, '');
            const creds = JSON.parse(raw);
            if (creds.demoStudentPassword) studentPass = creds.demoStudentPassword;
            if (creds.demoOfficerPassword) officerPass = creds.demoOfficerPassword;
            if (creds.demoMasterPassword) masterPass = creds.demoMasterPassword;
            console.log('Successfully loaded verified passwords from demo.local.json');
        } catch (e) {
            console.warn('Could not read demo.local.json:', e.message);
        }
    }

    const salt = bcrypt.genSaltSync(10);
    const studentHash = bcrypt.hashSync(studentPass, salt);
    const officerHash = bcrypt.hashSync(officerPass, salt);
    const masterHash = bcrypt.hashSync(masterPass, salt);

    const accounts = [
        { id: 'ACC-DEMO-01', user: 'demo.student', hash: studentHash, role: 'STUDENT', stuId: 'DEMO-STU-01', name: 'Aarav Sharma' },
        { id: 'ACC-DEMO-02', user: 'demo.officer', hash: officerHash, role: 'PLACEMENT_OFFICER', stuId: null, name: 'Placement Officer' },
        { id: 'ACC-DEMO-03', user: 'demo.master', hash: masterHash, role: 'ADMINISTRATOR', stuId: null, name: 'System Administrator' }
    ];

    for (const acc of accounts) {
        await client.query(`
            INSERT INTO user_accounts (id, username, password_hash, role, student_id, display_name, enabled)
            VALUES ($1, $2, $3, $4, $5, $6, true)
            ON CONFLICT (username) DO UPDATE
            SET password_hash = EXCLUDED.password_hash,
                student_id = EXCLUDED.student_id,
                display_name = EXCLUDED.display_name,
                enabled = true;
        `, [acc.id, acc.user, acc.hash, acc.role, acc.stuId, acc.name]);
        console.log(`Upserted account: ${acc.user} (${acc.role})`);
    }

    // Verify row counts
    const tables = ['students', 'companies', 'recruitment_drives', 'applications', 'interviewers', 'rooms', 'interview_sessions', 'user_accounts'];
    console.log('\n--- VERIFICATION OF PERSISTED TABLES ---');
    for (const t of tables) {
        const countRes = await client.query(`SELECT COUNT(*) FROM ${t}`);
        console.log(`Table ${t.padEnd(20)}: ${countRes.rows[0].count} rows`);
    }

    await client.end();
    console.log('\nNeon schema deployment and seeding COMPLETED SUCCESSFULLY!');
}

deploySchema().catch(err => {
    console.error('Migration failed:', err);
    process.exit(1);
});
