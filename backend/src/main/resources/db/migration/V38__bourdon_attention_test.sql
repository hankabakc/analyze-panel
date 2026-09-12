-- T-053A: Bourdon Dikkat Testi Veri Modeli ve Yanıt Tablosu
-- S-021 Kararı: 30 satırlık harf ızgarası yanıtları, blok bazlı skorlar ve süre doğrulaması

ALTER TABLE psych_test_assignments ADD COLUMN IF NOT EXISTS started_at TIMESTAMPTZ NULL;

CREATE TABLE IF NOT EXISTS bourdon_responses (
    id UUID PRIMARY KEY,
    assignment_id UUID NOT NULL REFERENCES psych_test_assignments(id) ON DELETE RESTRICT,
    started_at TIMESTAMPTZ NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL,
    duration_seconds INT NOT NULL,
    marked_cells TEXT NOT NULL,
    b1_correct INT NOT NULL,
    b1_omitted INT NOT NULL,
    b1_incorrect INT NOT NULL,
    b2_correct INT NOT NULL,
    b2_omitted INT NOT NULL,
    b2_incorrect INT NOT NULL,
    b3_correct INT NOT NULL,
    b3_omitted INT NOT NULL,
    b3_incorrect INT NOT NULL,
    total_correct INT NOT NULL,
    total_omitted INT NOT NULL,
    total_incorrect INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_bourdon_responses_assignment UNIQUE (assignment_id)
);

CREATE INDEX IF NOT EXISTS idx_bourdon_responses_assignment_id ON bourdon_responses(assignment_id);
