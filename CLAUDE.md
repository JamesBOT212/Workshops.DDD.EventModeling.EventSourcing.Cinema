# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Cinema Event Sourcing system built with **Kotlin**, **Spring Boot**, **Axon Framework 4**, and **PostgreSQL**. The application demonstrates **Vertical Slice Architecture** principles with **Domain-Driven Design** for a cinema booking system.

### Domain Boundaries

The system is organized into distinct bounded contexts:

- **seatsblocking** - Seat blocking/unblocking management
- **dayschedule** - Screening scheduling and cancellation
- **payments** - Payment processing
- **reservations** - Reservation lifecycle management
- **adminnotifications** - Admin notification system
- **issues** - Issue reporting and AI-powered forwarding

Each domain follows a **read/write** separation pattern with dedicated directories for:
- `write/` - Command handlers and business logic
- `read/` - Query handlers and projections
- `events/` - Domain events
- `automation/` - Process managers and sagas

## Build and Development Commands

### Core Commands
```bash
# Build the project
./gradlew build

# Run the application
./gradlew bootRun

# Run all tests
./gradlew test

# Run a specific test class
./gradlew test --tests "ClassName"

# Run tests for a specific package
./gradlew test --tests "com.dddheroes.cinema.seatsblocking.*"
```

### Database Setup
The application requires PostgreSQL. Use Docker Compose for local development:
```bash
# Start PostgreSQL and Axon Server containers
docker compose -f docker-compose.axon.yaml up -d

# The application connects to:
# - PostgreSQL: localhost:6556
# - Database: cinema_db
# - Username: cinema_user
# - Password: cinema_password
```

## Architecture Notes

### Technology Stack
- **Kotlin 2.2.10** with **Java 21**
- **Spring Boot 3.5.5** with virtual threads enabled
- **Axon Framework 4.12.1** (configurable: embedded mode or with Axon Server)
- **Spring Data JPA** with **Hibernate**
- **PostgreSQL** for persistence
- **Testcontainers** for integration testing
- **AssertK 0.28.1** for fluent assertions
- **Spring AI** with Anthropic Claude integration

### Testing Framework
- **JUnit 5** as the test runner
- **Axon Test** framework for testing command handlers and sagas
- **Spring Boot Test** with Testcontainers support
- Tests follow GIVEN-WHEN-THEN patterns for Event Sourcing scenarios

### Code Organization Patterns

1. **Vertical Slices**: Each feature is self-contained within its own package with write/read/automation/events subdirectories
2. **CQRS with Event Sourcing**: Commands and queries are separated, using Axon Framework
3. **Domain Events**: Each bounded context publishes domain events for inter-context communication
4. **Process Managers**: Automation packages contain process managers coordinating between contexts
5. **Feature Toggles**: Each slice can be enabled/disabled via configuration properties

### Key Configuration
- **Server Port**: 3883
- **Database**: PostgreSQL with JPA/Hibernate
- **Serialization**: Jackson for all Axon serializers (events, messages, general)
- **Virtual Threads**: Enabled for improved concurrency
- **Swagger UI**: Available at `/swagger-ui.html`
- **Axon Server**: Disabled by default (embedded mode), can be enabled via `axon.axonserver.enabled: true`

## Domain Simplifications

- Single screen cinema (one movie at a time)
- Operating hours: 9:00-21:00 daily
- 10x10 seat grid (100 seats per screening)
- Simplified validation and business rules for demonstration purposes