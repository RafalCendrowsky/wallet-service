# Wallet Service

Kotlin Spring Boot service for wallet account management and ledger-backed money movement.

## Stack

- Kotlin 2.2
- Spring Boot 4
- Java 24
- PostgreSQL
- Flyway
- jOOQ
- Gradle

## Features

- Customer and account creation
- Account status management
- Deposits, withdrawals, and transfers
- Immutable ledger entries
- Balance projection
- Funds holds with capture and release
- Idempotency handling for transfers
- OpenAPI documentation

## Requirements

- JDK 24
- Docker

## Database

Start PostgreSQL:

```powershell
docker compose up -d postgres
```

The local database is configured as:

```text
url: jdbc:postgresql://localhost:5432/wallet
user: wallet
password: wallet
```

## Build

```powershell
.\gradlew.bat build
```

## Run

```powershell
.\gradlew.bat bootRun
```

Swagger UI is available at:

```text
http://localhost:8080/swagger-ui/index.html
```

## Test

```powershell
.\gradlew.bat test
```

Integration tests use Testcontainers with PostgreSQL.