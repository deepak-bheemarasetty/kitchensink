# Kitchen Sink

Kitchen Sink is a Spring Boot REST application for managing member registrations. It provides endpoints to list members, retrieve a member by ID, and register a member. Member data is stored as MongoDB documents using Spring Data MongoDB. All member endpoints require HTTP Basic authentication.

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

Seed the sample member from the project root. This command is safe to run more than once:

```powershell
mongosh "mongodb://localhost:27017/kitchensink" scripts/seed-members.js
```

The application defaults to `mongodb://localhost:27017/kitchensink` and listens on `http://localhost:9080`. Set `MONGODB_URI` if your local MongoDB uses a different connection string. For example:

```powershell
$env:MONGODB_URI = "mongodb://localhost:27017/kitchensink"
```

Spring Security creates a `user` account with a randomly generated password when no credentials are configured; the password appears in the startup log. Alternatively, set local credentials before starting the app:

```powershell
$env:SPRING_SECURITY_USER_NAME = "demo"
$env:SPRING_SECURITY_USER_PASSWORD = "replace-with-a-local-password"
mvn spring-boot:run
```

If you set a username such as `demo`, use that same username in API requests. Stop the application with `Ctrl+C`. Do not use demo credentials in production; supply credentials through a secrets manager or environment variables.

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

Compose waits for MongoDB's health check before starting the application. The app is available at `http://localhost:9080`. MongoDB seeds the sample member when its data volume is first initialized. Spring Security generates a development password and prints it in the application logs; the foreground `up` output shows it, or use a second terminal to run:

```powershell
docker compose logs app
```

Use username `user` and that generated password with the authenticated API examples below. Stop the foreground Compose process with `Ctrl+C`, then run `docker compose down` to remove the containers and network. Member data is stored in the `mongo_data` named volume and remains when containers are removed. To permanently delete that database and its seeded/member data too, run:

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

## Member API

All endpoints use the `/rest/members` path and exchange JSON.

For the API examples below, set the username and password configured when starting the application, then create the Basic Authentication header once in PowerShell. For Docker Compose, the username is `user` and the password is the generated value printed in the application logs:

```powershell
$pair = "user:YOUR_PASSWORD"
$encoded = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($pair))
$headers = @{ Authorization = "Basic $encoded" }
```

### List members

Returns members ordered by name. In PowerShell, use:

```powershell
Invoke-RestMethod -Uri "http://localhost:9080/rest/members" -Headers $headers
```

### Get a member

Returns the member with the requested MongoDB ObjectId. To get an ID, list the members:

```powershell
$members = Invoke-RestMethod -Uri "http://localhost:9080/rest/members" -Headers $headers
$members
$memberId = $members[0].id
Invoke-RestMethod -Uri "http://localhost:9080/rest/members/$memberId" -Headers $headers
```

### Register a member

Send a name, email, and phone number:

```powershell
$member = @{
    name = "Jane Doe"
    email = "jane@example.com"
    phoneNumber = "2125551234"
} | ConvertTo-Json
Invoke-RestMethod -Uri "http://localhost:9080/rest/members" -Method Post `
    -Headers $headers `
    -ContentType "application/json" -Body $member
```

A successful registration returns `201 Created`, the saved member as JSON, and a `Location` header for the new member. The API returns `400 Bad Request` with field errors when validation fails, `404 Not Found` when a requested ID does not exist, and `409 Conflict` when the email is already registered or another data integrity constraint is violated.

### Member validation

- `name` is required, must contain 1 to 25 letters and spaces, and cannot contain digits or special characters.
- `email` is required and must be a valid email address.
- `phoneNumber` is required and must contain 8 to 12 digits.
- Email addresses must be unique.

The API uses stateless HTTP Basic authentication. Requests without valid credentials receive `401 Unauthorized`. CSRF protection is disabled because the API is stateless and does not use browser sessions.

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
