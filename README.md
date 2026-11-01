# Kitchen Sink

Kitchen Sink is a Spring Boot application for managing member registrations through a browser UI and REST API. Member data is stored as MongoDB documents using Spring Data MongoDB. The UI, API, and operational endpoints require HTTP Basic authentication.

Project author: Deepak Bheemarasetty.

## Requirements

- JDK 21
- Maven 3.9 or later
- MongoDB Community Server 8.0 or later
- MongoDB Shell (`mongosh`)

If Java or MongoDB is not installed, install the Windows packages:

```powershell
winget install --id Microsoft.OpenJDK.21 --exact
winget install --id MongoDB.Server --exact
winget install --id MongoDB.Shell --exact
```

Install [Maven 3.9 or later](https://maven.apache.org/install.html) separately if it is not already installed. Open a new PowerShell window after installing tools so it picks up their updated `PATH`, then verify:

```powershell
java -version
mvn -version
mongosh --version
```

## Run locally

The installer normally configures MongoDB as a Windows service. Start it if it is not already running:

```powershell
$service = Get-Service MongoDB
if ($service.Status -ne "Running") { Start-Service MongoDB }
Get-Service MongoDB
```

Seed 75 sample members from the project root. This command is safe to run more than once and does not overwrite existing member records:

```powershell
mongosh "mongodb://localhost:27017/kitchensink" scripts/seed-members.js
```

The application defaults to `mongodb://localhost:27017/kitchensink` and is available at `http://localhost:9080/kitchensink`. Set `MONGODB_URI` if your local MongoDB uses a different connection string. For example:

```powershell
$env:MONGODB_URI = "mongodb://localhost:27017/kitchensink"
```

For local development, the default HTTP Basic credentials are username `demo.user@gmail.com` and password `password@001`. All browser pages, static assets, and API endpoints require authentication. Override the defaults before starting the app when using a different device or environment:

```powershell
$env:SPRING_SECURITY_USER_NAME = "your-local-username"
$env:SPRING_SECURITY_USER_PASSWORD = "your-local-password"
mvn spring-boot:run
```

Do not use the documented defaults outside this local development setup; configure credentials through environment variables or a secrets manager.

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

For an existing checkout, run `git switch migration-docker` from the repository root instead. Build the application image and start both services:

```powershell
docker compose up --build
```

Compose waits for MongoDB's health check before starting the application. The app is available at `http://localhost:9080/kitchensink`. MongoDB seeds the sample member when its data volume is first initialized. For local Docker testing, Compose uses username `user` and password `password@001` by default; set `SPRING_SECURITY_USER_PASSWORD` before starting Compose to override the password:

```powershell
$env:SPRING_SECURITY_USER_PASSWORD = "your-local-password"
docker compose up --build
```

To find the password currently configured for the running app:

```powershell
docker compose logs app
```

Use username `user` and the configured password with the authenticated API examples below. Stop the foreground Compose process with `Ctrl+C`, then run `docker compose down` to remove the containers and network. Member data is stored in the `mongo_data` named volume and remains when containers are removed. To permanently delete that database and its seeded/member data too, run:

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

- `/kitchensink/` shows the member registration form and up to five most-viewed members from an in-memory cache. The cache starts empty on application startup and is populated as member details are viewed; it resets when the application restarts.
- `/kitchensink/members` shows a two-pane member browser: the keyset-paginated, searchable list is on the left and selected member details are on the right. The list is ordered by name and email and loads at most 20 records per MongoDB request, plus one lookahead record to determine whether a next page exists. Next/Previous use the last/first `(name, email)` cursor; there is no offset skip, full count query, or numbered page list.
- Selecting a member name opens its details in the right pane and increments its in-memory view count. Phone numbers are only shown in the details pane.

The All Members page supports exact email search, shows only the match with its details, and offers a clear-search link to restore the full paginated list. An unsuccessful search displays a dismissible warning. Member list rows show only name and email. The home-page form validates name, email, and phone in the browser as the user types and blocks invalid submissions; server-side validation remains enabled as a safety net. Pages use Thymeleaf and are available in a browser on the same machine as the app.

## Member API

All endpoints use the `/rest/members` path and exchange JSON.

For the API examples below, set the username and password configured when starting the application, then create the Basic Authentication header once in PowerShell. Use the credentials for your runtime:

```powershell
# Local default credentials:
$pair = "demo.user@gmail.com:password@001"
# Docker Compose defaults:
# $pair = "user:password@001"
# If SPRING_SECURITY_USER_PASSWORD was overridden, use that password instead.
$encoded = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($pair))
$headers = @{ Authorization = "Basic $encoded" }
```

### List members

Returns one keyset-paginated page ordered by name and email: at most 20 members, plus a lookahead row used internally to determine whether another page exists. The response includes `members`, `hasPrevious`, `hasNext`, and cursor objects. In PowerShell, use:

```powershell
$page = Invoke-RestMethod -Uri "http://localhost:9080/kitchensink/rest/members" -Headers $headers
$page.members
$page.hasNext
```

Use `nextCursor` as the exclusive cursor for the next page. Cursor values are URL-encoded because member names can contain spaces:

```powershell
$afterName = [uri]::EscapeDataString($page.nextCursor.name)
$afterEmail = [uri]::EscapeDataString($page.nextCursor.email)
$nextPage = Invoke-RestMethod `
    -Uri "http://localhost:9080/kitchensink/rest/members?afterName=$afterName&afterEmail=$afterEmail" `
    -Headers $headers
```

Use the complete `previousCursor` pair as `beforeName` and `beforeEmail` to navigate backward. Supply exactly one complete cursor pair per request; the endpoint does not accept a page number or offset.

### Get a member

Returns the member with the requested MongoDB ObjectId. To get an ID, list the members:

```powershell
$page = Invoke-RestMethod -Uri "http://localhost:9080/kitchensink/rest/members" -Headers $headers
$page.members
$memberId = $page.members[0].id
Invoke-RestMethod -Uri "http://localhost:9080/kitchensink/rest/members/$memberId" -Headers $headers
```

### Register a member

Send a name, email, and phone number:

```powershell
$member = @{
    name = "Jane Doe"
    email = "jane@example.com"
    phoneNumber = "2125551234"
} | ConvertTo-Json
Invoke-RestMethod -Uri "http://localhost:9080/kitchensink/rest/members" -Method Post `
    -Headers $headers `
    -ContentType "application/json" -Body $member
```

A successful registration returns `201 Created`, the saved member as JSON, and a `Location` header for the new member. The API returns `400 Bad Request` with field errors when validation fails, `404 Not Found` when a requested ID does not exist, and `409 Conflict` when the email is already registered or another data integrity constraint is violated.

### Member validation

- `name` is required, must contain 1 to 25 letters and spaces, and cannot contain digits or special characters.
- `email` is required and must be a valid email address.
- `phoneNumber` is required and must contain 8 to 12 digits.
- Email addresses must be unique.

The API uses stateless HTTP Basic authentication. Requests without valid credentials receive `401 Unauthorized`. CSRF protection is disabled only for `/rest/**`; the browser UI uses CSRF tokens for state-changing form submissions.

## Operational metrics

Spring Boot Actuator exposes authenticated `/kitchensink/actuator/health` and `/kitchensink/actuator/metrics` endpoints. Health details are hidden. MongoDB driver metrics expose database command counts and durations, tagged by database, collection, command, and status. Connection-pool gauges expose checked-out connections, total pool size, wait-queue size, and checkout failures. The checked-out gauge and configured pool maximum can be used to calculate utilization; the MongoDB driver's default maximum is 100 unless overridden in the connection URI. For example:

```powershell
Invoke-RestMethod -Uri "http://localhost:9080/kitchensink/actuator/metrics/http.server.requests" -Headers $headers
Invoke-RestMethod -Uri "http://localhost:9080/kitchensink/actuator/metrics/mongodb.driver.commands" -Headers $headers
Invoke-RestMethod -Uri "http://localhost:9080/kitchensink/actuator/metrics/mongodb.driver.commands?tag=database:kitchensink&tag=collection:members&tag=command:find" -Headers $headers
Invoke-RestMethod -Uri "http://localhost:9080/kitchensink/actuator/metrics/mongodb.driver.pool.checkedout" -Headers $headers
Invoke-RestMethod -Uri "http://localhost:9080/kitchensink/actuator/metrics/mongodb.driver.pool.size" -Headers $headers
Invoke-RestMethod -Uri "http://localhost:9080/kitchensink/actuator/metrics/mongodb.driver.pool.waitqueuesize" -Headers $headers
Invoke-RestMethod -Uri "http://localhost:9080/kitchensink/actuator/metrics/mongodb.driver.pool.checkoutfailed" -Headers $headers
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
