import dotenv from 'dotenv';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { MongoClient, ObjectId } from 'mongodb';
import { startMockAuthServer, stopMockAuthServer, generateToken } from './mock-auth.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
dotenv.config({ path: path.resolve(__dirname, '.env') });

const baseUrl = (process.env.BASE_URL || 'http://localhost:9081/kitchensink').replace(/\/$/, '');
const mongoUri = process.env.MONGODB_URI || 'mongodb://localhost:27017/kitchensink_loadtest?replicaSet=rs0';

async function runSmokeTest() {
  console.log('================ SMOKE TEST EXECUTION ================');
  console.log(`Target Base URL: ${baseUrl}`);
  console.log(`Target MongoDB URI: ${mongoUri}`);

  // 1. Start mock JWKS server
  console.log('\n[1/5] Starting Mock Auth JWKS server...');
  await startMockAuthServer(9089);

  // 2. Connect to MongoDB and verify database state
  console.log('\n[2/5] Checking MongoDB database and collections...');
  const client = new MongoClient(mongoUri);
  await client.connect();
  const db = client.db();
  console.log(`Connected to database: ${db.databaseName}`);

  const usersCount = await db.collection('users').countDocuments({ usernameLower: /@load\.test$/ });
  const membersCount = await db.collection('members').countDocuments({ emailLower: /@load\.test$/ });
  const rolesCount = await db.collection('roles').countDocuments();
  console.log(`Database state: users=${usersCount}, members=${membersCount}, roles=${rolesCount}`);

  if (usersCount === 0 || membersCount === 0) {
    throw new Error('Database not initialized. Please ensure seed data is populated.');
  }

  // 3. Verify App Health and Connectivity
  console.log('\n[3/5] Verifying Spring Boot Application Health...');
  const healthToken = generateToken('user.0001@load.test');
  try {
    const healthRes = await fetch(`${baseUrl}/actuator/health`, {
      headers: { 'Authorization': `Bearer ${healthToken}` }
    });
    console.log(`Actuator Health Status: ${healthRes.status} (${healthRes.statusText})`);
    if (!healthRes.ok) {
      throw new Error(`Application health check returned status ${healthRes.status}`);
    }
  } catch (err) {
    console.error(`Could not reach application at ${baseUrl}. Ensure Spring Boot is running on port 9081 with loadtest profile.`);
    throw err;
  }

  // 4. Test Token Authentication with different roles
  console.log('\n[4/5] Testing Authenticated REST API Operations...');
  const runId = Math.random().toString(36).substring(2, 7);

  // A. Admin user - test /api/v1/me and Actuator API metrics
  const adminToken = generateToken('user.0001@load.test');
  const meRes = await fetch(`${baseUrl}/api/v1/me`, {
    headers: { 'Authorization': `Bearer ${adminToken}` }
  });
  console.log(`GET /api/v1/me Status: ${meRes.status}`);
  if (!meRes.ok) throw new Error(`GET /api/v1/me failed: ${await meRes.text()}`);
  const meData = await meRes.json();
  console.log(`Identity resolved: ${meData.username}, permissions: [${meData.permissions.join(', ')}]`);

  // B. Creator user - test POST /api/v1/members
  const creatorUser = await db.collection('users').findOne({ roles: 'creator' });
  const creatorToken = generateToken(creatorUser.username);
  const testEmail = `smoke.${runId}@load.test`;
  const createRes = await fetch(`${baseUrl}/api/v1/members`, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${creatorToken}`,
      'Content-Type': 'application/json',
      'Idempotency-Key': `smoke-${runId}`
    },
    body: JSON.stringify({
      firstName: 'Smoke',
      lastName: 'Test',
      email: testEmail,
      countryCode: '1',
      phone: '2125559999'
    })
  });
  console.log(`POST /api/v1/members Status: ${createRes.status}`);
  if (!createRes.ok) throw new Error(`POST /api/v1/members failed: ${await createRes.text()}`);
  const createdMember = await createRes.json();
  console.log(`Created member id: ${createdMember.id}`);

  // C. Viewer user - test GET /api/v1/members/{id}
  const viewerUser = await db.collection('users').findOne({ roles: 'viewer' });
  const viewerToken = generateToken(viewerUser.username);
  const getRes = await fetch(`${baseUrl}/api/v1/members/${createdMember.id}`, {
    headers: { 'Authorization': `Bearer ${viewerToken}` }
  });
  console.log(`GET /api/v1/members/{id} Status: ${getRes.status}`);
  if (!getRes.ok) throw new Error(`GET /api/v1/members/{id} failed`);

  // D. Editor user - test PATCH /api/v1/members/{id}
  const editorUser = await db.collection('users').findOne({ roles: 'editor' });
  const editorToken = generateToken(editorUser.username);
  const patchRes = await fetch(`${baseUrl}/api/v1/members/${createdMember.id}`, {
    method: 'PATCH',
    headers: {
      'Authorization': `Bearer ${editorToken}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      version: createdMember.version || 1,
      firstName: 'SmokeUpdated'
    })
  });
  console.log(`PATCH /api/v1/members/{id} Status: ${patchRes.status}`);
  if (!patchRes.ok) throw new Error(`PATCH /api/v1/members/{id} failed: ${await patchRes.text()}`);

  // E. Deleter user - test DELETE /api/v1/members/{id}
  const deleterUser = await db.collection('users').findOne({ roles: 'deleter' });
  const deleterToken = generateToken(deleterUser.username);
  const deleteRes = await fetch(`${baseUrl}/api/v1/members/${createdMember.id}`, {
    method: 'DELETE',
    headers: { 'Authorization': `Bearer ${deleterToken}` }
  });
  console.log(`DELETE /api/v1/members/{id} Status: ${deleteRes.status}`);
  if (!deleteRes.ok && deleteRes.status !== 204) throw new Error(`DELETE failed: ${await deleteRes.text()}`);

  // F. Dedicated API & DB metrics endpoints
  console.log('\n[5/5] Verifying Actuator Metrics Endpoints...');
  const apiMetricsRes = await fetch(`${baseUrl}/actuator/api-metrics`, {
    headers: { 'Authorization': `Bearer ${adminToken}` }
  });
  console.log(`GET /actuator/api-metrics Status: ${apiMetricsRes.status}`);
  const apiMetrics = await apiMetricsRes.json();
  console.log(`Tracked API Routes in metrics: ${apiMetrics.length}`);

  const dbMetricsRes = await fetch(`${baseUrl}/actuator/db-metrics`, {
    headers: { 'Authorization': `Bearer ${adminToken}` }
  });
  console.log(`GET /actuator/db-metrics Status: ${dbMetricsRes.status}`);
  const dbMetrics = await dbMetricsRes.json();
  console.log(`Tracked DB Operations in metrics: ${dbMetrics.length}`);

  // Cleanup DB client & auth server
  await db.collection('members').deleteOne({ _id: new ObjectId(createdMember.id) });
  await client.close();
  await stopMockAuthServer();

  console.log('\n================ SMOKE TEST PASSED SUCCESSFULLY ================');
}

runSmokeTest().catch(async (err) => {
  console.error('\nSMOKE TEST FAILED:', err);
  await stopMockAuthServer();
  process.exit(1);
});
