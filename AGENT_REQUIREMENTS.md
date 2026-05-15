# Agent-Build Requirements: Distributed Tracing POC

## 1. Purpose

Build a proof of concept (POC) for distributed tracing across a small microservices system. The primary goal is to validate end-to-end trace propagation, storage, and visualization across heterogeneous services and infrastructure components.

This document is intended to be the authoritative requirements file for human contributors and autonomous agents that will design, scaffold, implement, test, and evolve the solution.

## 2. Desired Outcome

Deliver a runnable local development environment containing:

1. Two microservices:
   - One implemented in Java
   - One implemented in Kotlin
2. Gradle as the build tool for JVM services
3. Supporting infrastructure:
   - PostgreSQL
   - Redis
   - S3-compatible object storage
   - Jaeger for distributed trace visualization
4. OpenTelemetry-based instrumentation and trace propagation between services
5. A design that can be extended to additional services in Go, Python, or other languages without replacing the observability stack

## 3. Scope

### In Scope

- Local POC architecture for distributed tracing
- Service-to-service HTTP communication
- Trace context propagation across service boundaries
- Trace emission to Jaeger
- Persistence using PostgreSQL
- Cache or ephemeral state using Redis
- Binary/object persistence using an S3-compatible store
- Containerized infrastructure suitable for local development
- Basic scalability and extensibility considerations
- Shared conventions that future services must follow

### Out of Scope

- Production-grade security hardening
- Multi-region deployment
- Full CI/CD implementation
- Advanced service mesh adoption
- Load testing at production scale
- Full business feature set beyond what is required to exercise traces

## 4. Architecture Requirements

The solution must follow a modular microservices architecture with explicit boundaries.

### Required Services

#### Service A: Java Service

- Language: Java
- Build tool: Gradle
- Responsibility:
  - Expose a REST API
  - Receive inbound requests from a client
  - Call the Kotlin service over HTTP
  - Interact with at least one infrastructure dependency
  - Start or continue distributed traces

#### Service B: Kotlin Service

- Language: Kotlin
- Build tool: Gradle
- Responsibility:
  - Expose a REST API
  - Receive requests from the Java service
  - Interact with at least one infrastructure dependency
  - Continue propagated traces
  - Return a response that allows the full request chain to be observed

### Infrastructure Components

#### PostgreSQL

- Used by at least one service for durable relational persistence
- Should be reachable through configuration, not hardcoded connection details

#### Redis

- Used by at least one service for caching, coordination, or short-lived state
- Should be independently swappable through configuration

#### S3-Compatible Object Store

- Use any S3-compatible local object storage solution
- Suitable examples include MinIO or LocalStack S3-compatible endpoints
- At least one service must read or write an object to exercise tracing around object storage operations

#### Jaeger

- Must receive and display distributed traces
- Should support local developer inspection through a web UI

## 5. Observability Requirements

Observability is the core requirement of this POC.

### Mandatory Requirements

- Use OpenTelemetry for tracing
- Propagate trace context across all service-to-service calls
- Export traces to Jaeger
- Include spans for:
  - Incoming HTTP requests
  - Outgoing HTTP calls between services
  - Database operations where feasible
  - Redis interactions where feasible
  - Object store interactions where feasible
- Use consistent service naming conventions
- Include environment and deployment metadata in resource attributes

### Trace Propagation Rules

- All inter-service calls must forward trace context using standard OpenTelemetry propagation
- No custom proprietary propagation mechanism should be introduced unless explicitly justified
- Future services in Go or Python must be able to join the same trace using the same propagation standard

### Extensibility Constraint

The observability stack must not depend on JVM-only assumptions. Any future service written in:

- Go
- Python
- Java
- Kotlin

must be able to:

1. Receive inbound trace context
2. Create child spans
3. Export traces to the same observability backend
4. Appear correctly in Jaeger as part of the same distributed trace

## 6. Scalability and Extensibility Requirements

The POC must be intentionally structured so it can grow into a larger polyglot microservices platform.

### Required Design Principles

- Configuration-driven endpoints and credentials
- Clear separation between business logic and infrastructure code
- Service-specific code isolated from shared observability conventions
- Ability to add new services without redesigning the trace pipeline
- Avoid hardwiring technology choices into service contracts where not necessary

### Future Expansion Expectations

The design should support adding:

- A Go microservice
- A Python microservice
- Additional data stores
- Additional telemetry sinks or collectors
- Centralized configuration or service discovery later

## 7. Recommended Technical Approach

These recommendations are preferred unless a later implementation document provides a strong reason to change them.

### Service Runtime

- Java service: Spring Boot or a similarly mature Java HTTP framework
- Kotlin service: Spring Boot with Kotlin or Ktor
- Prefer ecosystem choices with strong OpenTelemetry support

### Telemetry Pipeline

- Preferred: Application -> OpenTelemetry SDK/agent -> OpenTelemetry Collector -> Jaeger
- Acceptable simplified POC: Application -> Jaeger exporter, if the implementation remains easy to evolve toward a collector-based pipeline

### Local Infrastructure

- Use Docker Compose or an equivalent local orchestration approach
- All infrastructure dependencies should be runnable locally with one command

## 8. Functional Workflow Requirements

The POC should include at least one end-to-end request flow that exercises all major components.

### Minimum Example Flow

1. A client calls the Java service
2. The Java service writes or reads relational data in PostgreSQL
3. The Java service calls the Kotlin service
4. The Kotlin service accesses Redis
5. The Kotlin service writes or reads an object from the S3-compatible store
6. The response returns to the Java service and then to the client
7. The full journey is visible in Jaeger as one distributed trace

## 9. Non-Functional Requirements

### Maintainability

- Repository layout should be understandable by new contributors
- Shared conventions must be documented
- Agents should be able to scaffold new services from documented patterns

### Operability

- Startup instructions must be deterministic
- Services must externalize ports, credentials, and endpoints
- Logs should be readable in local development

### Simplicity

- Keep the POC focused on proving tracing and extensibility
- Do not introduce unnecessary platform complexity

## 10. Repository Structure Expectations

The eventual repository should be organized so agents can easily work in parallel.

### Preferred High-Level Layout

```text
/
  AGENT_REQUIREMENTS.md
  docker-compose.yml
  docs/
  services/
    java-service/
    kotlin-service/
  infrastructure/
    otel/
    jaeger/
    postgres/
    redis/
    object-store/
  shared/
    conventions/
```

This layout is guidance, not a strict constraint, but implementations should preserve clarity and modularity.

## 11. Agent Instructions

Any agent consuming this file should follow these execution principles:

### Phase 1: Foundation

- Define repository structure
- Establish local infrastructure
- Define shared configuration conventions
- Decide the telemetry pipeline implementation

### Phase 2: Service Scaffolding

- Scaffold Java service with Gradle
- Scaffold Kotlin service with Gradle
- Add health endpoints
- Add basic API endpoints needed for the trace demo flow

### Phase 3: Observability

- Add OpenTelemetry instrumentation
- Ensure trace propagation between services
- Export traces to Jaeger
- Validate traces visually

### Phase 4: Infrastructure Integration

- Integrate PostgreSQL
- Integrate Redis
- Integrate S3-compatible object store
- Confirm spans are emitted for dependency interactions where possible

### Phase 5: Validation

- Run the full request chain locally
- Verify a single trace appears in Jaeger
- Verify the trace clearly shows both services and infrastructure interactions
- Document how to add future services in Go or Python

## 12. Acceptance Criteria

The POC is considered successful when all items below are true:

- A Java microservice exists and runs with Gradle
- A Kotlin microservice exists and runs with Gradle
- PostgreSQL, Redis, an S3-compatible object store, and Jaeger run locally
- A request can traverse both services
- Trace context is propagated end-to-end
- Jaeger shows a single distributed trace covering the request journey
- The architecture does not need to be redesigned to add Go or Python services later
- Configuration and structure are documented well enough for agents to continue implementation

## 13. Optional Enhancements

These are not required for the first POC iteration but are desirable follow-ups:

- OpenTelemetry Collector introduction if not included initially
- Metrics and logs correlation
- Trace sampling configuration
- Retry and failure scenarios to visualize error spans
- Service templates for Java, Kotlin, Go, and Python
- Contract tests for trace propagation

## 14. Decision Record Starters

If implementation agents need to make choices, they should record decisions for:

- Java framework selection
- Kotlin framework selection
- Object storage implementation choice
- Collector vs direct Jaeger export
- Database access library choice
- Redis client choice
- Container orchestration layout

## 15. Summary for Agents

Build a local, extensible distributed tracing POC with:

- Java service
- Kotlin service
- Gradle builds
- PostgreSQL
- Redis
- S3-compatible object storage
- OpenTelemetry tracing
- Jaeger visualization

The most important outcome is correct cross-service trace propagation in a design that can be extended later to Go and Python services without reworking the observability model.
