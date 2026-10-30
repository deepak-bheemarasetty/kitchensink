# Kitchen Sink

Kitchen Sink is a Spring Boot REST application for managing member registrations. It provides endpoints to list members, retrieve a member by ID, and register a member. Member data is stored with Spring Data JPA in an in-memory H2 database.

## Requirements

- JDK 21
- Maven 3.9 or later

Confirm Java and Maven are available:

```shell
java -version
mvn -version
```

## Run locally

From the project root, start the application with:

```shell
mvn spring-boot:run
```

The application listens on `http://localhost:9080`. Stop it with `Ctrl+C`.

H2 is in memory and belongs to this application process. Its data is cleared when the application stops. Each running application has its own in-memory database.

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

### List members

Returns members ordered by name:

```shell
curl http://localhost:9080/rest/members
```

### Get a member

Returns the member with the requested numeric ID. The seeded member has ID `0`:

```shell
curl http://localhost:9080/rest/members/0
```

### Register a member

Send a name, email, and phone number:

```shell
curl -i -X POST http://localhost:9080/rest/members \
  -H 'Content-Type: application/json' \
  -d '{"name":"Jane Doe","email":"jane@example.com","phoneNumber":"2125551234"}'
```

A successful registration returns `201 Created`, the saved member as JSON, and a `Location` header for the new member. The API returns `400 Bad Request` with field errors when validation fails, `404 Not Found` when a requested ID does not exist, and `409 Conflict` when the email is already registered or another data integrity constraint is violated.

### Member validation

- `name` is required, must contain 1–25 characters, and cannot contain digits.
- `email` is required and must be a valid email address.
- `phoneNumber` is required and must contain 10–12 digits.
- Email addresses must be unique.

## H2 console

While the application is running, the H2 console is available at `http://localhost:9080/h2-console`.

Use these connection settings:

- JDBC URL: `jdbc:h2:mem:kitchensink`
- Username: `sa`
- Password: `sa`

These are local development settings. The console and credentials are not intended as a production database setup.

## Tests

Run the JUnit 5 suite:

```shell
mvn test
```

The suite covers controller responses and validation, registration rules, and repository queries against isolated in-memory H2 databases.

## Configuration

Application settings are in `src/main/resources/application.properties`. The HTTP port, H2 connection, schema lifecycle, and H2 console are configured there. Hibernate creates the schema at startup and drops it when the application stops.
