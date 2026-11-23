const { execSync } = require('child_process');
const { Client } = require('pg');
const bcrypt = require('bcryptjs');
const fs = require('fs');
const path = require('path');

async function setDemoPasswords() {
    const newPass = '12345678';
    const salt = bcrypt.genSaltSync(10);
    const hash = bcrypt.hashSync(newPass, salt);

    console.log('Connecting to Neon...');
    const connStr = execSync('neon connection-string production --project-id shiny-brook-20410796 --ssl require', { encoding: 'utf-8' }).trim();
    const client = new Client({ connectionString: connStr, ssl: { rejectUnauthorized: false } });
    await client.connect();

    console.log('Updating user_accounts in Neon to password: 12345678...');
    await client.query(`
        UPDATE user_accounts 
        SET password_hash = $1, enabled = true 
        WHERE username IN ('demo.student', 'demo.officer', 'demo.master')
    `, [hash]);

    const res = await client.query('SELECT username, role, enabled FROM user_accounts');
    console.table(res.rows);
    await client.end();

    const prodCredPath = path.join(__dirname, '../.run/demo.production.json');
    if (fs.existsSync(prodCredPath)) {
        const creds = JSON.parse(fs.readFileSync(prodCredPath, 'utf8'));
        if (creds.accounts) {
            if (creds.accounts.student) { creds.accounts.student.password = newPass; creds.accounts.student.enabled = true; }
            if (creds.accounts.officer) { creds.accounts.officer.password = newPass; creds.accounts.officer.enabled = true; }
            if (creds.accounts.master) { creds.accounts.master.password = newPass; creds.accounts.master.enabled = true; }
        }
        fs.writeFileSync(prodCredPath, JSON.stringify(creds, null, 2));
        console.log('Updated .run/demo.production.json');
    }

    // Verify authentication with Playwright page/request against public endpoint
    const { chromium } = require(path.join(__dirname, '../hireflow-app/node_modules/playwright'));
    const browser = await chromium.launch({ headless: true });
    const context = await browser.newContext();
    const page = await context.newPage();

    console.log('Verifying login with 12345678 against https://hireflow-prod.onrender.com...');
    await page.goto('https://hireflow-prod.onrender.com/auth/login');
    await page.fill('#username', 'demo.student');
    await page.fill('#password', newPass);
    await page.click('button[type="submit"]');
    await page.waitForURL('**/student/**', { timeout: 15000 });
    console.log('[SUCCESS] demo.student authenticated with 12345678! Current URL:', page.url());

    const offContext = await browser.newContext();
    const offPage = await offContext.newPage();
    await offPage.goto('https://hireflow-prod.onrender.com/auth/login');
    await offPage.fill('#username', 'demo.officer');
    await offPage.fill('#password', newPass);
    await offPage.click('button[type="submit"]');
    await offPage.waitForURL('**/officer/**', { timeout: 15000 });
    console.log('[SUCCESS] demo.officer authenticated with 12345678! Current URL:', offPage.url());

    await browser.close();
    console.log('All demo passwords successfully changed to 12345678!');
}

setDemoPasswords().catch(err => {
    console.error('Failed to set demo passwords:', err);
    process.exit(1);
});
