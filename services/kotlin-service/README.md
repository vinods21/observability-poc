# Kotlin Service

Spring Boot Kotlin microservice for the distributed tracing POC. It accepts a request from the Java service, continues the incoming trace, touches Redis, writes an object to an S3-compatible store, and exports traces over OTLP so an OpenTelemetry Collector can forward them to Jaeger.

## Endpoints

- `POST /api/v1/traces/process` processes a payload and emits Redis and S3 spans within the current trace.
- `GET /api/v1/traces/ping` returns a lightweight service heartbeat.
- `GET /actuator/health`, `GET /actuator/health/liveness`, and `GET /actuator/health/readiness` expose basic health signals.

## Environment Variables

- `SERVER_PORT` default `8081`
- `REDIS_HOST` default `localhost`
- `REDIS_PORT` default `6379`
- `REDIS_PASSWORD` default empty
- `S3_ENDPOINT` default `http://localhost:9000`
- `S3_REGION` default `us-east-1`
- `S3_BUCKET` default `trace-artifacts`
- `AWS_ACCESS_KEY_ID` default `minio`
- `AWS_SECRET_ACCESS_KEY` default `minio123`
- `OTEL_EXPORTER_OTLP_ENDPOINT` default `http://localhost:4318`
- `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` default `http://localhost:4318/v1/traces`
- `OTEL_SERVICE_NAME` default `kotlin-service`
- `OTEL_SERVICE_NAMESPACE` default `distributed-tracing-poc`
- `APP_ENV` default `local`

## Local Run

```bash
./gradlew bootRun
```

Example request:

```bash
curl -X POST http://localhost:8081/api/v1/traces/process \
  -H "Content-Type: application/json" \
  -d '{"requestId":"demo-123","message":"hello from java-service"}'
```

The Java service should call this endpoint and forward standard W3C trace headers such as `traceparent`.
