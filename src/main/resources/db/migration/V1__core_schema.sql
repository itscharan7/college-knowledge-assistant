CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(120) NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_users_email ON users (lower(email));

CREATE TABLE documents (
    id                UUID PRIMARY KEY,
    title             VARCHAR(255) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    category          VARCHAR(50)  NOT NULL,
    audience          VARCHAR(20)  NOT NULL,
    description       TEXT,
    storage_key       VARCHAR(100) NOT NULL,
    checksum          VARCHAR(64)  NOT NULL,
    file_size         BIGINT       NOT NULL,
    page_count        INT          NOT NULL DEFAULT 0,
    chunk_count       INT          NOT NULL DEFAULT 0,
    processing_status VARCHAR(20)  NOT NULL,
    processing_error  TEXT,
    version           INT          NOT NULL DEFAULT 1,
    uploaded_by       BIGINT REFERENCES users (id) ON DELETE SET NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_documents_checksum ON documents (checksum);
CREATE INDEX idx_documents_status ON documents (processing_status);
CREATE INDEX idx_documents_audience ON documents (audience);

CREATE TABLE document_processing_logs (
    id               BIGSERIAL PRIMARY KEY,
    document_id      UUID        NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    processing_stage VARCHAR(30) NOT NULL,
    status           VARCHAR(20) NOT NULL,
    message          TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_doc_logs_document ON document_processing_logs (document_id, created_at);

CREATE TABLE conversations (
    id         UUID PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title      VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_conversations_user ON conversations (user_id, updated_at DESC);

CREATE TABLE messages (
    id              BIGSERIAL PRIMARY KEY,
    conversation_id UUID        NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    role            VARCHAR(20) NOT NULL,
    content         TEXT        NOT NULL,
    answerable      BOOLEAN,
    top_score       DOUBLE PRECISION,
    latency_ms      INT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_messages_conversation ON messages (conversation_id, created_at);

CREATE TABLE message_sources (
    id              BIGSERIAL PRIMARY KEY,
    message_id      BIGINT NOT NULL REFERENCES messages (id) ON DELETE CASCADE,
    document_id     UUID REFERENCES documents (id) ON DELETE SET NULL,
    document_title  VARCHAR(255) NOT NULL,
    page_number     INT          NOT NULL,
    chunk_reference VARCHAR(64),
    excerpt         TEXT,
    score           DOUBLE PRECISION
);
CREATE INDEX idx_message_sources_message ON message_sources (message_id);

CREATE TABLE feedback (
    id         BIGSERIAL PRIMARY KEY,
    message_id BIGINT      NOT NULL REFERENCES messages (id) ON DELETE CASCADE,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    rating     SMALLINT    NOT NULL CHECK (rating IN (-1, 1)),
    comment    TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_feedback_message_user UNIQUE (message_id, user_id)
);

-- Admin "unanswered questions" log. Kept separate so admins never need to read private conversations.
CREATE TABLE unanswered_questions (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT REFERENCES users (id) ON DELETE SET NULL,
    question   TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_unanswered_created ON unanswered_questions (created_at DESC);
