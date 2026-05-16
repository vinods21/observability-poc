package com.example.tracing.javaservice.trace;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TraceDemoService {

    private static final String INSERT_TRACE_REQUEST = """
            INSERT INTO trace_requests (client_message, status, created_at, updated_at)
            VALUES (:message, :status, :createdAt, :updatedAt)
            """;
    private static final String UPDATE_TRACE_REQUEST_STATUS = """
            UPDATE trace_requests
            SET status = :status, updated_at = :updatedAt
            WHERE id = :id
            """;
    private static final String SELECT_TRACE_REQUEST_BY_ID = """
            SELECT id, client_message, status, created_at, updated_at
            FROM trace_requests
            WHERE id = :id
            """;

    private static final RowMapper<TraceRequestEntity> TRACE_REQUEST_ROW_MAPPER = (rs, rowNum) ->
            new TraceRequestEntity(
                    rs.getLong("id"),
                    rs.getString("client_message"),
                    rs.getString("status"),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant()
            );

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final KotlinServiceClient kotlinServiceClient;
    private final ObservationRegistry observationRegistry;
    private final Tracer tracer;

    public TraceDemoService(
            NamedParameterJdbcTemplate jdbcTemplate,
            KotlinServiceClient kotlinServiceClient,
            ObservationRegistry observationRegistry,
            Tracer tracer
    ) {
        this.jdbcTemplate = jdbcTemplate;
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
                    TraceRequestEntity saved = insertTraceRequest(
                            new TraceRequestEntity(null, request.message(), "RECEIVED", now, now)
                    );

                    KotlinTraceResponse downstream = kotlinServiceClient.invokeTraceFlow(
                            new KotlinTraceRequest(String.valueOf(saved.getId()), saved.getClientMessage())
                    );

                    saved.setStatus("COMPLETED");
                    saved.setUpdatedAt(Instant.now());
                    TraceRequestEntity updated = updateTraceRequestStatus(saved);

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
        TraceRequestEntity entity = fetchTraceRequest(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trace request not found"));

        return new StoredTraceResponse(
                entity.getId(),
                entity.getClientMessage(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private TraceRequestEntity insertTraceRequest(TraceRequestEntity entity) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("message", entity.getClientMessage())
                .addValue("status", entity.getStatus())
                .addValue("createdAt", Timestamp.from(entity.getCreatedAt()))
                .addValue("updatedAt", Timestamp.from(entity.getUpdatedAt()));

        observeSql("INSERT", "trace_requests", INSERT_TRACE_REQUEST, () ->
                jdbcTemplate.update(INSERT_TRACE_REQUEST, parameters, keyHolder, new String[]{"id"})
        );

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to retrieve generated id for trace request");
        }

        entity.setId(key.longValue());
        return entity;
    }

    private TraceRequestEntity updateTraceRequestStatus(TraceRequestEntity entity) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("id", entity.getId())
                .addValue("status", entity.getStatus())
                .addValue("updatedAt", Timestamp.from(entity.getUpdatedAt()));

        observeSql("UPDATE", "trace_requests", UPDATE_TRACE_REQUEST_STATUS, () ->
                jdbcTemplate.update(UPDATE_TRACE_REQUEST_STATUS, parameters)
        );

        return entity;
    }

    private Optional<TraceRequestEntity> fetchTraceRequest(long requestId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource().addValue("id", requestId);

        return observeSql("SELECT", "trace_requests", SELECT_TRACE_REQUEST_BY_ID, () ->
                jdbcTemplate.query(SELECT_TRACE_REQUEST_BY_ID, parameters, TRACE_REQUEST_ROW_MAPPER)
                        .stream()
                        .findFirst()
        );
    }

    private <T> T observeSql(String operation, String table, String statement, java.util.function.Supplier<T> action) {
        return Observation.createNotStarted("jdbc.query", observationRegistry)
                .contextualName("jdbc.query")
                .lowCardinalityKeyValue("db.system", "postgresql")
                .lowCardinalityKeyValue("db.operation", operation.toLowerCase())
                .lowCardinalityKeyValue("db.collection.name", table)
                .highCardinalityKeyValue("db.query.text", statement.replaceAll("\\s+", " ").trim())
                .observe(action);
    }

    private String currentTraceId() {
        return tracer.currentSpan() != null ? tracer.currentSpan().context().traceId() : null;
    }
}
