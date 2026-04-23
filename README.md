# Rinoimob Backend

Spring Boot 3.x backend service for the Rinoimob property management platform.

## Prerequisites

- Java 17 or higher
- Maven 3.8.1 or higher
- PostgreSQL 12 or higher

## Setup

1. Clone the repository
2. Copy `.env.example` to `.env` and configure your environment variables
3. Ensure PostgreSQL is running and accessible
4. Run `mvn clean install` to build the project

## Running Locally

```bash
mvn spring-boot:run
```

The application will start on `http://localhost:39000`

## Building

```bash
mvn clean package
```

This creates a JAR file in the `target` directory.

## Testing

```bash
mvn test
```

## Environment Variables

See `.env.example` for available configuration options:
- `DB_URL`: PostgreSQL connection URL
- `DB_USER`: Database user
- `DB_PASSWORD`: Database password
- `SPRING_PROFILE`: Active Spring profile (dev, test, prod)

## Project Structure

```
src/
├── main/
│   ├── java/com/rinoimob/
│   │   ├── RinoimobApplication.java
│   │   ├── api/              # REST Controllers
│   │   ├── domain/           # JPA Entities
│   │   ├── service/          # Business Logic
│   │   └── config/           # Configuration Classes
│   └── resources/
│       ├── application.yml   # Base configuration
│       ├── application-dev.yml
│       └── logback-spring.xml
└── test/
    └── java/com/rinoimob/   # Test classes
```

Java Spring Boot API for Rinoimob - Multi-tenant property management platform.

## Tech Stack
- Java 17+
- Spring Boot 3.x
- PostgreSQL
- Redis
- RabbitMQ
- Maven

## Getting Started

```bash
mvn clean install
mvn spring-boot:run
```

## Project Structure
- `src/main/java/com/rinoimob/` - Application source code
- `src/test/java/` - Test code
- `src/main/resources/` - Configuration files
- `docs/` - API documentation

## Documentation
See [../rinoimob-docs](../rinoimob-docs) for architecture and API details.

## Issues
All development tasks are tracked in [.projects](https://github.com/revenlab/.projects/issues?q=label%3Abackend).
