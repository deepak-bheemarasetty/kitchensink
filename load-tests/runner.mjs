import dotenv from 'dotenv';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { MongoClient, ObjectId } from 'mongodb';
import { startMockAuthServer, stopMockAuthServer, generateToken } from './mock-auth.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
dotenv.config({ path: path.resolve(__dirname, '.env') });

const baseUrl = (process.env.BASE_URL || 'http://localhost:9081/kitchensink').replace(/\/$/, '');
const mongoUri = process.env.MONGODB_URI || 'mongodb://localhost:27017/kitchensink_loadtest?replicaSet=rs0';

// Configuration
const DURATION_SECONDS = parseInt(process.env.LOAD_MEASURE_SECONDS || '10', 10);
const CONCURRENCY = parseInt(process.env.LOAD_CONCURRENCY || '5', 10);

class MetricsCollector {
  constructor() {
    this.totalRequests = 0;
    this.byOperation = {};
  }

  record(op, status, latencyMs, success, error = null) {
    this.totalRequests++;
    if (!this.byOperation[op]) {
      this.byOperation[op] = { count: 0, successes: 0, failures: 0, latencies: [] };
    }
    const bucket = this.byOperation[op];
    bucket.count++;
    if (success) bucket.successes++;
    else bucket.failures++;
    bucket.latencies.push(latencyMs);

    if (!success && error) {
      console.error(`[Error] ${op} failed with ${status}: ${error}`);
    }
  }

  summary() {
    console.log('\n================ LOAD TEST EXECUTION SUMMARY ================');
    console.log(`Total Requests Sent: ${this.totalRequests}`);
    console.table(
      Object.entries(this.byOperation).map(([op, data]) => {
        const sorted = data.latencies.sort((a, b) => a - b);
        const p50 = sorted[Math.floor(sorted.length * 0.5)] || 0;
        const p95 = sorted[Math.floor(sorted.length * 0.95)] || 0;
        const p99 = sorted[Math.floor(sorted.length * 0.99)] || 0;
        const avg = data.latencies.reduce((a, b) => a + b, 0) / (data.latencies.length || 1);
        return {
          Operation: op,
          Calls: data.count,
          Share: `${((data.count / this.totalRequests) * 100).toFixed(1)}%`,
          Success: data.successes,
          Failed: data.failures,
          'Avg (ms)': avg.toFixed(1),
          'p50 (ms)': p50.toFixed(1),
          'p95 (ms)': p95.toFixed(1),
          'p99 (ms)': p99.toFixed(1),
        };
      })
    );
    console.log('==============================================================\n');
  }
}

async function runScenario() {
  console.log(`[Runner] Initializing against ${baseUrl}...`);
  await startMockAuthServer(9089);

  const client = new MongoClient(mongoUri);
  await client.connect();
  const db = client.db();

  // Load all 100 users grouped by role
  const users = await db.collection('users').find({ usernameLower: /@load\.test$/ }).toArray();
  if (users.length === 0) {
    throw new Error('No load.test users found in MongoDB. Run "node seed-100-users.mjs" first.');
  }

  const usersByRole = {
    admin: users.filter(u => u.roles.includes('admin')),
    manager: users.filter(u => u.roles.includes('manager')),
    creator: users.filter(u => u.roles.includes('creator')),
    editor: users.filter(u => u.roles.includes('editor')),
    deleter: users.filter(u => u.roles.includes('deleter')),
    viewer: users.filter(u => u.roles.includes('viewer')),
  };

  console.log(`[Runner] Loaded ${users.length} users: ` +
    Object.entries(usersByRole).map(([r, u]) => `${r}=${u.length}`).join(', '));

  // Load existing baseline member IDs and versions
  const baselineMembers = await db.collection('members')
    .find({ emailLower: /@load\.test$/, deletedAt: null })
    .project({ _id: 1, email: 1, version: 1, firstName: 1 })
    .limit(5000)
    .toArray();

  if (baselineMembers.length === 0) {
    throw new Error('No baseline members found. Run "node seed-30k-members.mjs" first.');
  }

  console.log(`[Runner] Cached ${baselineMembers.length} active baseline members for read/update operations.`);

  const metrics = new MetricsCollector();
  const createdMemberIds = new Set();
  const createdUserIds = new Set();

  function pickUser(roles) {
    for (const r of roles) {
      const pool = usersByRole[r];
      if (pool && pool.length > 0) {
        return pool[Math.floor(Math.random() * pool.length)];
      }
    }
    return users[0];
  }

  const runId = Math.random().toString(36).substring(2, 7);
  let memberSequence = 0;
  let userSequence = 0;

  // Operation executors
  async function opCreateMember() {
    const user = pickUser(['creator', 'manager', 'admin']);
    const token = generateToken(user.username);
    const num = ++memberSequence;
    const body = {
      firstName: `LoadTest`,
      lastName: `Tester`,
      email: `member.${runId}.${String(num).padStart(5, '0')}@load.test`,
      phone: String(2000000000 + (num % 7000000000)),
      countryCode: '1'
    };

    const start = performance.now();
    try {
      const res = await fetch(`${baseUrl}/api/v1/members`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json',
          'Idempotency-Key': `load-${runId}-${num}`
        },
        body: JSON.stringify(body)
      });
      const latency = performance.now() - start;
      if (res.ok) {
        const json = await res.json();
        if (json.id) {
          createdMemberIds.add(json.id);
          baselineMembers.push({ _id: json.id, version: json.version, firstName: json.firstName, email: json.email });
        }
        metrics.record('POST /api/v1/members', res.status, latency, true);
      } else {
        metrics.record('POST /api/v1/members', res.status, latency, false, await res.text());
      }
    } catch (err) {
      metrics.record('POST /api/v1/members', 0, performance.now() - start, false, err.message);
    }
  }

  async function opUpdateMember() {
    const user = pickUser(['editor', 'manager', 'admin']);
    const token = generateToken(user.username);
    const target = baselineMembers[Math.floor(Math.random() * baselineMembers.length)];

    const start = performance.now();
    try {
      const res = await fetch(`${baseUrl}/api/v1/members/${target._id}`, {
        method: 'PATCH',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          version: target.version || 1,
          firstName: `UpdatedName`
        })
      });
      const latency = performance.now() - start;
      if (res.ok) {
        const json = await res.json();
        target.version = json.version; // update cached version
        metrics.record('PATCH /api/v1/members/{id}', res.status, latency, true);
      } else {
        metrics.record('PATCH /api/v1/members/{id}', res.status, latency, false, await res.text());
      }
    } catch (err) {
      metrics.record('PATCH /api/v1/members/{id}', 0, performance.now() - start, false, err.message);
    }
  }

  async function opDeleteMember() {
    const user = pickUser(['deleter', 'manager', 'admin']);
    const token = generateToken(user.username);
    
    // Only delete transient created members to protect the baseline members
    if (createdMemberIds.size === 0) {
      return;
    }
    const targetId = [...createdMemberIds][0];
    createdMemberIds.delete(targetId);

    // Remove from in-memory pool so concurrent update/find won't hit a deleted member
    const poolIdx = baselineMembers.findIndex(m => String(m._id) === String(targetId));
    if (poolIdx !== -1) {
      baselineMembers.splice(poolIdx, 1);
    }

    const start = performance.now();
    try {
      const res = await fetch(`${baseUrl}/api/v1/members/${targetId}`, {
        method: 'DELETE',
        headers: { 'Authorization': `Bearer ${token}` }
      });
      const latency = performance.now() - start;
      if (res.ok || res.status === 204) {
        metrics.record('DELETE /api/v1/members/{id}', res.status, latency, true);
      } else {
        metrics.record('DELETE /api/v1/members/{id}', res.status, latency, false, await res.text());
      }
    } catch (err) {
      metrics.record('DELETE /api/v1/members/{id}', 0, performance.now() - start, false, err.message);
    }
  }

  async function opUserManagement() {
    const user = pickUser(['admin']);
    const token = generateToken(user.username);
    const num = ++userSequence;
    const username = `user.transient.${runId}.${String(num).padStart(4, '0')}@load.test`;

    const start = performance.now();
    try {
      // 1. Create User Identity
      const res = await fetch(`${baseUrl}/api/v1/users/identity`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          username: username,
          roles: ['viewer']
        })
      });
      const latency = performance.now() - start;
      if (res.ok) {
        metrics.record('POST /api/v1/users/identity', res.status, latency, true);
        
        // 2. Fetch user to obtain formatted hex id, then toggle active status
        const listRes = await fetch(`${baseUrl}/api/v1/users`, {
          headers: { 'Authorization': `Bearer ${token}` }
        });
        if (listRes.ok) {
          const list = await listRes.json();
          const target = list.find(u => u.username === username);
          if (target && target.id) {
            createdUserIds.add(target.id);
            const statusStart = performance.now();
            const statusRes = await fetch(`${baseUrl}/api/v1/users/${target.id}`, {
              method: 'PUT',
              headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json'
              },
              body: JSON.stringify({ isActive: false })
            });
            metrics.record('PUT /api/v1/users/{id}', statusRes.status, performance.now() - statusStart, statusRes.ok);
          }
        }
      } else {
        metrics.record('POST /api/v1/users/identity', res.status, latency, false, await res.text());
      }
    } catch (err) {
      metrics.record('POST /api/v1/users/identity', 0, performance.now() - start, false, err.message);
    }
  }

  async function opFindMember() {
    const user = pickUser(['viewer', 'creator', 'editor', 'deleter', 'manager', 'admin']);
    const token = generateToken(user.username);
    const target = baselineMembers[Math.floor(Math.random() * baselineMembers.length)];

    const start = performance.now();
    try {
      const res = await fetch(`${baseUrl}/api/v1/members/${target._id}`, {
        headers: { 'Authorization': `Bearer ${token}` }
      });
      const latency = performance.now() - start;
      metrics.record('GET /api/v1/members/{id}', res.status, latency, res.ok);
    } catch (err) {
      metrics.record('GET /api/v1/members/{id}', 0, performance.now() - start, false, err.message);
    }
  }

  async function opListMembers() {
    const user = pickUser(['viewer', 'creator', 'editor', 'deleter', 'manager', 'admin']);
    const token = generateToken(user.username);

    const start = performance.now();
    try {
      const res = await fetch(`${baseUrl}/api/v1/members?limit=10`, {
        headers: { 'Authorization': `Bearer ${token}` }
      });
      const latency = performance.now() - start;
      metrics.record('GET /api/v1/members?limit=10', res.status, latency, res.ok);
    } catch (err) {
      metrics.record('GET /api/v1/members?limit=10', 0, performance.now() - start, false, err.message);
    }
  }

  // Exact Traffic Distribution Generator:
  // 60% create member, 20% update member, 5% delete member, 5% user management, 10% find, 10% list
  function chooseOperation() {
    const r = Math.random() * 100;
    if (r < 60) return opCreateMember;       // 60%
    if (r < 80) return opUpdateMember;       // 20%
    if (r < 85) return opDeleteMember;       // 5%
    if (r < 90) return opUserManagement;     // 5%
    if (r < 95) return opFindMember;         // 5% (10% read total)
    return opListMembers;                    // 5% (10% read total)
  }

  console.log(`[Runner] Starting load execution with ${CONCURRENCY} workers for ${DURATION_SECONDS} seconds...`);
  const deadline = Date.now() + (DURATION_SECONDS * 1000);
  let activeWorkers = 0;

  async function workerLoop() {
    while (Date.now() < deadline) {
      const op = chooseOperation();
      await op();
    }
  }

  const workers = Array.from({ length: CONCURRENCY }, () => workerLoop());
  await Promise.all(workers);

  console.log('[Runner] Measurement window finished.');
  metrics.summary();

  // Cleanup transient test records
  console.log('[Runner] Cleaning up transient load test records...');
  if (createdMemberIds.size > 0) {
    const objIds = [...createdMemberIds].map(id => new ObjectId(id));
    const delRes = await db.collection('members').deleteMany({ _id: { $in: objIds } });
    console.log(`[Runner] Deleted ${delRes.deletedCount} transient created members.`);
  }
  const delUsersRes = await db.collection('users').deleteMany({ usernameLower: /transient/ });
  console.log(`[Runner] Deleted ${delUsersRes.deletedCount} transient users.`);

  await client.close();
  await stopMockAuthServer();
  console.log('[Runner] Done!');
}

runScenario().catch(err => {
  console.error('[Runner] Fatal Error:', err);
  process.exit(1);
});
