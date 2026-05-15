# Shared Observability Conventions

These conventions apply to all services in this repository, regardless of language.

## Export Pipeline

- Export traces via OTLP to `OTEL_EXPORTER_OTLP_ENDPOINT`
- Prefer OTLP/HTTP for local simplicity unless a service has a strong reason to use OTLP/gRPC
- Do not export directly to Jaeger from services unless explicitly required for a separate experiment

## Propagation

- Use standard W3C trace context propagation
- Preserve inbound trace context on outgoing HTTP calls
- Avoid custom propagation headers unless a specific external integration requires them

## Naming

- Use lowercase, hyphenated `service.name` values
- Recommended names:
  - `java-service`
  - `kotlin-service`
- Set `service.namespace=distributed-tracing-poc`
- Set `deployment.environment=local` for this POC

## Service Onboarding Checklist

When adding a new Java, Kotlin, Go, or Python service:

1. Configure OTLP export to the shared collector
2. Configure resource attributes for service name, namespace, and environment
3. Ensure incoming HTTP requests create or continue spans
4. Ensure outgoing HTTP clients propagate trace context
5. Add spans around key data-store or messaging operations where automatic instrumentation is unavailable
