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

Start the MongoDB service if it is not already running. On Windows PowerShell:

```powershell
$service = Get-Service MongoDB
if ($service.Status -ne "Running") { Start-Service MongoDB }
Get-Service MongoDB
```

On macOS installed with Homebrew, use `brew services start mongodb-community@8.0`. On Linux systems using systemd, use `sudo systemctl start mongod` and check status with `sudo systemctl status mongod`.

When the application starts, it idempotently inserts any missing records from the 75-member sample dataset. Existing member records are not overwritten. To seed manually without starting the app:

```shell
mongosh "mongodb://localhost:27017/kitchensink" scripts/seed-members.js
```

The application defaults to `mongodb://localhost:27017/kitchensink` and is available at `http://localhost:9080/kitchensink`. Set `MONGODB_URI` if your local MongoDB uses a different connection string. On Windows PowerShell:

```shell
$env:MONGODB_URI = "mongodb://localhost:27017/kitchensink"
```

In macOS/Linux shells, set the same variable with `export MONGODB_URI="mongodb://localhost:27017/kitchensink"`.

Authentication uses Auth0 OpenID Connect for browser sign-in and signed JWT bearer tokens for the REST API. Create a Regular Web Application and an API in Auth0, then configure the following application URLs:

- Allowed Callback URL: `http://localhost:9080/kitchensink/login/oauth2/code/auth0`
- Allowed Logout URL: `http://localhost:9080/kitchensink/`
- Set the Auth0 tenant's Default Audience to the API identifier.

Set these environment variables before starting the application. Keep the client secret private and never commit it:

```powershell
$env:OIDC_ISSUER_URI = "https://YOUR_AUTH0_DOMAIN/"
$env:OIDC_CLIENT_ID = "YOUR_CLIENT_ID"
$env:OIDC_CLIENT_SECRET = "YOUR_CLIENT_SECRET"
$env:OIDC_AUDIENCE = "YOUR_AUTH0_API_IDENTIFIER"
mvn spring-boot:run
```

In macOS/Linux shells, use:

```shell
export OIDC_ISSUER_URI="https://YOUR_AUTH0_DOMAIN/"
export OIDC_CLIENT_ID="YOUR_CLIENT_ID"
export OIDC_CLIENT_SECRET="YOUR_CLIENT_SECRET"
export OIDC_AUDIENCE="YOUR_AUTH0_API_IDENTIFIER"
mvn spring-boot:run
```

The issuer URL must end in `/`. The application has no local username or password; without the Auth0 settings it will fail to start.

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
git clone --branch migration-docker https://github.com/deepak-bheemarasetty/kitchensink.git
Set-Location kitchensink
```

For an existing checkout, run `git switch migration-docker` from the repository root instead.

Create a local Compose environment file from the template. It contains placeholders only; fill them with your Auth0 tenant, Regular Web Application, and API values. Never commit `.env` or share the client secret.

```powershell
Copy-Item .env.example .env
```

For macOS/Linux:

```shell
cp .env.example .env
```

Edit `.env` and set `OIDC_ISSUER_URI`, `OIDC_CLIENT_ID`, `OIDC_CLIENT_SECRET`, and `OIDC_AUDIENCE`. Configure the Auth0 Regular Web Application with:

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

The browser UI is available under the `/kitchensink` context path and has two main pages:

- `/kitchensink/` shows the Find and Add panes, exact-email search, up to five most-viewed members, and the registration form. The cache starts empty on application startup and is populated as member details are viewed; it resets when the application restarts.
- `/kitchensink/members` shows a two-pane member browser: the keyset-paginated, searchable list is on the left and selected member details are on the right. The list is ordered by name and email and loads at most 20 records per MongoDB request, plus one lookahead record to determine whether a next page exists. Next/Previous use the last/first `(name, email)` cursor; there is no offset skip, full count query, or numbered page list.
- Selecting a member name opens its details in the right pane and increments its in-memory view count. Phone numbers are only shown in the details pane.

The All Members page supports exact email search, shows only the match with its details, and offers a clear-search link to restore the full paginated list. An unsuccessful search displays a dismissible warning. Member list rows show only name and email. The home-page form validates name, email, and phone in the browser as the user types and blocks invalid submissions; server-side validation remains enabled as a safety net. Browser sign-in redirects to Auth0 and returns to the application; the app does not store user passwords.

## Member API

All endpoints use the `/rest/members` path and exchange JSON.

The following examples use a valid Auth0 access token for the configured API audience. Replace `REPLACE_WITH_ACCESS_TOKEN` with that token. On Windows PowerShell, use `curl.exe` in place of `curl` (PowerShell may define `curl` as an alias).

### List members

Returns one keyset-paginated page ordered by name and email: at most 20 members, plus a lookahead row used internally to determine whether another page exists. The response includes `members`, `hasPrevious`, `hasNext`, and cursor objects. These `curl` examples work in macOS/Linux shells; in Windows PowerShell, use `curl.exe` instead of `curl`.

```shell
curl --header 'Authorization: Bearer REPLACE_WITH_ACCESS_TOKEN' 'http://localhost:9080/kitchensink/rest/members'
```

Use the `nextCursor` name and email from the response as the exclusive cursor for the next page. `--data-urlencode` safely encodes values such as names containing spaces:

```shell
curl --header 'Authorization: Bearer REPLACE_WITH_ACCESS_TOKEN' --get --data-urlencode 'afterName=Jane Doe' --data-urlencode 'afterEmail=jane@example.com' 'http://localhost:9080/kitchensink/rest/members'
```

Use the complete `previousCursor` pair as `beforeName` and `beforeEmail` to navigate backward. Supply exactly one complete cursor pair per request; the endpoint does not accept a page number or offset.

### Get a member

Returns the member with the requested MongoDB ObjectId. To get an ID, list the members:

```shell
curl --header 'Authorization: Bearer REPLACE_WITH_ACCESS_TOKEN' 'http://localhost:9080/kitchensink/rest/members'
curl --header 'Authorization: Bearer REPLACE_WITH_ACCESS_TOKEN' 'http://localhost:9080/kitchensink/rest/members/REPLACE_WITH_MEMBER_ID'
```

### Register a member

Send a name, email, and phone number:

```shell
curl --header 'Authorization: Bearer REPLACE_WITH_ACCESS_TOKEN' --header 'Content-Type: application/json' --data '{"name":"Jane Doe","email":"jane@example.com","phoneNumber":"2125551234"}' 'http://localhost:9080/kitchensink/rest/members'
```

A successful registration returns `201 Created`, the saved member as JSON, and a `Location` header for the new member. The API returns `400 Bad Request` with field errors when validation fails, `404 Not Found` when a requested ID does not exist, and `409 Conflict` when the email is already registered or another data integrity constraint is violated.

### Member validation

- `name` is required, must contain 1 to 25 letters and spaces, and cannot contain digits or special characters.
- `email` is required and must be a valid email address.
- `phoneNumber` is required and must contain 8 to 12 digits.
- Email addresses must be unique.

The REST API and Actuator endpoints accept stateless JWT bearer tokens issued by the configured Auth0 tenant. The issuer and API audience are validated; missing or invalid tokens receive `401 Unauthorized`. CSRF protection is disabled for these stateless requests; the browser UI uses an OIDC login session and CSRF tokens for state-changing form submissions.

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

The suite covers application startup, authentication, controller responses and validation, registration rules, and MongoDB document mapping. Unit tests do not require a running MongoDB server. For an end-to-end local check, start MongoDB and the app as described above, then exercise the authenticated API with the examples. JaCoCo writes an HTML coverage report to `target/site/jacoco/index.html`.

## Configuration

Application settings are in `src/main/resources/application.properties`. The MongoDB URI is read from `MONGODB_URI`, with a local MongoDB default. Spring Data creates the unique email index when the application connects to MongoDB.
