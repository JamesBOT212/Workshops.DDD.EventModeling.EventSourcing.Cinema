# Vertical Cinema (Kotlin)

Shows how to use Domain-Driven Design, Event Storming, Event Modeling and Event Sourcing in the small Cinema domain.

MIRO Board with the complete Event Modeling:
[CLICK](https://miro.com/app/board/uXjVJNu8wic=/?share_link_id=289537667245)
(you will receive the password during the workshop)

## 🚀 How to run the project?

### Locally (recommended)

1. Install any distribution of Java. At least version 21 (on MacOS I recommend [SDKMAN](https://sdkman.io/install/) to
   do that) on your machine and Docker.
2. Install [Docker Compose](https://docs.docker.com/compose/install/)
3. Clone the repository
4. Make sure that Docker Daemon is running on your machine executing: `docker ps`. If you don't see output like `Cannot connect to the Docker daemon` you're good to go.
5. Build the project executing: `./gradlew build -x test`
6. Run tests after changes with `./gradlew test`
7. If you want to run the application, execute: `docker compose -f docker-compose.axon.yaml up` and `./gradlew bootRun`

To develop the application is the best to use the Intellij IDEA IDE. 
- Ultimate version with 30-days trial: [https://www.jetbrains.com/idea/download/](https://www.jetbrains.com/idea/download/)
- Community version: [https://www.jetbrains.com/idea/download](https://www.jetbrains.com/idea/download)

### Devcontainers (experimental)
[Development Containers](https://containers.dev/) - An open specification for enriching containers with development specific content and settings.
If you don't want to install anything on your machine, you can use the devcontainer from the `.devcontainer` directory.
But it gives you slower build/test times than running the project locally.

How to run devcontainer? It depends on your IDE:
- GitHub Codespaces - just go to the repository page, click the `Code` button, select `Codespaces` tab and run new environment.
- IntelliJ IDEA - open the project in IntelliJ IDEA, right click on `.devcontainer/devcontainer.json` and select `Dev Containers` -> `Create Dev Container and Clone Sources`
- Visual Studio Code ....

## 🌐 Interacting with the Application

### REST API
Access the REST API documentation at: [http://localhost:3883/swagger-ui/index.html](http://localhost:3773/swagger-ui/index.html)

### Model Context Protocol (MCP) Server
The application exposes a Model Context Protocol Server that allows AI assistants to interact directly with the domain. You can add it to Claude Code with:

```bash
claude mcp add --transport sse Cinema http://localhost:3883/sse
```

## 🗄️ Querying Events in PostgreSQL Event Store

When using PostgreSQL as the event store (instead of Axon Server), events are stored in the `domain_event_entry` table. The `payload` and `meta_data` columns use PostgreSQL's Large Object (LOB) storage with `oid` type.

### Database Connection
```bash
# Connect to the PostgreSQL database
psql -h localhost -p 6556 -U cinema_user -d cinema_db
```

### Basic Event Queries

**View recent events with readable content:**
```sql
SELECT
    global_index,
    event_identifier,
    payload_type,
    aggregate_identifier,
    sequence_number,
    time_stamp,
    convert_from(lo_get(payload), 'UTF8') as payload_content,
    convert_from(lo_get(meta_data), 'UTF8') as meta_data_content
FROM domain_event_entry
ORDER BY global_index DESC
LIMIT 10;
```

**View raw binary data:**
```sql
SELECT
    global_index,
    event_identifier,
    payload_type,
    lo_get(payload) as payload_bytes,
    lo_get(meta_data) as meta_data_bytes
FROM domain_event_entry
ORDER BY global_index DESC
LIMIT 5;
```

### Filtering Events

**Events by specific aggregate:**
```sql
SELECT
    global_index,
    payload_type,
    convert_from(lo_get(payload), 'UTF8') as payload_content
FROM domain_event_entry
WHERE aggregate_identifier = 'your-aggregate-id'
ORDER BY sequence_number;
```

**Events by type pattern:**
```sql
SELECT
    global_index,
    event_identifier,
    payload_type,
    convert_from(lo_get(payload), 'UTF8') as payload_content
FROM domain_event_entry
WHERE payload_type LIKE '%SeatBlocked%'
ORDER BY global_index DESC;
```

**Events within time range:**
```sql
SELECT
    global_index,
    payload_type,
    time_stamp,
    convert_from(lo_get(payload), 'UTF8') as payload_content
FROM domain_event_entry
WHERE time_stamp >= '2025-01-01T00:00:00.000Z'
ORDER BY global_index DESC;
```

### Understanding the Schema

- **`global_index`**: Unique sequential identifier across all events
- **`event_identifier`**: UUID for the specific event instance
- **`payload_type`**: Fully qualified class name of the event (e.g., `com.dddheroes.cinema.seatsblocking.events.SeatBlocked`)
- **`payload`**: Event data stored as Large Object (JSON serialized by Axon)
- **`meta_data`**: Event metadata stored as Large Object (timestamps, correlation IDs, etc.)
- **`aggregate_identifier`**: ID of the aggregate that produced the event
- **`sequence_number`**: Event sequence within the aggregate (starts from 0)
- **`time_stamp`**: ISO 8601 timestamp when event was stored

### Large Object Functions

PostgreSQL Large Objects require special functions:
- **`lo_get(oid)`**: Retrieves the binary content of a Large Object
- **`convert_from(bytea, encoding)`**: Converts binary data to text using specified encoding
- Use `'UTF8'` encoding since Axon stores JSON-serialized events