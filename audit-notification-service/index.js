const express = require('express');
const amqp = require('amqplib');
const crypto = require('crypto');
const { Pool } = require('pg');
const fs = require('fs');
const path = require('path');
const os = require('os');

// When started directly with `npm start`, load the shared project-level .env.
// Docker already injects the same variables, so a missing file is expected there.
try {
    process.loadEnvFile(path.resolve(__dirname, '..', '.env'));
} catch (error) {
    if (error.code !== 'ENOENT') throw error;
}

const app = express();
app.use(express.json({ limit: '256kb' }));

const PORT = Number(process.env.PORT || 8083);
const EUREKA_SERVER_URL = (process.env.EUREKA_SERVER_URL || 'http://localhost:8761/eureka').replace(/\/+$/, '');
const EUREKA_APP_NAME = 'AUDIT-NOTIFICATION-SERVICE';
// Với cấu hình hiện tại, Gateway chạy trên host và cổng 8083 được publish về loopback.
const EUREKA_INSTANCE_HOSTNAME = process.env.EUREKA_INSTANCE_HOSTNAME || 'localhost';
const EUREKA_INSTANCE_ID = process.env.EUREKA_INSTANCE_ID || `${EUREKA_INSTANCE_HOSTNAME}:audit-notification-service:${PORT}`;
const EUREKA_HEARTBEAT_MS = Math.max(Number(process.env.EUREKA_HEARTBEAT_MS || 30000), 5000);
const EUREKA_ENABLED = String(process.env.EUREKA_CLIENT_ENABLED || 'true').toLowerCase() !== 'false';
const QUEUE_NAME = process.env.QUEUE_NAME || 'profile_events_queue';
const JWT_SECRET = process.env.JWT_SECRET;
const JWT_ISSUER = process.env.JWT_ISSUER || 'student-profile-auth-service';
const RABBITMQ_HOST = process.env.RABBITMQ_HOST || 'localhost';
const RABBITMQ_PORT = Number(process.env.RABBITMQ_PORT || 5672);
const RABBITMQ_USER = process.env.RABBITMQ_USER || 'student_app';
const RABBITMQ_PASSWORD = process.env.RABBITMQ_PASSWORD;

if (!JWT_SECRET || Buffer.byteLength(JWT_SECRET, 'utf8') < 32) {
    console.error('[!] JWT_SECRET chua duoc cau hinh hoac ngan hon 32 byte.');
    process.exit(1);
}
if (!process.env.AUDIT_DB_PASSWORD) {
    console.error('[!] Thieu AUDIT_DB_PASSWORD. Audit Service khong duoc phep ket noi bang tai khoan mac dinh.');
    process.exit(1);
}
if (!RABBITMQ_PASSWORD) {
    console.error('[!] Thieu RABBITMQ_PASSWORD. Hay cau hinh RabbitMQ truoc khi chay Audit Service.');
    process.exit(1);
}

const pool = new Pool({
    host: process.env.AUDIT_DB_HOST || 'localhost',
    port: Number(process.env.AUDIT_DB_PORT || 5432),
    database: process.env.AUDIT_DB_NAME || 'audit_db',
    user: process.env.AUDIT_DB_USER || 'audit_app',
    password: process.env.AUDIT_DB_PASSWORD,
    max: Number(process.env.AUDIT_DB_POOL_MAX || 10),
    idleTimeoutMillis: 30000,
    connectionTimeoutMillis: 5000,
    application_name: 'audit-notification-service'
});
pool.on('error', (error) => {
    console.error('[PostgreSQL] Loi ket noi idle client:', error.message);
});

// Xac thuc JWT HS256 tuong thich voi Profile Service (JJWT).
function authenticateJwt(req, res, next) {
    const authHeader = req.get('Authorization') || '';
    const match = authHeader.match(/^Bearer\s+(.+)$/i);
    if (!match) {
        return res.status(401).json({ status: 401, error: 'Unauthorized', message: 'Thieu Bearer Token hop le.' });
    }

    try {
        const tokenParts = match[1].split('.');
        if (tokenParts.length !== 3) throw new Error('Invalid JWT format');
        const [encodedHeader, encodedPayload, encodedSignature] = tokenParts;
        const header = JSON.parse(Buffer.from(encodedHeader, 'base64url').toString('utf8'));
        const claims = JSON.parse(Buffer.from(encodedPayload, 'base64url').toString('utf8'));
        if (header.alg !== 'HS256') throw new Error('Unsupported JWT algorithm');

        const expectedSignature = crypto.createHmac('sha256', JWT_SECRET)
            .update(`${encodedHeader}.${encodedPayload}`).digest();
        const receivedSignature = Buffer.from(encodedSignature, 'base64url');
        if (receivedSignature.length !== expectedSignature.length ||
            !crypto.timingSafeEqual(receivedSignature, expectedSignature)) {
            throw new Error('Invalid JWT signature');
        }

        const now = Math.floor(Date.now() / 1000);
        if (claims.iss !== JWT_ISSUER || !claims.sub ||
            typeof claims.exp !== 'number' || claims.exp <= now ||
            (typeof claims.nbf === 'number' && claims.nbf > now)) {
            throw new Error('Invalid or expired JWT claims');
        }
        req.authenticatedUser = {
            username: claims.sub,
            role: typeof claims.role === 'string' ? claims.role.toUpperCase() : ''
        };
        return next();
    } catch (error) {
        return res.status(401).json({ status: 401, error: 'Unauthorized', message: 'Token khong hop le hoac da het han.' });
    }
}

function requireAdmin(req, res, next) {
    if (!req.authenticatedUser || req.authenticatedUser.role !== 'ADMIN') {
        return res.status(403).json({ status: 403, error: 'Forbidden', message: 'Chi ADMIN moi duoc phep xem nhat ky kiem toan.' });
    }
    return next();
}

function asOptionalString(value, maxLength = 255) {
    if (value === null || value === undefined) return null;
    return String(value).slice(0, maxLength);
}

async function ensureSchema() {
    const migrationPath = path.join(__dirname, 'migrations', '001_create_audit_logs.sql');
    const migrationSql = fs.readFileSync(migrationPath, 'utf8');
    await pool.query(migrationSql);
}

function createStableEventId(eventData) {
    // Tuong thich message cu chua co eventId; cung mot payload se co cung UUID khi redelivery.
    const digest = crypto.createHash('sha256').update(JSON.stringify(eventData)).digest();
    digest[6] = (digest[6] & 0x0f) | 0x50; // UUID version 5-style deterministic identifier.
    digest[8] = (digest[8] & 0x3f) | 0x80;
    const hex = digest.subarray(0, 16).toString('hex');
    return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

async function insertAuditEvent(eventData) {
    if (!eventData || typeof eventData !== 'object' || Array.isArray(eventData)) {
        throw new TypeError('RabbitMQ event must be a JSON object');
    }
    const eventId = asOptionalString(eventData.eventId || createStableEventId(eventData), 36);
    if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(eventId)) {
        throw new TypeError('Audit eventId is invalid');
    }
    const action = asOptionalString(eventData.action, 100);
    if (!action || !/^[A-Z0-9_:-]+$/i.test(action)) {
        throw new TypeError('Audit event action is missing or invalid');
    }

    const eventTimestamp = eventData.timestamp ? new Date(eventData.timestamp) : new Date();
    if (Number.isNaN(eventTimestamp.getTime())) throw new TypeError('Audit event timestamp is invalid');

    const values = [
        eventId,
        action,
        asOptionalString(eventData.studentId, 20),
        asOptionalString(eventData.fullName, 100),
        asOptionalString(eventData.email, 255),
        asOptionalString(eventData.description || eventData.details || eventData.message || 'Xu ly su kien tu RabbitMQ', 10000),
        eventTimestamp
    ];
    const result = await pool.query(
        `INSERT INTO audit_logs (event_id, action, student_id, full_name, email, description, event_timestamp)
         VALUES ($1, $2, $3, $4, $5, $6, $7)
         ON CONFLICT (event_id) DO NOTHING
         RETURNING id, event_id AS "eventId", action, student_id AS "studentId",
                   full_name AS "fullName", email, description, event_timestamp AS timestamp`,
        values
    );
    return result.rows[0];
}

app.get('/health', async (req, res) => {
    try {
        await pool.query('SELECT 1');
        return res.status(200).json({ status: 'UP', database: 'UP' });
    } catch (error) {
        return res.status(503).json({ status: 'DOWN', database: 'DOWN' });
    }
});

app.get('/api/v1/audits', authenticateJwt, requireAdmin, async (req, res, next) => {
    try {
        const limit = Math.min(Math.max(Number.parseInt(req.query.limit, 10) || 100, 1), 500);
        const offset = Math.max(Number.parseInt(req.query.offset, 10) || 0, 0);
        const result = await pool.query(
            `SELECT id, event_id AS "eventId", action, student_id AS "studentId", full_name AS "fullName",
                    email, description, event_timestamp AS timestamp
             FROM audit_logs ORDER BY event_timestamp DESC, id DESC LIMIT $1 OFFSET $2`, 
            [limit, offset]
        );
        return res.json(result.rows);
    } catch (error) { return next(error); }
});

app.get('/api/v1/audits/info', async (req, res, next) => {
    try {
        const result = await pool.query('SELECT COUNT(*)::bigint AS total FROM audit_logs');
        return res.json({
            service: 'Audit & Security Log Service (Node.js + Express + PostgreSQL)',
            port: PORT,
            queue: QUEUE_NAME,
            totalEventsProcessed: Number(result.rows[0].total)
        });
    } catch (error) { return next(error); }
});

app.use((error, req, res, next) => {
    console.error('[Audit API Error]', error.message);
    if (res.headersSent) return next(error);
    return res.status(500).json({ status: 500, error: 'Internal Server Error', message: 'Khong the xu ly yeu cau audit.' });
});

let eurekaTimer = null;
let eurekaRegistered = false;

function getContainerIpAddress() {
    const interfaces = os.networkInterfaces();
    for (const addresses of Object.values(interfaces)) {
        for (const address of addresses || []) {
            if (address.family === 'IPv4' && !address.internal) return address.address;
        }
    }
    return EUREKA_INSTANCE_HOSTNAME;
}

function eurekaInstancePayload() {
    const baseUrl = `http://${EUREKA_INSTANCE_HOSTNAME}:${PORT}`;
    return {
        instance: {
            instanceId: EUREKA_INSTANCE_ID,
            hostName: EUREKA_INSTANCE_HOSTNAME,
            app: EUREKA_APP_NAME,
            // Advertise the container address, never loopback: Eureka consumers run in other containers.
            ipAddr: getContainerIpAddress(),
            status: 'UP',
            overriddenstatus: 'UNKNOWN',
            port: { '$': PORT, '@enabled': 'true' },
            securePort: { '$': 443, '@enabled': 'false' },
            countryId: 1,
            dataCenterInfo: {
                '@class': 'com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo',
                name: 'MyOwn'
            },
            leaseInfo: { renewalIntervalInSecs: Math.round(EUREKA_HEARTBEAT_MS / 1000), durationInSecs: 90 },
            homePageUrl: `${baseUrl}/`,
            statusPageUrl: `${baseUrl}/health`,
            healthCheckUrl: `${baseUrl}/health`,
            metadata: { 'service.type': 'nodejs', 'management.port': String(PORT) }
        }
    };
}

async function registerWithEureka() {
    if (!EUREKA_ENABLED || shuttingDown) return;
    const response = await fetch(`${EUREKA_SERVER_URL}/apps/${EUREKA_APP_NAME}`, {
        method: 'POST',
        headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
        body: JSON.stringify(eurekaInstancePayload()),
        signal: AbortSignal.timeout(5000)
    });
    if (!response.ok && response.status !== 204) {
        throw new Error(`Eureka register returned HTTP ${response.status}`);
    }
    eurekaRegistered = true;
    console.log(`[Eureka] Da dang ky ${EUREKA_APP_NAME} (${EUREKA_INSTANCE_ID})`);
}

async function sendEurekaHeartbeat() {
    if (!EUREKA_ENABLED || shuttingDown) return;
    if (!eurekaRegistered) {
        await registerWithEureka();
        return;
    }
    const response = await fetch(`${EUREKA_SERVER_URL}/apps/${EUREKA_APP_NAME}/${encodeURIComponent(EUREKA_INSTANCE_ID)}`, {
        method: 'PUT',
        headers: { Accept: 'application/json' },
        signal: AbortSignal.timeout(5000)
    });
    if (response.status === 404) {
        eurekaRegistered = false;
        await registerWithEureka();
        return;
    }
    if (!response.ok) throw new Error(`Eureka heartbeat returned HTTP ${response.status}`);
}

async function deregisterFromEureka() {
    if (!EUREKA_ENABLED || !eurekaRegistered) return;
    try {
        await fetch(`${EUREKA_SERVER_URL}/apps/${EUREKA_APP_NAME}/${encodeURIComponent(EUREKA_INSTANCE_ID)}`, {
            method: 'DELETE',
            headers: { Accept: 'application/json' },
            signal: AbortSignal.timeout(3000)
        });
        console.log(`[Eureka] Da huy dang ky ${EUREKA_INSTANCE_ID}`);
    } catch (error) {
        console.warn('[Eureka] Khong the huy dang ky truoc khi dung:', error.message);
    } finally {
        eurekaRegistered = false;
    }
}

function startEurekaRegistration() {
    if (!EUREKA_ENABLED) {
        console.log('[Eureka] Da tat dang ky theo EUREKA_CLIENT_ENABLED=false');
        return;
    }
    sendEurekaHeartbeat().catch((error) => {
        console.warn('[Eureka] Chua dang ky duoc; se thu lai:', error.message);
    });
    eurekaTimer = setInterval(() => {
        sendEurekaHeartbeat().catch((error) => {
            console.warn('[Eureka] Heartbeat/registration that bai:', error.message);
        });
    }, EUREKA_HEARTBEAT_MS);
}

let reconnectTimer = null;
let shuttingDown = false;
async function connectQueue() {
    if (shuttingDown) return;
    try {
        const connection = await amqp.connect({
            protocol: 'amqp', hostname: RABBITMQ_HOST, port: RABBITMQ_PORT,
            username: RABBITMQ_USER, password: RABBITMQ_PASSWORD,
            heartbeat: 30
        });
        connection.on('error', (err) => console.error('[RabbitMQ] Connection error:', err.message));
        connection.on('close', () => scheduleReconnect());

        const channel = await connection.createChannel();
        await channel.assertQueue(QUEUE_NAME, { durable: true });
        await channel.prefetch(10);
        console.log(`[*] Dang lang nghe RabbitMQ queue: ${QUEUE_NAME}`);

        await channel.consume(QUEUE_NAME, async (message) => {
            if (message === null) return;
            let eventData;
            try {
                eventData = JSON.parse(message.content.toString('utf8'));
            } catch (error) {
                console.error('[RabbitMQ] Bo qua message JSON loi:', error.message);
                channel.nack(message, false, false);
                return;
            }
            try {
                const saved = await insertAuditEvent(eventData);
                if (saved) console.log(`[Audit] Da luu event #${saved.id}: ${saved.action}`);
                else console.log('[Audit] Bo qua event da nhan truoc do (duplicate eventId).');
                channel.ack(message);
            } catch (error) {
                if (error instanceof TypeError) {
                    console.error('[RabbitMQ] Event khong hop le, reject khong requeue:', error.message);
                    channel.nack(message, false, false);
                } else {
                    console.error('[RabbitMQ] Chua luu duoc event, se dua lai queue:', error.message);
                    channel.nack(message, false, true);
                }
            }
        }, { noAck: false });
    } catch (error) {
        console.error('[!] Khong ket noi duoc RabbitMQ:', error.message);
        scheduleReconnect();
    }
}

function scheduleReconnect() {
    if (shuttingDown || reconnectTimer) return;
    reconnectTimer = setTimeout(() => {
        reconnectTimer = null;
        connectQueue();
    }, 5000);
}

async function start() {
    await ensureSchema();
    const server = app.listen(PORT, () => {
        console.log(`[+] Audit Service dang chay tai http://localhost:${PORT}`);
        startEurekaRegistration();
    });
    connectQueue();

    const shutdown = async (signal) => {
        console.log(`[${signal}] Dang dong Audit Service...`);
        shuttingDown = true;
        if (reconnectTimer) clearTimeout(reconnectTimer);
        if (eurekaTimer) clearInterval(eurekaTimer);
        await deregisterFromEureka();
        await new Promise((resolve) => server.close(resolve));
        await pool.end();
        process.exit(0);
    };
    process.once('SIGINT', () => shutdown('SIGINT'));
    process.once('SIGTERM', () => shutdown('SIGTERM'));
}

start().catch((error) => {
    console.error('[!] Audit Service khong the khoi dong:', error);
    process.exit(1);
});
