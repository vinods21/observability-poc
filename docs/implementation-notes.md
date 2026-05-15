# Implementation Notes

## Chosen Direction

- Framework: Spring Boot for both JVM services
- Build tool: Gradle
- Object store: MinIO
- Telemetry backend path: Services -> OTel Collector -> Jaeger

## Why The Collector Is Included

Using an OpenTelemetry Collector keeps the architecture extensible. New services in Go or Python only need to send OTLP to the collector, rather than each service embedding backend-specific exporter logic.

## Service Responsibilities

- Java service:
  - Entry API
  - PostgreSQL persistence
  - Outbound call to Kotlin service
- Kotlin service:
  - Redis access
  - MinIO object interaction
  - Downstream response payload
