CREATE TABLE IF NOT EXISTS node (
    id BIGSERIAL PRIMARY KEY,
    node_type VARCHAR(64) NOT NULL,
    node_key VARCHAR(128) NOT NULL UNIQUE,
    payload TEXT,
    status VARCHAR(64) NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_node_type_status ON node (node_type, status);
CREATE INDEX IF NOT EXISTS idx_node_created_at ON node (created_at DESC);

CREATE TABLE IF NOT EXISTS node_associations (
    id BIGSERIAL PRIMARY KEY,
    parent_node_id BIGINT NOT NULL REFERENCES node (id) ON DELETE CASCADE,
    child_node_id BIGINT NOT NULL REFERENCES node (id) ON DELETE CASCADE,
    association_type VARCHAR(64) NOT NULL,
    ordinal INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (parent_node_id, child_node_id, association_type)
);

CREATE INDEX IF NOT EXISTS idx_node_associations_parent ON node_associations (parent_node_id, association_type, ordinal);
CREATE INDEX IF NOT EXISTS idx_node_associations_child ON node_associations (child_node_id);

INSERT INTO node (id, node_type, node_key, payload, status, metadata, created_at, updated_at)
SELECT
    tr.id,
    'TRACE_REQUEST',
    'legacy-request-' || tr.id,
    NULL,
    tr.status,
    jsonb_build_object('source', 'trace_requests', 'legacyTraceRequestId', tr.id),
    tr.created_at,
    tr.updated_at
FROM trace_requests tr
ON CONFLICT (id) DO NOTHING;

SELECT setval(
    pg_get_serial_sequence('node', 'id'),
    GREATEST((SELECT COALESCE(MAX(id), 1) FROM node), 1),
    true
);

INSERT INTO node (node_type, node_key, payload, status, metadata, created_at, updated_at)
SELECT
    'TRACE_MESSAGE',
    'legacy-message-' || tr.id,
    tr.client_message,
    'CAPTURED',
    jsonb_build_object('source', 'trace_requests', 'legacyTraceRequestId', tr.id),
    tr.created_at,
    tr.updated_at
FROM trace_requests tr
WHERE NOT EXISTS (
    SELECT 1
    FROM node message_node
    WHERE message_node.node_key = 'legacy-message-' || tr.id
);

INSERT INTO node_associations (parent_node_id, child_node_id, association_type, ordinal, created_at)
SELECT
    tr.id,
    message_node.id,
    'MESSAGE',
    1,
    tr.created_at
FROM trace_requests tr
JOIN node message_node
    ON message_node.node_key = 'legacy-message-' || tr.id
WHERE NOT EXISTS (
    SELECT 1
    FROM node_associations existing
    WHERE existing.parent_node_id = tr.id
      AND existing.child_node_id = message_node.id
      AND existing.association_type = 'MESSAGE'
);
