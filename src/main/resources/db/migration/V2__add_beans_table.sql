CREATE TABLE beans (
    id            UUID PRIMARY KEY,
    bean_name     VARCHAR(255) NOT NULL UNIQUE,
    class_name    VARCHAR(255) NOT NULL,
    jar_file_name VARCHAR(255) NOT NULL,
    jar_data      BYTEA NOT NULL,
    description   TEXT,
    registered    BOOLEAN NOT NULL DEFAULT FALSE,
    uploaded_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    registered_at TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_beans_name ON beans(bean_name);
