# Kitchen Sink

Kitchen Sink is a Spring Boot application for managing member registrations through a browser UI and REST API. Member data is stored as MongoDB documents using Spring Data MongoDB. Browser sign-in uses Auth0 OpenID Connect; REST and operational endpoints require Auth0 JWT bearer tokens.

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

Local execution uses the Spring `local` profile by default. Local-only seed data is disabled unless explicitly enabled. After applying V1 and V2, seed the six RBAC test users and default roles with `mongosh`:

```powershell
mongosh "mongodb://localhost:27017/kitchensink" scripts/seed-rbac-local.js
```

The script refuses to run against a database not named `kitchensink`. It stores BCrypt hashes and only creates users that are not already present. The generated plaintext test credentials are kept in the ignored root file `test-user-credentials.txt`.

To use the application startup seeder instead, run the local profile with seeding explicitly enabled:

```powershell
$env:LOCAL_SEED_ENABLED = 'true'
mvn spring-boot:run -Dspring-boot.run.profiles=local
Remove-Item Env:LOCAL_SEED_ENABLED
```

On macOS/Linux:

```shell
LOCAL_SEED_ENABLED=true mvn spring-boot:run -Dspring-boot.run.profiles=local
```

The local seed reads usernames and plaintext test passwords from the ignored root file `test-user-credentials.txt`; passwords must be BCrypt-hashed before they are written to MongoDB. Do not use this seed outside the local profile. The same usernames and passwords are listed in the ignored `CHECKPOINT.md` for creating the matching Auth0 test identities.

Start the MongoDB service if it is not already running. On Windows PowerShell:

```powershell
$service = Get-Service MongoDB
if ($service.Status -ne "Running") { Start-Service MongoDB }
Get-Service MongoDB
```

On macOS installed with Homebrew, use `brew services start mongodb-community@8.0`. On Linux systems using systemd, use `sudo systemctl start mongod` and check status with `sudo systemctl status mongod`.

Use a fresh `kitchensink` database for this version; the member `_id` is now a numeric sequence and existing ObjectId-based member records are not migrated. The application installs MongoDB schema validation on startup. Seed 75 sample members from the project root; the script includes the required audit fields and initializes the sequence counter:

```shell
mongosh "mongodb://localhost:27017/kitchensink" scripts/seed-members.js
```

The application defaults to `mongodb://localhost:27017/kitchensink` and is available at `http://localhost:9080/kitchensink`. To customize local settings, copy `.env.example` to `.env` and edit the values:

```powershell
Copy-Item .env.example .env
```

In macOS/Linux, use `cp .env.example .env`. Spring Boot loads the root `.env` when started from the project root. The file is ignored by Git; never commit secrets. Environment variables supplied by the shell or deployment environment take precedence over `.env`.

Authentication supports local username/password and Auth0 OpenID Connect for browser sign-in. REST requests accept HTTP Basic credentials from the local users collection or Auth0 JWT bearer tokens. Permissions come from the application's users and roles collections; Auth0 scopes do not grant application permissions. Create a Regular Web Application and an API in Auth0, then configure the following application URLs:

- Allowed Callback URL: `http://localhost:9080/kitchensink/login/oauth2/code/auth0`
- Allowed Logout URL: `http://localhost:9080/kitchensink/`
- Set the Auth0 tenant's Default Audience to the API identifier.
- Create API permissions `read:members` and `write:members`, enable RBAC and adding permissions to access tokens, and request the matching permission as an OAuth scope. Member list/detail requests require `read:members`; registration requires `write:members`.

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

The issuer URL must end in `/`. The application has no local username or password; without the Auth0 settings it will fail to start.

The browser session expires after 30 minutes without requests. When an expired session next accesses a protected page, the user must start the Auth0 sign-in flow again, which requests reauthentication. REST API authentication remains stateless and is unaffected by this browser-session timeout.

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

The browser UI is available under the `/kitchensink` context path and has two main pages:

- `/kitchensink/` shows the Find and Add panes, exact-email search, and the registration form.
- `/kitchensink/members` shows a searchable member list ordered by email. Its filter matches email addresses that start with the entered prefix and requires at least four characters. It loads at most 20 records per MongoDB request, plus one lookahead record to determine whether another page exists. Next/Previous use the exclusive email cursor; there is no offset skip, full count query, or numbered page list.
- Member rows show email, name, country code, and phone number in that order.

The All Members page supports case-insensitive email-prefix filtering and offers a clear-search link to restore the full paginated list. An unsuccessful filter displays a dismissible warning. The registration form submits fields without client-side validation; MongoDB enforces the persisted schema. Browser sign-in redirects to Auth0 and returns to the application; the app does not store user passwords.

## Member API

Member endpoints use `/api/v1/members` and exchange JSON. List responses use keyset cursors and limits capped at 100. Create requests require an `Idempotency-Key` header. Member IDs are MongoDB ObjectIds. Bulk/file import is outside this API's scope.

For interactive API testing, import [`postman/KitchenSink-API.postman_collection.json`](postman/KitchenSink-API.postman_collection.json) into Postman. Its default `baseUrl` matches the local server (`http://localhost:9080/kitchensink`). Set the collection's `username` and `password` variables for Basic Auth, or switch collection Authorization to Bearer Token and provide an Auth0 access token. Copy returned member and user IDs into the corresponding collection variables before calling ID-based routes.

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

The REST API and Actuator endpoints accept stateless JWT bearer tokens issued by the configured Auth0 tenant. The issuer and API audience are validated; missing or invalid tokens receive `401 Unauthorized`. REST member reads require the `read:members` scope and registration requires `write:members`; requests without the required permission are forbidden. CSRF protection is disabled for these stateless requests; the browser UI uses an OIDC login session and CSRF tokens for state-changing form submissions.

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

`mongodb.driver.commands` reports command frequency (`COUNT`) and database command time (`TOTAL_TIME` and `MAX`) over the application process lifetime. The application does not currently use MongoDB transactions, so there is no transaction-duration metric; command durations are available now, and transaction timing is deferred until transactional operations are introduced. Metrics remain in-process and are available through Actuator; no external dashboard or exporter is planned.

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
