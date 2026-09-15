CREATE TABLE link (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(20) NOT NULL UNIQUE,
    original_url    VARCHAR(2048) NOT NULL,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    total_accesses  BIGINT NOT NULL DEFAULT 0
);
