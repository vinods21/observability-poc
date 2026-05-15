package com.example.tracing.javaservice.trace;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class TraceDemoService {

    private final TraceRequestRepository repository;
    private final KotlinServiceClient kotlinServiceClient;
    private final ObservationRegistry observationRegistry;
    private final Tracer tracer;

    public TraceDemoService(
            TraceRequestRepository repository,
            KotlinServiceClient kotlinServiceClient,
            ObservationRegistry observationRegistry,
            Tracer tracer
    ) {
        this.repository = repository;
        this.kotlinServiceClient = kotlinServiceClient;
        this.observationRegistry = observationRegistry;
        this.tracer = tracer;
    }

    @Transactional
    public TraceDemoResponse createAndTrace(TraceDemoRequest request) {
        return Observation.createNotStarted("java-service.trace-demo", observationRegistry)
                .lowCardinalityKeyValue("component", "trace-demo-service")
                .observe(() -> {
                    Instant now = Instant.now();
                    TraceRequestEntity saved = repository.save(
                            new TraceRequestEntity(null, request.message(), "RECEIVED", now, now)
                    );

                    KotlinTraceResponse downstream = kotlinServiceClient.invokeTraceFlow(
                            new KotlinTraceRequest(String.valueOf(saved.getId()), saved.getClientMessage())
                    );

                    saved.setStatus("COMPLETED");
                    saved.setUpdatedAt(Instant.now());
                    TraceRequestEntity updated = repository.save(saved);

                    return new TraceDemoResponse(
                            updated.getId(),
                            updated.getClientMessage(),
                            updated.getStatus(),
                            updated.getCreatedAt(),
                            currentTraceId(),
                            downstream
                    );
                });
    }

    @Transactional(readOnly = true)
    public StoredTraceResponse getRequest(long requestId) {
        TraceRequestEntity entity = repository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trace request not found"));

        return new StoredTraceResponse(
                entity.getId(),
                entity.getClientMessage(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private String currentTraceId() {
        return tracer.currentSpan() != null ? tracer.currentSpan().context().traceId() : null;
    }
}
