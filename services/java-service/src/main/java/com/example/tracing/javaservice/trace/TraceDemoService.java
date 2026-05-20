package com.example.tracing.javaservice.trace;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
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

    private static final String INSERT_NODE = """
            INSERT INTO node (node_type, node_key, payload, status, metadata, created_at, updated_at)
            VALUES (:nodeType, :nodeKey, :payload, :status, CAST(:metadata AS jsonb), :createdAt, :updatedAt)
            """;
    private static final String INSERT_NODE_ASSOCIATION = """
            INSERT INTO node_associations (parent_node_id, child_node_id, association_type, ordinal, created_at)
            VALUES (:parentNodeId, :childNodeId, :associationType, :ordinal, :createdAt)
            """;
    private static final String UPDATE_NODE_STATUS = """
            UPDATE node
            SET status = :status, updated_at = :updatedAt
            WHERE id = :id
            """;
    private static final String SELECT_TRACE_GRAPH_BY_ID = """
            WITH root AS (
                SELECT id, status, created_at, updated_at
                FROM node
                WHERE id = :id
                  AND node_type = 'TRACE_REQUEST'
            ),
            message_node AS (
                SELECT child.payload AS message
                FROM root
                JOIN node_associations assoc
                    ON assoc.parent_node_id = root.id
                JOIN node child
                    ON child.id = assoc.child_node_id
                WHERE assoc.association_type = 'MESSAGE'
                ORDER BY assoc.ordinal ASC, child.created_at ASC
                LIMIT 1
            ),
            child_metrics AS (
                SELECT
                    COUNT(*) AS child_count,
                    COUNT(*) FILTER (WHERE child.status = 'COMPLETED') AS completed_child_count,
                    MAX(child.updated_at) AS max_child_updated_at
                FROM root
                LEFT JOIN node_associations assoc
                    ON assoc.parent_node_id = root.id
                LEFT JOIN node child
                    ON child.id = assoc.child_node_id
            )
            SELECT
                root.id,
                COALESCE(message_node.message, '') AS message,
                CASE
                    WHEN child_metrics.child_count > 0
                        AND child_metrics.completed_child_count = child_metrics.child_count
                        AND root.status = 'COMPLETED'
                    THEN 'COMPLETED'
                    ELSE root.status
                END AS status,
                root.created_at,
                GREATEST(root.updated_at, COALESCE(child_metrics.max_child_updated_at, root.updated_at)) AS updated_at
            FROM root
            CROSS JOIN child_metrics
            LEFT JOIN message_node ON TRUE
            """;

    private static final RowMapper<TraceGraphSnapshot> TRACE_GRAPH_ROW_MAPPER = (rs, rowNum) ->
            new TraceGraphSnapshot(
                    rs.getLong("id"),
                    rs.getString("message"),
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
        return withSpan("java-service.trace-demo", "trace-demo-service", () -> {
                    Instant now = Instant.now();
                    TraceNodeGraph graph = createTraceGraph(request.message(), now);

                    KotlinTraceResponse downstream = kotlinServiceClient.invokeTraceFlow(
                            new KotlinTraceRequest(String.valueOf(graph.rootNodeId()), request.message())
                    );

                    markGraphCompleted(graph, Instant.now());
                    TraceGraphSnapshot updated = fetchTraceGraph(graph.rootNodeId())
                            .orElseThrow(() -> new IllegalStateException("Trace graph disappeared after persistence"));

                    return new TraceDemoResponse(
                            updated.requestId(),
                            updated.message(),
                            updated.status(),
                            updated.createdAt(),
                            currentTraceId(),
                            downstream
                    );
                });
    }

    @Transactional(readOnly = true)
    public StoredTraceResponse getRequest(long requestId) {
        return withSpan("java-service.fetch-trace-request", "trace-demo-service", () -> {
            TraceGraphSnapshot entity = fetchTraceGraph(requestId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trace request not found"));

            return new StoredTraceResponse(
                    entity.requestId(),
                    entity.message(),
                    entity.status(),
                    entity.createdAt(),
                    entity.updatedAt()
            );
        });
    }

    private TraceNodeGraph createTraceGraph(String message, Instant createdAt) {
        long rootNodeId = insertNode(
                "TRACE_REQUEST",
                "trace-request-" + UUID.randomUUID(),
                null,
                "RECEIVED",
                "{\"role\":\"root\",\"complexity\":\"graph\"}",
                createdAt,
                createdAt
        );
        long messageNodeId = insertNode(
                "TRACE_MESSAGE",
                "trace-message-" + UUID.randomUUID(),
                message,
                "CAPTURED",
                "{\"role\":\"message\"}",
                createdAt,
                createdAt
        );
        long stateNodeId = insertNode(
                "TRACE_STATE",
                "trace-state-" + UUID.randomUUID(),
                "Awaiting downstream processing",
                "PENDING",
                "{\"role\":\"state\"}",
                createdAt,
                createdAt
        );

        createAssociation(rootNodeId, messageNodeId, "MESSAGE", 1, createdAt);
        createAssociation(rootNodeId, stateNodeId, "STATE", 2, createdAt);

        return new TraceNodeGraph(rootNodeId, messageNodeId, stateNodeId);
    }

    private long insertNode(
            String nodeType,
            String nodeKey,
            String payload,
            String status,
            String metadata,
            Instant createdAt,
            Instant updatedAt
    ) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("nodeType", nodeType)
                .addValue("nodeKey", nodeKey)
                .addValue("payload", payload)
                .addValue("status", status)
                .addValue("metadata", metadata)
                .addValue("createdAt", Timestamp.from(createdAt))
                .addValue("updatedAt", Timestamp.from(updatedAt));

        observeSql("INSERT", "node", INSERT_NODE, () ->
                jdbcTemplate.update(INSERT_NODE, parameters, keyHolder, new String[]{"id"})
        );

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to retrieve generated id for node");
        }

        return key.longValue();
    }

    private void createAssociation(long parentNodeId, long childNodeId, String associationType, int ordinal, Instant createdAt) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("parentNodeId", parentNodeId)
                .addValue("childNodeId", childNodeId)
                .addValue("associationType", associationType)
                .addValue("ordinal", ordinal)
                .addValue("createdAt", Timestamp.from(createdAt));

        observeSql("INSERT", "node_associations", INSERT_NODE_ASSOCIATION, () ->
                jdbcTemplate.update(INSERT_NODE_ASSOCIATION, parameters)
        );
    }

    private void markGraphCompleted(TraceNodeGraph graph, Instant updatedAt) {
        updateNodeStatus(graph.stateNodeId(), "COMPLETED", updatedAt);
        updateNodeStatus(graph.rootNodeId(), "COMPLETED", updatedAt);
    }

    private void updateNodeStatus(long nodeId, String status, Instant updatedAt) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("id", nodeId)
                .addValue("status", status)
                .addValue("updatedAt", Timestamp.from(updatedAt));

        observeSql("UPDATE", "node", UPDATE_NODE_STATUS, () ->
                jdbcTemplate.update(UPDATE_NODE_STATUS, parameters)
        );
    }

    private Optional<TraceGraphSnapshot> fetchTraceGraph(long requestId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource().addValue("id", requestId);

        return observeSql("SELECT", "node", SELECT_TRACE_GRAPH_BY_ID, () ->
                jdbcTemplate.query(SELECT_TRACE_GRAPH_BY_ID, parameters, TRACE_GRAPH_ROW_MAPPER)
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

    private <T> T withSpan(String spanName, String component, java.util.function.Supplier<T> action) {
        return Observation.createNotStarted(spanName, observationRegistry)
                .contextualName(spanName)
                .lowCardinalityKeyValue("component", component)
                .observe(action);
    }

    private String currentTraceId() {
        return tracer.currentSpan() != null ? tracer.currentSpan().context().traceId() : null;
    }

    private record TraceNodeGraph(long rootNodeId, long messageNodeId, long stateNodeId) {
    }

    private record TraceGraphSnapshot(Long requestId, String message, String status, Instant createdAt, Instant updatedAt) {
    }
}
