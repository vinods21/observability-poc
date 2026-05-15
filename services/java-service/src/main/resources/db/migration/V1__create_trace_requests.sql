CREATE TABLE IF NOT EXISTS trace_requests (
    id BIGSERIAL PRIMARY KEY,
    client_message VARCHAR(255) NOT NULL,
    status VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
