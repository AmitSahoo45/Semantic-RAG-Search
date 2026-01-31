-- Enable pgvector extension for vector similarity search
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE tenants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    api_key_hash VARCHAR(64) NOT NULL UNIQUE,
    token_limit_per_day BIGINT NOT NULL DEFAULT 100000,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Index for API key lookup during authentication
CREATE INDEX idx_tenants_api_key_hash ON tenants(api_key_hash);

COMMENT ON TABLE tenants IS 'Tenant configuration for multi-tenant isolation';
COMMENT ON COLUMN tenants.api_key_hash IS 'SHA-256 hash of API key, never store plaintext';
COMMENT ON COLUMN tenants.token_limit_per_day IS 'Daily token quota for rate limiting';

CREATE TABLE documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    title VARCHAR(500),
    source_url VARCHAR(2000),
    content TEXT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_documents_tenant_content UNIQUE(tenant_id, content_hash)
);

CREATE INDEX idx_documents_tenant_id ON documents(tenant_id);

CREATE INDEX idx_documents_metadata ON documents USING GIN(metadata);

COMMENT ON TABLE documents IS 'Original documents before chunking';
COMMENT ON COLUMN documents.content_hash IS 'SHA-256 hash for deduplication';
COMMENT ON COLUMN documents.metadata IS 'Flexible JSONB for custom fields like author, tags';

CREATE TABLE chunks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    chunk_index INTEGER NOT NULL,
    content TEXT NOT NULL,
    token_count INTEGER NOT NULL,
    embedding vector(768),
    metadata JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_chunks_document_index UNIQUE(document_id, chunk_index)
);

CREATE INDEX idx_chunks_embedding ON chunks
USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 64);

CREATE INDEX idx_chunks_tenant_id ON chunks(tenant_id);

CREATE INDEX idx_chunks_document_id ON chunks(document_id);

COMMENT ON TABLE chunks IS 'Document chunks with vector embeddings for semantic search';
COMMENT ON COLUMN chunks.embedding IS '768-dim vector from nomic-embed-text model';
COMMENT ON COLUMN chunks.token_count IS 'Token count for LLM context window management';


CREATE TABLE usage_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    operation_type VARCHAR(50) NOT NULL,
    tokens_used INTEGER NOT NULL,
    model_name VARCHAR(100),
    latency_ms INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_usage_records_tenant_date
ON usage_records(tenant_id, created_at DESC);

CREATE INDEX idx_usage_records_operation
ON usage_records(operation_type, created_at DESC);

COMMENT ON TABLE usage_records IS 'Token consumption tracking for billing and rate limiting';
COMMENT ON COLUMN usage_records.operation_type IS 'embedding, search, completion, or rerank';


INSERT INTO tenants (id, name, api_key_hash, token_limit_per_day)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'Development Tenant',
    'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',  -- SHA-256 placeholder
    1000000  -- Higher limit for dev purposes
);

COMMENT ON TABLE tenants IS 'Default dev tenant created - replace api_key_hash in production';

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_tenants_updated_at
    BEFORE UPDATE ON tenants
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trigger_documents_updated_at
    BEFORE UPDATE ON documents
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();