# Java Service

Spring Boot service for the distributed tracing POC. It accepts a client request, persists request metadata in PostgreSQL, calls the Kotlin service over HTTP with propagated trace context, and exports traces over OTLP to an OpenTelemetry Collector.

## Endpoints

- `POST /api/v1/trace-demo`
- `GET /api/v1/trace-demo/{requestId}`
- `GET /actuator/health`
- `GET /actuator/health/liveness`
- `GET /actuator/health/readiness`

Example request:

```bash
curl -X POST http://localhost:8080/api/v1/trace-demo ^
  -H "Content-Type: application/json" ^
  -d "{\"message\":\"hello from client\"}"
```

## Environment Variables

- `SPRING_APPLICATION_NAME` default: `java-service`
- `SERVER_PORT` default: `8080`
- `POSTGRES_URL` default: `jdbc:postgresql://localhost:5432/tracing_poc`
- `POSTGRES_HOST` default: `localhost`
- `POSTGRES_PORT` default: `5432`
- `POSTGRES_DB` default: `tracing_poc`
- `POSTGRES_USERNAME` default: `tracing`
- `POSTGRES_PASSWORD` default: `tracing`
- `KOTLIN_SERVICE_BASE_URL` default: `http://localhost:8081`
- `KOTLIN_SERVICE_TRACE_PATH` default: `/api/v1/traces/process`
- `OTEL_EXPORTER_OTLP_ENDPOINT` default: `http://localhost:4318/v1/traces`
- `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` optional override for traces only
- `OTEL_RESOURCE_ATTRIBUTES` recommended for shared metadata such as `deployment.environment=local,service.namespace=distributed-tracing-poc`
- `TRACING_SAMPLING_PROBABILITY` default: `1.0`

## Run

```bash
gradle wrapper
.\gradlew.bat bootRun
```

The Kotlin service should expose the path configured by `KOTLIN_SERVICE_TRACE_PATH` and accept a JSON body with `requestId` and `message` so the end-to-end trace remains connected across both services.
