ALTER TABLE student_documents
    ADD COLUMN IF NOT EXISTS storage_key VARCHAR(100),
    ADD COLUMN IF NOT EXISTS original_filename VARCHAR(255),
    ADD COLUMN IF NOT EXISTS content_type VARCHAR(100),
    ADD COLUMN IF NOT EXISTS file_size BIGINT;

CREATE UNIQUE INDEX IF NOT EXISTS uq_student_documents_storage_key
    ON student_documents (storage_key)
    WHERE storage_key IS NOT NULL;
