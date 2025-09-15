# Dev Container Configuration

This folder contains the Dev Container configuration for the Cinema Event Sourcing project, compatible with both local development and **GitHub Codespaces**.

## Features

- **Java 21** - Latest LTS Java version
- **Kotlin** - Full Kotlin language support
- **Docker-in-Docker** - Ability to run Docker commands inside the container
- **Gradle** - Build tool with caching enabled
- **Testcontainers Ready** - Full support for integration testing
- **Multi-IDE Support** - Works with VS Code and IntelliJ IDEA

## Services

The devcontainer automatically starts these services inside the container:

- **Cinema App**: `localhost:3883` - Main application
- **PostgreSQL**: `localhost:6556` - Database (cinema_db)
- **Axon Server HTTP**: `localhost:8024` - Axon management interface
- **Axon Server gRPC**: `localhost:8124` - Axon event store communication

## Automatic Setup

When the devcontainer starts:

1. **Create**: Sets up Gradle wrapper and permissions
2. **Post-Create**: Starts PostgreSQL and Axon Server inside container
3. **Build**: Runs `./gradlew build` to compile the project
4. **Ready**: Container is ready for development and testing

## IDE Support

### VS Code Extensions
Pre-installed extensions:
- Java Extension Pack
- Kotlin Language Server
- Gradle for Java
- Docker
- YAML support

### IntelliJ IDEA
Configured plugins:
- Java support
- Kotlin language server
- Gradle integration
- Database tools
- Docker integration

## Volumes

- `cinema-gradle-cache`: Persistent Gradle cache for faster builds
- `dind-var-lib-docker`: Docker-in-Docker storage

## Usage

### Local Development
1. Open the project in VS Code or IntelliJ IDEA
2. When prompted, select "Reopen in Container" (VS Code) or use Gateway (IntelliJ)
3. Wait for the container to build and initialize
4. Start coding! Services are running and tests can use Testcontainers.

### GitHub Codespaces
1. Create a new Codespace from your repository
2. Wait for automatic setup to complete
3. All services will be running and accessible
4. Port forwarding is automatically configured

## Testcontainers Integration

The configuration is optimized for Testcontainers:
- Docker-in-Docker enables container-based testing
- PostgreSQL and Axon Server are available for integration tests
- No additional configuration needed in test code

## Troubleshooting

### Check Services Status
```bash
# Check if services are running
docker compose -f docker-compose.axon.yaml ps

# Restart services if needed
docker compose -f docker-compose.axon.yaml restart
```

### Codespaces Specific
- Port forwarding is automatic
- Services run inside the Codespace container
- Access external services via forwarded ports in browser