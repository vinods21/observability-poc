# Spring Boot Observability Guide

This document describes the generic steps needed to enable distributed tracing and metrics for Spring Boot applications. It is written to be reusable across teams and environments, whether services run locally, in containers, or on Kubernetes.

## Goals

For most Spring Boot services, observability should provide:

- Distributed tracing across incoming requests, internal business operations, outgoing HTTP calls, messaging, and data-store interactions
- Application and infrastructure metrics that can be scraped and visualized
- Consistent service identity and resource metadata across environments
- A collector-based export path so Java, Kotlin, Go, Python, and other services can share the same backend

## Recommended Architecture

Use this high-level flow:

1. Spring Boot app emits traces and metrics
2. Traces are exported via OpenTelemetry Protocol (OTLP) to an OpenTelemetry Collector
3. Metrics are exposed on an HTTP endpoint for Prometheus to scrape
4. Prometheus stores metrics
5. Grafana visualizes metrics and links to tracing backends such as Jaeger, Tempo, or another tracing store

Recommended defaults:

- Traces: OTLP to OpenTelemetry Collector
- Metrics: Prometheus scrape from `/actuator/prometheus`
- Visualization: Grafana
- Trace UI: Jaeger or a compatible tracing backend

## Step 1: Add The Required Dependencies

At minimum, a Spring Boot service usually needs:

- `spring-boot-starter-actuator`
- `micrometer-tracing-bridge-otel`
- `opentelemetry-exporter-otlp`
- `micrometer-registry-prometheus`

Example Gradle dependencies:

```gradle
dependencies {
    implementation "org.springframework.boot:spring-boot-starter-actuator"
    implementation "io.micrometer:micrometer-tracing-bridge-otel"

    runtimeOnly "io.opentelemetry:opentelemetry-exporter-otlp"
    runtimeOnly "io.micrometer:micrometer-registry-prometheus"
}
```

If the service uses web, data, messaging, or clients, include the relevant Spring starters as usual. Observability builds on top of them.

## Step 2: Expose Actuator Endpoints

Expose only the endpoints you need. A common baseline is:

- `health`
- `info`
- `prometheus`

Example configuration:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  endpoint:
    health:
      probes:
        enabled: true
```

Notes:

- Keep actuator exposure minimal in production
- Put actuator behind authentication or network controls where required
- Enable readiness and liveness probes for orchestrated environments

## Step 3: Configure Tracing Export

Spring Boot can emit traces through Micrometer Tracing with OpenTelemetry export. A simple baseline is:

```yaml
management:
  tracing:
    sampling:
      probability: 1.0
  otlp:
    tracing:
      endpoint: ${OTEL_EXPORTER_OTLP_TRACES_ENDPOINT:http://localhost:4318/v1/traces}
      transport: http
```

Guidance:

- Use `1.0` sampling for local development and POCs
- Lower sampling in production unless full-fidelity traces are required
- Prefer exporting to an OpenTelemetry Collector instead of directly to Jaeger

## Step 4: Define Service Identity And Resource Metadata

Every service should consistently identify itself. The most important fields are:

- `service.name`
- `service.namespace`
- `deployment.environment`
- optional team, region, cluster, version, and instance metadata

Typical environment variables:

```text
SPRING_APPLICATION_NAME=orders-service
OTEL_SERVICE_NAME=orders-service
OTEL_RESOURCE_ATTRIBUTES=service.namespace=commerce,deployment.environment=prod
```

Recommended naming rules:

- Use lowercase service names
- Keep names stable across deployments
- Use namespace to group related services
- Keep environment names consistent: `local`, `dev`, `qa`, `stage`, `prod`

## Step 5: Ensure Trace Context Propagation

Distributed tracing only works if incoming and outgoing requests carry trace context.

Use these principles:

- Accept W3C trace context headers on inbound requests
- Propagate trace context on outbound HTTP calls
- Preserve context across async boundaries when possible
- Apply the same pattern to messaging systems if used

For typical Spring MVC or WebFlux HTTP services, Spring Boot and Micrometer tracing handle a large portion of this automatically, as long as tracing is enabled and the HTTP client is built through Spring-managed infrastructure.

## Step 6: Instrument Business Logic

Automatic instrumentation is not enough on its own. You should add spans around business operations that matter.

Examples:

- workflow orchestration methods
- external system calls
- cache reads and writes
- important transformations
- domain-specific decision points

A common pattern is to wrap service-layer methods in a helper like `withSpan(...)`.

Example:

```java
private <T> T withSpan(String spanName, ObservationRegistry registry, Supplier<T> action) {
    return Observation.createNotStarted(spanName, registry)
            .contextualName(spanName)
            .observe(action);
}
```

Use spans for:

- meaningful units of work
- operations you may need to debug later
- boundaries where latency or failures matter

Avoid:

- creating spans for trivial getters or tiny utility methods
- adding so many spans that traces become noisy and expensive

## Step 7: Instrument Database Calls

Database observability should include both high-level request spans and lower-level SQL visibility.

There are two common approaches:

1. Use automatic JDBC instrumentation where it fits your stack
2. Add manual spans around SQL operations when you need more control

Useful span attributes:

- `db.system`
- `db.operation`
- `db.collection.name` or table name
- `db.query.text` when allowed by policy

Be careful with query text:

- do not record secrets
- avoid logging raw personally identifiable information
- sanitize dynamic values when necessary

If your service has multi-step persistence workflows, span each important operation separately so traces show the real behavior of the transaction.

## Step 8: Expose Metrics For Prometheus

Once `micrometer-registry-prometheus` is present and actuator exposes the Prometheus endpoint, the service publishes metrics at:

```text
/actuator/prometheus
```

Prometheus then scrapes the service on a fixed interval.

Example scrape config:

```yaml
scrape_configs:
  - job_name: orders-service
    metrics_path: /actuator/prometheus
    static_configs:
      - targets:
          - orders-service:8080
```

## Step 9: Add Common Metric Tags

Consistent tags make dashboards and alerts much easier to reuse.

Recommended common tags:

- `application`
- `environment`
- `namespace`

Example:

```yaml
management:
  metrics:
    tags:
      application: ${spring.application.name}
      environment: ${APP_ENV:local}
      namespace: ${OTEL_SERVICE_NAMESPACE:default}
```

Notes:

- Keep tag cardinality low
- Do not use user IDs, request IDs, or other high-cardinality values as metric labels
- Reserve high-cardinality detail for traces and logs, not metrics

## Step 10: Provision Prometheus And Grafana

A standard deployment usually includes:

- Prometheus configured with service scrape targets
- Grafana configured with Prometheus as a datasource
- Grafana optionally configured with Jaeger, Tempo, or another tracing datasource

Typical Grafana dashboards for Spring Boot services include:

- request rate
- request latency
- error rate
- JVM heap usage
- CPU usage
- thread counts
- connection pool metrics
- HTTP traffic breakdown by route and status

## Step 11: Validate The End-To-End Flow

Do not stop at configuration. Validate the behavior.

Checklist:

1. Hit a service endpoint locally
2. Confirm a trace appears in the tracing backend
3. Confirm the trace contains expected child spans
4. Confirm `/actuator/prometheus` exposes metrics
5. Confirm Prometheus target status is `up`
6. Confirm Grafana panels show fresh data
7. Confirm service identity tags match expected values

## Step 12: Standardize Across Services

If your platform has multiple Spring Boot services, define shared conventions early.

Standardize:

- dependency set
- actuator exposure defaults
- OTLP export destination
- service naming
- resource attributes
- metric tags
- span naming
- business instrumentation patterns

This is what makes the stack extensible when new services are added in other languages.

## Production Considerations

Keep these in mind before moving from a POC to production:

- Sampling: lower the trace sampling rate if cost or scale requires it
- Security: protect actuator endpoints and sanitize telemetry payloads
- Cardinality: avoid high-cardinality metric labels
- Sensitive data: do not put secrets, tokens, or PII into span attributes or metrics
- Reliability: use a collector so backend changes do not require app changes
- Performance: benchmark tracing overhead under load
- Retention: define storage and retention policies for both traces and metrics
- Alerting: create alerts on latency, error rate, saturation, and scrape failures

## Common Pitfalls

- Exporting traces directly to a backend from each app instead of using a collector
- Forgetting to expose `/actuator/prometheus`
- Using different service names for traces and metrics
- Missing trace propagation on outbound clients
- Recording high-cardinality metric labels
- Relying only on automatic instrumentation with no business-level spans
- Logging raw SQL values or sensitive request content into telemetry
- Failing to validate that dashboards actually show current data

## Minimal Checklist

Use this as a generic onboarding checklist for a new Spring Boot service:

1. Add actuator, OTLP tracing, and Prometheus registry dependencies
2. Expose `health`, `info`, and `prometheus`
3. Configure OTLP trace export to a collector
4. Set service name, namespace, and environment metadata
5. Ensure HTTP client propagation works
6. Add business-level spans around important workflows
7. Instrument important DB, cache, or messaging operations
8. Expose `/actuator/prometheus`
9. Add low-cardinality common metric tags
10. Add Prometheus scrape config
11. Add Grafana dashboards and datasource wiring
12. Verify traces and metrics with live traffic

## Suggested Reference Implementation Pattern

For most teams, a strong default is:

- Spring Boot Actuator for metrics and health
- Micrometer Tracing bridge to OpenTelemetry
- OTLP export to OpenTelemetry Collector
- Prometheus scraping actuator metrics
- Grafana dashboards for metrics
- Jaeger or another trace backend for distributed trace inspection

That architecture keeps Spring Boot apps simple while leaving room for polyglot growth later.
