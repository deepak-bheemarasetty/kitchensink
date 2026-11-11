# API Load Test Harness

This isolated Node.js project exercises the authenticated Kitchen Sink REST API under concurrent load with zero external cloud dependencies. It tests the application running on port `9081` using the `kitchensink_loadtest` MongoDB database.

---

## Architecture & Authentication

- **Mock JWKS OIDC Server (`mock-auth.mjs`)**: Starts a lightweight HTTP server on port `9089` exposing standard OIDC discovery (`/.well-known/openid-configuration`) and JWKS (`/.well-known/jwks.json`).
- **Dynamic RS256 Token Minting**: Signs RFC 7519 compliant JWTs using an in-memory 2048-bit RSA key for any user in the test pool without contacting external Auth0 endpoints or incurring rate limits.
- **Dynamic RBAC**: The Spring Boot Resource Server validates token signatures against `http://localhost:9089/`, extracts the canonical email, and dynamically resolves permissions from the MongoDB `users` and `roles` collections on each call.

---

## Test Data Preparation

The load test suite uses synthetic users and members partitioned by `@load.test`:

1. **Baseline Members**: 30,000 members seeded via `seed-30k-members.mjs` with pattern `member.0#####@load.test`.
2. **User Accounts**: 100 user accounts seeded via `seed-100-users.mjs` (`user.0001@load.test` to `user.0100@load.test`) with the required role distribution:
   - `admin`: 15 users
   - `manager`: 20 users
   - `creator`: 25 users
   - `editor`: 20 users
   - `deleter`: 10 users
   - `viewer`: 10 users

---

## Traffic Distribution

The runner executes an **80% Write / 20% Read** workload:
- **60% Create Member**: `POST /api/v1/members` (Idempotent, role: `creator`, `manager`, or `admin`)
- **20% Update Member**: `PATCH /api/v1/members/{id}` (role: `editor`, `manager`, or `admin`)
- **5% Delete Member**: `DELETE /api/v1/members/{id}` (role: `deleter`, `manager`, or `admin`, deletes transient records)
- **5% User Management**: `POST /api/v1/users/identity` + `PUT /api/v1/users/{id}` (role: `admin`)
- **10% Find Member**: `GET /api/v1/members/{id}` (role: any authenticated)
- **10% List Members**: `GET /api/v1/members?limit=10` (role: any authenticated)

---

## Configuration

Configuration is loaded from `load-tests/.env` (or environment variables). To configure:

```powershell
Copy-Item load-tests/.env.example load-tests/.env
```

Default variables:
```properties
BASE_URL=http://localhost:9081/kitchensink
MONGODB_URI=mongodb://localhost:27017/kitchensink_loadtest?replicaSet=rs0
AUTH0_ISSUER_URI=http://localhost:9089/
AUTH0_AUDIENCE=https://kitchensink-api
LOAD_MEASURE_SECONDS=180
LOAD_CONCURRENCY=20
```

---

## Running the Application & Load Test

### 1. Start the Application in Load-Test Mode
From the repository root, run Spring Boot with the `loadtest` profile:

```powershell
mvn spring-boot:run -Dspring-boot.run.profiles=loadtest
```

Or via packaged JAR:
```powershell
java -jar target/kitchensink.jar --spring.profiles.active=loadtest
```

This starts the application on port `9081` connected to the `kitchensink_loadtest` database and points OIDC JWKS validation to `http://localhost:9089/`.

### 2. Run Smoke Test
Verify end-to-end connectivity, token minting, and API endpoints:

```powershell
cd load-tests
node smoke-test.mjs
```

### 3. Run the Full Load Test
```powershell
cd load-tests
npm run load
```

The runner:
1. Automatically boots the mock JWKS auth server on port `9089`.
2. Connects to MongoDB, verifies the baseline members and user pool.
3. Spawns concurrent virtual workers sending continuous traffic according to the configured distribution.
4. Outputs an execution summary table with request counts, success/failure counts, average latency, and p50/p95/p99 percentiles.
5. Cleans up all transient members and users created during the test run.
