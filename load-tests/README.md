# API load test

This isolated Node.js project exercises the authenticated member API. It does not change application runtime code. Run it against a test database and a running application.

## OAuth and concurrency model

The runner authenticates each virtual user separately with Auth0's password-realm grant, explicitly selecting `Username-Password-Authentication`, then sends one user's bearer token per concurrent API request. Set `AUTH0_CONNECTION` to the exact Auth0 Database connection name. It acquires or refreshes tokens before timing a scenario, so Auth0 token-endpoint latency is not included in API measurements. This is a test-only flow; enable it only for controlled load testing and never use it for production user authentication. The application must have the imported Database connection enabled and be authorized to request the API; the requested audience must exactly match the API Identifier. A dedicated test application and tenant are recommended, but the same Regular Web Application can be used if configured for the password-realm grant and API access.

Generate 20 synthetic users (enough for the 20-worker scenarios) from the `load-tests` directory:

```powershell
npm run generate-users
```

This creates `users/auth0-import.json` for Auth0 bulk import and `users/credentials.json` for the runner. Import the first file into the intended Auth0 **Database connection** using Dashboard > User Management > Users > Import/Export Users > Import Users. The generated `email_verified` values are `false`; the accounts use MD4 hashes only because this is an isolated test tenant. MD4 is obsolete and must not be used for production accounts. The plaintext random passwords are only in `credentials.json`; both generated files are Git-ignored. Keep that credentials file private and do not upload it to Auth0.

The sample email addresses use `mail.com` as confirmed for this test. Set `AUTH0_TEST_EMAIL_DOMAIN` before generation to use a different domain you control. The generator does not overwrite existing output files; remove or move the ignored `users` directory before regenerating, and re-import the matching import file if you do.

The load-test runner has its own `load-tests/.env`, separate from the application-root `.env`. Copy `load-tests/.env.example` to `load-tests/.env` and set `AUTH0_ISSUER_URI`, the configured application's client ID/secret, API audience, connection name, and the test MongoDB URI. `AUTH0_USERS_FILE` defaults to `users/credentials.json`. The application must be started separately using its Regular Web Application credentials in the root `.env`; the runner uses the configured application's credentials for per-user password-realm token requests. Both `.env` files and generated workbooks are ignored by Git.

## Prepare data

Before every scenario repetition, the runner counts the `members` collection and prepares that scenario exact baseline (10,000 or 15,000). It inserts only the shortfall in batches of up to 1,000, verifies the exact count, and resolves read IDs through the authenticated paginated API. If the database already contains more records than that scenario target, the run stops rather than deleting user data. Use a dedicated test database whose initial count is no greater than the smallest scenario baseline.

After each repetition, including when a scenario fails, the runner removes that repetition generated baseline records and API-created records, then verifies the collection count has returned to its pre-run value. Cleanup uses unique per-repetition email prefixes and captured IDs as a fallback. Keep the application and load-test MongoDB URIs pointed at the same isolated test database. Do not interrupt the process before its `finally` cleanup runs.

## Install and run

From the project root, after configuring both `.env` files, starting the application, and importing the Auth0 users:

```powershell
Copy-Item load-tests/.env.example load-tests/.env
Set-Location load-tests
npm install
npm run generate-users
npm run load
```

`npm run generate-users` is a one-time Auth0 user-file setup step; import `users/auth0-import.json` into the configured Database connection. **`npm run load` runs every scenario three times by default. Each repetition prepares the scenario exact baseline, ramps users up, sends warm-up traffic, measures for a fixed window, and cleans its generated records before continuing.** Each virtual user has at most one API request in flight, continuously using its distinct Auth0 user token. Operations are write (`POST /rest/members`), find by ID (`GET /rest/members/{id}`), and list (`GET /rest/members`, one cursor-paginated page of up to 10 members). Each request follows a shuffled 100-request schedule using the scenario write/find/list percentages. Set `LOAD_RAMP_SECONDS`, `LOAD_WARMUP_SECONDS`, `LOAD_MEASURE_SECONDS`, and `LOAD_REPETITIONS` in `.env`; defaults are 30s, 30s, 180s, and 3 repetitions (about 12 minutes per scenario, plus setup and cleanup).

A workbook in `load-tests/results/` contains:

- **Scenario Summary:** scenario repetition, concurrency, exact verified baseline, actual write/find/list counts and percentages, fixed measurement duration, total and per-operation throughput, average and p95 latency for each API type, and load-runner CPU/memory readings.
- **Operation Summary:** per-scenario, per-route call counts, success/failure, actual share, returned record/response-byte totals, and average/p50/p95/p99 client-observed latency.
- **API Type Metrics:** before/after Actuator `http.server.requests` measurements filtered by HTTP method and templated route for each operation type. Count and total-time deltas provide app-side route-level context; unrelated concurrent application traffic may also be counted.
- **Run Overview:** baseline count before setup, required target, generated and verified counts, baseline setup duration, cleanup counts/final database count, software/host context, timing boundaries, metric definitions, and limitations.
- **API Calls:** points to the companion CSV containing every measured request. Keeping high-volume raw samples in CSV avoids Excel row and memory limits.
- **`api-calls-<run-id>.csv`:** one row per measured request, with scenario/repetition, virtual user, operation, status, latency, response size, records returned, success, and error. The file is in the same `results/` folder as the workbook.
- **Actuator Metrics:** before/after values and deltas for cumulative HTTP/MongoDB counters, MongoDB pool gauges, app CPU/JVM memory/threads, and all metric errors. The pool `checkedout`, `size`, and `waitqueuesize` readings are point-in-time snapshots, not peak usage; the runner does not poll during the measurement window to avoid distorting load. Counter deltas include the Actuator snapshot requests themselves.
- **Runner Resources:** load-generator Node.js process RSS, heap, external memory, and CPU consumed per scenario; these are not application-server resource measurements.

Ramp-up and warm-up API calls are excluded from measured samples. The fixed measurement window begins after baseline setup, token acquisition, find-ID loading, and pre-scenario snapshots. Requests started before its deadline are drained and included. Throughput divides in-window calls by the configured measurement duration. App Actuator measurements are cumulative or point-in-time rather than isolated scenario metrics.

Each scenario repeats three times by default. Compare repetitions for variability and increase measurement duration if an API type has fewer than a few hundred samples. Scenarios run in listed order, so persistent server state and order effects may influence comparisons.

The runner tracks IDs returned by successful create calls and deletes those documents in a `finally` cleanup block even if a scenario fails. Cleanup requires direct MongoDB access and reports its deletion count. Do not interrupt the process before its cleanup block runs.
