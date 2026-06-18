-- 1. Table: services
CREATE TABLE services (
    id          UUID PRIMARY KEY,
    name        VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by  VARCHAR(50)
);
CREATE INDEX idx_services_name ON services(name);

-- 2. Table: route_versions
CREATE TABLE route_versions (
    id                 UUID PRIMARY KEY,
    service_id         UUID NOT NULL REFERENCES services(id) ON DELETE CASCADE,
    file_name          VARCHAR(255) NOT NULL,
    yaml_content       TEXT NOT NULL,
    version            INTEGER NOT NULL,
    auto_restore       BOOLEAN DEFAULT false,
    uploaded_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by         VARCHAR(50),
    deployed_at        TIMESTAMPTZ,
    route_ids          TEXT,
    original_route_ids TEXT,
    route_descriptions TEXT,
    description        TEXT,
    validate_result    TEXT,
    CONSTRAINT uk_service_version UNIQUE (service_id, version)
);
CREATE INDEX idx_route_versions_service ON route_versions(service_id);

-- 3. Table: routes
CREATE TABLE routes (
    route_id          VARCHAR(255) PRIMARY KEY,
    original_route_id VARCHAR(255) NOT NULL,
    version_id        UUID NOT NULL REFERENCES route_versions(id) ON DELETE CASCADE,
    description       TEXT,
    desired_state     VARCHAR(20) NOT NULL DEFAULT 'Started' CHECK (desired_state IN ('Started', 'Stopped', 'Suspended')),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_routes_original_id ON routes(original_route_id);

-- 4. Table: env_properties
CREATE TABLE env_properties (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key         VARCHAR(255) NOT NULL UNIQUE,
    value       TEXT,
    description TEXT,
    is_secret   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_env_properties_key ON env_properties(key);

-- 5. Table: system_logs
CREATE TABLE system_logs (
    id          VARCHAR(36) PRIMARY KEY,
    type        VARCHAR(50) NOT NULL,
    status      VARCHAR(20) NOT NULL,
    target      VARCHAR(500),
    message     TEXT,
    version     INTEGER,
    version_id  VARCHAR(36),
    file_name   VARCHAR(255),
    timestamp   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    instance_id VARCHAR(100)
);
CREATE INDEX idx_system_logs_type      ON system_logs(type);
CREATE INDEX idx_system_logs_timestamp ON system_logs(timestamp DESC);

-- 6. Table: db_connections
CREATE TABLE db_connections (
    id            UUID PRIMARY KEY,
    db_id         VARCHAR(255) NOT NULL UNIQUE,
    type          VARCHAR(50) NOT NULL,
    host          VARCHAR(255) NOT NULL,
    port          INTEGER NOT NULL,
    database_name VARCHAR(255) NOT NULL,
    username      VARCHAR(255),
    password      TEXT,
    query_options VARCHAR(512),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_db_connections_db_id ON db_connections(db_id);
