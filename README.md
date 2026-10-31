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

Seed 75 sample members from the project root. This command is safe to run more than once and does not overwrite existing member records:

```powershell
mongosh "mongodb://localhost:27017/kitchensink" scripts/seed-members.js
```

The application defaults to `mongodb://localhost:27017/kitchensink` and listens on `http://localhost:9080`. Set `MONGODB_URI` if your local MongoDB uses a different connection string. For example:

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

The browser UI has two main pages:

- `/` shows the member registration form and up to five most-viewed members from an in-memory cache. The cache starts empty on application startup and is populated as member details are viewed; it resets when the application restarts.
- `/members` shows a two-pane member browser: the paginated, searchable list is on the left and selected member details are on the right. The list is ordered by name and loads 20 records per MongoDB page.
- Selecting a member name opens its details in the right pane and increments its in-memory view count. Phone numbers are only shown in the details pane.

The All Members page supports exact email search, shows only the match with its details, and offers a clear-search link to restore the full paginated list. An unsuccessful search displays a dismissible warning. Member list rows show only name and email. The home-page form validates name, email, and phone in the browser as the user types and blocks invalid submissions; server-side validation remains enabled as a safety net. Pages use Thymeleaf and are available in a browser on the same machine as the app.

## Member API

All endpoints use the `/rest/members` path and exchange JSON.

For the API examples below, create the Basic Authentication header using the configured credentials. For the local defaults:

```powershell
$pair = "demo.user@gmail.com:password@001"
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
