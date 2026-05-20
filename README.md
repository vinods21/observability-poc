# Distributed Tracing POC

This repository contains a polyglot microservices proof of concept for distributed tracing. The design is centered on OpenTelemetry over OTLP into an OpenTelemetry Collector, with Jaeger used for trace inspection. That keeps the observability path language-agnostic so future Go or Python services can join without redesigning the stack.

## Architecture

- `services/java-service`: Java microservice, Gradle-based, owns the client-facing entrypoint and PostgreSQL interaction
- `services/kotlin-service`: Kotlin microservice, Gradle-based, owns Redis and S3-compatible object store interaction
- `infrastructure/otel`: OpenTelemetry Collector configuration
- `shared/conventions`: shared service and observability conventions for future services

## Local Stack

The repository provides Docker Compose infrastructure for:

- PostgreSQL
- Redis
- MinIO as the S3-compatible object store
- OpenTelemetry Collector
- Jaeger
- Prometheus
- Grafana
- Java service
- Kotlin service

Start the full stack:

```powershell
docker compose up -d --build
```

Jaeger UI is available at [http://localhost:16686](http://localhost:16686).
Prometheus UI is available at [http://localhost:9090](http://localhost:9090).
Grafana is available at [http://localhost:3000](http://localhost:3000) with `admin` / `admin`.
MinIO Console is available at [http://localhost:9001](http://localhost:9001).

## Configuration

Copy `.env.example` values into your local shell environment or a `.env` file before running services. The most important shared settings are:

- `OTEL_EXPORTER_OTLP_ENDPOINT`
- `OTEL_EXPORTER_OTLP_PROTOCOL`
- `OTEL_RESOURCE_ATTRIBUTES`
- `KOTLIN_SERVICE_BASE_URL`

The application entrypoints exposed from Docker Compose are:

- Java service: `http://localhost:8080`
- Kotlin service: `http://localhost:8081`
- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000`

## Trace Demo Flow

The intended request path is:

1. Client calls the Java service
2. Java service persists or reads data in PostgreSQL
3. Java service calls the Kotlin service
4. Kotlin service touches Redis
5. Kotlin service writes or reads an object in MinIO
6. The complete request appears as one distributed trace in Jaeger
7. Prometheus scrapes both services from `/actuator/prometheus`
8. Grafana visualizes the JVM and HTTP metrics with Jaeger available as a second datasource

Example request:

```powershell
curl -X POST http://localhost:8080/api/v1/trace-demo `
  -H "Content-Type: application/json" `
  -d "{\"message\":\"hello from client\"}"
```

To stop everything:

```powershell
docker compose down
```

## Extending With Go or Python

Any future service should:

1. Accept W3C trace context headers
2. Export OTLP traces to the same collector endpoint
3. Reuse the naming and resource conventions in `shared/conventions`
4. Avoid direct coupling to JVM-only tracing libraries or assumptions
