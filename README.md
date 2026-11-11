# Kitchen Sink

Kitchen Sink is a Spring Boot application for managing members through a browser UI and REST API. Member data is stored in MongoDB. Sign-in uses Auth0 OpenID Connect; identity email is mapped to local users and roles for authorization.

Project author: Deepak Bheemarasetty.

## Requirements

- JDK 21
- Maven 3.9 or later
- MongoDB Community Server 8.0 or later
- MongoDB Shell (`mongosh`)

Install Java, Maven, MongoDB, and `mongosh` using the package manager for your operating system or the official downloads:

| Tool | Installation |
|---|---|
| JDK 21 | [OpenJDK](https://jdk.java.net/21/) or your OS package manager |
| Maven 3.9+ | [Maven installation guide](https://maven.apache.org/install.html) |
| MongoDB Community Server | [MongoDB Community download](https://www.mongodb.com/try/download/community) |
| `mongosh` | [MongoDB Shell installation guide](https://www.mongodb.com/docs/mongodb-shell/install/) |

On Windows, `winget` can install Java and MongoDB:

```powershell
winget install --id Microsoft.OpenJDK.21 --exact
winget install --id MongoDB.Server --exact
winget install --id MongoDB.Shell --exact
```

Verify the tools are available in a new terminal:

```shell
java -version
mvn -version
mongosh --version
```

## Run locally

Local execution uses the Spring `local` profile by default. Configure the MongoDB application service account in `.env` with `MONGODB_URI`, and configure Auth0 values as described below. For local development without MongoDB authentication, the default localhost URI remains available. Create or invite matching Auth0 users through the Auth0 tenant and assign their local roles in the Users workspace. On Windows PowerShell:

```powershell
$service = Get-Service MongoDB
if ($service.Status -ne "Running") { Start-Service MongoDB }
Get-Service MongoDB
```

On macOS installed with Homebrew, use `brew services start mongodb-community@8.0`. On Linux systems using systemd, use `sudo systemctl start mongod` and check status with `sudo systemctl status mongod`.

### MongoDB Replica Set Configuration, Verification & Restart (Required for Transactions)

The application uses multi-document Spring Data MongoDB transactions (`@Transactional`), which strictly require MongoDB to run as a **replica set** (e.g. name `rs0`). Single standalone mongod instances will fail with `Command failed with error 20 (IllegalOperation): Transaction numbers are only allowed on a replica set member or mongos`.

Follow these step-by-step instructions:

---

#### 1. Check if Replication is Enabled

Open your terminal and run `mongosh`:

```shell
mongosh --eval "rs.status()"
```

- **If replication is NOT enabled**, it will output:
  ```json
  MongoServerError[NoReplicationEnabled]: not running with --replSet
  ```
- **If replication IS already enabled and initialized**, it will output JSON containing:
  ```json
  "set": "rs0",
  "ok": 1,
  "members": [ ... ]
  ```
  and the prompt in `mongosh` will show `rs0 [direct: primary]>`.

You can also run a quick one-liner to get a boolean check:
```shell
mongosh --quiet --eval "try { print('Replication Active:', rs.status().ok === 1); } catch(e) { print('Replication Active: false (No replication enabled)'); }"
```

---

#### 2. How to Enable Replication

You can enable replication either via configuration file (`mongod.cfg` / `mongod.conf`) or directly via terminal flags.

##### Option A: From Configuration File (Recommended for System Services)

1. Locate the MongoDB configuration file:
   - **Windows**: `C:\Program Files\MongoDB\Server\8.0\bin\mongod.cfg` (or matching version directory)
   - **Linux (Ubuntu/Debian/RHEL)**: `/etc/mongod.conf`
   - **macOS (Homebrew Apple Silicon)**: `/opt/homebrew/etc/mongod.conf`
   - **macOS (Homebrew Intel)**: `/usr/local/etc/mongod.conf`

2. Open the file in an administrative / root text editor (e.g., Notepad as Administrator on Windows, or `sudo nano /etc/mongod.conf` on Linux).

3. Add or uncomment the `replication` section (ensure valid YAML indentation with 2 spaces):
   ```yaml
   replication:
     replSetName: "rs0"
   ```

4. Save the file.

##### Option B: From the Terminal (CLI Command Line)

If you run MongoDB standalone in a terminal (outside system services) or for temporary local testing, pass `--replSet rs0`:

- **Windows PowerShell**:
  ```powershell
  & "C:\Program Files\MongoDB\Server\8.0\bin\mongod.exe" --dbpath "C:\data\db" --replSet rs0
  ```
- **Linux / macOS**:
  ```bash
  mongod --dbpath /var/lib/mongodb --replSet rs0
  ```

---

#### 3. How to Restart MongoDB

Restart the service so it loads the new replica set configuration.

##### Windows
Open PowerShell as **Administrator**:
```powershell
# Check current status
Get-Service MongoDB

# Restart the service
Restart-Service MongoDB

# Verify it is running
Get-Service MongoDB
```

*Alternative via Command Prompt (Administrator):*
```cmd
net stop MongoDB
net start MongoDB
```

##### Linux (systemd)
```bash
# Restart the daemon
sudo systemctl restart mongod

# Verify service is running active
sudo systemctl status mongod
```

##### macOS (Homebrew)
```bash
# Restart the service
brew services restart mongodb-community@8.0

# Verify status
brew services list
```

---

#### 4. Initiate and Verify the Replica Set in `mongosh`

Once MongoDB is running with `--replSet rs0` or `replSetName: "rs0"`, initiate the replica set (this only needs to be run once per database instance):

1. Connect with `mongosh`:
   ```shell
   mongosh
   ```

2. Run `rs.initiate()`:
   ```javascript
   rs.initiate()
   ```
   Expected response:
   ```json
   { "ok": 1 }
   ```
   *(Note: If it returns `{ "ok": 0, "errmsg": "already initialized" }`, the replica set is already initialized and good to go).*

3. Verify status:
   ```javascript
   rs.status().members[0].stateStr
   ```
   Wait 1-2 seconds until it transitions to:
   ```
   'PRIMARY'
   ```

4. Verify your `.env` connection string:
   Ensure your `.env` connects to the replica set:
   ```dotenv
   MONGODB_URI=mongodb://localhost:27017/kitchensink?replicaSet=rs0
   ```

Use a fresh `kitchensink` database for this version. The application installs MongoDB schema validation on startup.

To seed the initial application roles and users (matching the Auth0 users list in `documentation/users.json`), run:

```shell
mongosh "mongodb://localhost:27017/kitchensink" scripts/seed-users-and-roles.js
```

To seed sample member records for local development:

```shell
mongosh "mongodb://localhost:27017/kitchensink" scripts/reset-and-seed-members.js
```

The application defaults to `mongodb://localhost:27017/kitchensink` and is available at `http://localhost:9080/kitchensink`. To customize local settings, copy `.env.example` to `.env` and edit the values:

```powershell
Copy-Item .env.example .env
```

In macOS/Linux, use `cp .env.example .env`. Spring Boot loads the root `.env` when started from the project root. The file is ignored by Git; never commit secrets. Environment variables supplied by the shell or deployment environment take precedence over `.env`.

Set `LOG_LEVEL` in `.env` to control the root application logging level (`TRACE`, `DEBUG`, `INFO`, `WARN`, or `ERROR`). It defaults to `INFO` when omitted.

Authentication uses Auth0 OpenID Connect for browser sign-in. The Auth0 email identity is matched to an active account in the application's users collection, and effective permissions are loaded from that account's assigned roles. The same database-backed permissions protect the UI and API. User administration registers an Auth0 email identity in the local users collection; administrators must also invite that email through Auth0. Create a Regular Web Application and an API in Auth0, then configure the following application URLs:

- Allowed Callback URL: `http://localhost:9080/kitchensink/login/oauth2/code/auth0`
- Allowed Logout URL: `http://localhost:9080/kitchensink/`
- Set the Auth0 tenant's Default Audience to the API identifier.
- Configure the Auth0 API audience and ensure the email claim is available to the application. Application permissions are loaded from local role assignments, not from Auth0 scopes.

Set these values in the root `.env` using your Auth0 Regular Web Application credentials and API identifier:

```dotenv
OIDC_ISSUER_URI=https://YOUR_AUTH0_DOMAIN/
OIDC_CLIENT_ID=YOUR_AUTH0_REGULAR_WEB_APP_CLIENT_ID
OIDC_CLIENT_SECRET=YOUR_AUTH0_REGULAR_WEB_APP_CLIENT_SECRET
OIDC_AUDIENCE=YOUR_AUTH0_API_IDENTIFIER
```

Then start the application from the project root:

```shell
mvn spring-boot:run
```

The issuer URL must end in `/`. The application uses Auth0 only; without the Auth0 settings it will fail to start.

The browser session expires after 30 minutes without requests. When an expired session next accesses a protected page, the user must start the Auth0 sign-in flow again, which requests reauthentication. REST API authentication remains stateless and is unaffected by this browser-session timeout.

## Run with Docker Compose

Docker Desktop with its Compose plugin is required. Start Docker Desktop and wait for its engine to be ready, then verify both commands work:

```powershell
docker --version
docker compose version
docker info
```

The Docker setup runs both the application and MongoDB in containers; you do not need to install Java, Maven, or MongoDB on the Docker host. The Compose file publishes MongoDB on host port `27018` to avoid conflicts with a local MongoDB service on the usual port `27017`.

Clone this branch on a new machine, or switch an existing checkout to it:

```powershell
git clone --branch migration-main-docker https://github.com/deepak-bheemarasetty/kitchensink.git
Set-Location kitchensink
```

For an existing checkout, run `git switch migration-main-docker` from the repository root instead.

Create a local Compose environment file from the template. It contains placeholders only; fill them with your Auth0 tenant, Regular Web Application, and API values. Never commit `.env` or share the client secret.

```powershell
Copy-Item .env.example .env
```

For macOS/Linux:

```shell
cp .env.example .env
```

Set `OIDC_ISSUER_URI`, `OIDC_CLIENT_ID`, `OIDC_CLIENT_SECRET`, and `OIDC_AUDIENCE` in `.env`. Docker Compose reads those values from `.env` and passes them to the application container. Configure the Auth0 Regular Web Application with:

- Allowed Callback URL: `http://localhost:9080/kitchensink/login/oauth2/code/auth0`
- Allowed Logout URL: `http://localhost:9080/kitchensink/`
- Allowed Web Origin: `http://localhost:9080`
- Default Audience: the KitchenSink API Identifier

The issuer must end in `/`. For each local Auth0 application, use credentials from that same app. The Compose configuration fails early if any required OIDC setting is missing.

Start the app and MongoDB:

```shell
docker compose up --build
```

Compose waits for MongoDB's health check before starting the application. The app is available at `http://localhost:9080/kitchensink`. At every application startup, the app safely upserts the 75 sample members into the `kitchensink` database without replacing existing documents. MongoDB data persists in the `mongo_data` named volume.

Check that the stylesheet is served by the container (HTTP 200 and `text/css`):

```powershell
curl.exe -I http://localhost:9080/kitchensink/css/styles.css
```

The image build compiles and packages the application, then verifies the stylesheet is present inside the executable JAR; it does not run the Maven test suite. Run `mvn verify` separately for tests and coverage. If you have a stale image, rebuild it with `docker compose build --no-cache app` and then run `docker compose up`.

To confirm MongoDB contains the seeded members:

```shell
docker compose exec mongodb mongosh "mongodb://localhost:27017/kitchensink" --quiet --eval "db.members.countDocuments()"
```

Stop the foreground Compose process with `Ctrl+C`, then run `docker compose down` to remove the containers and network. Member data remains in the `mongo_data` volume. To permanently delete that database and its member data too, run:

```powershell
docker compose down --volumes
```

The application connects to MongoDB over the private Compose network. If you want to connect to the containerized MongoDB from a host tool, use `mongodb://localhost:27018/kitchensink`.

To build and run the executable JAR instead:

```shell
mvn package
java -jar target/kitchensink.jar
```

The application uses port `9080` by default. You can override it at startup, for example:

```shell
java -jar target/kitchensink.jar --server.port=9081
```

## Browser UI

The browser UI is available under the `/kitchensink` context path. OAuth sign-in uses Auth0, then maps the identity email to an active local account and its database roles. Members, Users, and Roles navigation and controls are rendered only when the corresponding permission is present.

- `/kitchensink/` redirects to the first permitted workspace.
- `/kitchensink/members` shows a searchable member directory with status filtering, keyset Load more pagination, and responsive mobile cards. Permission-gated add, edit, and delete actions use the existing `/api/v1/members` API.
- `/kitchensink/users` manages local account role assignments and active status. `/kitchensink/roles` reviews and updates role permissions.

Member add and edit share a responsive drawer. The UI displays loading, empty, success, and error states; server validation and authorization remain authoritative.

## Member API

Member endpoints use `/api/v1/members` and exchange JSON. List responses use keyset cursors and limits capped at 100. Create requests require an `Idempotency-Key` header. Member IDs are MongoDB ObjectIds. Bulk/file import is outside this API's scope.

For interactive API testing, import [`postman/KitchenSink-API.postman_collection.json`](postman/KitchenSink-API.postman_collection.json) into Postman. Its default `baseUrl` matches the local server (`http://localhost:9080/kitchensink`). Use an Auth0 access token as a Bearer token. Copy returned member and user IDs into the corresponding collection variables before calling ID-based routes.

The following examples use a valid Auth0 access token for the configured API audience. Replace `REPLACE_WITH_ACCESS_TOKEN` with that token. On Windows PowerShell, use `curl.exe` in place of `curl` (PowerShell may define `curl` as an alias).

### List members

Returns one keyset-paginated page ordered by email: at most 20 members, plus a lookahead row used internally to determine whether another page exists. The response includes `members`, `hasPrevious`, `hasNext`, and email cursor objects. These `curl` examples work in macOS/Linux shells; in Windows PowerShell, use `curl.exe` instead of `curl`.

```shell
curl --header 'Authorization: Bearer REPLACE_WITH_ACCESS_TOKEN' 'http://localhost:9080/kitchensink/api/v1/members'
```

Use the `nextCursor` email from the response as the exclusive cursor for the next page. `--data-urlencode` safely encodes the address:

```shell
curl --header 'Authorization: Bearer REPLACE_WITH_ACCESS_TOKEN' --get --data-urlencode 'q=jane' 'http://localhost:9080/kitchensink/api/v1/members'
```

Use the `previousCursor` email as `beforeEmail` to navigate backward. Supply at most one cursor per request; the endpoint does not accept a page number or offset. The list is ordered by email to match its single-field cursor and the existing unique email index.

### Get a member

Returns the member with the requested numeric sequence ID. To get an ID, list the members:

```shell
curl --header 'Authorization: Bearer REPLACE_WITH_ACCESS_TOKEN' 'http://localhost:9080/kitchensink/api/v1/members'
curl --header 'Authorization: Bearer REPLACE_WITH_ACCESS_TOKEN' 'http://localhost:9080/kitchensink/api/v1/members/REPLACE_WITH_MEMBER_ID'
```

### Register a member

Send `firstName`, optional `lastName`, email, and E.164 phone with an idempotency key:

```shell
curl --header 'Authorization: Bearer REPLACE_WITH_ACCESS_TOKEN' --header 'Idempotency-Key: demo-request-001' --header 'Content-Type: application/json' --data '{"firstName":"Jane","lastName":"Doe","email":"jane@example.com","phone":"+12125551234"}' 'http://localhost:9080/kitchensink/api/v1/members'
```

A successful registration returns `201 Created`, the saved member as JSON, and a `Location` header for the new member. MongoDB schema violations return `400 Bad Request`; the API returns `404 Not Found` when a requested ID does not exist and `409 Conflict` when the email is already registered or another data integrity constraint is violated.

### MongoDB member schema

- `id` is a `Long` generated from an atomic counter in the `sequences` collection.
- `name` is required text up to 40 characters; `email` is required text up to 40 characters and must include a dotted domain.
- `phoneNumber` is required text containing 4 to 15 digits; `countryCode` is a required integer from 1 through 9999.
- `createdBy` and `lastUpdatedBy` come from the authenticated username; creation and update timestamps and the version are managed by Spring Data MongoDB.
- Email is trimmed and lowercased before storage, and a unique index enforces uniqueness. Other field constraints are enforced by MongoDB schema validation, not application Bean Validation.

REST endpoints accept Auth0 JWT bearer tokens; browser UI requests use the OAuth login session with CSRF protection for state-changing calls. Both authentication paths resolve application permissions from the local users and roles collections. The API enforces permissions for every operation; UI visibility is a usability layer only.

## Operational metrics

Spring Boot Actuator exposes JWT-protected `/kitchensink/actuator/health` and `/kitchensink/actuator/metrics` endpoints. Health details are hidden. MongoDB driver metrics expose database command counts and durations, tagged by database, collection, command, and status. Connection-pool gauges expose checked-out connections, total pool size, wait-queue size, and checkout failures. The checked-out gauge and configured pool maximum can be used to calculate utilization; the MongoDB driver's default maximum is 100 unless overridden in the connection URI. Obtain an Auth0 access token for the configured API audience before using these examples:

```shell
curl --header 'Authorization: Bearer REPLACE_WITH_ACCESS_TOKEN' 'http://localhost:9080/kitchensink/actuator/metrics/http.server.requests'
curl --header 'Authorization: Bearer REPLACE_WITH_ACCESS_TOKEN' 'http://localhost:9080/kitchensink/actuator/metrics/mongodb.driver.commands'
curl --header 'Authorization: Bearer REPLACE_WITH_ACCESS_TOKEN' 'http://localhost:9080/kitchensink/actuator/metrics/mongodb.driver.commands?tag=database:kitchensink&tag=collection:members&tag=command:find'
curl --header 'Authorization: Bearer REPLACE_WITH_ACCESS_TOKEN' 'http://localhost:9080/kitchensink/actuator/metrics/mongodb.driver.pool.checkedout'
curl --header 'Authorization: Bearer REPLACE_WITH_ACCESS_TOKEN' 'http://localhost:9080/kitchensink/actuator/metrics/mongodb.driver.pool.size'
curl --header 'Authorization: Bearer REPLACE_WITH_ACCESS_TOKEN' 'http://localhost:9080/kitchensink/actuator/metrics/mongodb.driver.pool.waitqueuesize'
curl --header 'Authorization: Bearer REPLACE_WITH_ACCESS_TOKEN' 'http://localhost:9080/kitchensink/actuator/metrics/mongodb.driver.pool.checkoutfailed'
```

`mongodb.driver.commands` reports command frequency (`COUNT`) and database command time (`TOTAL_TIME` and `MAX`) over the application process lifetime. Member write workflows use MongoDB transactions for atomicity; transaction-duration metrics are not currently exposed. Local MongoDB must run as a replica set for these workflows. Metrics remain in-process and are available through Actuator; no external dashboard or exporter is planned.

## Code style

The Maven build checks application and test Java sources against the Google Checkstyle rules. The check runs during Maven's `validate` phase and fails the build when it finds a warning or error. Run the style check by itself with:

```shell
mvn validate
```

## Tests

Run the JUnit 5 suite and enforce 100% line and branch coverage for application code:

```shell
mvn verify
```

The suite covers application startup, authentication, controller responses, registration, sequence generation, MongoDB schema enforcement, and document mapping. A running MongoDB server is required for the integration tests. For an end-to-end local check, start MongoDB and the app as described above, then exercise the authenticated API with the examples. JaCoCo writes an HTML coverage report to `target/site/jacoco/index.html`.

## Configuration

Application settings are in `src/main/resources/application.properties`. The MongoDB URI is read from `MONGODB_URI`, with a local MongoDB default. Spring Data creates the unique email index, and startup configures the member collection's schema validator.

The MongoDB converter omits Spring Data's `_class` type metadata so documents remain compatible with the strict collection schemas. Repository and template reads specify their target Java model explicitly.

### Java formatting and Checkstyle

Spotless and the recommended VS Code Google Java Format extension use Google Java Format 1.24.0 as the formatting source of truth. Java files format on save; to format every application and test Java source at once, run `mvn spotless:apply` or choose **Format all Java files (Spotless)** from **Terminal → Run Task** in VS Code. Run `mvn spotless:check` or choose **Check Java formatting (Spotless)** to check formatting without changing files. Maven and VS Code download the formatter the first time they run.

Checkstyle separately checks `config/checkstyle/google_checks.xml` for code-rule violations, such as naming and Javadocs. Formatting is checked by Spotless; Checkstyle's brace requirement is omitted because Google Java Format does not change control flow, and long `Pattern.compile` literals are excluded from its line-length check. Run `mvn validate` or choose **Check Java style (Checkstyle)** from **Terminal → Run Task**. **Generate Checkstyle report** writes its report under `target/site`.

### MongoDB database roles

The application uses Auth0 for sign-in. MongoDB users below are separate database identities; they do not grant application UI permissions. For self-managed MongoDB, connect with an existing deployment administrator, set `MONGODB_APP_DB` if the database is not `kitchensink`, and run `scripts/setup-mongodb-roles.js`. MongoDB passwords and their corresponding Auth0 email identifiers are stored in the ignored `mongo-database-credentials.txt` document. These database passwords are not for Auth0 sign-in; create/invite those emails in Auth0 and let Auth0 manage authentication. The current app-role mapping is admin=`admin`, editor=`manager` (full member CRUD), viewer=`viewer`, and database admin=`admin`. Protect the credentials document and distribute it only over a secure channel. For MongoDB Atlas, create equivalent custom roles and users in Atlas because Atlas does not support the `createRole` shell command. Set `MONGODB_URI` to the `kitchensink_app` connection string in `.env` so the application connects with its own service identity. Store that credential in ignored `.env` or a deployment secret store, never in source control. Do not give the app credential to human users.

| MongoDB role | Access | Intended user |
| --- | --- | --- |
| `kitchensinkApp` | Read/write data, create/drop collections and indexes, modify validators, and bypass document validation | Application service account only |
| `kitchensinkAdmin` | Read/write data, manage collections/indexes/validators, and manage users/roles in the application database | Database administrator |
| `kitchensinkEditor` | Read, insert, update, and remove application documents | Data editor |
| `kitchensinkViewer` | Read application data only | Debugging |

Editors and viewers receive no collection administration, user/role administration, or `bypassDocumentValidation` privileges. MongoDB privileges are database/collection action based: standard MongoDB RBAC cannot let an editor change document values while preventing changes to selected fields or document structure. The JSON Schema validators reject invalid document shapes, but an editor with `update` can still make any update that passes validation. Use the application API for field-level rules and auditing; give direct editor access only to trusted operators. The app account has the broadest routine application permissions because it performs migrations, auditing, and CRUD. The separate human admin manages database users and roles and can modify schemas. For strongest separation, keep the deployment administrator account offline except during administration. MongoDB authorization is currently disabled on the local server, so these accounts are not enforced until authorization is enabled and the server is restarted.
