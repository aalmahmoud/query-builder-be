-- Saved (named) query requests, scoped per entity and per owner (created_by).
-- The QueryRequest is stored as JSON text; the owner is filled by JPA auditing.
CREATE TABLE saved_queries (
    id                 BIGSERIAL     PRIMARY KEY,
    entity_name        VARCHAR(50)   NOT NULL,
    name               VARCHAR(150)  NOT NULL,
    query_json         VARCHAR(4000) NOT NULL,
    created_date       TIMESTAMP     NOT NULL,
    last_modified_date TIMESTAMP,
    created_by         VARCHAR(100),
    last_modified_by   VARCHAR(100)
);

CREATE INDEX idx_saved_queries_entity_owner ON saved_queries(entity_name, created_by);
