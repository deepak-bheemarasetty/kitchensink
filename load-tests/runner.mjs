import 'dotenv/config';
import { MongoClient, ObjectId } from 'mongodb';
import ExcelJS from 'exceljs';
import { randomUUID } from 'node:crypto';
import { createWriteStream } from 'node:fs';
import { mkdir, readFile } from 'node:fs/promises';
import { finished } from 'node:stream/promises';
import os from 'node:os';
import path from 'node:path';

const baseUrl = required('BASE_URL').replace(/\/$/, '');
const mongoUri = required('MONGODB_URI');
const rampSeconds = positiveNumber('LOAD_RAMP_SECONDS', 30);
const warmupSeconds = positiveNumber('LOAD_WARMUP_SECONDS', 30, true);
const measureSeconds = positiveNumber('LOAD_MEASURE_SECONDS', 180);
const repetitions = positiveNumber('LOAD_REPETITIONS', 3);
const scenarios = [
  { id: 'Scenario-01', concurrency: 10, existing: 10000, writePct: 70, findPct: 20, listPct: 10 },
  { id: 'Scenario-02', concurrency: 15, existing: 10000, writePct: 80, findPct: 15, listPct: 5 },
  { id: 'Scenario-03', concurrency: 20, existing: 10000, writePct: 80, findPct: 15, listPct: 5 },
  { id: 'Scenario-04', concurrency: 20, existing: 15000, writePct: 80, findPct: 15, listPct: 5 },
  { id: 'Scenario-05', concurrency: 20, existing: 15000, writePct: 20, findPct: 70, listPct: 10 },
];
const runId = `${new Date().toISOString().replace(/[:.]/g, '-')}-${randomUUID().slice(0, 8)}`;
const createdIds = new Set();
const resultDir = path.resolve('results');
const apiCallsCsvPath = path.join(resultDir, `api-calls-${runId}.csv`);
const credentialsPath = path.resolve(required('AUTH0_USERS_FILE'));
const runStartedAt = new Date();
let mongo;
let users;
const userTokens = [];
const baselineRuns = [];
const cleanup = { baselineDeleted: 0, apiCreatedDeleted: 0, finalRecordCount: null, error: '' };
const summaries = [];
const operationSummaryRows = [];
const metricSnapshots = [];
const operationMetricSnapshots = [];
let apiCallsStream;
let initialRecordCount = null;
let databaseName = '';
let runError = '';
let activeBaselineEmailPrefix = '';
let activeApiEmailPrefix = '';
let apiCallsStreamError = null;

function required(key) {
  const value = process.env[key];
  if (!value) throw new Error(`Missing ${key} in load-tests/.env`);
  return value;
}

function positiveNumber(key, fallback, allowZero = false) {
  const raw = process.env[key];
  const value = raw === undefined || raw === '' ? fallback : Number(raw);
  if (!Number.isInteger(value) || (allowZero ? value < 0 : value <= 0)) {
    throw new Error(`${key} must be a ${allowZero ? 'non-negative' : 'positive'} integer.`);
  }
  return value;
}

const delay = milliseconds => new Promise(resolve => setTimeout(resolve, milliseconds));

async function getToken(user) {
  const issuer = required('AUTH0_ISSUER_URI').replace(/\/$/, '');
  const body = new URLSearchParams({
    grant_type: 'http://auth0.com/oauth/grant-type/password-realm',
    client_id: required('AUTH0_CLIENT_ID'),
    client_secret: required('AUTH0_CLIENT_SECRET'),
    audience: required('AUTH0_AUDIENCE'),
    realm: required('AUTH0_CONNECTION'),
    username: user.email,
    password: user.password,
  });
  if (process.env.AUTH0_SCOPE) body.set('scope', process.env.AUTH0_SCOPE);
  const response = await fetch(`${issuer}/oauth/token`, {
    method: 'POST', headers: { 'content-type': 'application/x-www-form-urlencoded' }, body,
  });
  if (!response.ok) throw new Error(`OAuth token request failed (${response.status}): ${await response.text()}`);
  const token = await response.json();
  return { accessToken: token.access_token, expiresAt: Date.now() + token.expires_in * 1000 };
}

async function loadUsers() {
  const parsed = JSON.parse(await readFile(credentialsPath, 'utf8'));
  if (!Array.isArray(parsed) || parsed.some(user => !user?.email || !user?.password)) {
    throw new Error(`Expected an array of { email, password } users in ${credentialsPath}.`);
  }
  const requiredCount = Math.max(...scenarios.map(scenario => scenario.concurrency));
  if (parsed.length < requiredCount) {
    throw new Error(`Need at least ${requiredCount} Auth0 test users for the highest-concurrency scenario; found ${parsed.length}.`);
  }
  users = parsed;
}

async function tokensForScenario(count) {
  const tokens = [];
  for (const [index, user] of users.slice(0, count).entries()) {
    let tokenState = userTokens[index];
    if (!tokenState || tokenState.expiresAt < Date.now() + 30000) {
      tokenState = await getToken(user);
      userTokens[index] = tokenState;
    }
    tokens.push(tokenState.accessToken);
  }
  return tokens;
}

async function api(url, accessToken, options = {}) {
  return fetch(`${baseUrl}${url}`, {
    ...options,
    headers: { authorization: `Bearer ${accessToken}`, ...(options.body ? { 'content-type': 'application/json' } : {}), ...options.headers },
  });
}

async function getExistingIds(requiredCount, accessToken) {
  const ids = [];
  let cursor;
  while (ids.length < requiredCount) {
    const query = cursor ? `?afterName=${encodeURIComponent(cursor.name)}&afterEmail=${encodeURIComponent(cursor.email)}` : '';
    const response = await api(`/rest/members${query}`, accessToken);
    if (!response.ok) throw new Error(`Could not load existing members (${response.status}): ${await response.text()}`);
    const page = await response.json();
    ids.push(...page.members.map(member => member.id));
    if (!page.hasNext || !page.nextCursor) break;
    cursor = page.nextCursor;
  }
  if (ids.length < requiredCount) throw new Error(`Expected ${requiredCount} readable members after baseline setup; API returned ${ids.length}. Check API visibility and pagination.`);
  return ids.slice(0, requiredCount);
}

async function ensureBaselineRecords(requiredCount, emailPrefix) {
  const collection = mongo.db().collection('members');
  const started = performance.now();
  const beforeCount = await collection.countDocuments();
  if (beforeCount > requiredCount) {
    throw new Error(`Scenario requires exactly ${requiredCount} baseline records, but the test database already has ${beforeCount}. Use a dedicated test database with no more than the scenario baseline.`);
  }
  const shortfall = Math.max(0, requiredCount - beforeCount);
  console.log(`Baseline estimate: ${beforeCount} existing + ${shortfall} generated = ${beforeCount + shortfall} required records.`);
  for (let offset = 0; offset < shortfall; offset += 1000) {
    const batchSize = Math.min(1000, shortfall - offset);
    const records = Array.from({ length: batchSize }, (_, batchIndex) => {
      const sequence = offset + batchIndex;
      return {
        name: `Load Baseline ${String(sequence).padStart(6, '0')}`,
        email: `${emailPrefix}${sequence}@example.invalid`,
        phoneNumber: '2125550199',
      };
    });
    await collection.insertMany(records, { ordered: true });
  }
  const afterCount = await collection.countDocuments();
  if (afterCount < requiredCount) {
    throw new Error(`Baseline setup failed verification: found ${afterCount}; need ${requiredCount}.`);
  }
  console.log(`Baseline verification passed: ${afterCount} member records are available.`);
  return {
    recordsBefore: beforeCount,
    requiredRecords: requiredCount,
    recordsGenerated: shortfall,
    recordsAfter: afterCount,
    setupDurationMs: +(performance.now() - started).toFixed(2),
  };
}

async function actuatorSnapshot(accessToken) {
  const names = [
    'http.server.requests',
    'mongodb.driver.commands',
    'mongodb.driver.pool.checkedout',
    'mongodb.driver.pool.size',
    'mongodb.driver.pool.waitqueuesize',
    'mongodb.driver.pool.checkoutfailed',
    'process.cpu.usage',
    'process.cpu.time',
    'jvm.memory.used',
    'jvm.memory.committed',
    'jvm.memory.max',
    'jvm.threads.live',
    'system.cpu.usage',
  ];
  const data = {};
  for (const name of names) {
    try {
      const response = await api(`/actuator/metrics/${name}`, accessToken);
      data[name] = response.ok ? await response.json() : { error: `HTTP ${response.status}` };
    } catch (error) { data[name] = { error: error.message }; }
  }
  return data;
}

async function captureActuator(scope, moment, accessToken) {
  const snapshot = await actuatorSnapshot(accessToken);
  metricSnapshots.push({ scope, moment, snapshot });
  return snapshot;
}

async function captureOperationMetrics(scope, moment, accessToken) {
  const operations = [
    { operation: 'write', method: 'POST', uri: '/rest/members' },
    { operation: 'find', method: 'GET', uri: '/rest/members/{id}' },
    { operation: 'list', method: 'GET', uri: '/rest/members' },
  ];
  for (const operation of operations) {
    try {
      const tags = new URLSearchParams();
      tags.append('tag', `method:${operation.method}`);
      tags.append('tag', `uri:${operation.uri}`);
      const response = await api(`/actuator/metrics/http.server.requests?${tags}`, accessToken);
      operationMetricSnapshots.push({
        scope,
        moment,
        ...operation,
        metric: response.ok ? await response.json() : { error: `HTTP ${response.status}` },
      });
    } catch (error) {
      operationMetricSnapshots.push({ scope, moment, ...operation, metric: { error: error.message } });
    }
  }
}

function runnerResourceSnapshot() {
  const memory = process.memoryUsage();
  return {
    rssBytes: memory.rss,
    heapUsedBytes: memory.heapUsed,
    heapTotalBytes: memory.heapTotal,
    externalBytes: memory.external,
    cpu: process.cpuUsage(),
  };
}

function csvCell(value) {
  const text = value == null ? '' : String(value);
  return /[",\r\n]/.test(text) ? `"${text.replaceAll('"', '""')}"` : text;
}

async function writeApiCallRows(rows) {
  if (apiCallsStreamError) throw apiCallsStreamError;
  const timestamp = new Date().toISOString();
  for (const row of rows) {
    const values = [
      runId, timestamp, row.scenario, row.repetition, row.worker, row.operation,
      row.method, row.route, row.status, row.latencyMs, row.responseBytes,
      row.recordsReturned, row.success, row.error,
    ];
    if (!apiCallsStream.write(`${values.map(csvCell).join(',')}\r\n`)) {
      await new Promise((resolve, reject) => {
        const onDrain = () => {
          apiCallsStream.removeListener('error', onError);
          resolve();
        };
        const onError = error => {
          apiCallsStream.removeListener('drain', onDrain);
          reject(error);
        };
        apiCallsStream.once('drain', onDrain);
        apiCallsStream.once('error', onError);
      });
    }
    if (apiCallsStreamError) throw apiCallsStreamError;
  }
}

async function runScenario(scenario, repetition) {
  const scope = `${scenario.id} / Rep-${repetition}`;
  activeBaselineEmailPrefix = `load-baseline-${runId}-${scenario.id.toLowerCase()}-rep-${repetition}-`;
  activeApiEmailPrefix = `load-${runId}-${scenario.id.toLowerCase()}-${repetition}-`;
  createdIds.clear();
  let baselineInfo;
  let results = [];
  try {
    const scenarioTokens = await tokensForScenario(scenario.concurrency);
    await captureActuator(scope, 'Before baseline setup', scenarioTokens[0]);
    baselineInfo = await ensureBaselineRecords(scenario.existing, activeBaselineEmailPrefix);
    baselineRuns.push({ scenario: scope, ...baselineInfo });
    await captureActuator(scope, 'After baseline setup', scenarioTokens[0]);
    const readIds = await getExistingIds(scenario.existing, scenarioTokens[0]);
    await captureActuator(scope, 'Before', scenarioTokens[0]);
    await captureOperationMetrics(scope, 'Before', scenarioTokens[0]);

    const runnerBefore = runnerResourceSnapshot();
    const ticketCounts = { write: scenario.writePct, find: scenario.findPct, list: scenario.listPct };
    const schedule = Object.entries(ticketCounts).flatMap(([operation, count]) => Array(count).fill(operation));
    for (let index = schedule.length - 1; index > 0; index--) {
      const swapIndex = Math.floor(Math.random() * (index + 1));
      [schedule[index], schedule[swapIndex]] = [schedule[swapIndex], schedule[index]];
    }
    let nextSequence = 0;
    let requestIndex = 0;
    let active = true;

    async function makeRequest(worker, deadline = Infinity) {
      const tokenState = userTokens[worker];
      let accessToken = tokenState?.accessToken;
      if (!tokenState || tokenState.expiresAt < Date.now() + 30000) {
        const refreshed = await getToken(users[worker]);
        userTokens[worker] = refreshed;
        accessToken = refreshed.accessToken;
      }
      if (!active || performance.now() >= deadline) return null;
      const sequence = requestIndex++;
      const operation = schedule[nextSequence++ % schedule.length];
      const begun = performance.now();
      let status = 0;
      let error = '';
      let method = 'GET';
      let route = '/rest/members';
      let responseBytes = 0;
      let recordsReturned = 0;
      try {
        let response;
        if (operation === 'write') {
          method = 'POST';
          response = await api(route, accessToken, { method, body: JSON.stringify({ name: 'Load Test Member', email: `${activeApiEmailPrefix}${sequence}@example.invalid`, phoneNumber: '2125550199' }) });
        } else if (operation === 'find') {
          route = '/rest/members/{id}';
          const id = readIds[sequence % readIds.length];
          response = await api(`/rest/members/${encodeURIComponent(id)}`, accessToken, { method });
        } else {
          response = await api(route, accessToken, { method });
        }
        status = response.status;
        const body = await response.text();
        responseBytes = Buffer.byteLength(body);
        if (response.ok) {
          if (operation === 'write') {
            const member = JSON.parse(body);
            if (!member.id) throw new Error('Create response did not contain a member ID.');
            createdIds.add(member.id);
            recordsReturned = 1;
          } else if (operation === 'find') {
            await JSON.parse(body);
            recordsReturned = 1;
          } else {
            const page = JSON.parse(body);
            if (!Array.isArray(page.members)) throw new Error('Member list response did not contain a members array.');
            recordsReturned = page.members.length;
          }
        } else error = body;
      } catch (cause) { error = cause.message; }
      return { scenario: scope, repetition, worker: worker + 1, operation, method, route, status, latencyMs: +(performance.now() - begun).toFixed(2), responseBytes, recordsReturned, success: status >= 200 && status < 300 && !error, error };
    }

    async function userLoop(worker, startDelay = 0, deadline = Infinity, record = false) {
      if (startDelay) await delay(startDelay);
      while (active && performance.now() < deadline) {
        const row = await makeRequest(worker, deadline);
        if (!row) break;
        if (record) results.push(row);
      }
    }

    const rampWorkers = Array.from({ length: scenario.concurrency }, (_, worker) =>
      userLoop(worker, Math.floor((rampSeconds * 1000 * worker) / scenario.concurrency)));
    await delay(rampSeconds * 1000);
    active = false;
    await Promise.all(rampWorkers);

    if (warmupSeconds > 0) {
      active = true;
      const warmupDeadline = performance.now() + warmupSeconds * 1000;
      await Promise.all(Array.from({ length: scenario.concurrency }, (_, worker) => userLoop(worker, 0, warmupDeadline)));
    }

    active = true;
    results = [];
    const startedAt = new Date();
    const measuredStart = performance.now();
    const measuredDeadline = measuredStart + measureSeconds * 1000;
    const measuredWorkers = Array.from({ length: scenario.concurrency }, (_, worker) => userLoop(worker, 0, measuredDeadline, true));
    await delay(measureSeconds * 1000);
    const measuredEnd = performance.now();
    active = false;
    await Promise.all(measuredWorkers); // Drain requests that started inside the window.
    const endedAt = new Date();
    const runnerAfter = runnerResourceSnapshot();
    const cpuDelta = process.cpuUsage(runnerBefore.cpu);
    await writeApiCallRows(results);
    await captureActuator(scope, 'After', scenarioTokens[0]);
    await captureOperationMetrics(scope, 'After', scenarioTokens[0]);

    const latencies = results.map(row => row.latencyMs).sort((a, b) => a - b);
    const percentile = (values, p) => values[Math.min(values.length - 1, Math.ceil(p * values.length) - 1)] ?? 0;
    const operationStats = {};
    for (const operation of ['write', 'find', 'list']) {
      const rows = results.filter(row => row.operation === operation);
      const values = rows.map(row => row.latencyMs).sort((a, b) => a - b);
      operationStats[operation] = {
        calls: rows.length,
        successful: rows.filter(row => row.success).length,
        failed: rows.filter(row => !row.success).length,
        callsPerSecond: +(rows.length / measureSeconds).toFixed(2),
        method: rows[0]?.method ?? '',
        route: rows[0]?.route ?? '',
        responseBytes: rows.reduce((sum, row) => sum + row.responseBytes, 0),
        recordsReturned: rows.reduce((sum, row) => sum + row.recordsReturned, 0),
        averageLatencyMs: rows.length ? +(values.reduce((sum, value) => sum + value, 0) / rows.length).toFixed(2) : 0,
        p50LatencyMs: +percentile(values, .5).toFixed(2),
        p95LatencyMs: +percentile(values, .95).toFixed(2),
        p99LatencyMs: +percentile(values, .99).toFixed(2),
      };
      operationSummaryRows.push({
        scenario: scope,
        repetition,
        operation,
        method: operationStats[operation].method,
        route: operationStats[operation].route,
        calls: operationStats[operation].calls,
        successful: operationStats[operation].successful,
        failed: operationStats[operation].failed,
        actualSharePercent: results.length ? +(rows.length / results.length * 100).toFixed(2) : 0,
        recordsReturned: operationStats[operation].recordsReturned,
        responseBytes: operationStats[operation].responseBytes,
        averageLatencyMs: operationStats[operation].averageLatencyMs,
        p50LatencyMs: operationStats[operation].p50LatencyMs,
        p95LatencyMs: operationStats[operation].p95LatencyMs,
        p99LatencyMs: operationStats[operation].p99LatencyMs,
      });
    }
    const summary = {
      scenario: scope, repetition, concurrency: scenario.concurrency, existingRecords: scenario.existing,
      writePercent: scenario.writePct, findPercent: scenario.findPct, listPercent: scenario.listPct,
      baselineGenerated: baselineInfo.recordsGenerated, baselineVerified: baselineInfo.recordsAfter,
      rampSeconds, warmupSeconds, measureSeconds,
      writeCalls: operationStats.write.calls, findCalls: operationStats.find.calls, listCalls: operationStats.list.calls,
      actualWritePercent: +(operationStats.write.calls / results.length * 100).toFixed(2),
      actualFindPercent: +(operationStats.find.calls / results.length * 100).toFixed(2),
      actualListPercent: +(operationStats.list.calls / results.length * 100).toFixed(2),
      totalCalls: results.length, successful: results.filter(row => row.success).length,
      failed: results.filter(row => !row.success).length, elapsedMs: measureSeconds * 1000,
      throughputPerSecond: +(results.length / measureSeconds).toFixed(2),
      averageLatencyMs: latencies.length ? +(latencies.reduce((sum, value) => sum + value, 0) / latencies.length).toFixed(2) : 0,
      p95LatencyMs: +percentile(latencies, .95).toFixed(2),
      writeAverageLatencyMs: operationStats.write.averageLatencyMs, writeP95LatencyMs: operationStats.write.p95LatencyMs,
      findAverageLatencyMs: operationStats.find.averageLatencyMs, findP95LatencyMs: operationStats.find.p95LatencyMs,
      listAverageLatencyMs: operationStats.list.averageLatencyMs, listP95LatencyMs: operationStats.list.p95LatencyMs,
      writeCallsPerSecond: operationStats.write.callsPerSecond, findCallsPerSecond: operationStats.find.callsPerSecond, listCallsPerSecond: operationStats.list.callsPerSecond,
      startedAt, endedAt, runnerBefore, runnerAfter,
      runnerCpuUserMs: +(cpuDelta.user / 1000).toFixed(2), runnerCpuSystemMs: +(cpuDelta.system / 1000).toFixed(2),
      runnerRssDeltaBytes: runnerAfter.rssBytes - runnerBefore.rssBytes,
      runnerHeapUsedDeltaBytes: runnerAfter.heapUsedBytes - runnerBefore.heapUsedBytes,
    };
    return summary;
  } finally {
    const collection = mongo.db().collection('members');
    const baselineResult = await collection.deleteMany({ email: { $regex: `^${activeBaselineEmailPrefix}` } });
    cleanup.baselineDeleted += baselineResult.deletedCount;
    if (createdIds.size) {
      const result = await collection.deleteMany({ _id: { $in: [...createdIds].map(id => new ObjectId(id)) } });
      cleanup.apiCreatedDeleted += result.deletedCount;
    }
    const taggedWrites = await collection.deleteMany({ email: { $regex: `^${activeApiEmailPrefix}` } });
    cleanup.apiCreatedDeleted += taggedWrites.deletedCount;
    createdIds.clear();
    const afterCleanup = await collection.countDocuments();
    if (afterCleanup !== initialRecordCount) {
      throw new Error(`${scope} cleanup left ${afterCleanup} records; database had ${initialRecordCount} before the run.`);
    }
    cleanup.finalRecordCount = afterCleanup;
    console.log(`${scope} cleanup verified: database restored to ${afterCleanup} records.`);
  }
}

async function writeWorkbook() {
  const workbook = new ExcelJS.Workbook();
  workbook.creator = 'Kitchen Sink Load Test';
  const summary = workbook.addWorksheet('Scenario Summary');
  summary.columns = [
    { header: 'Scenario', key: 'scenario', width: 18 },
    { header: 'Repetition', key: 'repetition', width: 12 },
    { header: 'Concurrency', key: 'concurrency', width: 14 },
    { header: 'Existing records target', key: 'existingRecords', width: 22 },
    { header: 'Baseline generated', key: 'baselineGenerated', width: 18 },
    { header: 'Baseline verified', key: 'baselineVerified', width: 18 },
    { header: 'Ramp seconds', key: 'rampSeconds', width: 14 },
    { header: 'Warm-up seconds', key: 'warmupSeconds', width: 16 },
    { header: 'Measure seconds', key: 'measureSeconds', width: 16 },
    { header: 'Write calls', key: 'writeCalls', width: 14 },
    { header: 'Find calls', key: 'findCalls', width: 14 },
    { header: 'List calls', key: 'listCalls', width: 14 },
    { header: 'Actual write %', key: 'actualWritePercent', width: 16 },
    { header: 'Actual find %', key: 'actualFindPercent', width: 15 },
    { header: 'Actual list %', key: 'actualListPercent', width: 15 },
    { header: 'Total calls', key: 'totalCalls', width: 13 },
    { header: 'Success', key: 'successful', width: 12 },
    { header: 'Failed', key: 'failed', width: 10 },
    { header: 'API window ms', key: 'elapsedMs', width: 16 },
    { header: 'Calls/sec', key: 'throughputPerSecond', width: 13 },
    { header: 'Write calls/sec', key: 'writeCallsPerSecond', width: 16 },
    { header: 'Find calls/sec', key: 'findCallsPerSecond', width: 15 },
    { header: 'List calls/sec', key: 'listCallsPerSecond', width: 15 },
    { header: 'Avg latency ms', key: 'averageLatencyMs', width: 16 },
    { header: 'P50 ms', key: 'p50LatencyMs', width: 12 },
    { header: 'P95 ms', key: 'p95LatencyMs', width: 12 },
    { header: 'P99 ms', key: 'p99LatencyMs', width: 12 },
    { header: 'Write avg ms', key: 'writeAverageLatencyMs', width: 15 },
    { header: 'Write p95 ms', key: 'writeP95LatencyMs', width: 15 },
    { header: 'Find avg ms', key: 'findAverageLatencyMs', width: 15 },
    { header: 'Find p95 ms', key: 'findP95LatencyMs', width: 15 },
    { header: 'List avg ms', key: 'listAverageLatencyMs', width: 15 },
    { header: 'List p95 ms', key: 'listP95LatencyMs', width: 15 },
    { header: 'Started at', key: 'startedAt', width: 28 },
    { header: 'Ended at', key: 'endedAt', width: 28 },
    { header: 'Runner CPU user ms', key: 'runnerCpuUserMs', width: 20 },
    { header: 'Runner CPU system ms', key: 'runnerCpuSystemMs', width: 22 },
    { header: 'Runner RSS before bytes', key: 'runnerRssBeforeBytes', width: 24 },
    { header: 'Runner RSS after bytes', key: 'runnerRssAfterBytes', width: 23 },
    { header: 'Runner heap before bytes', key: 'runnerHeapBeforeBytes', width: 25 },
    { header: 'Runner heap after bytes', key: 'runnerHeapAfterBytes', width: 24 },
  ];
  summaries.forEach(item => summary.addRow({
    ...item,
    runnerRssBeforeBytes: item.runnerBefore.rssBytes,
    runnerRssAfterBytes: item.runnerAfter.rssBytes,
    runnerHeapBeforeBytes: item.runnerBefore.heapUsedBytes,
    runnerHeapAfterBytes: item.runnerAfter.heapUsedBytes,
  }));

  const overview = workbook.addWorksheet('Run Overview');
  overview.columns = [
    { header: 'Item', key: 'item', width: 36 },
    { header: 'Value', key: 'value', width: 110 },
  ];
  const requiredRecords = Math.max(...scenarios.map(scenario => scenario.existing));
  const overviewRows = [
    ['Run ID', runId],
    ['Status', runError || cleanup.error ? 'FAILED' : 'COMPLETED'],
    ['Started at (UTC)', runStartedAt.toISOString()],
    ['Finished at (UTC)', new Date().toISOString()],
    ['Application URL', `${new URL(baseUrl).origin}${new URL(baseUrl).pathname}`],
    ['MongoDB database', databaseName || '(not connected)'],
    ['Node.js', process.version],
    ['Host platform / architecture', `${os.type()} ${os.release()} / ${process.arch}`],
    ['Logical CPU count', os.cpus().length],
    ['Baseline count before setup', initialRecordCount ?? 'not measured'],
    ['Baseline target records', requiredRecords],
    ['Baseline records generated', baselineRuns.reduce((total, item) => total + item.recordsGenerated, 0)],
    ['Baseline setup per repetition', JSON.stringify(baselineRuns)],
    ['Baseline load method', 'Each scenario repetition starts from exactly its configured record count; direct MongoDB insertMany in batches up to 1,000; outside measurement timing'],
    ['Baseline records deleted', cleanup.baselineDeleted],
    ['API-created records deleted (IDs plus tagged fallback)', cleanup.apiCreatedDeleted],
    ['Final database record count', cleanup.finalRecordCount ?? 'not measured'],
    ['Full request-level results CSV', apiCallsCsvPath],
    ['Cleanup error', cleanup.error || 'none'],
    ['Run error', runError || 'none'],
    ['Scenario model', `${repetitions} repetitions per scenario; ${rampSeconds}s ramp-up, ${warmupSeconds}s warm-up, ${measureSeconds}s measurement; each virtual user has at most one API call in flight.`],
    ['Operation mix', 'Each request uses a shuffled 100-request schedule matching the configured write/find/list percentages. Actual request counts are listed per repetition.'],
    ['API operation meanings', 'Write = POST /rest/members; find = GET /rest/members/{id}; list = GET /rest/members, which returns one cursor-paginated page (up to 10 members), not the full collection in one response.'],
    ['Operation-level Actuator metrics', 'http.server.requests is filtered by method and route before/after each scenario. Counts and total-time deltas isolate write/find/list routes; unrelated concurrent app traffic may still be included.'],
    ['Scenario timing includes', 'Fixed measurement window only; requests started before the deadline are drained and included in latency samples. Throughput divides completed in-window calls by the configured measurement seconds.'],
    ['Scenario timing excludes', 'Ramp-up, warm-up, OAuth token acquisition, per-repetition data setup, API pagination to collect read IDs, before/after Actuator snapshots, and cleanup'],
    ['Request latency method', 'Client-side performance.now around fetch and response handling; includes network, server processing, and response body parsing'],
    ['Throughput method', 'Completed requests started within the fixed measurement window divided by configured measurement seconds'],
    ['Percentile method', 'Nearest-rank percentile. Operation Summary reports average and p95 independently for write, find, and list.'],
    ['Warm-up / repetition', `${warmupSeconds}s warm-up before each ${measureSeconds}s measurement; each scenario repeats ${repetitions} times in listed order. Compare repetitions to assess variability.`],
    ['Actuator snapshot caveat', 'Snapshots are outside timed API windows. HTTP and MongoDB counters are cumulative; interval deltas include the snapshot requests themselves. Gauges are point-in-time, not peaks.'],
    ['MongoDB pool interpretation', 'checkedout, size, and waitqueuesize are point-in-time gauges before/after each scenario; checkoutfailed is a cumulative counter delta. No polling is used to avoid adding load, so peak concurrent connections are not captured.'],
    ['Application resource metrics', 'Captured from protected Spring Boot Actuator endpoints when available; missing metrics are retained as HTTP errors in Actuator Metrics. CPU usage is a point-in-time fraction; JVM memory is bytes.'],
    ['Load-runner resource metrics', 'Node process CPU and memory snapshots before/after each timed scenario; these describe the load generator, not the Spring application.'],
    ['Monitoring overhead', 'Actuator snapshots add untimed requests to the app and appear in cumulative HTTP metrics; they are not included in scenario API call counts.'],
    ['Cleanup safety', 'After each repetition only its uniquely prefixed baseline records and IDs returned by successful create calls are deleted; record count is verified back to the pre-run count.'],
  ];
  overviewRows.forEach(([item, value]) => overview.addRow({ item, value }));

  const calls = workbook.addWorksheet('API Calls');
  calls.columns = [{ header: 'Full request-level results', key: 'details', width: 110 }];
  calls.addRow({ details: `Complete per-request measurements are in ${path.basename(apiCallsCsvPath)} in the same results folder.` });

  const operationSummary = workbook.addWorksheet('Operation Summary');
  operationSummary.columns = [
    { header: 'Scenario', key: 'scenario', width: 18 },
    { header: 'Repetition', key: 'repetition', width: 12 },
    { header: 'Operation', key: 'operation', width: 16 },
    { header: 'Method', key: 'method', width: 10 },
    { header: 'Route', key: 'route', width: 28 },
    { header: 'Calls', key: 'calls', width: 10 },
    { header: 'Success', key: 'successful', width: 12 },
    { header: 'Failed', key: 'failed', width: 10 },
    { header: 'Actual share %', key: 'actualSharePercent', width: 16 },
    { header: 'Records returned', key: 'recordsReturned', width: 18 },
    { header: 'Response bytes', key: 'responseBytes', width: 16 },
    { header: 'Avg latency ms', key: 'averageLatencyMs', width: 16 },
    { header: 'P50 ms', key: 'p50LatencyMs', width: 12 },
    { header: 'P95 ms', key: 'p95LatencyMs', width: 12 },
    { header: 'P99 ms', key: 'p99LatencyMs', width: 12 },
  ];
  operationSummaryRows.forEach(row => operationSummary.addRow(row));

  const operationMetrics = workbook.addWorksheet('API Type Metrics');
  operationMetrics.columns = [
    { header: 'Scenario', key: 'scenario', width: 18 },
    { header: 'Operation', key: 'operation', width: 16 },
    { header: 'Method', key: 'method', width: 10 },
    { header: 'Route', key: 'route', width: 28 },
    { header: 'Statistic', key: 'statistic', width: 16 },
    { header: 'Before cumulative', key: 'before', width: 20 },
    { header: 'After cumulative', key: 'after', width: 20 },
    { header: 'Interval delta', key: 'delta', width: 18 },
    { header: 'Unit', key: 'unit', width: 14 },
    { header: 'Interpretation', key: 'note', width: 72 },
  ];
  const operationMetricsByKey = new Map();
  for (const entry of operationMetricSnapshots) {
    const key = `${entry.scope}|${entry.operation}`;
    if (!operationMetricsByKey.has(key)) operationMetricsByKey.set(key, { operation: entry.operation, method: entry.method, uri: entry.uri, moments: {} });
    operationMetricsByKey.get(key).moments[entry.moment] = entry.metric;
  }
  for (const [key, entry] of operationMetricsByKey) {
    const scope = key.split('|')[0];
    const before = entry.moments.Before;
    const after = entry.moments.After;
    if (before?.error || after?.error) {
      operationMetrics.addRow({
        scenario: scope, operation: entry.operation, method: entry.method, route: entry.uri,
        statistic: 'ERROR', before: before?.error || '', after: after?.error || '',
        note: 'Route-filtered Actuator metric unavailable; per-request results remain in API Calls.',
      });
      continue;
    }
    const beforeMeasurements = new Map((before?.measurements || []).map(item => [item.statistic, item.value]));
    const afterMeasurements = new Map((after?.measurements || []).map(item => [item.statistic, item.value]));
    for (const statistic of new Set([...beforeMeasurements.keys(), ...afterMeasurements.keys()])) {
      const beforeValue = beforeMeasurements.get(statistic);
      const afterValue = afterMeasurements.get(statistic);
      const delta = Number.isFinite(beforeValue) && Number.isFinite(afterValue) && ['COUNT', 'TOTAL_TIME'].includes(statistic)
        ? +(afterValue - beforeValue).toFixed(6)
        : '';
      operationMetrics.addRow({
        scenario: scope,
        operation: entry.operation,
        method: entry.method,
        route: entry.uri,
        statistic,
        before: beforeValue ?? '',
        after: afterValue ?? '',
        delta,
        unit: after?.baseUnit || before?.baseUnit || '',
        note: statistic === 'COUNT' || statistic === 'TOTAL_TIME'
          ? 'Filtered by HTTP method and templated route. Delta is application-wide during the interval and can include unrelated traffic.'
          : 'MAX is the application metric maximum, not a scenario-only or per-request maximum; use Operation Summary for this run’s samples.',
      });
    }
  }

  const ops = workbook.addWorksheet('Actuator Metrics');
  ops.columns = [
    { header: 'Scope', key: 'scope', width: 22 },
    { header: 'Metric', key: 'metric', width: 38 },
    { header: 'Statistic', key: 'statistic', width: 16 },
    { header: 'Before', key: 'before', width: 18 },
    { header: 'After', key: 'after', width: 18 },
    { header: 'Delta', key: 'delta', width: 18 },
    { header: 'Unit', key: 'unit', width: 14 },
    { header: 'Available tag dimensions', key: 'tags', width: 55 },
    { header: 'Interpretation', key: 'note', width: 72 },
  ];
  const snapshotsByScope = new Map();
  for (const entry of metricSnapshots) {
    if (!snapshotsByScope.has(entry.scope)) snapshotsByScope.set(entry.scope, {});
    snapshotsByScope.get(entry.scope)[entry.moment] = entry.snapshot;
  }
  for (const [scope, moments] of snapshotsByScope) {
    const metricNames = new Set([
      ...Object.keys(moments.Before || {}),
      ...Object.keys(moments.After || {}),
    ]);
    for (const metricName of metricNames) {
      const beforeMetric = moments.Before?.[metricName];
      const afterMetric = moments.After?.[metricName];
      if (beforeMetric?.error || afterMetric?.error) {
        ops.addRow({
          scope, metric: metricName, statistic: 'ERROR',
          before: beforeMetric?.error || '',
          after: afterMetric?.error || '',
          note: 'Metric endpoint unavailable or returned an error.',
        });
        continue;
      }
      const beforeMeasurements = new Map((beforeMetric?.measurements || []).map(item => [item.statistic, item.value]));
      const afterMeasurements = new Map((afterMetric?.measurements || []).map(item => [item.statistic, item.value]));
      for (const statistic of new Set([...beforeMeasurements.keys(), ...afterMeasurements.keys()])) {
        const beforeValue = beforeMeasurements.get(statistic);
        const afterValue = afterMeasurements.get(statistic);
        const isCounter = ['COUNT', 'TOTAL_TIME'].includes(statistic);
        const delta = Number.isFinite(beforeValue) && Number.isFinite(afterValue) && (isCounter || statistic === 'VALUE')
          ? +(afterValue - beforeValue).toFixed(6)
          : '';
        const isPoolGauge = metricName.startsWith('mongodb.driver.pool.') && statistic === 'VALUE';
        ops.addRow({
          scope,
          metric: metricName,
          statistic,
          before: beforeValue ?? '',
          after: afterValue ?? '',
          delta,
          unit: afterMetric?.baseUnit || beforeMetric?.baseUnit || '',
          tags: JSON.stringify(afterMetric?.availableTags || beforeMetric?.availableTags || []),
          note: isCounter
            ? 'Cumulative counter/time; delta spans the interval and includes metric-snapshot HTTP traffic.'
            : isPoolGauge
              ? 'Point-in-time pool gauge; delta is only the difference between snapshots, not peak usage.'
              : statistic === 'MAX'
                ? 'Maximum observed by the application metric since its reset; not a scenario-only maximum.'
                : 'Point-in-time or non-additive measurement; compare snapshots cautiously.',
        });
      }
    }
  }

  const resources = workbook.addWorksheet('Runner Resources');
  resources.columns = [
    { header: 'Scenario', key: 'scenario', width: 18 },
    { header: 'Measurement', key: 'measurement', width: 25 },
    { header: 'Before', key: 'before', width: 20 },
    { header: 'After', key: 'after', width: 20 },
    { header: 'Delta', key: 'delta', width: 20 },
    { header: 'Unit / note', key: 'note', width: 75 },
  ];
  for (const item of summaries) {
    for (const [measurement, beforeKey, afterKey, note] of [
      ['RSS memory', 'rssBytes', 'rssBytes', 'Load-runner process resident memory; bytes.'],
      ['Heap used', 'heapUsedBytes', 'heapUsedBytes', 'Load-runner process V8 heap used; bytes.'],
      ['Heap allocated', 'heapTotalBytes', 'heapTotalBytes', 'Load-runner process V8 heap total; bytes.'],
      ['External memory', 'externalBytes', 'externalBytes', 'Load-runner process external memory; bytes.'],
    ]) {
      resources.addRow({
        scenario: item.scenario, measurement,
        before: item.runnerBefore[beforeKey], after: item.runnerAfter[afterKey],
        delta: item.runnerAfter[afterKey] - item.runnerBefore[beforeKey],
        note,
      });
    }
    resources.addRow({ scenario: item.scenario, measurement: 'CPU user time', before: '', after: item.runnerCpuUserMs, delta: item.runnerCpuUserMs, note: 'Load-runner process CPU consumed during timed API window; milliseconds.' });
    resources.addRow({ scenario: item.scenario, measurement: 'CPU system time', before: '', after: item.runnerCpuSystemMs, delta: item.runnerCpuSystemMs, note: 'Load-runner process CPU consumed during timed API window; milliseconds.' });
  }

  for (const sheet of workbook.worksheets) {
    sheet.getRow(1).font = { bold: true, color: { argb: 'FFFFFFFF' } };
    sheet.getRow(1).fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: 'FF2F5597' } };
    sheet.views = [{ state: 'frozen', ySplit: 1 }];
    if (sheet.columnCount) {
      let column = sheet.columnCount;
      let address = '';
      while (column > 0) {
        const remainder = (column - 1) % 26;
        address = String.fromCharCode(65 + remainder) + address;
        column = Math.floor((column - 1) / 26);
      }
      sheet.autoFilter = { from: 'A1', to: `${address}1` };
    }
  }
  await mkdir(resultDir, { recursive: true });
  const filename = path.join(resultDir, `load-test-${runId}.xlsx`);
  await workbook.xlsx.writeFile(filename);
  return filename;
}

try {
  await mkdir(resultDir, { recursive: true });
  apiCallsStream = createWriteStream(apiCallsCsvPath, { encoding: 'utf8' });
  await new Promise((resolve, reject) => {
    apiCallsStream.once('open', resolve);
    apiCallsStream.once('error', reject);
  });
  apiCallsStream.on('error', error => { apiCallsStreamError = error; });
  apiCallsStream.write('Run ID,Timestamp,Scenario,Repetition,Worker,Operation,Method,Route,HTTP status,Latency ms,Response bytes,Records returned,Success,Error\r\n');
  await loadUsers();
  await tokensForScenario(Math.max(...scenarios.map(scenario => scenario.concurrency)));
  console.log(`Authenticated ${users.length} user credentials; per-user OAuth requests are outside measured API timings.`);
  mongo = new MongoClient(mongoUri);
  await mongo.connect();
  databaseName = mongo.db().databaseName;
  initialRecordCount = await mongo.db().collection('members').countDocuments();
  console.log(`Database records before test: ${initialRecordCount}`);
  for (const scenario of scenarios) {
    for (let repetition = 1; repetition <= repetitions; repetition++) {
      console.log(`Running ${scenario.id} repetition ${repetition}/${repetitions}: ${scenario.concurrency} users, ${scenario.existing} records, ${rampSeconds}s ramp, ${warmupSeconds}s warm-up, ${measureSeconds}s measurement.`);
      const result = await runScenario(scenario, repetition);
      summaries.push(result);
      console.log(`${result.scenario}: ${result.totalCalls} calls (${result.successful} successful, ${result.failed} failed), ${result.throughputPerSecond} calls/sec; write p95 ${result.writeP95LatencyMs} ms, find p95 ${result.findP95LatencyMs} ms, list p95 ${result.listP95LatencyMs} ms.`);
    }
  }
  console.table(summaries.map(({ scenario, concurrency, totalCalls, successful, failed, throughputPerSecond, writeAverageLatencyMs, writeP95LatencyMs, findAverageLatencyMs, findP95LatencyMs, listAverageLatencyMs, listP95LatencyMs }) => ({ scenario, concurrency, totalCalls, successful, failed, throughputPerSecond, writeAverageLatencyMs, writeP95LatencyMs, findAverageLatencyMs, findP95LatencyMs, listAverageLatencyMs, listP95LatencyMs })));
} catch (error) {
  runError = error.message;
  console.error(error);
  process.exitCode = 1;
} finally {
  if (apiCallsStream) {
    try {
      apiCallsStream.end();
      await finished(apiCallsStream);
    } catch (error) {
      runError = [runError, `API call CSV write failed: ${error.message}`].filter(Boolean).join(' ');
      console.error(runError);
      process.exitCode = 1;
    }
  }
  if (mongo) {
    try {
      if (activeBaselineEmailPrefix) {
        const baselineResult = await mongo.db().collection('members').deleteMany({ email: { $regex: `^${activeBaselineEmailPrefix}` } });
        cleanup.baselineDeleted += baselineResult.deletedCount;
        console.log(`Cleanup: deleted ${baselineResult.deletedCount} leftover baseline records.`);
      }
    } catch (error) {
      cleanup.error = `Baseline cleanup failed: ${error.message}`;
      console.error(cleanup.error);
      process.exitCode = 1;
    }
    try {
      if (createdIds.size) {
        const result = await mongo.db().collection('members').deleteMany({ _id: { $in: [...createdIds].map(id => new ObjectId(id)) } });
        cleanup.apiCreatedDeleted += result.deletedCount;
        console.log(`Cleanup: deleted ${result.deletedCount} test-created records.`);
      }
      if (activeApiEmailPrefix) {
        const taggedWrites = await mongo.db().collection('members').deleteMany({ email: { $regex: `^${activeApiEmailPrefix}` } });
        cleanup.apiCreatedDeleted += taggedWrites.deletedCount;
        console.log(`Cleanup: deleted ${taggedWrites.deletedCount} leftover tagged API records.`);
      }
    } catch (error) {
      cleanup.error = [cleanup.error, `API-created member cleanup failed: ${error.message}`].filter(Boolean).join(' ');
      console.error(cleanup.error);
      process.exitCode = 1;
    }
    try {
      cleanup.finalRecordCount = await mongo.db().collection('members').countDocuments();
      if (userTokens.length) await captureActuator('Run cleanup', 'After', userTokens[0].accessToken);
    } catch (error) {
      cleanup.error = [cleanup.error, `Final verification failed: ${error.message}`].filter(Boolean).join(' ');
      console.error(cleanup.error);
      process.exitCode = 1;
    }
    await mongo.close();
  }
  try {
    const workbook = await writeWorkbook();
    console.log(`Workbook: ${workbook}`);
  } catch (error) {
    console.error(`Could not write load-test workbook: ${error.message}`);
    process.exitCode = 1;
  }
}
