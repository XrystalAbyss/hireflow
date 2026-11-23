const { execSync } = require('child_process');
const { Client } = require('pg');

async function testNeon() {
    try {
        console.log('Retrieving connection string from Neon CLI...');
        const connStr = execSync('neon connection-string production --project-id shiny-brook-20410796 --ssl require', { encoding: 'utf-8' }).trim();
        const client = new Client({
            connectionString: connStr,
            ssl: { rejectUnauthorized: false }
        });
        await client.connect();
        console.log('Successfully connected to Neon PostgreSQL over TLS!');
        
        const verRes = await client.query('SELECT version();');
        console.log('PostgreSQL Version:', verRes.rows[0].version.split(' on ')[0]);
        
        console.log('Checking/creating btree_gist extension...');
        await client.query('CREATE EXTENSION IF NOT EXISTS btree_gist;');
        const extRes = await client.query("SELECT extname, extversion FROM pg_extension WHERE extname = 'btree_gist';");
        console.log('btree_gist Extension:', extRes.rows[0]);

        const dbRes = await client.query('SELECT current_database(), current_user;');
        console.log('Current DB / User:', dbRes.rows[0]);

        await client.end();
        console.log('Neon database verification SUCCESSFUL!');
    } catch (err) {
        console.error('Neon test failed:', err.message);
        process.exit(1);
    }
}

testNeon();
